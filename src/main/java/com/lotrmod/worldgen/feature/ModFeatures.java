package com.lotrmod.worldgen.feature;

import com.lotrmod.LOTRMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, LOTRMod.MODID);

    public static final DeferredHolder<Feature<?>, TemplateTreeFeature> TEMPLATE_TREE =
            FEATURES.register("template_tree", () -> new TemplateTreeFeature(TemplateTreeFeatureConfig.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
