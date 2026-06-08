package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.client.GuildScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client to populate the guild info screen.
 */
public record S2CGuildDataPacket(
    String guildName,
    String guildTag,
    String masterName,
    List<String> officerNames,
    List<String> memberNames,
    String joinMode,
    String factionId,
    int bannerCount,
    long bread, long cobblestone, long logs, long gold, long iron, long silver,
    List<String> warOpponents,
    long onlineDay
) implements CustomPacketPayload {

    public static final Type<S2CGuildDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "guild_data"));

    public static final StreamCodec<FriendlyByteBuf, S2CGuildDataPacket> STREAM_CODEC =
        StreamCodec.of(S2CGuildDataPacket::encode, S2CGuildDataPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, S2CGuildDataPacket pkt) {
        buf.writeUtf(pkt.guildName);
        buf.writeUtf(pkt.guildTag);
        buf.writeUtf(pkt.masterName);
        writeStringList(buf, pkt.officerNames);
        writeStringList(buf, pkt.memberNames);
        buf.writeUtf(pkt.joinMode);
        buf.writeUtf(pkt.factionId);
        buf.writeInt(pkt.bannerCount);
        buf.writeLong(pkt.bread);
        buf.writeLong(pkt.cobblestone);
        buf.writeLong(pkt.logs);
        buf.writeLong(pkt.gold);
        buf.writeLong(pkt.iron);
        buf.writeLong(pkt.silver);
        writeStringList(buf, pkt.warOpponents);
        buf.writeLong(pkt.onlineDay);
    }

    private static S2CGuildDataPacket decode(FriendlyByteBuf buf) {
        return new S2CGuildDataPacket(
            buf.readUtf(), buf.readUtf(), buf.readUtf(),
            readStringList(buf), readStringList(buf),
            buf.readUtf(), buf.readUtf(),
            buf.readInt(),
            buf.readLong(), buf.readLong(), buf.readLong(),
            buf.readLong(), buf.readLong(), buf.readLong(),
            readStringList(buf),
            buf.readLong()
        );
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(buf.readUtf());
        return list;
    }

    /** Handled on the client thread. */
    public static void handle(S2CGuildDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new GuildScreen(packet)));
    }
}
