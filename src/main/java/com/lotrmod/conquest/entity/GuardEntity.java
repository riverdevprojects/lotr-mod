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
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Target players at war with the owning guild
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true,
            this::isWarTarget));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    private boolean isWarTarget(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        if (guildId == null || !(level() instanceof ServerLevel sl)) return false;
        GuildSavedData data = GuildSavedData.get(sl.getServer());
        Guild ownerGuild = data.getGuild(guildId);
        if (ownerGuild == null) return false;
        Guild playerGuild = data.getGuildForPlayer(player.getUUID());
        if (playerGuild == null) return false;
        return ownerGuild.wars.containsKey(playerGuild.id);
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

    public void setHomePos(BlockPos pos)            { this.homePos = pos; }
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
            homePos = new BlockPos(tag.getInt("homeX"), tag.getInt("homeY"), tag.getInt("homeZ"));
        }
    }
}
