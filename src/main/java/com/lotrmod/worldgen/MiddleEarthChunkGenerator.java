package com.lotrmod.worldgen;

import com.lotrmod.worldgen.biome.LOTRBiome;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
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

public class MiddleEarthChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<MiddleEarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(gen -> gen.generatorSettings())
            ).apply(instance, MiddleEarthChunkGenerator::new)
    );

    // Primary terrain noise layers (multi-octave for natural look)
    private final PerlinSimplexNoise continentalNoise;   // Very large scale shape
    private final PerlinSimplexNoise erosionNoise;       // Erosion / valley carving
    private final PerlinSimplexNoise peaksValleysNoise;  // Local peaks and valleys
    private final PerlinSimplexNoise detailNoise;        // Surface fine detail
    private final PerlinSimplexNoise ridgeNoise;         // Mountain ridgelines

    // Used for biome boundary jitter and landmask coastline noise
    private final PerlinSimplexNoise coastlineNoise;

    // ── Inland water (small natural rivers + lakes) ───────────────────────────
    // These carve gentle channels/basins into the dry land and fill them with
    // water. They are deliberately SMALL and local — the large landmask rivers
    // (Anduin / Celduin) are part of the geography and are left untouched.
    private final PerlinSimplexNoise riverNoise;      // winding river centre-lines
    private final PerlinSimplexNoise riverWarpNoise;  // domain-warp for meanders
    private final PerlinSimplexNoise riverPresenceNoise; // gates where rivers exist
    private final PerlinSimplexNoise lakeNoise;       // lake basins
    private final PerlinSimplexNoise lakeEdgeNoise;   // irregular lake shorelines

    private static final int SEA_LEVEL = 63;
    // Dry land is always kept above this — no ocean, no sub-surface voids.
    // (River/lake channels are allowed to carve a little below this, but they
    //  are always filled with water so no voids are created.)
    private static final int MIN_TERRAIN_HEIGHT = 65;

    // ── River/lake tunables ───────────────────────────────────────────────────
    // Half-width of a river in noise units (smaller = narrower river).
    private static final double RIVER_HALF_WIDTH = 0.014;
    private static final double RIVER_SCALE      = 720.0; // larger = longer, smoother rivers
    private static final double RIVER_MAX_DEPTH  = 4.0;   // channel depth at centre
    // Lakes only appear where the lake noise rises above this (higher = rarer).
    private static final double LAKE_THRESHOLD   = 0.70;
    private static final double LAKE_SCALE       = 300.0;
    private static final double LAKE_MAX_DEPTH   = 8.0;
    // How far the water surface sits below the surrounding land. Acts as the
    // bank freeboard that keeps the water contained.
    private static final int WATER_FREEBOARD     = 2;
    // Above this land height (mountains/highlands) we never place inland water.
    private static final double WATER_MAX_LAND_HEIGHT = 96.0;
    // Maximum terrain slope (blocks per block) that may hold water — keeps
    // rivers/lakes in valleys and plains, never clinging to steep hillsides.
    private static final double WATER_MAX_SLOPE  = 0.085;

    public MiddleEarthChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);

        RandomSource r1 = RandomSource.create(12345L);
        this.continentalNoise  = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3, 4));
        this.erosionNoise      = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));
        this.peaksValleysNoise = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));
        this.detailNoise       = new PerlinSimplexNoise(r1, List.of(0, 1, 2));
        this.ridgeNoise        = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));

        RandomSource r2 = RandomSource.create(98765L);
        this.coastlineNoise = new PerlinSimplexNoise(r2, List.of(0, 1, 2));

        RandomSource r3 = RandomSource.create(54321L);
        this.riverNoise         = new PerlinSimplexNoise(r3, List.of(0, 1, 2));
        this.riverWarpNoise     = new PerlinSimplexNoise(r3, List.of(0, 1));
        this.riverPresenceNoise = new PerlinSimplexNoise(r3, List.of(0, 1, 2));
        this.lakeNoise          = new PerlinSimplexNoise(r3, List.of(0, 1, 2));
        this.lakeEdgeNoise      = new PerlinSimplexNoise(r3, List.of(0, 1));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registryAccess, ChunkGeneratorStructureState state,
            StructureManager structureManager, ChunkAccess chunk,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager) {
        // All structure placement handled by LOTR mod separately
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

                double baseLand = getBaseLandHeight(worldX, worldZ);
                WaterColumn water = computeWater(worldX, worldZ, baseLand);
                int terrainHeight = water.floor; // solid top (channel floor under water)

                // Slight position jitter so biome surface boundaries look organic
                double jScale = 1.0 / 40.0;
                int jx = (int)(detailNoise.getValue(worldX * jScale, worldZ * jScale, false) * 14.0);
                int jz = (int)(detailNoise.getValue((worldX + 7919) * jScale, worldZ * jScale, false) * 14.0);
                LOTRBiome biome = getBiomeAt(worldX + jx, worldZ + jz);

                for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
                    pos.set(startX + x, y, startZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.is(Blocks.STONE)) {
                        BlockState surfaceBlock;
                        if (water.hasWater()) {
                            // Underwater bed material — never grass under a river/lake.
                            surfaceBlock = getWaterBedBlock(biome, water.isLake);
                        } else {
                            // Snow cap on high peaks
                            boolean isHighPeak = terrainHeight >= 160;
                            surfaceBlock = isHighPeak
                                    ? Blocks.SNOW_BLOCK.defaultBlockState()
                                    : getSurfaceBlockForBiome(biome, terrainHeight, y);
                        }
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

    /** Bed material under inland water — gravel for rivers, sand for lakes. */
    private BlockState getWaterBedBlock(LOTRBiome biome, boolean isLake) {
        if (biome == LOTRBiome.HARAD_DESERT || biome == LOTRBiome.HARAD_SAVANNA) {
            return Blocks.SAND.defaultBlockState();
        }
        return isLake ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    private BlockState getSurfaceBlockForBiome(LOTRBiome biome, int terrainHeight, int surfaceY) {
        if (biome == null) return Blocks.GRASS_BLOCK.defaultBlockState();

        // Stone-capped mountains (above tree line)
        if (terrainHeight >= 120 && biome.isMountain()) return Blocks.STONE.defaultBlockState();

        return switch (biome) {
            // Deserts
            case HARAD_DESERT -> Blocks.SAND.defaultBlockState();

            // Marshes / wet ground (MUD is vanilla)
            case ARNOR_MARSH, VALE_OF_ANDUIN_FLOODPLAINS -> Blocks.MUD.defaultBlockState();

            // Dry riverbeds — gravel, no water
            case ANDUIN_RIVER, CELDUIN_RIVER -> Blocks.GRAVEL.defaultBlockState();

            // Bare stone mountains
            case BLUE_MOUNTAINS, MISTY_MOUNTAINS, GREY_MOUNTAINS,
                 WHITE_MOUNTAINS, EREBOR, FORODWAITH_ICY_MOUNTAINS,
                 MOUNTAINS_OF_SHADOW, IRON_HILLS -> Blocks.STONE.defaultBlockState();

            // Mordor — coarse gravel/dirt, no custom blocks
            case MORDOR_VOLCANIC_WASTE -> Blocks.GRAVEL.defaultBlockState();

            // Dead, barren ground
            case DEAD_LANDS_EMPTY -> Blocks.COARSE_DIRT.defaultBlockState();
            case RHUN_SHRUBLANDS, EASTERN_RHOVANIAN_SHRUBLANDS -> Blocks.COARSE_DIRT.defaultBlockState();

            // Cold north
            case FORODWAITH_TUNDRA, FORODWAITH_ROCKY_BARRENS -> Blocks.SNOW_BLOCK.defaultBlockState();

            // Dense dark forest floor
            case MIRKWOOD -> Blocks.COARSE_DIRT.defaultBlockState();

            // Everything else — grass
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private BlockState getUnderBlockForBiome(LOTRBiome biome, int terrainHeight) {
        if (biome == null) return Blocks.DIRT.defaultBlockState();

        return switch (biome) {
            case HARAD_DESERT -> Blocks.SAND.defaultBlockState();
            case ARNOR_MARSH, VALE_OF_ANDUIN_FLOODPLAINS -> Blocks.MUD.defaultBlockState();
            case ANDUIN_RIVER, CELDUIN_RIVER -> Blocks.GRAVEL.defaultBlockState();
            case BLUE_MOUNTAINS, MISTY_MOUNTAINS, GREY_MOUNTAINS, WHITE_MOUNTAINS,
                 MOUNTAINS_OF_SHADOW, EREBOR, FORODWAITH_ICY_MOUNTAINS, IRON_HILLS -> Blocks.STONE.defaultBlockState();
            case MORDOR_VOLCANIC_WASTE -> Blocks.GRAVEL.defaultBlockState();
            case DEAD_LANDS_EMPTY, RHUN_SHRUBLANDS, EASTERN_RHOVANIAN_SHRUBLANDS -> Blocks.DIRT.defaultBlockState();
            case FORODWAITH_TUNDRA, FORODWAITH_ROCKY_BARRENS -> Blocks.DIRT.defaultBlockState();
            default -> Blocks.DIRT.defaultBlockState();
        };
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {}

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // Vanilla carvers (cave, canyon, extra_underground) defined in biome JSONs.
        // aquifers_enabled=false in noise settings ensures no water spawns in carved voids.
        super.applyCarvers(level, seed, randomState, biomeManager, structureManager, chunk, step);
    }

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            doFill(chunk);
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

                double baseLand = getBaseLandHeight(worldX, worldZ);
                WaterColumn water = computeWater(worldX, worldZ, baseLand);
                int terrainTop = water.floor;

                for (int y = chunk.getMinBuildHeight(); y <= Math.min(terrainTop, chunk.getMaxBuildHeight() - 1); y++) {
                    pos.set(startX + x, y, startZ + z);

                    if (y <= chunk.getMinBuildHeight() + 5) {
                        boolean isBedrock = (y == chunk.getMinBuildHeight())
                                || (y <= chunk.getMinBuildHeight() + 4 && Math.random() < 0.8);
                        chunk.setBlockState(pos,
                                isBedrock ? Blocks.BEDROCK.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState(),
                                false);
                    } else if (y < 0) {
                        chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                    } else {
                        chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                    }
                }

                // Fill small natural rivers / lakes with water (no ocean anywhere).
                if (water.hasWater()) {
                    boolean freezing = isFreezing(worldX, worldZ);
                    int top = Math.min(water.waterSurface, chunk.getMaxBuildHeight() - 1);
                    for (int y = terrainTop + 1; y <= top; y++) {
                        pos.set(startX + x, y, startZ + z);
                        boolean surface = (y == top);
                        BlockState fluid = (surface && freezing)
                                ? Blocks.ICE.defaultBlockState()
                                : Blocks.WATER.defaultBlockState();
                        chunk.setBlockState(pos, fluid, false);
                    }
                }
            }
        }
    }

    /** Cold regions freeze the surface of inland water into ice. */
    private boolean isFreezing(int worldX, int worldZ) {
        LOTRBiome biome = getBiomeAt(worldX, worldZ);
        return biome != null && biome.getTemperature() < 0.15f;
    }

    // ======================================================================
    // TERRAIN HEIGHT — vanilla-style noise stack
    // ======================================================================

    /**
     * Final solid terrain top, including any river/lake channel carving.
     * This is the heightmap-relevant surface (the block water rests on, or the
     * dry ground where there is no water).
     */
    private int getTerrainHeight(int worldX, int worldZ) {
        double baseLand = getBaseLandHeight(worldX, worldZ);
        return computeWater(worldX, worldZ, baseLand).floor;
    }

    /**
     * Vanilla-inspired terrain height using a continental/erosion/peaks-valleys stack.
     *
     * Continental noise sets the broad elevation baseline (like vanilla continentalness).
     * Erosion noise carves valleys and flattens plains (like vanilla erosion).
     * Peaks-and-valleys noise adds local relief on top.
     * Ridge noise shapes mountain spines and passes.
     *
     * Biome type modulates how strongly each layer contributes, so mountains are
     * dramatic while plains stay gently rolling — all from the same noise fields.
     */
    private double getBaseLandHeight(int worldX, int worldZ) {

        // ── Continental baseline (very large scale, smooth) ──────────────────
        double contRaw = continentalNoise.getValue(worldX / 1400.0, worldZ / 1400.0, false);
        // Shape into 0-1 so we can use it as a multiplier for regional height
        double continental = (contRaw + 1.0) / 2.0; // 0..1

        // ── Erosion (determines how flat/hilly an area feels) ─────────────────
        double erosionRaw = erosionNoise.getValue(worldX / 500.0, worldZ / 500.0, false);
        // 0 = highly eroded (flat valleys), 1 = uneroded (tall terrain)
        double erosion = (erosionRaw + 1.0) / 2.0;

        // ── Peaks and valleys (medium-frequency local relief) ─────────────────
        double pvRaw = peaksValleysNoise.getValue(worldX / 180.0, worldZ / 180.0, false);
        // Sharpen valley floors and peaks with a squishing transform
        double pv = squishPeaksValleys(pvRaw);

        // ── Fine surface detail ───────────────────────────────────────────────
        double detail = detailNoise.getValue(worldX / 45.0, worldZ / 45.0, false);

        // ── Ridge noise for mountain spines ───────────────────────────────────
        double ridgeRaw = ridgeNoise.getValue(worldX / 220.0, worldZ / 220.0, false);
        double ridge = 1.0 - Math.abs(ridgeRaw); // 0 = valley, 1 = ridge crest
        ridge = ridge * ridge; // sharpen

        // ── Get biome modifiers at this location ──────────────────────────────
        // Use a 96-block grid + smoothstep to avoid visible grid artefacts
        final int GRID = 96;
        int gx0 = Math.floorDiv(worldX, GRID) * GRID;
        int gz0 = Math.floorDiv(worldZ, GRID) * GRID;
        double fx = smoothstep((double)(worldX - gx0) / GRID);
        double fz = smoothstep((double)(worldZ - gz0) / GRID);

        BiomeModifiers m00 = getBiomeModifiersAt(gx0,        gz0);
        BiomeModifiers m10 = getBiomeModifiersAt(gx0 + GRID, gz0);
        BiomeModifiers m01 = getBiomeModifiersAt(gx0,        gz0 + GRID);
        BiomeModifiers m11 = getBiomeModifiersAt(gx0 + GRID, gz0 + GRID);

        // Interpolate the modifier fields themselves (smooth transition between biomes)
        double baseOffset       = bilinearInterp(m00.baseOffset,       m10.baseOffset,       m01.baseOffset,       m11.baseOffset,       fx, fz);
        double continentScale   = bilinearInterp(m00.continentScale,   m10.continentScale,   m01.continentScale,   m11.continentScale,   fx, fz);
        double erosionScale     = bilinearInterp(m00.erosionScale,     m10.erosionScale,     m01.erosionScale,     m11.erosionScale,     fx, fz);
        double pvScale          = bilinearInterp(m00.pvScale,          m10.pvScale,          m01.pvScale,          m11.pvScale,          fx, fz);
        double ridgeScale       = bilinearInterp(m00.ridgeScale,       m10.ridgeScale,       m01.ridgeScale,       m11.ridgeScale,       fx, fz);
        double detailScale      = bilinearInterp(m00.detailScale,      m10.detailScale,      m01.detailScale,      m11.detailScale,      fx, fz);

        // ── Combine layers ────────────────────────────────────────────────────
        double height = SEA_LEVEL
                + baseOffset
                + continental * continentScale
                + (1.0 - erosion) * erosionScale   // high erosion = flat = subtract less
                + pv * pvScale
                + ridge * ridgeScale
                + detail * detailScale;

        // ── Landmask shapes the broad Middle-earth geography ──────────────────
        height = applyLandmask(worldX, worldZ, height);

        // Clamp: terrain is never below MIN_TERRAIN_HEIGHT — no water, no voids
        return Math.max(MIN_TERRAIN_HEIGHT, height);
    }

    /**
     * Squishes the peaks-and-valleys noise so valley floors are flat and
     * ridge crests are sharp — mirrors what vanilla's PV spline does.
     */
    private double squishPeaksValleys(double raw) {
        // raw in [-1, 1]; fold negative (valleys) to compress them,
        // amplify positive (peaks) slightly
        if (raw < 0) {
            return raw * 0.5; // valleys are gentle
        } else {
            return raw * 1.2; // peaks are a bit sharper
        }
    }

    private double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Uses the landmask to shape Middle-earth's geography.
     * Ocean pixels → lower terrain (dry plains/lowlands, not water).
     * Land pixels → terrain unmodified.
     * Coastal zones get a gentle slope.
     */
    private double applyLandmask(int worldX, int worldZ, double height) {
        if (!LandmaskLoader.isLoaded()) return height;

        double noiseOffX = coastlineNoise.getValue(worldX / 80.0, worldZ / 80.0, false) * 10.0;
        double noiseOffZ = coastlineNoise.getValue((worldX + 10000) / 80.0, (worldZ + 10000) / 80.0, false) * 10.0;

        double brightness = LandmaskLoader.getInterpolatedBrightness(
                worldX + (int) noiseOffX,
                worldZ + (int) noiseOffZ);

        final double LAND_MAX       = 100.0;  // fully land
        final double COAST_START    = 140.0;  // start of coastal lowering
        final double OCEAN_MIN      = 210.0;  // fully "ocean" → low dry terrain

        if (brightness <= LAND_MAX) {
            // Deep inland: landmask pushes height up slightly for prominence
            double boost = ((LAND_MAX - brightness) / LAND_MAX) * 8.0;
            return height + boost;
        } else if (brightness < COAST_START) {
            // Near-coastal land: no change
            return height;
        } else if (brightness < OCEAN_MIN) {
            // Coastal transition: gradually lower toward lowland plains
            double t = (brightness - COAST_START) / (OCEAN_MIN - COAST_START);
            t = smoothstep(t);
            double lowlandTarget = SEA_LEVEL + 8.0; // low but still dry
            return height * (1.0 - t) + lowlandTarget * t;
        } else {
            // Ocean pixel → lowland plains, still fully dry
            return SEA_LEVEL + 5.0;
        }
    }

    // ======================================================================
    // INLAND WATER — small natural rivers & lakes
    // ======================================================================

    /**
     * Result of carving inland water at a column.
     * {@code floor} is the solid terrain top after carving; when {@code waterSurface}
     * is greater than the floor, the column is filled with water up to that level.
     */
    private static final class WaterColumn {
        final int floor;
        final int waterSurface; // Integer.MIN_VALUE when dry
        final boolean isLake;

        WaterColumn(int floor, int waterSurface, boolean isLake) {
            this.floor = floor;
            this.waterSurface = waterSurface;
            this.isLake = isLake;
        }

        boolean hasWater() {
            return waterSurface > floor;
        }
    }

    /**
     * Computes river/lake carving for a single column.
     *
     * The approach: a winding "river factor" and a blobby "lake factor" describe
     * how strongly a column belongs to a water feature (0 = none, 1 = centre).
     * We carve the land down by that factor and fill the resulting basin with
     * water up to a surface that sits {@link #WATER_FREEBOARD} blocks below the
     * surrounding land — the un-carved banks contain the water laterally.
     *
     * Rivers/lakes are only placed in sensible places (gentle, low-to-mid
     * terrain, away from deserts, mountains and the great landmask rivers).
     */
    private WaterColumn computeWater(int worldX, int worldZ, double baseLand) {
        int dryFloor = (int) Math.round(baseLand);

        if (!waterAllowedHere(worldX, worldZ, baseLand)) {
            return new WaterColumn(dryFloor, Integer.MIN_VALUE, false);
        }

        double river = riverFactor(worldX, worldZ);
        double lake  = lakeFactor(worldX, worldZ);
        double factor = Math.max(river, lake);
        if (factor <= 0.0) {
            return new WaterColumn(dryFloor, Integer.MIN_VALUE, false);
        }

        boolean isLake = lake >= river;
        double maxDepth = isLake ? LAKE_MAX_DEPTH : RIVER_MAX_DEPTH;

        // Smoothed (detail-free-ish) land height gives a flat-ish water surface,
        // while the slope of that smoothed field tells us if it's too steep.
        SmoothSample s = getSmoothLandHeight(worldX, worldZ);
        if (s.slope > WATER_MAX_SLOPE) {
            return new WaterColumn(dryFloor, Integer.MIN_VALUE, false);
        }

        // Carve the channel/basin floor. Banks (small factor) barely move; the
        // centre (factor ~1) drops by the full depth.
        int floor = (int) Math.round(baseLand - factor * maxDepth);

        // Water surface derived from the smoothed terrain so it stays flat across
        // the channel; it sits WATER_FREEBOARD blocks below the surrounding land.
        int waterSurface = (int) Math.floor(s.height) - WATER_FREEBOARD;

        if (waterSurface <= floor) {
            // Dry bank. Raise it to at least one block above the local water
            // level so neighbouring water can never spill out and run downhill.
            int sealedFloor = Math.max(floor, waterSurface + 1);
            return new WaterColumn(sealedFloor, Integer.MIN_VALUE, isLake);
        }
        return new WaterColumn(floor, waterSurface, isLake);
    }

    /** Biomes/areas where inland water should never appear. */
    private boolean waterAllowedHere(int worldX, int worldZ, double baseLand) {
        if (baseLand > WATER_MAX_LAND_HEIGHT) return false;

        LOTRBiome biome = getBiomeAt(worldX, worldZ);
        if (biome == null) return true;

        // Never touch the great landmask rivers, and skip dry/dead/hostile lands.
        if (biome.isRiver() || biome.isMountain()) return false;
        return switch (biome) {
            case HARAD_DESERT,
                 MORDOR_VOLCANIC_WASTE,
                 DEAD_LANDS_EMPTY,
                 FORODWAITH_ROCKY_BARRENS -> false;
            default -> true;
        };
    }

    /**
     * River centre-lines: a domain-warped noise field whose zero-crossings form
     * winding channels. Returns 0 away from rivers, up to 1 at a channel centre.
     */
    private double riverFactor(int worldX, int worldZ) {
        // Gate: only roughly half the world has rivers, so they're not everywhere.
        double presence = riverPresenceNoise.getValue(worldX / 1100.0, worldZ / 1100.0, false);
        if (presence <= 0.0) return 0.0;
        double presenceFade = Math.min(1.0, presence * 3.0);

        // Domain warp to make the channels meander instead of running straight.
        double warpX = riverWarpNoise.getValue(worldX / 240.0, worldZ / 240.0, false) * 90.0;
        double warpZ = riverWarpNoise.getValue((worldX + 4000) / 240.0, (worldZ + 4000) / 240.0, false) * 90.0;

        double n = riverNoise.getValue((worldX + warpX) / RIVER_SCALE, (worldZ + warpZ) / RIVER_SCALE, false);
        double dist = Math.abs(n);
        if (dist >= RIVER_HALF_WIDTH) return 0.0;

        double f = 1.0 - dist / RIVER_HALF_WIDTH; // 1 at centre, 0 at bank
        return smoothstep(f) * presenceFade;
    }

    /**
     * Lake basins: rare blobs where the lake noise rises above a high threshold,
     * with an irregular shoreline from a finer noise. 0 = no lake, up to 1 centre.
     */
    private double lakeFactor(int worldX, int worldZ) {
        double n = lakeNoise.getValue(worldX / LAKE_SCALE, worldZ / LAKE_SCALE, false);
        double edge = lakeEdgeNoise.getValue(worldX / 60.0, worldZ / 60.0, false) * 0.10;
        double v = n + edge;
        if (v <= LAKE_THRESHOLD) return 0.0;

        double f = (v - LAKE_THRESHOLD) / (1.0 - LAKE_THRESHOLD);
        return smoothstep(Math.min(1.0, f * 1.8));
    }

    /** Smoothed land height (coarse bilinear sample) plus its local slope. */
    private static final class SmoothSample {
        final double height;
        final double slope;
        SmoothSample(double height, double slope) {
            this.height = height;
            this.slope = slope;
        }
    }

    private SmoothSample getSmoothLandHeight(int worldX, int worldZ) {
        final int G = 48;
        int gx0 = Math.floorDiv(worldX, G) * G;
        int gz0 = Math.floorDiv(worldZ, G) * G;
        double fx = smoothstep((double) (worldX - gx0) / G);
        double fz = smoothstep((double) (worldZ - gz0) / G);

        double h00 = getBaseLandHeight(gx0,     gz0);
        double h10 = getBaseLandHeight(gx0 + G, gz0);
        double h01 = getBaseLandHeight(gx0,     gz0 + G);
        double h11 = getBaseLandHeight(gx0 + G, gz0 + G);

        double height = bilinearInterp(h00, h10, h01, h11, fx, fz);

        // Slope estimate from the coarse cell corners (blocks per block).
        double dx = (Math.abs(h10 - h00) + Math.abs(h11 - h01)) * 0.5 / G;
        double dz = (Math.abs(h01 - h00) + Math.abs(h11 - h10)) * 0.5 / G;
        double slope = Math.max(dx, dz);

        return new SmoothSample(height, slope);
    }

    // ======================================================================
    // BIOME MODIFIERS
    // ======================================================================

    private static class BiomeModifiers {
        double baseOffset     = 10.0;  // vertical shift above sea level
        double continentScale = 20.0;  // how much continental noise contributes
        double erosionScale   = 15.0;  // how deep erosion carves
        double pvScale        = 18.0;  // peaks-and-valleys amplitude
        double ridgeScale     =  0.0;  // mountain ridges (0 for flat/hilly biomes)
        double detailScale    =  4.0;  // surface detail amplitude
    }

    private BiomeModifiers getBiomeModifiersAt(int worldX, int worldZ) {
        BiomeModifiers m = new BiomeModifiers();
        LOTRBiome biome = getBiomeAt(worldX, worldZ);
        if (biome == null) return m;

        if (biome.isMountain()) {
            double peakHeight = switch (biome) {
                case BLUE_MOUNTAINS, MISTY_MOUNTAINS, MOUNTAINS_OF_SHADOW -> 110.0;
                case WHITE_MOUNTAINS, GREY_MOUNTAINS                       ->  85.0;
                case IRON_HILLS, EREBOR, FORODWAITH_ICY_MOUNTAINS          ->  60.0;
                default                                                    ->  45.0;
            };
            m.baseOffset     = 25.0;
            m.continentScale = 30.0;
            m.erosionScale   = 10.0;  // mountains resist erosion
            m.pvScale        = 40.0;
            m.ridgeScale     = peakHeight;
            m.detailScale    =  8.0;

        } else if (biome.isRiver()) {
            // Dry riverbeds: gently lower than surroundings but above sea level
            m.baseOffset     =  4.0;
            m.continentScale = 10.0;
            m.erosionScale   = 20.0;  // heavily eroded = low and flat
            m.pvScale        =  6.0;
            m.ridgeScale     =  0.0;
            m.detailScale    =  3.0;

        } else if (biome.isHilly()) {
            // Rolling hills (Shire, Limestone Hills, Rocky Hills, etc.)
            m.baseOffset     = 12.0;
            m.continentScale = 18.0;
            m.erosionScale   = 14.0;
            m.pvScale        = 22.0;
            m.ridgeScale     =  0.0;
            m.detailScale    =  5.0;

        } else if (isFlatBiome(biome)) {
            // Plains — still rolling, just less dramatic than hills
            m.baseOffset     = 10.0;
            m.continentScale = 14.0;
            m.erosionScale   = 18.0;  // more erosion = flatter valleys
            m.pvScale        = 12.0;
            m.ridgeScale     =  0.0;
            m.detailScale    =  4.0;

        } else if (isForestBiome(biome)) {
            // Forest — mid-range variation, terrain defined by trees not shape
            m.baseOffset     = 10.0;
            m.continentScale = 16.0;
            m.erosionScale   = 16.0;
            m.pvScale        = 16.0;
            m.ridgeScale     =  0.0;
            m.detailScale    =  5.0;

        } else {
            // Default mixed terrain
            m.baseOffset     = 10.0;
            m.continentScale = 18.0;
            m.erosionScale   = 16.0;
            m.pvScale        = 18.0;
            m.ridgeScale     =  0.0;
            m.detailScale    =  5.0;
        }

        return m;
    }

    private boolean isFlatBiome(LOTRBiome biome) {
        return switch (biome) {
            case ERIADOR_PLAINS, ARNOR_PLAINS, GONDOR_PLAINS, DALE_PLAINS,
                 ROHAN_GRASSLAND, RHUN_GRASSLAND, EASTERN_RHOVANIAN_GRASSLAND,
                 HARAD_DESERT, LINDON_MEADOW, HARAD_SAVANNA,
                 VALE_OF_ANDUIN_FLOODPLAINS, ARNOR_MARSH,
                 DEAD_LANDS_EMPTY, THE_SHIRE, SEA_OF_RHUN -> true;
            default -> false;
        };
    }

    private boolean isForestBiome(LOTRBiome biome) {
        return switch (biome) {
            case MIRKWOOD, FANGORN_FOREST,
                 ERIADOR_MIXED_FOREST, ERIADOR_OLD_FOREST,
                 ARNOR_OLD_FOREST,
                 GONDOR_OLIVE_FOREST,
                 DALE_MIXED_FOREST,
                 LINDON_BEECH_FOREST,
                 HARAD_JUNGLE,
                 LOTHLORIEN -> true;
            default -> false;
        };
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    private LOTRBiome getBiomeAt(int worldX, int worldZ) {
        if (!(getBiomeSource() instanceof MiddleEarthBiomeSource src)) return null;
        return src.getLOTRBiomeAt(worldX, worldZ);
    }

    private double bilinearInterp(double v00, double v10, double v01, double v11, double fx, double fz) {
        double v0 = v00 * (1.0 - fx) + v10 * fx;
        double v1 = v01 * (1.0 - fx) + v11 * fx;
        return v0 * (1.0 - fz) + v1 * fz;
    }

    // ======================================================================
    // OVERRIDES
    // ======================================================================

    @Override
    public int getSeaLevel() { return SEA_LEVEL; }

    @Override
    public int getMinY() { return -64; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState random) {
        double baseLand = getBaseLandHeight(x, z);
        WaterColumn water = computeWater(x, z, baseLand);
        // Surface heightmaps should sit on top of the water; floor heightmaps on the bed.
        boolean wantsSurface = heightmapType == Heightmap.Types.WORLD_SURFACE
                || heightmapType == Heightmap.Types.WORLD_SURFACE_WG
                || heightmapType == Heightmap.Types.MOTION_BLOCKING
                || heightmapType == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        if (wantsSurface && water.hasWater()) {
            return water.waterSurface;
        }
        return water.floor;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        double baseLand = getBaseLandHeight(x, z);
        WaterColumn water = computeWater(x, z, baseLand);
        int floor = water.floor;
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= floor) {
                states[i] = y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            } else if (water.hasWater() && y <= water.waterSurface) {
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
        info.add("Landmask: " + LandmaskLoader.isLoaded());
        info.add("Region map: " + RegionMapLoader.isLoaded());

        if (RegionMapLoader.isLoaded()) {
            Region region = RegionMapLoader.getRegion(pos.getX(), pos.getZ());
            info.add("Region: " + region.getDisplayName());
        }

        LOTRBiome biome = getBiomeAt(pos.getX(), pos.getZ());
        if (biome != null) {
            info.add("LOTR Biome: " + biome.getName());
        }

        if (LandmaskLoader.isLoaded()) {
            double brightness = LandmaskLoader.getInterpolatedBrightness(pos.getX(), pos.getZ());
            info.add(String.format("Landmask brightness: %.1f", brightness));
        }

        double baseLand = getBaseLandHeight(pos.getX(), pos.getZ());
        WaterColumn water = computeWater(pos.getX(), pos.getZ(), baseLand);
        info.add("Terrain height: " + water.floor);
        if (water.hasWater()) {
            info.add((water.isLake ? "Lake" : "River") + " surface: " + water.waterSurface);
        }
    }
}
