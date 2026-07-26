package com.lotrmod.worldgen.feature;

import com.lotrmod.block.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Star-iron meteorite impact site (Materials GDD §5.4). Star-iron does not generate as a vein — it
 * arrives as a surface crater that players hunt as a landmark, so this deliberately leaves a visible
 * scar rather than a buried vein.
 *
 * <p>Per §5.4: a crater 7–15 blocks across of scorched/displaced terrain, with 2–5
 * {@link ModBlocks#STAR_IRON_BLOCK} at the impact point buried 1–3 blocks deep. Water and dense
 * forest are suppressed here rather than in placement, because both need to inspect the terrain that
 * is actually there.
 */
public class StarIronCraterFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 3;  // 7 blocks across
    private static final int MAX_RADIUS = 7;  // 15 blocks across
    /** Bowl depth as a fraction of radius — shallow enough to read as a dish, not a shaft. */
    private static final double DEPTH_RATIO = 0.6;
    /** Above this many logs/leaves in the footprint the site counts as dense forest (§5.4). */
    private static final int FOREST_SUPPRESSION_THRESHOLD = 12;

    public StarIronCraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int radius = Mth.nextInt(random, MIN_RADIUS, MAX_RADIUS);
        int centreTop = topSolidY(level, origin.getX(), origin.getZ());

        if (isSubmerged(level, origin.getX(), origin.getZ(), centreTop) || isDenseForest(level, origin, radius)) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int centreDepth = (int) Math.round(radius * DEPTH_RATIO);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                // Ragged edge, so the rim does not read as a perfect circle.
                if (distance > radius - 0.5 + random.nextDouble()) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = topSolidY(level, x, z);
                int depth = (int) Math.round((1.0 - distance / radius) * radius * DEPTH_RATIO);
                int floorY = top - depth;

                // Excavate the bowl.
                for (int y = top; y > floorY; y--) {
                    cursor.set(x, y, z);
                    setBlock(level, cursor, Blocks.AIR.defaultBlockState());
                }

                cursor.set(x, floorY, z);
                setBlock(level, cursor, scorchedState(random, distance, radius));

                // Displaced material thrown up as a low rim — this is what makes it visible from afar.
                if (distance > radius * 0.8 && random.nextInt(3) == 0) {
                    cursor.set(x, top + 1, z);
                    if (level.getBlockState(cursor).isAir()) {
                        setBlock(level, cursor, Blocks.COARSE_DIRT.defaultBlockState());
                    }
                }
            }
        }

        placeCore(level, random, origin, centreTop - centreDepth, cursor);
        return true;
    }

    /** 2–5 star-iron blocks at the impact point, buried 1–3 blocks below the crater floor (§5.4). */
    private void placeCore(WorldGenLevel level, RandomSource random, BlockPos origin,
                           int craterFloorY, BlockPos.MutableBlockPos cursor) {
        BlockState starIron = ModBlocks.STAR_IRON_BLOCK.get().defaultBlockState();
        int count = Mth.nextInt(random, 2, 5);
        for (int i = 0; i < count; i++) {
            int x = origin.getX() + random.nextInt(3) - 1;
            int z = origin.getZ() + random.nextInt(3) - 1;
            int y = craterFloorY - Mth.nextInt(random, 1, 3);
            cursor.set(x, y, z);
            if (!level.isOutsideBuildHeight(cursor) && !level.getBlockState(cursor).isAir()) {
                setBlock(level, cursor, starIron);
            }
        }
    }

    /** Blackstone at the point of impact, fading to scorched earth at the rim. */
    private static BlockState scorchedState(RandomSource random, double distance, int radius) {
        boolean nearCentre = distance < radius * 0.4;
        if (nearCentre) {
            return random.nextInt(4) == 0
                ? Blocks.MAGMA_BLOCK.defaultBlockState()
                : Blocks.BLACKSTONE.defaultBlockState();
        }
        return random.nextBoolean()
            ? Blocks.BLACKSTONE.defaultBlockState()
            : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** Y of the topmost solid block in a column. */
    private static int topSolidY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    private static boolean isSubmerged(WorldGenLevel level, int x, int z, int topSolid) {
        return !level.getFluidState(new BlockPos(x, topSolid + 1, z)).isEmpty();
    }

    private static boolean isDenseForest(WorldGenLevel level, BlockPos origin, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int woody = 0;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = topSolidY(level, x, z);
                // Canopy sits above the ground column, so sample upward from the surface.
                for (int y = top + 1; y <= top + 12; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        woody++;
                        if (woody > FOREST_SUPPRESSION_THRESHOLD) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
