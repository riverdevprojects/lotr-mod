package com.lotrmod;

import com.lotrmod.block.ModBlocks;
import com.lotrmod.command.MiddleEarthCommand;
import com.lotrmod.conquest.ConquestSystem;
import com.lotrmod.item.ModCreativeTabs;
import com.lotrmod.item.ModItems;
import com.lotrmod.worldgen.LOTRWorldGen;
import com.lotrmod.worldgen.LandmaskLoader;
import com.lotrmod.worldgen.RegionMapLoader;
import com.lotrmod.worldgen.feature.ModFeatures;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(LOTRMod.MODID)
public class LOTRMod {
    public static final String MODID = "lotrmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LOTRMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Register blocks and items
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Register world generation components
        LOTRWorldGen.register(modEventBus);
        ModFeatures.register(modEventBus);

        // Register conquest system (guilds, claims, war, guards)
        ConquestSystem.register(modEventBus, modContainer);

        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Lord of the Rings Mod initializing...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("LOTR Mod common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("LOTR Mod: Server starting, loading world generation maps...");
        ResourceManager resourceManager = event.getServer().getResourceManager();
        LandmaskLoader.loadLandmask(resourceManager);
        RegionMapLoader.loadRegionMap(resourceManager);
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        // Reload maps when resources are reloaded
        event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller2, executor, executor2) ->
                preparationBarrier.wait(null).thenRunAsync(() -> {
                    LandmaskLoader.loadLandmask(resourceManager);
                    RegionMapLoader.loadRegionMap(resourceManager);
                }, executor2));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MiddleEarthCommand.register(event.getDispatcher());
        ConquestSystem.registerCommands(event.getDispatcher());
        LOGGER.info("Registered /middleearth, /guild, /guilddebug commands");
    }
}
