package com.lotrmod.conquest.client;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.registry.ConquestEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = LOTRMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ConquestClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ConquestEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerRenderer::new);
        event.registerEntityRenderer(ConquestEntities.GUILD_GUARD.get(), GuardRenderer::new);
    }
}
