package com.lotrmod.conquest.command;

import com.lotrmod.conquest.block.ClaimBannerBlock;
import com.lotrmod.conquest.block.ClaimBannerBlockEntity;
import com.lotrmod.conquest.data.*;
import com.lotrmod.conquest.entity.FakePlayerEntity;
import com.lotrmod.conquest.network.S2CFakePlayerScreenPacket;
import com.lotrmod.conquest.network.S2CGuildDataPacket;
import com.lotrmod.conquest.registry.ConquestBlocks;
import com.lotrmod.conquest.registry.ConquestEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Debug commands — require op level 2.
 *
 * /guilddebug list
 * /guilddebug info <guildname>
 * /guilddebug addresource <guildname> <resource> <amount>
 * /guilddebug removeresource <guildname> <resource> <amount>
 * /guilddebug claim <guildname>          — claim 3×3 at current foot pos
 * /guilddebug unclaim                    — free all chunks at current pos
 * /guilddebug endwar <guild1> <guild2>
 * /guilddebug setday <day>               — jump onlineTicks to that day (× 24000)
 * /guilddebug listclaims <guildname>
 * /guilddebug kick <player>              — remove player from their guild
 * /guilddebug setmaster <guildname> <player>
 * /guilddebug disbandforce <guildname>
 * /guilddebug showchunk                  — who owns the current chunk
 * /guilddebug ui <guildname>             — open guild UI for <guildname> on your screen
 *
 * Fake player commands (testing second player without a second account):
 * /guilddebug spawnfakeplayer <name>     — spawn a fake player NPC at your feet
 * /guilddebug removefakeplayer <name>    — despawn fake player(s) with that name
 * /guilddebug listfakeplayers            — list all fake players within 512 blocks
 * /guilddebug fpscreen <name>            — open the fake player control screen directly
 */
public class GuildDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guilddebug")
            .requires(src -> src.hasPermission(2))

            .then(Commands.literal("list")
                .executes(GuildDebugCommand::listGuilds))

            .then(Commands.literal("info")
                .then(Commands.argument("guildname", StringArgumentType.greedyString())
                    .executes(ctx -> guildInfo(ctx, StringArgumentType.getString(ctx, "guildname")))))

            .then(Commands.literal("addresource")
                .then(Commands.argument("guildname", StringArgumentType.word())
                    .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> addResource(ctx,
                                StringArgumentType.getString(ctx, "guildname"),
                                StringArgumentType.getString(ctx, "resource"),
                                LongArgumentType.getLong(ctx, "amount")))))))

            .then(Commands.literal("removeresource")
                .then(Commands.argument("guildname", StringArgumentType.word())
                    .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> removeResource(ctx,
                                StringArgumentType.getString(ctx, "guildname"),
                                StringArgumentType.getString(ctx, "resource"),
                                LongArgumentType.getLong(ctx, "amount")))))))

            .then(Commands.literal("claim")
                .then(Commands.argument("guildname", StringArgumentType.greedyString())
                    .executes(ctx -> forceClaim(ctx, StringArgumentType.getString(ctx, "guildname")))))

            .then(Commands.literal("unclaim")
                .executes(GuildDebugCommand::forceUnclaim))

            .then(Commands.literal("endwar")
                .then(Commands.argument("guild1", StringArgumentType.word())
                    .then(Commands.argument("guild2", StringArgumentType.greedyString())
                        .executes(ctx -> endWar(ctx,
                            StringArgumentType.getString(ctx, "guild1"),
                            StringArgumentType.getString(ctx, "guild2"))))))

            .then(Commands.literal("setday")
                .then(Commands.argument("day", LongArgumentType.longArg(0))
                    .executes(ctx -> setDay(ctx, LongArgumentType.getLong(ctx, "day")))))

            .then(Commands.literal("listclaims")
                .then(Commands.argument("guildname", StringArgumentType.greedyString())
                    .executes(ctx -> listClaims(ctx, StringArgumentType.getString(ctx, "guildname")))))

            .then(Commands.literal("kick")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> kickPlayer(ctx, EntityArgument.getPlayer(ctx, "player")))))

            .then(Commands.literal("setmaster")
                .then(Commands.argument("guildname", StringArgumentType.word())
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> setMaster(ctx,
                            StringArgumentType.getString(ctx, "guildname"),
                            EntityArgument.getPlayer(ctx, "player"))))))

            .then(Commands.literal("disbandforce")
                .then(Commands.argument("guildname", StringArgumentType.greedyString())
                    .executes(ctx -> disbandForce(ctx, StringArgumentType.getString(ctx, "guildname")))))

            .then(Commands.literal("showchunk")
                .executes(GuildDebugCommand::showChunk))

            .then(Commands.literal("ui")
                .then(Commands.argument("guildname", StringArgumentType.greedyString())
                    .executes(ctx -> openUI(ctx, StringArgumentType.getString(ctx, "guildname")))))

            // ── Fake player commands ──────────────────────────────────────
            .then(Commands.literal("spawnfakeplayer")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> spawnFakePlayer(ctx, StringArgumentType.getString(ctx, "name")))))

            .then(Commands.literal("removefakeplayer")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> removeFakePlayer(ctx, StringArgumentType.getString(ctx, "name")))))

            .then(Commands.literal("listfakeplayers")
                .executes(GuildDebugCommand::listFakePlayers))

            .then(Commands.literal("fpscreen")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> openFakePlayerScreen(ctx, StringArgumentType.getString(ctx, "name")))))
        );
    }

    // ── /guilddebug list ─────────────────────────────────────────────────────

    private static int listGuilds(CommandContext<CommandSourceStack> ctx) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Collection<Guild> guilds = data.allGuilds();
        if (guilds.isEmpty()) { send(ctx, "No guilds exist."); return 1; }
        send(ctx, "=== " + guilds.size() + " guild(s) ===");
        for (Guild g : guilds) {
            send(ctx, "  " + g.name + " [" + g.tag + "]  members:" + g.memberUUIDs.size()
                + "  banners:" + g.bannerCount() + "  wars:" + g.wars.size());
        }
        return 1;
    }

    // ── /guilddebug info <name> ──────────────────────────────────────────────

    private static int guildInfo(CommandContext<CommandSourceStack> ctx, String name) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(name);
        if (g == null) return fail(ctx, "No guild named '" + name + "'.");

        send(ctx, "=== " + g.name + " [" + g.tag + "] (id:" + g.id + ") ===");
        send(ctx, "  Master: " + g.masterUUID);
        send(ctx, "  Officers(" + g.officerUUIDs.size() + "): " + g.officerUUIDs.stream().map(UUID::toString).collect(Collectors.joining(", ")));
        send(ctx, "  Members(" + g.memberUUIDs.size() + "): " + g.memberUUIDs.stream().map(UUID::toString).collect(Collectors.joining(", ")));
        send(ctx, "  JoinMode: " + g.joinMode + "  Faction: " + g.factionId);
        send(ctx, "  Banners: " + g.bannerCount() + "  DevScore: " + g.developmentScore);
        send(ctx, "  LastUpkeepDay: " + g.lastUpkeepDay + "  GracePeriod: " + g.inGracePeriod);
        send(ctx, "  Treasury: Bread=" + g.treasury.get(TreasuryResource.BREAD)
            + " Cobble=" + g.treasury.get(TreasuryResource.COBBLESTONE)
            + " Logs=" + g.treasury.get(TreasuryResource.LOGS)
            + " Gold=" + g.treasury.get(TreasuryResource.GOLD)
            + " Iron=" + g.treasury.get(TreasuryResource.IRON)
            + " Silver=" + g.treasury.get(TreasuryResource.SILVER));
        if (!g.wars.isEmpty()) {
            send(ctx, "  Wars: " + g.wars.keySet().stream().map(id -> {
                Guild opp = data.getGuild(id);
                return (opp != null ? opp.name : id.toString());
            }).collect(Collectors.joining(", ")));
        }
        return 1;
    }

    // ── /guilddebug addresource ──────────────────────────────────────────────

    private static int addResource(CommandContext<CommandSourceStack> ctx, String guildName, String resource, long amount) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        TreasuryResource res = parseRes(ctx, resource);
        if (res == null) return 0;
        g.treasury.merge(res, amount, Long::sum);
        data.setDirty();
        send(ctx, "Added " + amount + " " + res.displayName() + " to " + g.name + ".");
        return 1;
    }

    private static int removeResource(CommandContext<CommandSourceStack> ctx, String guildName, String resource, long amount) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        TreasuryResource res = parseRes(ctx, resource);
        if (res == null) return 0;
        long cur = g.treasury.getOrDefault(res, 0L);
        g.treasury.put(res, Math.max(0, cur - amount));
        data.setDirty();
        send(ctx, "Removed " + Math.min(amount, cur) + " " + res.displayName() + " from " + g.name + ".");
        return 1;
    }

    // ── /guilddebug claim <guildname> ────────────────────────────────────────

    private static int forceClaim(CommandContext<CommandSourceStack> ctx, String guildName) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);

        BlockPos pos = player.blockPosition();
        Set<ChunkPos> chunks = ClaimBannerBlock.getClaimChunks(pos);

        for (ChunkPos cp : chunks) {
            Guild owner = data.getChunkOwner(cp);
            if (owner != null && !owner.id.equals(g.id))
                return fail(ctx, "Chunk " + cp.x + "," + cp.z + " already claimed by '" + owner.name + "'.");
        }

        // Place the claim banner block and register
        ServerLevel level = player.serverLevel();
        level.setBlock(pos, ConquestBlocks.CLAIM_BANNER_BASE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ClaimBannerBlockEntity be) {
            be.initialize(g.id, chunks);
        }
        g.addBanner(pos, chunks);
        data.refreshChunkIndex(g);
        data.setDirty();
        send(ctx, "Force-claimed 81 chunks for '" + g.name + "' at " + pos.toShortString() + ".");
        return 1;
    }

    // ── /guilddebug unclaim ──────────────────────────────────────────────────

    private static int forceUnclaim(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");
        GuildSavedData data = GuildSavedData.get(player.getServer());
        ChunkPos cp = new ChunkPos(player.blockPosition());
        Guild g = data.getChunkOwner(cp);
        if (g == null) return fail(ctx, "Current chunk is not claimed.");

        // Remove all banners that cover this chunk
        List<BlockPos> toRemove = g.banners.entrySet().stream()
            .filter(e -> e.getValue().contains(cp))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        for (BlockPos bp : toRemove) g.removeBanner(bp);
        data.refreshChunkIndex(g);
        data.setDirty();
        send(ctx, "Freed " + toRemove.size() + " banner claim(s) from '" + g.name + "' covering current chunk.");
        return 1;
    }

    // ── /guilddebug endwar ───────────────────────────────────────────────────

    private static int endWar(CommandContext<CommandSourceStack> ctx, String name1, String name2) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g1 = data.getGuildByName(name1);
        Guild g2 = data.getGuildByName(name2);
        if (g1 == null) return fail(ctx, "Guild not found: " + name1);
        if (g2 == null) return fail(ctx, "Guild not found: " + name2);
        // Admin override only. The player-facing way to end a war is the mutual peace
        // treaty flow (/guild peace request|accept|decline), which both sides must agree to.
        g1.wars.remove(g2.id);
        g2.wars.remove(g1.id);
        g1.peaceRequestsReceived.remove(g2.id);
        g2.peaceRequestsReceived.remove(g1.id);
        data.setDirty();
        send(ctx, "War between '" + g1.name + "' and '" + g2.name + "' forcibly ended (admin override).");
        return 1;
    }

    // ── /guilddebug setday ───────────────────────────────────────────────────

    private static int setDay(CommandContext<CommandSourceStack> ctx, long day) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        data.onlineTicks = day * 24000L;
        data.setDirty();
        send(ctx, "Online day set to " + day + " (onlineTicks=" + data.onlineTicks + ").");
        return 1;
    }

    // ── /guilddebug listclaims ───────────────────────────────────────────────

    private static int listClaims(CommandContext<CommandSourceStack> ctx, String guildName) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        send(ctx, g.name + " has " + g.bannerCount() + " banner(s), " + g.claimedChunks.size() + " chunks:");
        for (Map.Entry<BlockPos, Set<ChunkPos>> e : g.banners.entrySet()) {
            String chunkList = e.getValue().stream()
                .map(cp -> cp.x + "," + cp.z)
                .collect(Collectors.joining(" "));
            send(ctx, "  Banner@" + e.getKey().toShortString() + " → " + chunkList);
        }
        return 1;
    }

    // ── /guilddebug kick <player> ────────────────────────────────────────────

    private static int kickPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildForPlayer(target.getUUID());
        if (g == null) return fail(ctx, target.getName().getString() + " is not in any guild.");
        if (g.isMaster(target.getUUID())) return fail(ctx, "Cannot kick the guild master.");
        g.memberUUIDs.remove(target.getUUID());
        g.officerUUIDs.remove(target.getUUID());
        data.refreshPlayerIndex(g);
        data.setDirty();
        target.sendSystemMessage(Component.literal("[Guild] You were removed from '" + g.name + "' by an admin."));
        send(ctx, "Kicked " + target.getName().getString() + " from '" + g.name + "'.");
        return 1;
    }

    // ── /guilddebug setmaster ────────────────────────────────────────────────

    private static int setMaster(CommandContext<CommandSourceStack> ctx, String guildName, ServerPlayer player) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        if (!g.isMember(player.getUUID())) return fail(ctx, player.getName().getString() + " is not in that guild.");
        g.masterUUID = player.getUUID();
        g.officerUUIDs.add(player.getUUID()); // ensure they're an officer too
        data.setDirty();
        player.sendSystemMessage(Component.literal("[Guild] You are now the Guild Master of '" + g.name + "'."));
        send(ctx, player.getName().getString() + " is now the master of '" + g.name + "'.");
        return 1;
    }

    // ── /guilddebug disbandforce ─────────────────────────────────────────────

    private static int disbandForce(CommandContext<CommandSourceStack> ctx, String guildName) {
        GuildSavedData data = GuildSavedData.get(ctx.getSource().getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        String name = g.name;
        data.removeGuild(g.id);
        send(ctx, "Force-disbanded guild '" + name + "'.");
        return 1;
    }

    // ── /guilddebug showchunk ────────────────────────────────────────────────

    private static int showChunk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");
        GuildSavedData data = GuildSavedData.get(player.getServer());
        ChunkPos cp = new ChunkPos(player.blockPosition());
        Guild g = data.getChunkOwner(cp);
        if (g == null) {
            send(ctx, "Chunk " + cp.x + "," + cp.z + " is unclaimed.");
        } else {
            send(ctx, "Chunk " + cp.x + "," + cp.z + " owned by '" + g.name + "' [" + g.tag + "]");
        }
        return 1;
    }

    // ── /guilddebug ui <guildname> ───────────────────────────────────────────

    private static int openUI(CommandContext<CommandSourceStack> ctx, String guildName) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild g = data.getGuildByName(guildName);
        if (g == null) return fail(ctx, "Guild not found: " + guildName);
        S2CGuildDataPacket pkt = GuildCommand.buildPacket(g, data, player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pkt);
        return 1;
    }

    // ── /guilddebug spawnfakeplayer <name> ───────────────────────────────────

    private static int spawnFakePlayer(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");

        ServerLevel level = player.serverLevel();
        FakePlayerEntity entity = ConquestEntities.FAKE_PLAYER_ENTITY.get().create(level);
        if (entity == null) return fail(ctx, "Failed to create entity.");

        UUID fakeUUID = UUID.nameUUIDFromBytes(("fakeplayer:" + name).getBytes());
        entity.setFakeIdentity(name, fakeUUID);
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        level.addFreshEntity(entity);

        send(ctx, "Spawned fake player '" + name + "' (UUID: " + fakeUUID.toString().substring(0, 8) + "...).");
        send(ctx, "Right-click the entity to open its control screen.");
        return 1;
    }

    // ── /guilddebug removefakeplayer <name> ──────────────────────────────────

    private static int removeFakePlayer(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");

        UUID fakeUUID = UUID.nameUUIDFromBytes(("fakeplayer:" + name).getBytes());
        ServerLevel level = player.serverLevel();

        List<FakePlayerEntity> found = level.getEntitiesOfClass(FakePlayerEntity.class,
            player.getBoundingBox().inflate(512),
            e -> name.equals(e.getFakeName()) || fakeUUID.equals(e.getFakeUUID()));

        if (found.isEmpty()) return fail(ctx, "No fake player named '" + name + "' found nearby.");
        found.forEach(e -> e.discard());
        send(ctx, "Removed " + found.size() + " fake player(s) named '" + name + "'.");
        return 1;
    }

    // ── /guilddebug listfakeplayers ───────────────────────────────────────────

    private static int listFakePlayers(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");

        ServerLevel level = player.serverLevel();
        List<FakePlayerEntity> found = level.getEntitiesOfClass(FakePlayerEntity.class,
            player.getBoundingBox().inflate(512));

        if (found.isEmpty()) { send(ctx, "No fake players in range."); return 1; }

        GuildSavedData data = GuildSavedData.get(player.getServer());
        send(ctx, "=== " + found.size() + " fake player(s) ===");
        for (FakePlayerEntity e : found) {
            Guild g = data.getGuildForPlayer(e.getFakeUUID());
            String gInfo = g != null ? "guild: " + g.name : "no guild";
            send(ctx, "  " + e.getFakeName() + "  uuid:" + e.getFakeUUID().toString().substring(0, 8) + "...  " + gInfo);
        }
        return 1;
    }

    // ── /guilddebug fpscreen <name> ───────────────────────────────────────────

    private static int openFakePlayerScreen(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = playerOrFail(ctx);
        if (player == null) return fail(ctx, "Must be run by a player.");

        UUID fakeUUID = UUID.nameUUIDFromBytes(("fakeplayer:" + name).getBytes());
        GuildSavedData data = GuildSavedData.get(player.getServer());
        Guild guild = data.getGuildForPlayer(fakeUUID);
        PacketDistributor.sendToPlayer(player, S2CFakePlayerScreenPacket.build(fakeUUID, name, guild, data));
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerPlayer playerOrFail(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return null; }
    }

    private static TreasuryResource parseRes(CommandContext<CommandSourceStack> ctx, String name) {
        try { return TreasuryResource.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            fail(ctx, "Unknown resource: " + name);
            return null;
        }
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("[GuildDebug] " + msg), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("[GuildDebug] " + msg));
        return 0;
    }
}
