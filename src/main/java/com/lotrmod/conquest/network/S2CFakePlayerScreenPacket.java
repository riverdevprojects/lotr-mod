package com.lotrmod.conquest.network;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.client.FakePlayerScreen;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** S2C — opens the fake player control screen. */
public record S2CFakePlayerScreenPacket(
    UUID fakeUUID,
    String fakeName,
    boolean inGuild,
    String guildName,
    String guildTag,
    String masterName,
    List<String> wars
) implements CustomPacketPayload {

    public static final Type<S2CFakePlayerScreenPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, "fake_player_screen"));

    public static final StreamCodec<FriendlyByteBuf, S2CFakePlayerScreenPacket> STREAM_CODEC =
        StreamCodec.of(S2CFakePlayerScreenPacket::encode, S2CFakePlayerScreenPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, S2CFakePlayerScreenPacket p) {
        buf.writeUUID(p.fakeUUID);
        buf.writeUtf(p.fakeName);
        buf.writeBoolean(p.inGuild);
        buf.writeUtf(p.guildName);
        buf.writeUtf(p.guildTag);
        buf.writeUtf(p.masterName);
        buf.writeInt(p.wars.size());
        for (String w : p.wars) buf.writeUtf(w);
    }

    private static S2CFakePlayerScreenPacket decode(FriendlyByteBuf buf) {
        UUID uuid      = buf.readUUID();
        String name    = buf.readUtf();
        boolean inG    = buf.readBoolean();
        String gName   = buf.readUtf();
        String gTag    = buf.readUtf();
        String master  = buf.readUtf();
        int wCount     = buf.readInt();
        List<String> wars = new ArrayList<>(wCount);
        for (int i = 0; i < wCount; i++) wars.add(buf.readUtf());
        return new S2CFakePlayerScreenPacket(uuid, name, inG, gName, gTag, master, wars);
    }

    public static void handle(S2CFakePlayerScreenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new FakePlayerScreen(pkt)));
    }

    /** Server-side convenience builder. */
    public static S2CFakePlayerScreenPacket build(UUID fakeUUID, String fakeName, Guild guild, GuildSavedData data) {
        if (guild == null) {
            return new S2CFakePlayerScreenPacket(fakeUUID, fakeName, false, "", "", "", List.of());
        }
        String masterName = guild.masterUUID.toString().substring(0, 8);
        List<String> wars = guild.wars.keySet().stream()
            .map(id -> { Guild g = data.getGuild(id); return g != null ? g.name : id.toString(); })
            .collect(Collectors.toList());
        return new S2CFakePlayerScreenPacket(fakeUUID, fakeName, true,
            guild.name, guild.tag, masterName, wars);
    }
}
