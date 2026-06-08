package com.lotrmod.conquest.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ConquestNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("lotrmod")
            .playToClient(S2CGuildDataPacket.TYPE, S2CGuildDataPacket.STREAM_CODEC,
                S2CGuildDataPacket::handle);
    }
}
