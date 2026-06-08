package com.lotrmod.conquest.event;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.TreasuryResource;
import com.lotrmod.conquest.registry.ConquestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class UpkeepHandler {

    /** 1 in-game hour = 1000 ticks. */
    private static final long TICKS_PER_ONLINE_HOUR = 1000L;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerCount() == 0) return;

        GuildSavedData data = GuildSavedData.get(server);
        data.onlineTicks++;
        data.setDirty();

        long currentDay = data.onlineDay();

        // Dev-score ticking: already handled via block place event in DevelopmentTracker
        for (Guild guild : new ArrayList<>(data.allGuilds())) {
            tickUpkeep(server, data, guild, currentDay);
        }
    }

    private void tickUpkeep(MinecraftServer server, GuildSavedData data, Guild guild, long currentDay) {
        if (guild.bannerCount() == 0) {
            guild.lastUpkeepDay = currentDay;
            return;
        }

        // New day since last charge?
        if (guild.lastUpkeepDay < 0) {
            guild.lastUpkeepDay = currentDay;
            return;
        }
        if (currentDay <= guild.lastUpkeepDay) return;

        // Day has rolled over — charge upkeep
        guild.lastUpkeepDay = currentDay;

        if (guild.canAffordUpkeep()) {
            guild.chargeUpkeep();
            guild.inGracePeriod = false;
            guild.gracePeriodStartDay = -1L;
        } else {
            // Can't afford
            if (!guild.inGracePeriod) {
                guild.inGracePeriod = true;
                guild.gracePeriodStartDay = currentDay;
                notifyGuild(server, guild, "[Upkeep] Your guild treasury cannot cover today's upkeep! " +
                    "You have 1 in-game day grace period before banners start dropping.");
            } else {
                // In grace period — check if expired (1 online day)
                if (currentDay - guild.gracePeriodStartDay >= 1) {
                    // Start dropping outermost banners at 1 per online-hour
                    tryDropOutermostBanner(server, data, guild);
                }
            }
        }
        data.setDirty();
    }

    private void tryDropOutermostBanner(MinecraftServer server, GuildSavedData data, Guild guild) {
        long currentOnlineTicks = data.onlineTicks;
        if (currentOnlineTicks - guild.lastBannerDropTick < TICKS_PER_ONLINE_HOUR) return;
        if (guild.bannerCount() == 0) return;

        guild.lastBannerDropTick = currentOnlineTicks;

        if (guild.canAffordUpkeep()) {
            // Back in the black
            guild.inGracePeriod = false;
            guild.gracePeriodStartDay = -1L;
            notifyGuild(server, guild, "[Upkeep] Your treasury can now cover upkeep. Banner drops have stopped.");
            return;
        }

        List<BlockPos> order = guild.bannersOutermostFirst();
        if (order.isEmpty()) return;

        BlockPos drop = order.get(0);
        guild.removeBanner(drop);
        data.refreshChunkIndex(guild);

        // Destroy the physical banner block in the world
        ServerLevel overworld = server.overworld();
        if (overworld.getBlockState(drop).is(ConquestBlocks.CLAIM_BANNER_BASE.get())) {
            var be = overworld.getBlockEntity(drop);
            if (be instanceof com.lotrmod.conquest.block.ClaimBannerBlockEntity cbe) cbe.guildId = null;
            overworld.removeBlock(drop, false);
        }

        notifyGuild(server, guild,
            "[Upkeep] Your guild cannot afford upkeep! A claim banner at " +
            drop.getX() + "," + drop.getY() + "," + drop.getZ() + " has been lost. " +
            guild.bannerCount() + " banners remain.");
    }

    /** Track development score when a member places a block in claimed territory. */
    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(sp.level() instanceof ServerLevel sl)) return;

        GuildSavedData data = GuildSavedData.get(sl.getServer());
        Guild guild = data.getChunkOwner(new ChunkPos(event.getPos()));
        if (guild != null && guild.isMember(sp.getUUID())) {
            guild.developmentScore++;
            data.setDirty();
        }
    }

    private void notifyGuild(MinecraftServer server, Guild guild, String msg) {
        for (java.util.UUID uuid : guild.memberUUIDs) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }
    }
}
