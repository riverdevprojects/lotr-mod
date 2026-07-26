package com.lotrmod.worldgen.feature;

import com.lotrmod.LOTRMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom world generation features. */
public final class ModFeatures {
    private ModFeatures() {}

    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, LOTRMod.MODID);

    /** Star-iron meteorite impact site (Materials GDD §5.4). */
    public static final DeferredHolder<Feature<?>, StarIronCraterFeature> STAR_IRON_CRATER =
        FEATURES.register("star_iron_crater",
            () -> new StarIronCraterFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
