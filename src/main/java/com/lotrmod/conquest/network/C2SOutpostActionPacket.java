package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.block.ClaimBannerBlockEntity;
import com.lotrmod.conquest.block.OutpostActions;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.TreasuryResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S — an outpost-menu button: HIRE_GOLD | HIRE_SILVER | ABANDON. */
public record C2SOutpostActionPacket(BlockPos pos, String action) implements CustomPacketPayload {

    public static final Type<C2SOutpostActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "outpost_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SOutpostActionPacket> STREAM_CODEC =
        StreamCodec.of(C2SOutpostActionPacket::encode, C2SOutpostActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, C2SOutpostActionPacket p) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.action);
    }

    private static C2SOutpostActionPacket decode(FriendlyByteBuf buf) {
        return new C2SOutpostActionPacket(buf.readBlockPos(), buf.readUtf());
    }

    public static void handle(C2SOutpostActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String result;
            boolean abandoned = false;
            switch (pkt.action()) {
                case "HIRE_GOLD"   -> result = OutpostActions.hire(player, pkt.pos(), TreasuryResource.GOLD);
                case "HIRE_SILVER" -> result = OutpostActions.hire(player, pkt.pos(), TreasuryResource.SILVER);
                case "ABANDON"     -> { result = OutpostActions.abandon(player, pkt.pos()); abandoned = true; }
                default            -> result = "Unknown action.";
            }
            player.sendSystemMessage(Component.literal("[Outpost] " + result));

            // Refresh the open screen (unless the outpost is gone).
            GuildSavedData data = GuildSavedData.get(player.getServer());
            ClaimBannerBlockEntity be = abandoned ? null : OutpostActions.resolve(player, pkt.pos());
            if (be != null) {
                Guild guild = data.getGuild(be.guildId);
                PacketDistributor.sendToPlayer(player,
                    S2COutpostScreenPacket.build(pkt.pos(), be, guild, guild != null && guild.canManage(player.getUUID())));
            }
        });
    }
}
