package com.lotrmod.conquest.block;

import com.lotrmod.conquest.data.ConquestCosts;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.TreasuryResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-side outpost operations (hire / abandon) shared by the outpost GUI and the
 * /guild outpost commands. Each method validates the player against the targeted flag and
 * returns a human-readable result line.
 */
public final class OutpostActions {

    private OutpostActions() {}

    /** Validates that the flag at {@code pos} exists and belongs to the player's guild. */
    public static ClaimBannerBlockEntity resolve(ServerPlayer player, BlockPos pos) {
        if (pos == null || !(player.level() instanceof ServerLevel level)) return null;
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = data.getGuildForPlayer(player.getUUID());
        if (guild == null) return null;
        if (!(level.getBlockEntity(pos) instanceof ClaimBannerBlockEntity be) || be.guildId == null) return null;
        if (!be.guildId.equals(guild.id)) return null;
        return be;
    }

    public static String hire(ServerPlayer player, BlockPos pos, TreasuryResource currency) {
        ServerLevel level = (ServerLevel) player.level();
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = data.getGuildForPlayer(player.getUUID());
        if (guild == null) return "You are not in a guild.";
        ClaimBannerBlockEntity be = resolve(player, pos);
        if (be == null) return "That outpost isn't yours.";
        if (be.getGuardCount() >= ConquestCosts.MAX_GUARDS_PER_OUTPOST)
            return "Garrison is full (" + ConquestCosts.MAX_GUARDS_PER_OUTPOST + " guards).";

        Map<TreasuryResource, Long> cost = ConquestCosts.guardHireCost(currency);
        long amount = cost.get(currency);
        if (!guild.canAfford(cost)) return "Your guild needs " + amount + " " + currency.displayName() + ".";

        guild.charge(cost);
        if (!be.hireGuard(level)) {
            guild.refund(cost);
            return "Could not hire a guard right now.";
        }
        be.addInvested(cost);
        data.setDirty();
        return "Hired a guard for " + amount + " " + currency.displayName()
            + ". Garrison: " + be.getGuardCount() + "/" + ConquestCosts.MAX_GUARDS_PER_OUTPOST + ".";
    }

    public static String abandon(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = data.getGuildForPlayer(player.getUUID());
        if (guild == null) return "You are not in a guild.";
        if (!guild.canManage(player.getUUID()))
            return "Only the guild master or an officer can abandon an outpost.";
        ClaimBannerBlockEntity be = resolve(player, pos);
        if (be == null) return "That outpost isn't yours.";

        Map<TreasuryResource, Long> refund = be.getInvested();
        be.despawnGuards(level);
        guild.refund(refund);
        level.removeBlock(pos, false); // fires onRemove -> unclaims chunks and clears pole/top
        data.setDirty();

        String refundStr = refund.isEmpty() ? "nothing" : refund.entrySet().stream()
            .map(e -> e.getValue() + " " + e.getKey().displayName())
            .collect(Collectors.joining(", "));
        Component note = Component.literal("[Guild] " + player.getName().getString()
            + " abandoned an outpost. Refunded to treasury: " + refundStr + ".");
        for (UUID uuid : guild.memberUUIDs) {
            ServerPlayer p = player.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(note);
        }
        return "Outpost abandoned. Refunded: " + refundStr + ".";
    }
}
