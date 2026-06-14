package com.lotrmod.conquest.event;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.command.GuildCommand;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * Routes chat lines beginning with '!' into the guild-only chat channel, so they are visible
 * only to online members of the sender's guild. Players who are not in a guild fall through to
 * normal chat. Members can also use the /guild chat and /gc commands.
 */
public class GuildChatHandler {

    private static final String PREFIX = "!";

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        String raw = event.getRawText();
        if (raw == null || !raw.startsWith(PREFIX)) return;

        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = data.getGuildForPlayer(player.getUUID());
        if (guild == null) return; // not in a guild — leave it as ordinary chat

        // From here on this is guild chat — keep it off the public channel.
        event.setCanceled(true);
        String message = raw.substring(PREFIX.length()).trim();
        if (message.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Guild Chat] Type a message after '" + PREFIX + "'."));
            return;
        }
        GuildCommand.sendGuildChat(player, guild, message);
    }
}
