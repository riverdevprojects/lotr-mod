package com.lotrmod.conquest;

import com.lotrmod.conquest.command.GuildCommand;
import com.lotrmod.conquest.command.GuildDebugCommand;
import com.lotrmod.conquest.entity.FakePlayerEntity;
import com.lotrmod.conquest.entity.GuardEntity;
import com.lotrmod.conquest.event.ClaimProtectionHandler;
import com.lotrmod.conquest.event.GuildChatHandler;
import com.lotrmod.conquest.event.UpkeepHandler;
import com.lotrmod.conquest.event.WarHandler;
import com.lotrmod.conquest.network.ConquestNetwork;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import com.lotrmod.conquest.registry.ConquestEntities;
import com.lotrmod.conquest.registry.ConquestItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Central registration hub for the conquest system.
 * Call {@link #register} from the main mod constructor.
 */
public class ConquestSystem {

    public static void register(IEventBus modBus, ModContainer container) {
        // Config
        container.registerConfig(ModConfig.Type.SERVER, ConquestConfig.SERVER_SPEC, "lotrmod-conquest-server.toml");

        // Registries
        ConquestBlocks.register(modBus);
        ConquestBlockEntities.register(modBus);
        ConquestItems.register(modBus);
        ConquestEntities.register(modBus);

        // Network (RegisterPayloadHandlersEvent)
        modBus.addListener(ConquestNetwork::register);

        // Entity attributes
        modBus.addListener(ConquestSystem::registerEntityAttributes);

        // NeoForge event bus listeners (server-side gameplay)
        NeoForge.EVENT_BUS.register(new ClaimProtectionHandler());
        NeoForge.EVENT_BUS.register(new UpkeepHandler());
        NeoForge.EVENT_BUS.register(new WarHandler());
        NeoForge.EVENT_BUS.register(new GuildChatHandler());
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ConquestEntities.GUILD_GUARD.get(),       GuardEntity.createAttributes().build());
        event.put(ConquestEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerEntity.createAttributes().build());
    }

    /** Called from RegisterCommandsEvent in LOTRMod. */
    public static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        GuildCommand.register(dispatcher);
        GuildDebugCommand.register(dispatcher);
    }
}
