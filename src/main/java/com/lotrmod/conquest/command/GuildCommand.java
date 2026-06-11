package com.lotrmod.conquest.command;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.*;
import com.lotrmod.conquest.network.S2CGuildDataPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class GuildCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guild")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("tag", StringArgumentType.word())
                        .executes(ctx -> create(ctx,
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "tag"))))))
            .then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> invite(ctx, EntityArgument.getPlayer(ctx, "player")))))
            .then(Commands.literal("kick")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> kick(ctx, EntityArgument.getPlayer(ctx, "player")))))
            .then(Commands.literal("join")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("leave")
                .executes(GuildCommand::leave))
            .then(Commands.literal("promote")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> promote(ctx, EntityArgument.getPlayer(ctx, "player")))))
            .then(Commands.literal("demote")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> demote(ctx, EntityArgument.getPlayer(ctx, "player")))))
            .then(Commands.literal("setjoin")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .executes(ctx -> setJoin(ctx, StringArgumentType.getString(ctx, "mode")))))
            .then(Commands.literal("info")
                .executes(GuildCommand::info))
            .then(Commands.literal("ui")
                .executes(GuildCommand::openUI))
            .then(Commands.literal("disband")
                .executes(GuildCommand::disband))
            .then(Commands.literal("vote")
                .then(Commands.argument("choice", StringArgumentType.word())
                    .executes(ctx -> vote(ctx, StringArgumentType.getString(ctx, "choice")))))
            .then(Commands.literal("war")
                .then(Commands.literal("declare")
                    .then(Commands.argument("guildname", StringArgumentType.greedyString())
                        .executes(ctx -> warDeclare(ctx, StringArgumentType.getString(ctx, "guildname"))))))
            .then(Commands.literal("treasury")
                .then(Commands.literal("deposit")
                    .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> treasuryDeposit(ctx,
                                StringArgumentType.getString(ctx, "resource"),
                                LongArgumentType.getLong(ctx, "amount"))))))
                .then(Commands.literal("withdraw")
                    .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> treasuryWithdraw(ctx,
                                StringArgumentType.getString(ctx, "resource"),
                                LongArgumentType.getLong(ctx, "amount")))))))
        );
    }

    // ── /guild create ────────────────────────────────────────────────────────

    private static int create(CommandContext<CommandSourceStack> ctx, String name, String tag) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        if (!ConquestConfig.SERVER.enabled.get()) return fail(player, "Conquest system is disabled.");

        int maxName = ConquestConfig.SERVER.maxGuildNameLength.get();
        int maxTag  = ConquestConfig.SERVER.maxGuildTagLength.get();
        if (name.length() > maxName) return fail(player, "Name too long (max " + maxName + ").");
        if (tag.length()  > maxTag)  return fail(player, "Tag too long (max " + maxTag + ").");

        GuildSavedData data = GuildSavedData.get(player.getServer());
        if (data.getGuildForPlayer(player.getUUID()) != null)
            return fail(player, "You are already in a guild. Leave first.");
        if (data.isNameTaken(name))
            return fail(player, "A guild named '" + name + "' already exists.");

        Guild guild = new Guild(UUID.randomUUID(), name, tag, player.getUUID());
        data.addGuild(guild);
        player.sendSystemMessage(msg("Guild '" + name + "' [" + tag + "] created! You are the Guild Master."));
        return 1;
    }

    // ── /guild invite ────────────────────────────────────────────────────────

    private static int invite(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.canManage(player.getUUID())) return fail(player, "Only officers and the master can invite players.");
        if (data.getGuildForPlayer(target.getUUID()) != null) return fail(player, target.getName().getString() + " is already in a guild.");

        guild.pendingInvites.add(target.getUUID());
        guild.kickedUUIDs.remove(target.getUUID()); // an invite lifts a prior kick bar
        data.setDirty();
        player.sendSystemMessage(msg("Invited " + target.getName().getString() + " to " + guild.name + "."));
        target.sendSystemMessage(msg("You have been invited to join '" + guild.name + "'! Type /guild join " + guild.name));
        return 1;
    }

    // ── /guild kick ──────────────────────────────────────────────────────────

    private static int kick(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        // Permission: only the master and officers may kick (enforced server-side).
        if (!guild.canManage(player.getUUID())) return fail(player, "Only officers and the master can kick members.");
        if (!guild.isMember(target.getUUID())) return fail(player, target.getName().getString() + " is not in your guild.");
        if (target.getUUID().equals(player.getUUID())) return fail(player, "You cannot kick yourself. Use /guild leave.");
        // Immunity: the master and officers cannot be kicked by anyone.
        if (guild.isMaster(target.getUUID())) return fail(player, "The Guild Master cannot be kicked.");
        if (guild.isOfficer(target.getUUID())) return fail(player, "Officers cannot be kicked. Demote them first.");

        guild.memberUUIDs.remove(target.getUUID());
        guild.officerUUIDs.remove(target.getUUID());
        guild.pendingInvites.remove(target.getUUID());
        guild.kickedUUIDs.add(target.getUUID()); // barred until re-invited
        data.refreshPlayerIndex(guild);
        data.setDirty();
        target.sendSystemMessage(msg("You were kicked from '" + guild.name + "'. You cannot rejoin unless invited."));
        player.sendSystemMessage(msg("Kicked " + target.getName().getString() + " from " + guild.name + "."));
        broadcastGuild(player.getServer(), guild, target.getName().getString() + " was kicked from the guild.", target.getUUID());
        return 1;
    }

    // ── /guild join ──────────────────────────────────────────────────────────

    private static int join(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        if (data.getGuildForPlayer(player.getUUID()) != null) return fail(player, "You are already in a guild.");

        Guild guild = data.getGuildByName(name);
        if (guild == null) return fail(player, "No guild named '" + name + "' found.");

        if (guild.kickedUUIDs.contains(player.getUUID()) && !guild.pendingInvites.contains(player.getUUID()))
            return fail(player, "You were kicked from '" + guild.name + "' and cannot rejoin until an officer invites you.");

        if (guild.joinMode == JoinMode.INVITE_ONLY && !guild.pendingInvites.contains(player.getUUID()))
            return fail(player, "'" + guild.name + "' is invite-only.");

        guild.memberUUIDs.add(player.getUUID());
        guild.pendingInvites.remove(player.getUUID());
        data.refreshPlayerIndex(guild);
        data.setDirty();
        player.sendSystemMessage(msg("You joined '" + guild.name + "'!"));
        broadcastGuild(player.getServer(), guild, player.getName().getString() + " joined the guild!", player.getUUID());
        return 1;
    }

    // ── /guild leave ─────────────────────────────────────────────────────────

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (guild.isMaster(player.getUUID())) return fail(player, "The Guild Master cannot leave. Transfer mastership or disband.");

        guild.memberUUIDs.remove(player.getUUID());
        guild.officerUUIDs.remove(player.getUUID());
        data.refreshPlayerIndex(guild);
        data.setDirty();
        player.sendSystemMessage(msg("You left '" + guild.name + "'."));
        broadcastGuild(player.getServer(), guild, player.getName().getString() + " left the guild.", player.getUUID());
        return 1;
    }

    // ── /guild promote / demote ──────────────────────────────────────────────

    private static int promote(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.isMaster(player.getUUID())) return fail(player, "Only the Guild Master can promote.");
        if (!guild.isMember(target.getUUID())) return fail(player, target.getName().getString() + " is not in your guild.");
        if (guild.isOfficer(target.getUUID())) return fail(player, target.getName().getString() + " is already an officer.");

        guild.officerUUIDs.add(target.getUUID());
        data.setDirty();
        target.sendSystemMessage(msg("You were promoted to officer in '" + guild.name + "'!"));
        player.sendSystemMessage(msg(target.getName().getString() + " is now an officer."));
        return 1;
    }

    private static int demote(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.isMaster(player.getUUID())) return fail(player, "Only the Guild Master can demote.");
        if (!guild.isOfficer(target.getUUID())) return fail(player, target.getName().getString() + " is not an officer.");

        guild.officerUUIDs.remove(target.getUUID());
        data.setDirty();
        target.sendSystemMessage(msg("You were demoted from officer in '" + guild.name + "'."));
        player.sendSystemMessage(msg(target.getName().getString() + " is no longer an officer."));
        return 1;
    }

    // ── /guild setjoin ───────────────────────────────────────────────────────

    private static int setJoin(CommandContext<CommandSourceStack> ctx, String mode) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.isMaster(player.getUUID())) return fail(player, "Only the Guild Master can change join mode.");

        JoinMode jm;
        try { jm = JoinMode.valueOf(mode.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return fail(player, "Invalid mode. Use: open | invite"); }

        guild.joinMode = jm;
        data.setDirty();
        player.sendSystemMessage(msg("Join mode set to " + jm.name().toLowerCase(Locale.ROOT) + "."));
        return 1;
    }

    // ── /guild info ──────────────────────────────────────────────────────────

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;

        player.sendSystemMessage(msg("=== " + guild.name + " [" + guild.tag + "] ==="));
        player.sendSystemMessage(msg("Master: " + resolvePlayerName(player, guild.masterUUID)));
        player.sendSystemMessage(msg("Officers: " + guild.officerUUIDs.size() + "  Members: " + guild.memberUUIDs.size()));
        player.sendSystemMessage(msg("Join mode: " + guild.joinMode.name().toLowerCase(Locale.ROOT)));
        player.sendSystemMessage(msg("Banners: " + guild.bannerCount() + "  Dev score: " + guild.developmentScore));
        player.sendSystemMessage(msg("Treasury — Bread:" + guild.treasury.get(TreasuryResource.BREAD)
            + " Cobble:" + guild.treasury.get(TreasuryResource.COBBLESTONE)
            + " Logs:" + guild.treasury.get(TreasuryResource.LOGS)
            + " Gold:" + guild.treasury.get(TreasuryResource.GOLD)
            + " Iron:" + guild.treasury.get(TreasuryResource.IRON)
            + " Silver:" + guild.treasury.get(TreasuryResource.SILVER)));
        if (!guild.wars.isEmpty()) {
            player.sendSystemMessage(msg("At war with: " + guild.wars.keySet().stream()
                .map(id -> { Guild g = data.getGuild(id); return g != null ? g.name : id.toString(); })
                .collect(Collectors.joining(", "))));
        }
        return 1;
    }

    // ── /guild ui (opens client screen) ─────────────────────────────────────

    private static int openUI(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;

        S2CGuildDataPacket pkt = buildPacket(guild, data, player);
        PacketDistributor.sendToPlayer(player, pkt);
        return 1;
    }

    public static S2CGuildDataPacket buildPacket(Guild guild, GuildSavedData data, ServerPlayer viewer) {
        String masterName = resolvePlayerName(viewer, guild.masterUUID);
        List<String> officerNames = guild.officerUUIDs.stream()
            .map(u -> resolvePlayerName(viewer, u))
            .collect(Collectors.toList());
        // Members excluding master and officers
        List<String> memberNames = guild.memberUUIDs.stream()
            .filter(u -> !u.equals(guild.masterUUID) && !guild.officerUUIDs.contains(u))
            .map(u -> resolvePlayerName(viewer, u))
            .collect(Collectors.toList());
        List<String> wars = guild.wars.keySet().stream()
            .map(id -> { Guild g = data.getGuild(id); return g != null ? g.name : id.toString(); })
            .collect(Collectors.toList());
        return new S2CGuildDataPacket(
            guild.name, guild.tag, masterName,
            officerNames, memberNames,
            guild.joinMode.name().toLowerCase(Locale.ROOT),
            guild.factionId,
            guild.bannerCount(),
            guild.treasury.getOrDefault(TreasuryResource.BREAD, 0L),
            guild.treasury.getOrDefault(TreasuryResource.COBBLESTONE, 0L),
            guild.treasury.getOrDefault(TreasuryResource.LOGS, 0L),
            guild.treasury.getOrDefault(TreasuryResource.GOLD, 0L),
            guild.treasury.getOrDefault(TreasuryResource.IRON, 0L),
            guild.treasury.getOrDefault(TreasuryResource.SILVER, 0L),
            wars,
            data.onlineDay()
        );
    }

    // ── /guild disband ───────────────────────────────────────────────────────

    private static int disband(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.isMaster(player.getUUID())) return fail(player, "Only the Guild Master can initiate a disband vote.");

        if (guild.disbandVoteActive) return fail(player, "A disband vote is already in progress.");
        guild.disbandVoteActive = true;
        guild.disbandVotes.clear();
        guild.disbandVotes.add(player.getUUID());
        data.setDirty();

        String needed = "Master + all officers must vote yes.";
        if (guild.officerUUIDs.isEmpty()) {
            // No officers — master vote alone is enough
            return doDisband(player, data, guild);
        }
        broadcastGuild(player.getServer(), guild,
            "⚠ " + player.getName().getString() + " has started a disband vote! " + needed +
            " Type /guild vote yes to confirm, /guild vote no to cancel.", null);
        return 1;
    }

    // ── /guild vote ──────────────────────────────────────────────────────────

    private static int vote(CommandContext<CommandSourceStack> ctx, String choice) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.disbandVoteActive) return fail(player, "No disband vote is active.");
        if (!guild.isMaster(player.getUUID()) && !guild.isOfficer(player.getUUID()))
            return fail(player, "Only the master and officers vote on disbanding.");

        if (choice.equalsIgnoreCase("no")) {
            guild.disbandVoteActive = false;
            guild.disbandVotes.clear();
            data.setDirty();
            broadcastGuild(player.getServer(), guild, player.getName().getString() + " voted NO — disband cancelled.", null);
            return 1;
        }
        if (!choice.equalsIgnoreCase("yes")) return fail(player, "Use: /guild vote yes | no");

        guild.disbandVotes.add(player.getUUID());
        data.setDirty();

        // Check if all required voters have voted
        Set<UUID> required = new HashSet<>(guild.officerUUIDs);
        required.add(guild.masterUUID);
        if (guild.disbandVotes.containsAll(required)) {
            return doDisband(player, data, guild);
        }
        long remaining = required.stream().filter(u -> !guild.disbandVotes.contains(u)).count();
        broadcastGuild(player.getServer(), guild,
            player.getName().getString() + " voted yes. " + remaining + " more required.", null);
        return 1;
    }

    private static int doDisband(ServerPlayer initiator, GuildSavedData data, Guild guild) {
        String name = guild.name;
        broadcastGuild(initiator.getServer(), guild, "Guild '" + name + "' has been disbanded. All territory is now unclaimed.", null);
        data.removeGuild(guild.id);
        return 1;
    }

    // ── /guild war declare ───────────────────────────────────────────────────

    private static int warDeclare(CommandContext<CommandSourceStack> ctx, String targetName) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.canManage(player.getUUID())) return fail(player, "Only officers and the master can declare war.");

        Guild target = data.getGuildByName(targetName);
        if (target == null) return fail(player, "No guild named '" + targetName + "' found.");
        if (target.id.equals(guild.id)) return fail(player, "You cannot declare war on yourself.");
        if (guild.wars.containsKey(target.id)) return fail(player, "Already at war with '" + target.name + "'.");

        long day = data.onlineDay();
        guild.wars.put(target.id, new WarState(target.id, day));
        target.wars.put(guild.id, new WarState(guild.id, day));
        data.setDirty();

        String declMsg = "⚔ " + guild.name + " has declared WAR on " + target.name + "!";
        String targetMsg = "⚔ " + guild.name + " has declared WAR on your guild! Prepare to defend your territory!";
        broadcastGuild(player.getServer(), guild, declMsg, null);
        broadcastGuild(player.getServer(), target, targetMsg, null);
        return 1;
    }

    // ── /guild treasury ──────────────────────────────────────────────────────

    private static int treasuryDeposit(CommandContext<CommandSourceStack> ctx, String resource, long amount) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;

        TreasuryResource res = parseResource(player, resource);
        if (res == null) return 0;

        guild.treasury.merge(res, amount, Long::sum);
        data.setDirty();
        player.sendSystemMessage(msg("Deposited " + amount + " " + res.displayName() + " into the treasury. (Note: no item check in V1 — honour system.)"));
        return 1;
    }

    private static int treasuryWithdraw(CommandContext<CommandSourceStack> ctx, String resource, long amount) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return 0;

        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = requireGuild(player, data);
        if (guild == null) return 0;
        if (!guild.canManage(player.getUUID())) return fail(player, "Only officers and the master can withdraw.");

        TreasuryResource res = parseResource(player, resource);
        if (res == null) return 0;

        long current = guild.treasury.getOrDefault(res, 0L);
        if (current < amount) return fail(player, "Insufficient " + res.displayName() + " (have " + current + ").");

        guild.treasury.put(res, current - amount);
        data.setDirty();
        player.sendSystemMessage(msg("Withdrew " + amount + " " + res.displayName() + " from the treasury."));
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerPlayer playerOrFail(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return null; }
    }

    private static Guild requireGuild(ServerPlayer player, GuildSavedData data) {
        Guild g = data.getGuildForPlayer(player.getUUID());
        if (g == null) { fail(player, "You are not in a guild."); return null; }
        return g;
    }

    private static TreasuryResource parseResource(ServerPlayer player, String name) {
        try { return TreasuryResource.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            fail(player, "Unknown resource '" + name + "'. Valid: bread, cobblestone, logs, gold, iron, silver");
            return null;
        }
    }

    private static int fail(ServerPlayer player, String msg) {
        player.sendSystemMessage(Component.literal("[Guild] " + msg));
        return 0;
    }

    private static Component msg(String text) {
        return Component.literal("[Guild] " + text);
    }

    private static String resolvePlayerName(ServerPlayer viewer, UUID uuid) {
        ServerPlayer p = viewer.getServer().getPlayerList().getPlayer(uuid);
        if (p != null) return p.getName().getString();
        // Try offline profile
        var profile = viewer.getServer().getProfileCache().get(uuid);
        return profile.map(com.mojang.authlib.GameProfile::getName).orElse(uuid.toString().substring(0, 8) + "...");
    }

    private static void broadcastGuild(net.minecraft.server.MinecraftServer server, Guild guild, String msg, UUID exclude) {
        Component c = Component.literal("[Guild] " + msg);
        for (UUID uuid : guild.memberUUIDs) {
            if (uuid.equals(exclude)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(c);
        }
    }
}
