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

    // Used for cave generation and biome jitter
    private final PerlinSimplexNoise caveNoise1;
    private final PerlinSimplexNoise caveNoise2;
    private final PerlinSimplexNoise coastlineNoise;

    private static final int SEA_LEVEL = 63;
    // Terrain is always kept above this — no water, no sub-surface voids
    private static final int MIN_TERRAIN_HEIGHT = 65;

    public MiddleEarthChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);

        RandomSource r1 = RandomSource.create(12345L);
        this.continentalNoise  = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3, 4));
        this.erosionNoise      = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));
        this.peaksValleysNoise = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));
        this.detailNoise       = new PerlinSimplexNoise(r1, List.of(0, 1, 2));
        this.ridgeNoise        = new PerlinSimplexNoise(r1, List.of(0, 1, 2, 3));

        RandomSource r2 = RandomSource.create(98765L);
        this.caveNoise1    = new PerlinSimplexNoise(r2, List.of(0, 1, 2, 3));
        this.caveNoise2    = new PerlinSimplexNoise(r2, List.of(0, 1, 2, 3));
        this.coastlineNoise = new PerlinSimplexNoise(r2, List.of(0, 1, 2));
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
                int terrainHeight = getTerrainHeight(worldX, worldZ);

                // Slight position jitter so biome surface boundaries look organic
                double jScale = 1.0 / 40.0;
                int jx = (int)(detailNoise.getValue(worldX * jScale, worldZ * jScale, false) * 14.0);
                int jz = (int)(detailNoise.getValue((worldX + 7919) * jScale, worldZ * jScale, false) * 14.0);
                LOTRBiome biome = getBiomeAt(worldX + jx, worldZ + jz);

                for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight(); y--) {
                    pos.set(startX + x, y, startZ + z);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.is(Blocks.STONE)) {
                        // Snow cap on high peaks
                        boolean isHighPeak = terrainHeight >= 160;
                        BlockState surfaceBlock = isHighPeak
                                ? Blocks.SNOW_BLOCK.defaultBlockState()
                                : getSurfaceBlockForBiome(biome, terrainHeight, y);
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
        // Cave generation is handled in fillFromNoise via isNoiseCaveAt().
        // Suppressing the parent call prevents the vanilla aquifer from flooding caves.
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
                int height = getTerrainHeight(worldX, worldZ);

                for (int y = chunk.getMinBuildHeight(); y <= Math.min(height, chunk.getMaxBuildHeight() - 1); y++) {
                    pos.set(startX + x, y, startZ + z);

                    if (y <= chunk.getMinBuildHeight() + 5) {
                        boolean isBedrock = (y == chunk.getMinBuildHeight())
                                || (y <= chunk.getMinBuildHeight() + 4 && Math.random() < 0.8);
                        chunk.setBlockState(pos,
                                isBedrock ? Blocks.BEDROCK.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState(),
                                false);
                    } else if (isNoiseCaveAt(worldX, y, worldZ, height)) {
                        chunk.setBlockState(pos, Blocks.CAVE_AIR.defaultBlockState(), false);
                    } else if (y < 0) {
                        chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), false);
                    } else {
                        chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                    }
                }
                // No water filling — the world is entirely dry land.
            }
        }
    }

    // ======================================================================
    // CAVE GENERATION — vanilla-inspired noise caves
    // ======================================================================

    private boolean isNoiseCaveAt(int worldX, int y, int worldZ, int terrainHeight) {
        final int BEDROCK_SAFE_Y = -54;
        if (y <= BEDROCK_SAFE_Y || y >= terrainHeight) return false;

        double relDepth = (double)(terrainHeight - y) / Math.max(1.0, terrainHeight - BEDROCK_SAFE_Y);
        if (relDepth < 0.02) return false;

        // Cheese caves — two orthogonal noise fields; both near zero = open chamber
        double cScale = 1.0 / 90.0;
        double dShift = y * 0.009;
        double c1 = caveNoise1.getValue(worldX * cScale, worldZ * cScale + dShift, false);
        double c2 = caveNoise2.getValue(
                (worldX + 9371) * cScale * 0.9,
                (worldZ + 9371) * cScale * 0.9 - dShift * 1.1,
                false);
        // Chambers grow with depth, matching vanilla behaviour
        double cheeseThresh = 0.05 + relDepth * 0.30;
        if (Math.abs(c1) < cheeseThresh && Math.abs(c2) < cheeseThresh) return true;

        // Spaghetti tunnels — narrow winding passages connecting chambers
        if (relDepth > 0.05) {
            double sScale = 1.0 / 35.0;
            double sShift = y * 0.020;
            double s1 = caveNoise1.getValue(worldX * sScale + sShift, worldZ * sScale - sShift * 0.8, false);
            double s2 = caveNoise2.getValue(
                    (worldX + 4000) * sScale,
                    (worldZ + 4000) * sScale + sShift * 1.3,
                    false);
            double spagThresh = 0.07 + relDepth * 0.05;
            if (Math.abs(s1) < spagThresh && Math.abs(s2) < spagThresh) return true;
        }

        return false;
    }

    // ======================================================================
    // TERRAIN HEIGHT — vanilla-style noise stack
    // ======================================================================

    private int getTerrainHeight(int worldX, int worldZ) {
        return (int) Math.round(getTerrainHeightDouble(worldX, worldZ));
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
    private double getTerrainHeightDouble(int worldX, int worldZ) {

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
        return getTerrainHeight(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int height = getTerrainHeight(x, z);
        BlockState[] states = new BlockState[level.getHeight()];

        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= height) {
                states[i] = y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
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

        info.add("Terrain height: " + getTerrainHeight(pos.getX(), pos.getZ()));
    }
}
