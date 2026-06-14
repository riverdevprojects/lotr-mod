package com.lotrmod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Procedural generator for Orthanc — the black tower of Isengard.
 *
 * <p>Modelled on the Weta statue: not a square box but <em>four separate pillars</em>
 * clustered around a hollow central stair-shaft, fused near the core and separated on the
 * four cardinal faces by deep vertical clefts. Each pillar is covered in fine vertical
 * fluting and ringed by protruding horizontal string-courses; the whole mass tapers as it
 * climbs and, at the summit, the four pillars flare into a crenellated crown and split into
 * four clawed horns around a small pinnacle platform (where the palantír once sat).
 *
 * <p>Geometry is generated cell-by-cell from a handful of rules so the tower can be raised
 * anywhere with one command.
 *
 * <h2>Stair facing convention (important!)</h2>
 * A stair's {@code facing} is the direction of its <em>full-height</em> side; you climb a
 * bottom stair <em>toward</em> its facing. We rely on this everywhere:
 * <ul>
 *   <li>Talus skirt — bottom stairs facing <em>inward</em> so the slope leans up against the
 *       wall: a battered fortress base.</li>
 *   <li>Spiral stair — every step faces its direction of travel (= the way you ascend).</li>
 *   <li>Horn barbs — bottom stairs facing <em>outward</em> so the claw juts from the tip.</li>
 * </ul>
 */
public final class OrthancBuilder {

    private OrthancBuilder() {}

    // ── Geometry ────────────────────────────────────────────────────────────────
    /** Pillar-centre offset from the axis (the four pillars sit at (±c, ±c)). */
    static final int C_BASE = 6, C_TOP = 3;
    /** Pillar half-size. */
    static final int P_BASE = 4, P_TOP = 2;
    /** Central hollow stair-shaft: outer Chebyshev radius and wall thickness. */
    static final int CORE_OUT = 4, CORE_WT = 2;
    /** Height of the fused shaft (y = 0 .. SHAFT_H). */
    public static final int SHAFT_H = 112;
    /** Height of the crown + horns above the shaft. */
    public static final int HORN_H = 34;
    /** Spacing of protruding horizontal string-courses. */
    static final int TIER_STEP = 12;
    /** Spacing of the vertical fluting ribs on each pillar. */
    static final int RIB_STEP = 3;
    /** Depth the stepped foundation is sunk below the foot. */
    static final int FOUNDATION_D = 6;
    /** Chebyshev radius of the internal spiral stair path. */
    static final int SPIRAL_R = 2;

    /**
     * Raise Orthanc with its foot centred on {@code origin}.
     *
     * @return the number of blocks placed.
     */
    public static int build(ServerLevel level, BlockPos origin) {
        int placed = 0;
        placed += buildFoundation(level, origin);
        placed += buildShaft(level, origin);
        placed += buildCentralColumn(level, origin);
        placed += buildSpiralStair(level, origin);
        placed += buildCleftLights(level, origin);
        placed += buildCrownAndHorns(level, origin);
        placed += buildPinnacle(level, origin);
        placed += buildTalus(level, origin);
        placed += buildDoorway(level, origin);
        return placed;
    }

    // ── Foundation ──────────────────────────────────────────────────────────────

    private static int buildFoundation(ServerLevel level, BlockPos origin) {
        int placed = 0;
        for (int y = -FOUNDATION_D; y < 0; y++) {
            int fr = (C_BASE + P_BASE) - (y + FOUNDATION_D); // widest at the very bottom
            fr = Math.max(C_BASE + P_BASE - 1, Math.min(C_BASE + P_BASE + 1, fr));
            for (int dx = -fr; dx <= fr; dx++) {
                for (int dz = -fr; dz <= fr; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > fr) continue;
                    if (set(level, origin.getX() + dx, origin.getY() + y, origin.getZ() + dz,
                            foundationPalette(origin.getX() + dx, origin.getY() + y, origin.getZ() + dz))) placed++;
                }
            }
        }
        return placed;
    }

    // ── Shaft: four fluted pillars + hollow core + deep clefts + string-courses ──

    private static int buildShaft(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int reach = C_BASE + P_BASE + 2;

        for (int y = 0; y <= SHAFT_H; y++) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            double t = y / (double) SHAFT_H;
            int c = lerp(C_BASE, C_TOP, t);
            int p = lerp(P_BASE, P_TOP, t);
            boolean band = (y % TIER_STEP) < 2 && y > 2 && y < SHAFT_H - 2;
            int pEff = band ? p + 1 : p;

            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                    if (pillarSolid(dx, dz, c, pEff, band)) {
                        BlockState s = band
                                ? bandPalette(origin.getX() + dx, wy, origin.getZ() + dz)
                                : pillarPalette(origin.getX() + dx, wy, origin.getZ() + dz);
                        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz, s)) placed++;
                    } else if (cheb <= CORE_OUT && cheb > CORE_OUT - CORE_WT) {
                        // central shaft wall
                        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                wallPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                    } else if (cheb <= c + p + 1) {
                        // interior shaft + open clefts: clear of any intruding terrain
                        if (setAir(level, origin.getX() + dx, wy, origin.getZ() + dz)) placed++;
                    }
                }
            }
        }
        return placed;
    }

    /**
     * Is (dx,dz) part of one of the four pillars? Pillars are solid; their exposed outer
     * faces are fluted (every {@link #RIB_STEP} a rib stands proud, the rest recess by one),
     * except on string-course bands where the full, un-fluted profile is used.
     */
    private static boolean pillarSolid(int dx, int dz, int c, int p, boolean band) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int lx = Math.abs(dx - sx * c);
                int lz = Math.abs(dz - sz * c);
                if (lx > p || lz > p) continue;
                if (band) return true;                 // full profile on string-courses
                boolean bx = lx == p, bz = lz == p;
                if (!bx && !bz) return true;            // pillar interior
                if (bx && bz) return true;              // pillar arris (corner edge)
                if (bx && Math.floorMod(dz, RIB_STEP) == 0) return true; // rib on x-face
                if (bz && Math.floorMod(dx, RIB_STEP) == 0) return true; // rib on z-face
                // otherwise this is a recessed flute on this pillar; another pillar may
                // still cover the cell, so keep checking.
            }
        }
        return false;
    }

    // ── Central column (spine for the spiral) ───────────────────────────────────

    private static int buildCentralColumn(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        for (int y = 0; y <= SHAFT_H; y++) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > 1) continue;
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            pillarPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                }
            }
        }
        return placed;
    }

    // ── Internal spiral staircase ───────────────────────────────────────────────

    private static int buildSpiralStair(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int s = SPIRAL_R;
        final int top = SHAFT_H - 4;
        int y = 1;

        outer:
        while (y < top) {
            for (int x = -s; x <= s; x++) {                  // north side, → east
                placed += step(level, origin, x, y, -s, Direction.EAST, maxBuild);
                if (++y >= top) break outer;
            }
            for (int z = -s + 1; z <= s; z++) {              // east side, → south
                placed += step(level, origin, s, y, z, Direction.SOUTH, maxBuild);
                if (++y >= top) break outer;
            }
            for (int x = s - 1; x >= -s; x--) {              // south side, → west
                placed += step(level, origin, x, y, s, Direction.WEST, maxBuild);
                if (++y >= top) break outer;
            }
            for (int z = s - 1; z >= -s + 1; z--) {          // west side, → north
                placed += step(level, origin, -s, y, z, Direction.NORTH, maxBuild);
                if (++y >= top) break outer;
            }
        }
        return placed;
    }

    private static int step(ServerLevel level, BlockPos origin, int dx, int y, int dz, Direction travel, int maxBuild) {
        int wy = origin.getY() + y;
        if (wy > maxBuild) return 0;
        int placed = 0;
        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                stair(Blocks.POLISHED_BLACKSTONE_STAIRS, travel, Half.BOTTOM))) placed++;
        if (set(level, origin.getX() + dx, wy - 1, origin.getZ() + dz,
                Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
        return placed;
    }

    // ── Glowing clefts ──────────────────────────────────────────────────────────

    /** Soul-lanterns recessed in the deep cardinal clefts, glowing up the four channels. */
    private static int buildCleftLights(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        int d = CORE_OUT + 1;
        for (int y = 8; y < SHAFT_H - 8; y += 8) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) continue;
            placed += lantern(level, origin, d, wy, 0);
            placed += lantern(level, origin, -d, wy, 0);
            placed += lantern(level, origin, 0, wy, d);
            placed += lantern(level, origin, 0, wy, -d);
        }
        return placed;
    }

    private static int lantern(ServerLevel level, BlockPos origin, int dx, int wy, int dz) {
        return set(level, origin.getX() + dx, wy, origin.getZ() + dz, Blocks.SOUL_LANTERN.defaultBlockState()) ? 1 : 0;
    }

    // ── Crown + horns ───────────────────────────────────────────────────────────

    /**
     * The summit: the four pillars flare into a crenellated crown (the chunky, jagged base)
     * and taper into four horns that splay outward and curl to sharp, barbed points.
     */
    private static int buildCrownAndHorns(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;

        for (int hy = 0; hy <= HORN_H; hy++) {
            int wy = origin.getY() + SHAFT_H + hy;
            if (wy > maxBuild) break;
            double t = hy / (double) HORN_H;
            int hs = lerp(3, 0, t);                                 // crown 7-wide → 1-wide tip
            int lean = (int) Math.round(8 * Math.pow(t, 1.4));      // splay outward, curling up
            int base = C_TOP + lean;

            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = sx * base, cz = sz * base;
                    for (int dx = cx - hs; dx <= cx + hs; dx++) {
                        for (int dz = cz - hs; dz <= cz + hs; dz++) {
                            if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                    pillarPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                        }
                    }
                    // Jagged teeth on the crown base: raise the outer corner of each lump.
                    if (hy <= 2) {
                        int tx = cx + sx * hs, tz = cz + sz * hs;
                        if (set(level, origin.getX() + tx, wy + 1, origin.getZ() + tz,
                                pillarPalette(origin.getX() + tx, wy + 1, origin.getZ() + tz))) placed++;
                    }
                    // Claw barb jutting from the outer face, two short of the tip.
                    if (hy == HORN_H - 2) {
                        Direction out = (Math.abs(cx) >= Math.abs(cz))
                                ? (sx > 0 ? Direction.EAST : Direction.WEST)
                                : (sz > 0 ? Direction.SOUTH : Direction.NORTH);
                        int bx = cx + (out == Direction.EAST ? 1 : out == Direction.WEST ? -1 : 0);
                        int bz = cz + (out == Direction.SOUTH ? 1 : out == Direction.NORTH ? -1 : 0);
                        if (set(level, origin.getX() + bx, wy, origin.getZ() + bz,
                                stair(Blocks.BLACKSTONE_STAIRS, out, Half.BOTTOM))) placed++;
                    }
                }
            }
        }
        return placed;
    }

    // ── Pinnacle platform ───────────────────────────────────────────────────────

    private static int buildPinnacle(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        int wy = origin.getY() + SHAFT_H;
        if (wy > maxBuild) return 0;
        int r = CORE_OUT;

        // Floor + crenellated parapet between the four horn-roots.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb > r) continue;
                if (cheb == r) {
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())) placed++;
                    if (Math.floorMod(dx + dz, 2) == 0 && wy + 1 <= maxBuild
                            && set(level, origin.getX() + dx, wy + 1, origin.getZ() + dz,
                            Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState())) placed++;
                } else if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                        Blocks.POLISHED_BLACKSTONE.defaultBlockState())) {
                    placed++;
                }
            }
        }
        // Central dais — the seat of the palantír.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (set(level, origin.getX() + dx, wy + 1, origin.getZ() + dz, Blocks.OBSIDIAN.defaultBlockState())) placed++;
            }
        }
        if (set(level, origin.getX(), wy + 2, origin.getZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState())) placed++;
        return placed;
    }

    // ── Battered base (talus) ───────────────────────────────────────────────────

    private static int buildTalus(ServerLevel level, BlockPos origin) {
        int placed = 0;
        int r = C_BASE + P_BASE + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                Direction inward = inwardFace(dx, dz);
                if (inward == null) continue;
                if (set(level, origin.getX() + dx, origin.getY(), origin.getZ() + dz,
                        stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, inward, Half.BOTTOM))) placed++;
                if (set(level, origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz,
                        Blocks.BLACKSTONE.defaultBlockState())) placed++;
            }
        }
        return placed;
    }

    // ── Great doors ─────────────────────────────────────────────────────────────

    /** A tall arched gateway through the south cleft into the central stair-shaft. */
    private static int buildDoorway(ServerLevel level, BlockPos origin) {
        int placed = 0;
        int zOuter = C_BASE + P_BASE; // mouth of the south cleft
        // Punch the passage from the cleft, through the core wall, to the shaft.
        for (int dz = CORE_OUT - CORE_WT; dz <= zOuter; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 0; y <= 5; y++) {
                    boolean opening = !(y == 5 && Math.abs(dx) == 1); // pointed arch top
                    if (opening && setAir(level, origin.getX() + dx, origin.getY() + y, origin.getZ() + dz)) placed++;
                }
            }
        }
        // Frame the mouth on the core wall (chiselled jambs, gilded keystone).
        int zf = CORE_OUT;
        for (int y = 0; y <= 6; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean frame = Math.abs(dx) == 2 || y == 6 || (y == 5 && Math.abs(dx) == 1);
                if (!frame) continue;
                BlockState b = (y >= 5 && dx == 0)
                        ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                        : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                if (set(level, origin.getX() + dx, origin.getY() + y, origin.getZ() + zf, b)) placed++;
            }
        }
        // Threshold step out of the cleft.
        for (int dx = -1; dx <= 1; dx++) {
            if (set(level, origin.getX() + dx, origin.getY() - 1, origin.getZ() + zOuter,
                    Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
        }
        return placed;
    }

    // ── Palettes (deterministic, position-hashed for stable texture) ────────────

    private static BlockState wallPalette(int x, int y, int z) {
        int r = Math.floorMod(hash(x, y, z), 100);
        if (r < 50) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 75) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (r < 87) return Blocks.BLACKSTONE.defaultBlockState();
        if (r < 95) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (r < 99) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        return Blocks.GILDED_BLACKSTONE.defaultBlockState();
    }

    private static BlockState pillarPalette(int x, int y, int z) {
        int r = Math.floorMod(hash(x, y, z), 100);
        if (r < 64) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (r < 84) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 94) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 98) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    private static BlockState bandPalette(int x, int y, int z) {
        int r = Math.floorMod(hash(x, y, z), 100);
        if (r < 70) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 90) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        return Blocks.GILDED_BLACKSTONE.defaultBlockState();
    }

    private static BlockState foundationPalette(int x, int y, int z) {
        int r = Math.floorMod(hash(x, y, z), 100);
        if (r < 55) return Blocks.BLACKSTONE.defaultBlockState();
        if (r < 80) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 95) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static BlockState stair(net.minecraft.world.level.block.Block block, Direction facing, Half half) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    /** The cardinal direction pointing from a perimeter cell back toward the tower centre. */
    private static Direction inwardFace(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
        if (Math.abs(dz) > Math.abs(dx)) return dz > 0 ? Direction.NORTH : Direction.SOUTH;
        return null;
    }

    private static boolean set(ServerLevel level, int x, int y, int z, BlockState state) {
        return level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static boolean setAir(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).isAir()) return false;
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093;
        h ^= y * 19349663;
        h ^= z * 83492791;
        h ^= (h >>> 13);
        return h;
    }
}
