package com.lotrmod.conquest.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ConquestNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("lotrmod");

        // Guild info screen (S2C)
        registrar.playToClient(S2CGuildDataPacket.TYPE,
            S2CGuildDataPacket.STREAM_CODEC, S2CGuildDataPacket::handle);

        // Fake player screen (S2C)
        registrar.playToClient(S2CFakePlayerScreenPacket.TYPE,
            S2CFakePlayerScreenPacket.STREAM_CODEC, S2CFakePlayerScreenPacket::handle);

        // Fake player action (C2S)
        registrar.playToServer(C2SFakePlayerActionPacket.TYPE,
            C2SFakePlayerActionPacket.STREAM_CODEC, C2SFakePlayerActionPacket::handle);

        // Outpost management screen (S2C)
        registrar.playToClient(S2COutpostScreenPacket.TYPE,
            S2COutpostScreenPacket.STREAM_CODEC, S2COutpostScreenPacket::handle);

        // Outpost action (C2S)
        registrar.playToServer(C2SOutpostActionPacket.TYPE,
            C2SOutpostActionPacket.STREAM_CODEC, C2SOutpostActionPacket::handle);
    }
}
