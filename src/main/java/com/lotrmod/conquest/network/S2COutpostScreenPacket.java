package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.block.ClaimBannerBlockEntity;
import com.lotrmod.conquest.client.OutpostScreen;
import com.lotrmod.conquest.data.ConquestCosts;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.TreasuryResource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C — opens / refreshes the outpost management screen. */
public record S2COutpostScreenPacket(
    BlockPos pos,
    String guildName,
    int guardCount,
    int maxGuards,
    long hireGoldCost,
    long hireSilverCost,
    long treasuryGold,
    long treasurySilver,
    boolean canManage
) implements CustomPacketPayload {

    public static final Type<S2COutpostScreenPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "outpost_screen"));

    public static final StreamCodec<FriendlyByteBuf, S2COutpostScreenPacket> STREAM_CODEC =
        StreamCodec.of(S2COutpostScreenPacket::encode, S2COutpostScreenPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, S2COutpostScreenPacket p) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.guildName);
        buf.writeInt(p.guardCount);
        buf.writeInt(p.maxGuards);
        buf.writeLong(p.hireGoldCost);
        buf.writeLong(p.hireSilverCost);
        buf.writeLong(p.treasuryGold);
        buf.writeLong(p.treasurySilver);
        buf.writeBoolean(p.canManage);
    }

    private static S2COutpostScreenPacket decode(FriendlyByteBuf buf) {
        return new S2COutpostScreenPacket(
            buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(),
            buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(S2COutpostScreenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof OutpostScreen existing) {
                existing.refresh(pkt);
            } else {
                mc.setScreen(new OutpostScreen(pkt));
            }
        });
    }

    /** Server-side builder from a banner block entity. */
    public static S2COutpostScreenPacket build(BlockPos pos, ClaimBannerBlockEntity be, Guild guild, boolean canManage) {
        return new S2COutpostScreenPacket(
            pos,
            guild != null ? guild.name : "",
            be.getGuardCount(),
            ConquestCosts.MAX_GUARDS_PER_OUTPOST,
            ConquestCosts.GUARD_HIRE_GOLD,
            ConquestCosts.GUARD_HIRE_SILVER,
            guild != null ? guild.treasury.getOrDefault(TreasuryResource.GOLD, 0L) : 0L,
            guild != null ? guild.treasury.getOrDefault(TreasuryResource.SILVER, 0L) : 0L,
            canManage
        );
    }
}
