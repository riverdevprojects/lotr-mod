package com.lotrmod.worldgen;

import com.lotrmod.LOTRMod;
import com.lotrmod.block.ModBlocks;
import com.lotrmod.worldgen.biome.LOTRBiome;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom chunk generator that creates terrain based on the Middle-earth landmask
 * 
 * FIXED VERSION - Eliminates vertical cliffs by blending actual terrain heights
 * instead of just biome modifiers
 */
public class MiddleEarthChunkGenerator extends ChunkGenerator {
    public static final MapCodec<MiddleEarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(gen -> gen.settings)
            ).apply(instance, MiddleEarthChunkGenerator::new)
    );

    private final Holder<NoiseGeneratorSettings> settings;
    private final PerlinSimplexNoise coastlineNoise;
    private final PerlinSimplexNoise terrainNoise;
    private final PerlinSimplexNoise detailNoise;

    // Additional noise generators for multi-scale coastline generation
    private final PerlinSimplexNoise largeScaleCoastNoise;   // Major coastal shapes
    private final PerlinSimplexNoise mediumScaleCoastNoise;  // Bays and peninsulas
    private final PerlinSimplexNoise smallScaleCoastNoise;   // Detailed coastline jaggedness

    // Sea level for the world
    private static final int SEA_LEVEL = 63;

    // ========================================
    // TERRAIN GENERATION TUNING PARAMETERS
    // ========================================

    private static final double LANDMASK_INFLUENCE_STRENGTH = 0.6;
    private static final double LANDMASK_HEIGHT_BIAS = 15.0;
    private static final double OCEAN_BRIGHTNESS_THRESHOLD = 220.0;

    // ========================================
    // MULTI-SCALE NOISE PARAMETERS
    // ========================================

    private static final double LARGE_SCALE_WAVELENGTH = 1200.0;
    private static final double LARGE_SCALE_AMPLITUDE = 25.0;

    private static final double MEDIUM_SCALE_WAVELENGTH = 300.0;
    private static final double MEDIUM_SCALE_AMPLITUDE = 15.0;

    private static final double SMALL_SCALE_WAVELENGTH = 60.0;
    private static final double SMALL_SCALE_AMPLITUDE = 4.0;

    private static final double DETAIL_SCALE_WAVELENGTH = 20.0;
    private static final double DETAIL_SCALE_AMPLITUDE = 1.5;

    public MiddleEarthChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;

        RandomSource random = RandomSource.create(12345);
        this.coastlineNoise = new PerlinSimplexNoise(random, List.of(0, 1, 2, 3));
        this.terrainNoise = new PerlinSimplexNoise(random, List.of(0, 1, 2));
        this.detailNoise = new PerlinSimplexNoise(random, List.of(0, 1));

        RandomSource coastRandom = RandomSource.create(54321);
        this.largeScaleCoastNoise = new PerlinSimplexNoise(coastRandom, List.of(0, 1, 2, 3, 4));
        this.mediumScaleCoastNoise = new PerlinSimplexNoise(coastRandom, List.of(0, 1, 2, 3));
        this.smallScaleCoastNoise = new PerlinSimplexNoise(coastRandom, List.of(0, 1, 2));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registryAccess, ChunkGeneratorStructureState chunkGeneratorStructureState, StructureManager structureManager, ChunkAccess chunk, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager) {
        // COMPLETELY DISABLE ALL VANILLA STRUCTURE GENERATION
        // Do not call super.createStructures() - this prevents ALL structures from being placed
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // Disabled - using custom terrain generation
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int terrainHeight = getTerrainHeight(worldX, worldZ);

                LOTRBiome biome = getBiomeAt(worldX, worldZ);

                for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
                    pos.set(startX + x, y, startZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.is(Blocks.STONE)) {
                        BlockState surfaceBlock = getSurfaceBlockForBiome(biome, terrainHeight);
                        BlockState underBlock = getUnderBlockForBiome(biome, terrainHeight);

                        chunk.setBlockState(pos, surfaceBlock, false);
                        pos.setY(y - 1);
                        chunk.setBlockState(pos, underBlock, false);
                        pos.setY(y - 2);
                        chunk.setBlockState(pos, underBlock, false);
                        pos.setY(y - 3);
                        chunk.setBlockState(pos, underBlock, false);

                        break;
                    }
                }
            }
        }
    }

    private BlockState getSurfaceBlockForBiome(LOTRBiome biome, int terrainHeight) {
        if (biome == null) {
            return terrainHeight >= 68 ? Blocks.GRASS_BLOCK.defaultBlockState() :
                   terrainHeight >= 60 ? Blocks.SAND.defaultBlockState() :
                   Blocks.GRAVEL.defaultBlockState();
        }

        return switch (biome) {
            case HARAD_DESERT -> Blocks.SAND.defaultBlockState();
            case ROHAN_GRASSLAND, RHUN_GRASSLAND, EASTERN_RHOVANIAN_GRASSLAND,
                 HARAD_SAVANNA, LINDON_MEADOW -> Blocks.GRASS_BLOCK.defaultBlockState();
            case ARNOR_MARSH, VALE_OF_ANDUIN_FLOODPLAINS -> Blocks.MUD.defaultBlockState();
            case ANDUIN_RIVER, CELDUIN_RIVER -> ModBlocks.SILT.get().defaultBlockState();
            case BLUE_MOUNTAINS, MISTY_MOUNTAINS, GREY_MOUNTAINS,
                 WHITE_MOUNTAINS, EREBOR, FORODWAITH_ICY_MOUNTAINS -> Blocks.STONE.defaultBlockState();
            case MOUNTAINS_OF_SHADOW -> ModBlocks.STONE_TYPES.get("volcanic_stone").stone.get().defaultBlockState();
            case IRON_HILLS -> Blocks.GRAVEL.defaultBlockState();
            case MORDOR_VOLCANIC_WASTE -> ModBlocks.VOLCANIC_ASH_BLOCK.get().defaultBlockState();
            case MIRKWOOD -> Blocks.COARSE_DIRT.defaultBlockState();
            case DEAD_LANDS_EMPTY -> ModBlocks.CRACKED_MUD.get().defaultBlockState();
            case FORODWAITH_TUNDRA, FORODWAITH_ROCKY_BARRENS -> ModBlocks.FROZEN_DIRT.get().defaultBlockState();
            case RHUN_SHRUBLANDS, EASTERN_RHOVANIAN_SHRUBLANDS -> ModBlocks.CRACKED_MUD.get().defaultBlockState();
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private BlockState getLiquidForBiome(LOTRBiome biome) {
        if (biome == null) {
            return Blocks.WATER.defaultBlockState();
        }

        return switch (biome) {
            case MORDOR_VOLCANIC_WASTE, MOUNTAINS_OF_SHADOW -> Blocks.LAVA.defaultBlockState();
            case FORODWAITH_TUNDRA, FORODWAITH_ICY_MOUNTAINS, FORODWAITH_ROCKY_BARRENS -> Blocks.ICE.defaultBlockState();
            default -> Blocks.WATER.defaultBlockState();
        };
    }

    private BlockState getUnderBlockForBiome(LOTRBiome biome, int terrainHeight) {
        if (biome == null) {
            return terrainHeight >= 60 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }

        return switch (biome) {
            case HARAD_DESERT -> Blocks.SAND.defaultBlockState();
            case ARNOR_MARSH, VALE_OF_ANDUIN_FLOODPLAINS -> Blocks.MUD.defaultBlockState();
            case ANDUIN_RIVER, CELDUIN_RIVER -> ModBlocks.SILT.get().defaultBlockState();
            case MOUNTAINS_OF_SHADOW -> ModBlocks.STONE_TYPES.get("volcanic_stone").stone.get().defaultBlockState();
            case BLUE_MOUNTAINS, MISTY_MOUNTAINS, GREY_MOUNTAINS, WHITE_MOUNTAINS,
                 EREBOR, FORODWAITH_ICY_MOUNTAINS, IRON_HILLS -> Blocks.STONE.defaultBlockState();
            case MORDOR_VOLCANIC_WASTE -> ModBlocks.VOLCANIC_ASH_BLOCK.get().defaultBlockState();
            default -> Blocks.DIRT.defaultBlockState();
        };
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            this.doFill(chunk);
            return chunk;
        });
    }

    private void doFill(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                int height = getTerrainHeight(worldX, worldZ);

                for (int y = chunk.getMinBuildHeight(); y <= Math.min(height, chunk.getMaxBuildHeight() - 1); y++) {
                    pos.set(startX + x, y, startZ + z);

                    if (y <= chunk.getMinBuildHeight() + 5) {
                        if (y == chunk.getMinBuildHeight() || (y <= chunk.getMinBuildHeight() + 4 && Math.random() < 0.8)) {
                            chunk.setBlockState(pos, Blocks.BEDROCK.defaultBlockState(), false);
                        } else {
                            chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                        }
                    } else {
                        chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                    }
                }

                if (height < SEA_LEVEL) {
                    LOTRBiome biome = getBiomeAt(worldX, worldZ);
                    BlockState liquidState = getLiquidForBiome(biome);

                    for (int y = height + 1; y <= SEA_LEVEL; y++) {
                        pos.set(startX + x, y, startZ + z);
                        chunk.setBlockState(pos, liquidState, false);
                    }
                }
            }
        }
    }

    /**
     * Calculate the terrain height at a given world position using biome-aware generation.
     */
    private int getTerrainHeight(int worldX, int worldZ) {
        double height = getTerrainHeightAtBiome(worldX, worldZ);
        return (int) Math.round(height);
    }

    /**
     * ⚠️ CRITICAL FIX: Calculate terrain height by blending ACTUAL HEIGHTS, not just modifiers
     * 
     * The old approach blended biome modifiers (mountainFactor, hillFactor), then applied them
     * to noise. This caused cliffs because different modifiers * same noise = very different heights.
     * 
     * The NEW approach:
     * 1. Generate noise ONCE at the query position
     * 2. Calculate what the HEIGHT would be at each of 4 corner grid points
     * 3. Bilinearly interpolate those HEIGHTS
     * 
     * This ensures smooth transitions because we're blending the END RESULT, not the multipliers.
     */
    private double getTerrainHeightAtBiome(int worldX, int worldZ) {
        // =====================================
        // STEP 1: Generate base noise values (ONCE, at query position)
        // =====================================
        // Generate all noise values ONCE and store them
        // We'll apply different biome interpretations to these SAME noise values

        double largeScale = 1.0 / LARGE_SCALE_WAVELENGTH;
        double largeNoiseRaw = this.largeScaleCoastNoise.getValue(
            worldX * largeScale,
            worldZ * largeScale,
            false
        );

        double mediumScale = 1.0 / MEDIUM_SCALE_WAVELENGTH;
        double mediumNoiseRaw = this.mediumScaleCoastNoise.getValue(
            worldX * mediumScale,
            worldZ * mediumScale,
            false
        );

        double smallScale = 1.0 / SMALL_SCALE_WAVELENGTH;
        double smallNoiseRaw = this.smallScaleCoastNoise.getValue(
            worldX * smallScale,
            worldZ * smallScale,
            false
        );

        double detailScale = 1.0 / DETAIL_SCALE_WAVELENGTH;
        double detailNoiseRaw = this.detailNoise.getValue(
            worldX * detailScale,
            worldZ * detailScale,
            false
        );

        // Mountain-specific noise (normalized 0-1)
        double mountainNoiseRaw = generateMountainVariationNoiseRaw(worldX, worldZ);

        // Hill-specific noise (returns height in blocks directly)
        double hillNoiseRaw = generateHillVariationNoiseRaw(worldX, worldZ);

        // =====================================
        // STEP 2: Get grid cell for biome blending
        // =====================================
        final int GRID_SIZE = 32;
        int x0 = Math.floorDiv(worldX, GRID_SIZE) * GRID_SIZE;
        int z0 = Math.floorDiv(worldZ, GRID_SIZE) * GRID_SIZE;
        int x1 = x0 + GRID_SIZE;
        int z1 = z0 + GRID_SIZE;

        double fx = (double)(worldX - x0) / GRID_SIZE;
        double fz = (double)(worldZ - z0) / GRID_SIZE;

        // Smoothstep for extra smoothness
        fx = fx * fx * (3.0 - 2.0 * fx);
        fz = fz * fz * (3.0 - 2.0 * fz);

        // =====================================
        // STEP 3: Calculate HEIGHT at each corner using SAME noise
        // =====================================
        // ⚠️ KEY FIX: We apply each corner's biome properties to the SAME noise values
        // This means the underlying terrain features are continuous, only the
        // interpretation changes smoothly between biomes

        double height00 = calculateHeightForBiome(worldX, worldZ, x0, z0,
            largeNoiseRaw, mediumNoiseRaw, smallNoiseRaw, detailNoiseRaw,
            mountainNoiseRaw, hillNoiseRaw);

        double height10 = calculateHeightForBiome(worldX, worldZ, x1, z0,
            largeNoiseRaw, mediumNoiseRaw, smallNoiseRaw, detailNoiseRaw,
            mountainNoiseRaw, hillNoiseRaw);

        double height01 = calculateHeightForBiome(worldX, worldZ, x0, z1,
            largeNoiseRaw, mediumNoiseRaw, smallNoiseRaw, detailNoiseRaw,
            mountainNoiseRaw, hillNoiseRaw);

        double height11 = calculateHeightForBiome(worldX, worldZ, x1, z1,
            largeNoiseRaw, mediumNoiseRaw, smallNoiseRaw, detailNoiseRaw,
            mountainNoiseRaw, hillNoiseRaw);

        // =====================================
        // STEP 4: Bilinearly interpolate the HEIGHTS (not the modifiers!)
        // =====================================
        double baseTerrainHeight = bilinearInterp(height00, height10, height01, height11, fx, fz);

        // =====================================
        // STEP 5: Add landmask influence
        // =====================================
        double landmaskBias = getLandmaskHeightBias(worldX, worldZ);
        double finalHeight = baseTerrainHeight + (landmaskBias * LANDMASK_INFLUENCE_STRENGTH);

        // =====================================
        // STEP 6: Apply ocean transition
        // =====================================
        if (LandmaskLoader.isLoaded()) {
            final double COASTAL_NOISE_SCALE = 1.0 / 80.0;
            final double COASTAL_NOISE_STRENGTH = 12.0;

            double offsetX = this.coastlineNoise.getValue(
                worldX * COASTAL_NOISE_SCALE,
                worldZ * COASTAL_NOISE_SCALE,
                false
            ) * COASTAL_NOISE_STRENGTH;

            double offsetZ = this.coastlineNoise.getValue(
                (worldX + 10000) * COASTAL_NOISE_SCALE,
                (worldZ + 10000) * COASTAL_NOISE_SCALE,
                false
            ) * COASTAL_NOISE_STRENGTH;

            double brightness = LandmaskLoader.getInterpolatedBrightness(
                worldX + (int)offsetX,
                worldZ + (int)offsetZ
            );

            final double LAND_THRESHOLD = 120.0;
            final double TRANSITION_START = 140.0;
            final double OCEAN_THRESHOLD = 220.0;
            final int OCEAN_FLOOR_DEPTH = SEA_LEVEL - 15;

            if (brightness > LAND_THRESHOLD) {
                if (brightness >= OCEAN_THRESHOLD) {
                    finalHeight = OCEAN_FLOOR_DEPTH;
                } else if (brightness >= TRANSITION_START) {
                    double blendFactor = (brightness - TRANSITION_START) / (OCEAN_THRESHOLD - TRANSITION_START);
                    blendFactor = blendFactor * blendFactor * (3.0 - 2.0 * blendFactor);
                    finalHeight = finalHeight * (1.0 - blendFactor) + OCEAN_FLOOR_DEPTH * blendFactor;
                } else {
                    double gentleFactor = (brightness - LAND_THRESHOLD) / (TRANSITION_START - LAND_THRESHOLD);
                    gentleFactor = gentleFactor * gentleFactor;

                    if (finalHeight > SEA_LEVEL + 20) {
                        double excessHeight = finalHeight - (SEA_LEVEL + 20);
                        double reducedHeight = (SEA_LEVEL + 20) + excessHeight * (1.0 - gentleFactor * 0.5);
                        finalHeight = reducedHeight;
                    }
                }
            }
        }

        return finalHeight;
    }

    /**
     * ⚠️ NEW METHOD: Calculate the terrain height for a specific biome location
     * using pre-generated noise values.
     * 
     * This applies the biome-specific interpretation (mountain scale, hill factor, etc.)
     * to the SAME noise values that are used everywhere. This ensures terrain continuity.
     * 
     * @param queryX, queryZ - The position we're calculating height for (for landmask/region lookup)
     * @param biomeX, biomeZ - The grid position to sample biome properties from
     * @param largeNoiseRaw, mediumNoiseRaw, smallNoiseRaw, detailNoiseRaw - Raw noise values (-1 to 1)
     * @param mountainNoiseRaw - Normalized mountain noise (0 to 1)
     * @param hillNoiseRaw - Hill noise in blocks
     * @return The terrain height at this location
     */
    private double calculateHeightForBiome(
        int queryX, int queryZ,  // Where we're calculating
        int biomeX, int biomeZ,   // Where we sample biome from
        double largeNoiseRaw,
        double mediumNoiseRaw,
        double smallNoiseRaw,
        double detailNoiseRaw,
        double mountainNoiseRaw,
        double hillNoiseRaw
    ) {
        // Get biome modifiers at the biome sample position
        BiomeModifiers modifiers = getBiomeModifiersAt(biomeX, biomeZ);

        // Apply amplitude scaling to raw noise based on biome type
        double largeNoise = largeNoiseRaw * LARGE_SCALE_AMPLITUDE;
        double mediumNoise = mediumNoiseRaw * MEDIUM_SCALE_AMPLITUDE;
        double smallNoise = smallNoiseRaw * SMALL_SCALE_AMPLITUDE;
        double detailNoise = detailNoiseRaw * DETAIL_SCALE_AMPLITUDE;

        // Calculate base height from multi-scale noise
        double baseHeight = SEA_LEVEL +
                           (largeNoise * modifiers.terrainVariationScale) +
                           (mediumNoise * modifiers.terrainVariationScale) +
                           (smallNoise * modifiers.terrainVariationScale * 0.5) +
                           (detailNoise * modifiers.terrainVariationScale * 0.3);

        // Add mountain variation (if this biome has mountains)
        double mountainVariation = mountainNoiseRaw * modifiers.mountainBaseHeight;

        // Add hill variation (if this biome has hills)
        double hillVariation = hillNoiseRaw * modifiers.hillFactor;

        // Add base height offset (e.g., rivers are lower)
        double heightOffset = modifiers.baseHeightOffset;

        // Combine all components
        return baseHeight + mountainVariation + hillVariation + heightOffset;
    }

    /**
     * Generate raw mountain noise (returns 0-1, NOT scaled by amplitude yet)
     * Uses ridged noise for jagged, sharp peaks and an additional high-frequency
     * octave for fine-grained ruggedness.
     */
    private double generateMountainVariationNoiseRaw(int worldX, int worldZ) {
        double mountainScale1 = 1.0 / 400.0;  // Large envelope
        double mountainScale2 = 1.0 / 150.0;  // Medium ridges
        double mountainScale3 = 1.0 / 50.0;   // Small ridges
        double mountainScale4 = 1.0 / 20.0;   // Fine jaggedness

        double noise1 = this.terrainNoise.getValue(worldX * mountainScale1, worldZ * mountainScale1, false);
        double noise2 = this.terrainNoise.getValue(worldX * mountainScale2, worldZ * mountainScale2, false);
        double noise3 = this.detailNoise.getValue(worldX * mountainScale3, worldZ * mountainScale3, false);
        double noise4 = this.detailNoise.getValue(worldX * mountainScale4, worldZ * mountainScale4, false);

        // Large-scale envelope controls where mountains exist (smooth 0-1)
        double envelope = (noise1 + 1.0) / 2.0;

        // Ridged noise: sharp peaks where noise crosses zero
        double ridge2 = 1.0 - Math.abs(noise2);
        double ridge3 = 1.0 - Math.abs(noise3);

        // Combine ridged octaves with fine jaggedness for sharp, erratic peaks
        double jagged = ridge2 * 0.4 + ridge3 * 0.35 + Math.abs(noise4) * 0.25;

        // Envelope shapes overall mountain placement; jagged shapes the peaks
        double combined = envelope * jagged;

        return combined * combined;  // Square for dramatic peaks
    }

    /**
     * Generate raw hill noise (returns height in blocks)
     */
    private double generateHillVariationNoiseRaw(int worldX, int worldZ) {
        double hillScale = 1.0 / 250.0;
        double hillNoise = this.terrainNoise.getValue(worldX * hillScale, worldZ * hillScale, false);

        double normalized = (hillNoise + 1.0) / 2.0;
        return Math.sin(normalized * Math.PI) * 10.0;  // Low-lying rolling hills
    }

    private double getLandmaskHeightBias(int worldX, int worldZ) {
        if (!LandmaskLoader.isLoaded()) {
            return 0.0;
        }

        final double COASTAL_NOISE_SCALE = 1.0 / 80.0;
        final double COASTAL_NOISE_STRENGTH = 12.0;

        double offsetX = this.coastlineNoise.getValue(
            worldX * COASTAL_NOISE_SCALE,
            worldZ * COASTAL_NOISE_SCALE,
            false
        ) * COASTAL_NOISE_STRENGTH;

        double offsetZ = this.coastlineNoise.getValue(
            (worldX + 10000) * COASTAL_NOISE_SCALE,
            (worldZ + 10000) * COASTAL_NOISE_SCALE,
            false
        ) * COASTAL_NOISE_STRENGTH;

        double brightness = LandmaskLoader.getInterpolatedBrightness(
            worldX + (int)offsetX,
            worldZ + (int)offsetZ
        );

        double normalized = 1.0 - (brightness / 127.5);
        return normalized * LANDMASK_HEIGHT_BIAS;
    }

    private boolean isLandAt(int worldX, int worldZ) {
        int terrainHeight = getTerrainHeight(worldX, worldZ);
        return terrainHeight >= SEA_LEVEL;
    }

    private LOTRBiome getBiomeAt(int worldX, int worldZ) {
        if (!(this.getBiomeSource() instanceof MiddleEarthBiomeSource middleEarthSource)) {
            return null;
        }
        return middleEarthSource.getLOTRBiomeAt(worldX, worldZ);
    }

    private static class BiomeModifiers {
        double flatFactor = 0.0;
        double hillFactor = 0.0;
        double mountainFactor = 0.0;
        double riverFactor = 0.0;

        double baseHeightOffset = 0.0;
        double terrainVariationScale = 0.0;
        double mountainBaseHeight = 0.0;
    }

    private double bilinearInterp(double v00, double v10, double v01, double v11, double fx, double fz) {
        double v0 = v00 * (1.0 - fx) + v10 * fx;
        double v1 = v01 * (1.0 - fx) + v11 * fx;
        return v0 * (1.0 - fz) + v1 * fz;
    }

    private BiomeModifiers getBiomeModifiersAt(int worldX, int worldZ) {
        BiomeModifiers result = new BiomeModifiers();

        LOTRBiome biome = getBiomeAt(worldX, worldZ);
        if (biome == null) {
            result.flatFactor = 1.0;
            result.terrainVariationScale = 1.0;
            return result;
        }

        if (biome.isRiver()) {
            result.riverFactor = 1.0;
            result.baseHeightOffset = -8.0;
            result.terrainVariationScale = 0.2;
        } else if (biome.isMountain()) {
            result.mountainFactor = 1.0;

            double mountainScale = switch (biome) {
                case BLUE_MOUNTAINS, MISTY_MOUNTAINS, MOUNTAINS_OF_SHADOW -> 200.0;
                case WHITE_MOUNTAINS, GREY_MOUNTAINS -> 160.0;
                case IRON_HILLS, EREBOR, FORODWAITH_ICY_MOUNTAINS -> 120.0;
                default -> 80.0;
            };

            result.mountainBaseHeight = mountainScale;
            result.baseHeightOffset = 0.0;
            result.terrainVariationScale = 1.5;
        } else if (biome.isHilly()) {
            result.hillFactor = 1.0;
            result.baseHeightOffset = 0.0;
            result.terrainVariationScale = 1.0;
        } else if (isFlatBiome(biome)) {
            result.flatFactor = 1.0;
            result.baseHeightOffset = 0.0;
            result.terrainVariationScale = 0.3;
        } else {
            result.hillFactor = 0.5;
            result.flatFactor = 0.5;
            result.terrainVariationScale = 0.7;
        }

        return result;
    }

    private boolean isFlatBiome(LOTRBiome biome) {
        return switch (biome) {
            case ERIADOR_PLAINS, ARNOR_PLAINS, GONDOR_PLAINS, DALE_PLAINS,
                 ROHAN_GRASSLAND, RHUN_GRASSLAND, EASTERN_RHOVANIAN_GRASSLAND,
                 HARAD_DESERT, LINDON_MEADOW, HARAD_SAVANNA,
                 VALE_OF_ANDUIN_FLOODPLAINS, ARNOR_MARSH,
                 DEAD_LANDS_EMPTY, THE_SHIRE -> true;
            default -> false;
        };
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState random) {
        return getTerrainHeight(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int height = getTerrainHeight(x, z);
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= height) {
                states[i] = Blocks.STONE.defaultBlockState();
            } else if (y <= SEA_LEVEL) {
                states[i] = Blocks.WATER.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Middle-earth Chunk Generator");
        info.add("Landmask loaded: " + LandmaskLoader.isLoaded());
        info.add("Region map loaded: " + RegionMapLoader.isLoaded());

        if (RegionMapLoader.isLoaded()) {
            Region region = RegionMapLoader.getRegion(pos.getX(), pos.getZ());
            info.add("Region: " + region.getDisplayName());

            int color = RegionMapLoader.getInterpolatedColor(pos.getX(), pos.getZ());
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            info.add(String.format("Region RGB: (%d, %d, %d)", r, g, b));
        }

        LOTRBiome biome = getBiomeAt(pos.getX(), pos.getZ());
        if (biome != null) {
            info.add("LOTR Biome: " + biome.getName());
            info.add("Is Mountain: " + biome.isMountain());
            info.add("Is River: " + biome.isRiver());
            info.add("Is Hilly: " + biome.isHilly());
        }

        if (LandmaskLoader.isLoaded()) {
            double brightness = LandmaskLoader.getInterpolatedBrightness(pos.getX(), pos.getZ());
            info.add(String.format("Landmask: %.1f", brightness));
        }

        int terrainHeight = getTerrainHeight(pos.getX(), pos.getZ());
        info.add("Terrain height: " + terrainHeight);
        info.add("Is land: " + isLandAt(pos.getX(), pos.getZ()));
    }
}
