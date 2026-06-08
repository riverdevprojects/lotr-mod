package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.data.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.util.UUID;

/**
 * C2S — the real player submits a guild action to be executed on behalf of
 * the fake player's UUID.
 */
public record C2SFakePlayerActionPacket(
    UUID fakeUUID,
    String fakeName,
    String action,  // CREATE_GUILD | JOIN_GUILD | LEAVE_GUILD | DECLARE_WAR | DEPOSIT | WITHDRAW
    String arg1,    // guild name / target guild / resource
    String arg2,    // guild tag (CREATE only)
    long amount     // DEPOSIT / WITHDRAW only
) implements CustomPacketPayload {

    public static final Type<C2SFakePlayerActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "fake_player_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SFakePlayerActionPacket> STREAM_CODEC =
        StreamCodec.of(C2SFakePlayerActionPacket::encode, C2SFakePlayerActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, C2SFakePlayerActionPacket p) {
        buf.writeUUID(p.fakeUUID);
        buf.writeUtf(p.fakeName);
        buf.writeUtf(p.action);
        buf.writeUtf(p.arg1);
        buf.writeUtf(p.arg2);
        buf.writeLong(p.amount);
    }

    private static C2SFakePlayerActionPacket decode(FriendlyByteBuf buf) {
        return new C2SFakePlayerActionPacket(
            buf.readUUID(), buf.readUtf(), buf.readUtf(),
            buf.readUtf(), buf.readUtf(), buf.readLong());
    }

    public static void handle(C2SFakePlayerActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer realPlayer = (ServerPlayer) ctx.player();
            GuildSavedData data = GuildSavedData.get(realPlayer.getServer());
            String result = executeAction(pkt, data, realPlayer);
            realPlayer.sendSystemMessage(Component.literal("[FakePlayer] " + result));

            // Refresh the screen with updated guild state
            Guild guild = data.getGuildForPlayer(pkt.fakeUUID());
            PacketDistributor.sendToPlayer(realPlayer,
                S2CFakePlayerScreenPacket.build(pkt.fakeUUID(), pkt.fakeName(), guild, data));
        });
    }

    private static String executeAction(C2SFakePlayerActionPacket pkt, GuildSavedData data, ServerPlayer realPlayer) {
        UUID fakeUUID = pkt.fakeUUID();
        String fakeName = pkt.fakeName();

        switch (pkt.action().toUpperCase(Locale.ROOT)) {
            case "CREATE_GUILD": {
                String name = pkt.arg1().trim();
                String tag  = pkt.arg2().trim();
                if (name.isEmpty() || tag.isEmpty()) return "Name and tag cannot be empty.";
                if (data.getGuildForPlayer(fakeUUID) != null) return fakeName + " is already in a guild.";
                if (data.isNameTaken(name)) return "Guild name '" + name + "' is already taken.";
                Guild guild = new Guild(UUID.randomUUID(), name, tag, fakeUUID);
                data.addGuild(guild);
                return "Created guild '" + name + "' [" + tag + "] as " + fakeName + ".";
            }
            case "JOIN_GUILD": {
                String targetName = pkt.arg1().trim();
                if (data.getGuildForPlayer(fakeUUID) != null) return fakeName + " is already in a guild.";
                Guild target = data.getGuildByName(targetName);
                if (target == null) return "Guild '" + targetName + "' not found.";
                // Force-join regardless of join mode (debug)
                target.memberUUIDs.add(fakeUUID);
                target.pendingInvites.remove(fakeUUID);
                data.refreshPlayerIndex(target);
                data.setDirty();
                return fakeName + " joined '" + target.name + "'.";
            }
            case "LEAVE_GUILD": {
                Guild guild = data.getGuildForPlayer(fakeUUID);
                if (guild == null) return fakeName + " is not in a guild.";
                if (guild.isMaster(fakeUUID)) return "Cannot leave as guild master. Use disbandforce or setmaster first.";
                guild.memberUUIDs.remove(fakeUUID);
                guild.officerUUIDs.remove(fakeUUID);
                data.refreshPlayerIndex(guild);
                data.setDirty();
                return fakeName + " left '" + guild.name + "'.";
            }
            case "DECLARE_WAR": {
                String targetName = pkt.arg1().trim();
                Guild guild = data.getGuildForPlayer(fakeUUID);
                if (guild == null) return fakeName + " is not in a guild.";
                // Ensure fake player can manage (make them officer first if not)
                guild.officerUUIDs.add(fakeUUID);
                Guild target = data.getGuildByName(targetName);
                if (target == null) return "Guild '" + targetName + "' not found.";
                if (target.id.equals(guild.id)) return "Cannot declare war on own guild.";
                if (guild.wars.containsKey(target.id)) return "Already at war with '" + target.name + "'.";
                long day = data.onlineDay();
                guild.wars.put(target.id, new WarState(target.id, day));
                target.wars.put(guild.id, new WarState(guild.id, day));
                data.setDirty();
                // Notify target guild members
                for (UUID uid : target.memberUUIDs) {
                    ServerPlayer p = realPlayer.getServer().getPlayerList().getPlayer(uid);
                    if (p != null) p.sendSystemMessage(Component.literal(
                        "⚔ " + guild.name + " (via fake player " + fakeName + ") declared war on your guild!"));
                }
                return fakeName + "'s guild '" + guild.name + "' declared war on '" + target.name + "'.";
            }
            case "DEPOSIT": {
                Guild guild = data.getGuildForPlayer(fakeUUID);
                if (guild == null) return fakeName + " is not in a guild.";
                TreasuryResource res = parseResource(pkt.arg1());
                if (res == null) return "Unknown resource: " + pkt.arg1();
                guild.treasury.merge(res, pkt.amount(), Long::sum);
                data.setDirty();
                return "Deposited " + pkt.amount() + " " + res.displayName() + " into " + guild.name + "'s treasury.";
            }
            case "WITHDRAW": {
                Guild guild = data.getGuildForPlayer(fakeUUID);
                if (guild == null) return fakeName + " is not in a guild.";
                guild.officerUUIDs.add(fakeUUID); // ensure withdraw rights
                TreasuryResource res = parseResource(pkt.arg1());
                if (res == null) return "Unknown resource: " + pkt.arg1();
                long cur = guild.treasury.getOrDefault(res, 0L);
                if (cur < pkt.amount()) return "Not enough " + res.displayName() + " (have " + cur + ").";
                guild.treasury.put(res, cur - pkt.amount());
                data.setDirty();
                return "Withdrew " + pkt.amount() + " " + res.displayName() + " from " + guild.name + "'s treasury.";
            }
            case "MAKE_MASTER": {
                Guild guild = data.getGuildForPlayer(fakeUUID);
                if (guild == null) return fakeName + " is not in a guild.";
                guild.masterUUID = fakeUUID;
                guild.officerUUIDs.add(fakeUUID);
                data.setDirty();
                return fakeName + " is now the Guild Master of '" + guild.name + "'.";
            }
            default:
                return "Unknown action: " + pkt.action();
        }
    }

    private static TreasuryResource parseResource(String name) {
        try { return TreasuryResource.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }
}
