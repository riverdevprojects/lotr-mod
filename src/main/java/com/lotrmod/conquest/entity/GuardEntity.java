package com.lotrmod.conquest.entity;

import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
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
