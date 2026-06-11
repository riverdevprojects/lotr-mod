package com.lotrmod.conquest.entity;

import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Faction guard NPC — defends claimed land.
 *
 * Aggro rules (per design doc):
 *  1. Players at war with the owning guild → aggro on sight.
 *  2. Players that break/place/open containers inside a guard's LoS
 *     → triggered via ClaimProtectionHandler, not this class.
 *  3. Neutral, non-hostile players → ignored.
 */
public class GuardEntity extends Monster {

    /** Horizontal distance from the flag within which a guard always pursues. */
    private static final double SOFT_RANGE = 24.0;
    /** Hard leash: beyond this distance from the flag a guard always abandons its target. */
    private static final double HARD_LEASH = 64.0;
    /** Max guards that may pursue a single player once beyond {@link #SOFT_RANGE}. */
    private static final int MAX_PURSUERS = 3;
    /** Radius of the patrol perimeter around the flag that idle guards return to. */
    private static final int PATROL_RADIUS = 32;

    @Nullable private UUID guildId = null;
    @Nullable private BlockPos homePos = null;
    /** Set by ClaimProtectionHandler when a hostile action is detected. */
    @Nullable public UUID aggroTarget = null;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        // Return to the patrol perimeter around the flag when not engaged.
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Auto-aggro players at war with the owning guild who are inside its territory.
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
            this::canAutoAggro));
        // Guards of guilds at war attack each other on sight (enables holding a captured flag).
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, GuardEntity.class, true,
            this::isEnemyGuard));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    /** A guard belonging to a guild that is at war with this guard's guild. */
    private boolean isEnemyGuard(LivingEntity entity) {
        if (!(entity instanceof GuardEntity other)) return false;
        if (guildId == null || other.getGuildId() == null || guildId.equals(other.getGuildId())) return false;
        if (!(level() instanceof ServerLevel sl)) return false;
        Guild ownerGuild = GuildSavedData.get(sl.getServer()).getGuild(guildId);
        return ownerGuild != null && ownerGuild.wars.containsKey(other.getGuildId());
    }

    /** Acquisition rule: a war enemy inside the owning guild's territory and within the pursuit budget. */
    private boolean canAutoAggro(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        if (guildId == null || !(level() instanceof ServerLevel sl)) return false;
        GuildSavedData data = GuildSavedData.get(sl.getServer());
        Guild ownerGuild = data.getGuild(guildId);
        if (ownerGuild == null) return false;
        Guild playerGuild = data.getGuildForPlayer(player.getUUID());
        if (playerGuild == null || !ownerGuild.wars.containsKey(playerGuild.id)) return false;
        // Bug 4: never engage outside our own claimed territory.
        if (!ownerGuild.claimedChunks.contains(new ChunkPos(player.blockPosition()))) return false;
        // Bug 5: respect the pursuit budget so guards aren't all kited away.
        return withinPursuitBudget(player);
    }

    /**
     * Enforces leash, territory and max-pursuer rules each tick. This catches every target source
     * (war auto-aggro, intrusion {@link #aggroOn}, and retaliation) — not just acquisition.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target == null || homePos == null) return;

        // Bug 5: a guard dragged past the hard leash always gives up and heads home.
        if (homeStrayDistSq() > HARD_LEASH * HARD_LEASH) {
            forgetTarget();
            return;
        }

        if (target instanceof Player && guildId != null && level() instanceof ServerLevel sl) {
            GuildSavedData data = GuildSavedData.get(sl.getServer());
            Guild ownerGuild = data.getGuild(guildId);
            if (ownerGuild != null) {
                // Bug 4: drop chase the instant the player leaves our claimed territory.
                if (!ownerGuild.claimedChunks.contains(new ChunkPos(target.blockPosition()))) {
                    forgetTarget();
                    return;
                }
                // Bug 5: excess guards peel off beyond the soft range, keeping the closest in.
                if (!withinPursuitBudget((Player) target)) {
                    forgetTarget();
                }
            }
        }
    }

    private void forgetTarget() {
        setTarget(null);
        setLastHurtByMob(null);
    }

    /** Horizontal squared distance from this guard's current position to its flag. */
    private double homeStrayDistSq() {
        if (homePos == null) return 0.0;
        double dx = getX() - (homePos.getX() + 0.5);
        double dz = getZ() - (homePos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /**
     * True if this guard is allowed to keep pursuing the given player. Guards within {@link #SOFT_RANGE}
     * of their flag always may; beyond it, only the {@link #MAX_PURSUERS} guards closest to a flag pursue,
     * so a player can't kite the entire garrison away.
     */
    private boolean withinPursuitBudget(Player player) {
        double myStray = homeStrayDistSq();
        if (myStray <= SOFT_RANGE * SOFT_RANGE) return true;
        if (!(level() instanceof ServerLevel sl)) return true;
        java.util.List<GuardEntity> peers = sl.getEntitiesOfClass(GuardEntity.class,
            getBoundingBox().inflate(HARD_LEASH * 2),
            g -> g != this && guildId != null && guildId.equals(g.getGuildId()) && g.getTarget() == player);
        int closer = 1; // counting this guard
        for (GuardEntity g : peers) {
            if (g.homeStrayDistSq() < myStray) closer++;
        }
        return closer <= MAX_PURSUERS;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ARMOR, 4.0);
    }

    public void setGuildId(@Nullable UUID guildId) { this.guildId = guildId; }
    public @Nullable UUID getGuildId()              { return guildId; }

    public void setHomePos(BlockPos pos)            {
        this.homePos = pos;
        // Anchor the guard's wandering and return-home behaviour to the flag.
        restrictTo(pos, PATROL_RADIUS);
    }
    public @Nullable BlockPos getHomePos()          { return homePos; }

    /** Called by ClaimProtectionHandler to trigger aggro on a specific player. */
    public void aggroOn(Player player) {
        this.setTarget(player);
    }

    /**
     * Equipment system (#10): a member of the owning guild right-clicks the guard with an armor
     * piece or weapon to equip it (swapping out any current piece), or sneak + empty hand to
     * recover all gear. Worn equipment renders on the guard and its attribute modifiers (armor,
     * attack damage) apply automatically.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        // Only members of the owning guild may equip this guard.
        if (guildId == null) return InteractionResult.PASS;
        Guild owner = GuildSavedData.get(sp.getServer()).getGuild(guildId);
        if (owner == null || !owner.isMember(sp.getUUID())) {
            sp.sendSystemMessage(Component.literal("[Guild] This guard isn't yours to equip."));
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                returnAllGear(sp);
            } else {
                sendLoadout(sp);
            }
            return InteractionResult.CONSUME;
        }

        EquipmentSlot slot = slotForItem(held);
        ItemStack current = getItemBySlot(slot);
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);
        setItemSlot(slot, toEquip);
        setDropChance(slot, 0.0f); // gear is guild property — not dropped randomly on death
        held.shrink(1);
        if (!current.isEmpty() && !player.addItem(current)) {
            player.drop(current, false);
        }
        sp.sendSystemMessage(Component.literal("[Guild] Equipped " + toEquip.getHoverName().getString()
            + " on the guard (" + slot.getName() + ")."));
        return InteractionResult.CONSUME;
    }

    private static EquipmentSlot slotForItem(ItemStack stack) {
        Equipable eq = Equipable.get(stack);
        if (eq != null) {
            EquipmentSlot s = eq.getEquipmentSlot();
            if (s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST
                || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET) {
                return s;
            }
        }
        return EquipmentSlot.MAINHAND;
    }

    private void returnAllGear(ServerPlayer player) {
        boolean any = false;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = getItemBySlot(s);
            if (!st.isEmpty()) {
                any = true;
                ItemStack copy = st.copy();
                if (!player.addItem(copy)) player.drop(copy, false);
                setItemSlot(s, ItemStack.EMPTY);
            }
        }
        player.sendSystemMessage(Component.literal(any
            ? "[Guild] Recovered all of the guard's gear."
            : "[Guild] This guard has no gear equipped."));
    }

    private void sendLoadout(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Guild] Guard loadout (right-click with gear to equip, sneak + empty hand to recover):"));
        for (EquipmentSlot s : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
            ItemStack st = getItemBySlot(s);
            String name = st.isEmpty() ? "(empty)" : st.getHoverName().getString();
            player.sendSystemMessage(Component.literal("  " + s.getName() + ": " + name));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (guildId != null) tag.putUUID("guardGuild", guildId);
        if (homePos  != null) {
            tag.putInt("homeX", homePos.getX());
            tag.putInt("homeY", homePos.getY());
            tag.putInt("homeZ", homePos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("guardGuild")) guildId = tag.getUUID("guardGuild");
        if (tag.contains("homeX")) {
            setHomePos(new BlockPos(tag.getInt("homeX"), tag.getInt("homeY"), tag.getInt("homeZ")));
        }
    }
}
