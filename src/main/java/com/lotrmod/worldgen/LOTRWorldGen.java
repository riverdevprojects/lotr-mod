package com.lotrmod.worldgen;

import com.lotrmod.LOTRMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration for custom world generation components
 */
public class LOTRWorldGen {
    // Deferred registers for chunk generators and biome sources
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, LOTRMod.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, LOTRMod.MODID);

    // Register our custom chunk generator
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<MiddleEarthChunkGenerator>> MIDDLEEARTH_CHUNK_GENERATOR =
            CHUNK_GENERATORS.register("middleearth", () -> MiddleEarthChunkGenerator.CODEC);

    // Register our custom biome source
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<MiddleEarthBiomeSource>> MIDDLEEARTH_BIOME_SOURCE =
            BIOME_SOURCES.register("middleearth", () -> MiddleEarthBiomeSource.CODEC);

    public static void register(IEventBus modEventBus) {
        // Register chunk generators and biome sources
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);

        LOTRMod.LOGGER.info("Registered LOTR world generation components");
    }
}
