package com.lotrmod.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public class TemplateTreeFeature extends Feature<TemplateTreeFeatureConfig> {

    public TemplateTreeFeature(Codec<TemplateTreeFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<TemplateTreeFeatureConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        RandomSource random = context.random();
        TemplateTreeFeatureConfig config = context.config();

        StructureTemplateManager templateManager = level.getLevel().getServer().getStructureManager();

        Optional<StructureTemplate> templateOpt = templateManager.get(config.template());
        if (templateOpt.isEmpty()) {
            return false;
        }

        StructureTemplate template = templateOpt.get();

        // Check if position is valid for tree placement (on grass or dirt)
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.DIRT) && !below.is(Blocks.COARSE_DIRT)) {
            return false;
        }

        // Random rotation for variety
        Rotation rotation = Rotation.getRandom(random);

        // Set up placement settings - ignore air and structure blocks so the
        // template only places solid blocks (logs, leaves) without carving holes
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);

        // Center the tree horizontally on the placement position
        Vec3i size = template.getSize();
        BlockPos placementPos = pos.offset(-size.getX() / 2, 0, -size.getZ() / 2);

        template.placeInWorld(level, placementPos, placementPos, settings, random, 2);

        return true;
    }
}
