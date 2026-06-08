package com.lotrmod.conquest.block;

import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class WarBannerBlockEntity extends BlockEntity {

    /** Siege window = 1 in-game day = 24 000 online ticks. */
    private static final long SIEGE_WINDOW_TICKS = 24_000L;

    public UUID attackerGuildId = null;
    public UUID defenderGuildId = null;
    /** ChunkPos of the target 3×3 claim the war banner was placed in. */
    public ChunkPos targetChunk = null;
    /** onlineTicks value at siege start. */
    public long siegeStartOnlineTick = 0L;
    private boolean active = false;

    public WarBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ConquestBlockEntities.WAR_BANNER.get(), pos, state);
    }

    public boolean isActive() { return active; }

    public void initialize(UUID attackerGuildId, UUID defenderGuildId, ChunkPos targetChunk, long startOnlineTick) {
        this.attackerGuildId      = attackerGuildId;
        this.defenderGuildId      = defenderGuildId;
        this.targetChunk          = targetChunk;
        this.siegeStartOnlineTick = startOnlineTick;
        this.active               = true;
        setChanged();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, WarBannerBlockEntity be) {
        if (!be.active || be.attackerGuildId == null) return;

        GuildSavedData data = GuildSavedData.get(level.getServer());
        long elapsed = data.onlineTicks - be.siegeStartOnlineTick;
        if (elapsed < SIEGE_WINDOW_TICKS) return;

        // Siege succeeded — transfer the claim
        Guild attacker = data.getGuild(be.attackerGuildId);
        Guild defender = data.getGuild(be.defenderGuildId);

        if (attacker == null || defender == null) {
            level.removeBlock(pos, false);
            return;
        }

        // Find which defender banner covers targetChunk
        BlockPos defBannerPos = null;
        for (var entry : defender.banners.entrySet()) {
            if (entry.getValue().contains(be.targetChunk)) {
                defBannerPos = entry.getKey();
                break;
            }
        }

        if (defBannerPos != null) {
            java.util.Set<ChunkPos> transferredChunks = defender.banners.get(defBannerPos);
            // Transfer chunks to attacker
            if (transferredChunks != null) {
                attacker.addBanner(defBannerPos, transferredChunks);
                defender.removeBanner(defBannerPos);
                // Remove the original claim banner block from world
                if (level.getBlockState(defBannerPos).is(
                        ConquestBlocks.CLAIM_BANNER_BASE.get())) {
                    BlockEntity defBE = level.getBlockEntity(defBannerPos);
                    if (defBE instanceof ClaimBannerBlockEntity cbe) cbe.guildId = null;
                    level.removeBlock(defBannerPos, false);
                }
                data.refreshChunkIndex(attacker);
                data.refreshChunkIndex(defender);
            }
        }

        be.active = false;
        data.setDirty();

        // Remove this war banner
        level.removeBlock(pos, false);

        // Notify both guilds
        String victMsg = "[WAR] Victory! '" + attacker.name + "' has captured territory from '" + defender.name + "'!";
        String defMsg  = "[WAR] '" + attacker.name + "' has captured your territory! Your claim banner was destroyed.";
        broadcast(level, attacker, victMsg);
        broadcast(level, defender, defMsg);
    }

    private static void broadcast(ServerLevel level, Guild guild, String msg) {
        for (UUID uuid : guild.memberUUIDs) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(Component.literal(msg));
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("active", active);
        if (attackerGuildId != null) tag.putUUID("attacker", attackerGuildId);
        if (defenderGuildId != null) tag.putUUID("defender", defenderGuildId);
        if (targetChunk    != null) tag.putLong("targetChunk", targetChunk.toLong());
        tag.putLong("siegeStart", siegeStartOnlineTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        active = tag.getBoolean("active");
        if (tag.hasUUID("attacker"))    attackerGuildId      = tag.getUUID("attacker");
        if (tag.hasUUID("defender"))    defenderGuildId      = tag.getUUID("defender");
        if (tag.contains("targetChunk")) targetChunk         = new ChunkPos(tag.getLong("targetChunk"));
        siegeStartOnlineTick = tag.getLong("siegeStart");
    }
}
