package com.lotrmod.conquest.entity;

import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.network.S2CFakePlayerScreenPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Debug-only fake player NPC.
 * Has its own stable UUID and name that participates in the guild system.
 * Right-clicking opens FakePlayerScreen, allowing the real player to
 * execute guild commands (create guild, declare war, etc.) on behalf of
 * this fake identity.
 */
public class FakePlayerEntity extends PathfinderMob {

    /** The UUID used as this fake player's identity in guild operations. */
    private UUID fakeUUID = UUID.randomUUID();
    private String fakeName = "FakePlayer";

    public FakePlayerEntity(EntityType<? extends FakePlayerEntity> type, Level level) {
        super(type, level);
        setCustomNameVisible(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0); // stationary
    }

    public void setFakeIdentity(String name, UUID uuid) {
        this.fakeName = name;
        this.fakeUUID = uuid;
        setCustomName(Component.literal("§e[FP] §f" + name));
    }

    public UUID getFakeUUID()  { return fakeUUID; }
    public String getFakeName(){ return fakeName; }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            GuildSavedData data = GuildSavedData.get(sp.getServer());
            Guild guild = data.getGuildForPlayer(fakeUUID);
            PacketDistributor.sendToPlayer(sp,
                S2CFakePlayerScreenPacket.build(fakeUUID, fakeName, guild, data));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putUUID("fakeUUID", fakeUUID);
        tag.putString("fakeName", fakeName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("fakeUUID")) fakeUUID = tag.getUUID("fakeUUID");
        if (tag.contains("fakeName")) fakeName = tag.getString("fakeName");
        setCustomName(Component.literal("§e[FP] §f" + fakeName));
        setCustomNameVisible(true);
    }

    /** Fake players should not despawn. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }
}
