package com.lotrmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record TemplateTreeFeatureConfig(ResourceLocation template) implements FeatureConfiguration {
    public static final Codec<TemplateTreeFeatureConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("template").forGetter(TemplateTreeFeatureConfig::template)
            ).apply(instance, TemplateTreeFeatureConfig::new)
    );
}
