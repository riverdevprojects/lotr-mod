package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.command.GuildCommand;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.TreasuryResource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** C2S — a guild-screen button. action DEPOSIT carries a resource name; deposits all the player holds. */
public record C2SGuildActionPacket(String action, String resource) implements CustomPacketPayload {

    public static final Type<C2SGuildActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "guild_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SGuildActionPacket> STREAM_CODEC =
        StreamCodec.of(C2SGuildActionPacket::encode, C2SGuildActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, C2SGuildActionPacket p) {
        buf.writeUtf(p.action);
        buf.writeUtf(p.resource);
    }

    private static C2SGuildActionPacket decode(FriendlyByteBuf buf) {
        return new C2SGuildActionPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SGuildActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild guild = data.getGuildForPlayer(player.getUUID());
            if (guild == null) return;

            if ("DEPOSIT".equals(pkt.action())) {
                // Only masters/officers manage the treasury via the guild screen.
                if (!guild.canManage(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("[Guild] Only the master or an officer can deposit here."));
                    return;
                }
                TreasuryResource res;
                try { res = TreasuryResource.valueOf(pkt.resource().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return; }
                player.sendSystemMessage(Component.literal(
                    "[Guild] " + GuildCommand.depositResource(player, res, Long.MAX_VALUE)));
            }

            // Refresh the open guild screen with updated treasury.
            PacketDistributor.sendToPlayer(player, GuildCommand.buildPacket(guild, data, player));
        });
    }
}
