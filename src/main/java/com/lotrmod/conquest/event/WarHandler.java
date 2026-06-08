package com.lotrmod.conquest.event;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.WarState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class WarHandler {

    /** Check for expired wars every 200 ticks (10 seconds) to avoid per-tick overhead. */
    private static final int CHECK_INTERVAL = 200;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerCount() == 0) return;

        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        GuildSavedData data = GuildSavedData.get(server);
        long currentDay = data.onlineDay();

        for (Guild guild : new ArrayList<>(data.allGuilds())) {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, WarState> entry : guild.wars.entrySet()) {
                if (entry.getValue().isExpired(currentDay)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (UUID expiredOpponent : toRemove) {
                guild.wars.remove(expiredOpponent);
                Guild opponent = data.getGuild(expiredOpponent);
                if (opponent != null) opponent.wars.remove(guild.id);

                String msg = "[War] The war between '" + guild.name + "' and '" +
                    (opponent != null ? opponent.name : "?") +
                    "' has expired — 50 in-game days passed with no war banner placed by either side.";
                notifyGuild(server, guild, msg);
                if (opponent != null) notifyGuild(server, opponent, msg);
                data.setDirty();
            }
        }
    }

    private void notifyGuild(MinecraftServer server, Guild guild, String msg) {
        for (UUID uuid : guild.memberUUIDs) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }
    }
}
