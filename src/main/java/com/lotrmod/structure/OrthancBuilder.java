package com.lotrmod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Procedural generator for Orthanc — the black tower of Isengard.
 *
 * <p>The plan is a <b>cruciform (+)</b>: four arms reach out along the cardinal axes and
 * meet at right angles around a central core, leaving deep right-angled re-entrant notches
 * at the four diagonal corners (exactly the silhouette of the Weta model). The tower is a
 * genuine <b>hollow shell</b> — thick fluted walls around an open interior with floors and a
 * spiral stair — not a solid mass. It is ringed by protruding string-courses, pierced by rows
 * of gothic pointed-arch windows, and crowned at the summit by pointed gables and four thin,
 * splayed, blade-like horns around a small pinnacle.
 *
 * <h2>Stair facing convention</h2>
 * Vanilla stairs set {@code facing} to the placer's look direction (no {@code getOpposite}),
 * so a stair's {@code facing} is its <em>full-height</em> side and you climb a bottom stair
 * <em>toward</em> its facing. The internal spiral is the only staircase, and every step faces
 * its direction of travel (= the way you ascend). The base is a stepped plinth of solid blocks
 * and slabs — no facing-sensitive stairs on the exterior.
 */
public final class OrthancBuilder {

    private OrthancBuilder() {}

    // ── Geometry ────────────────────────────────────────────────────────────────
    /** Arm half-width (the cross-bars are 2·W+1 wide). */
    static final int W_BASE = 5, W_TOP = 4;
    /** Arm half-length / reach from the axis. */
    static final int L_BASE = 10, L_TOP = 6;
    /** Shell wall thickness. */
    static final int WALL_T = 2;
    /** Height of the fused shaft. */
    public static final int SHAFT_H = 112;
    /** Height of crown + horns above the shaft. */
    public static final int HORN_H = 34;
    /** Spacing of protruding horizontal string-courses. */
    static final int TIER_STEP = 14;
    /** Spacing of interior floors. */
    static final int FLOOR_STEP = 14;
    /** Vertical fluting rib spacing. */
    static final int RIB_STEP = 3;
    /** Depth of the sunk foundation. */
    static final int FOUNDATION_D = 6;
    /** Chebyshev radius of the internal spiral stair path. */
    static final int SPIRAL_R = 2;

    public static int build(ServerLevel level, BlockPos origin) {
        int placed = 0;
        placed += buildFoundation(level, origin);
        placed += buildPlinth(level, origin);
        placed += buildShaft(level, origin);
        placed += buildFloors(level, origin);
        placed += buildCentralColumn(level, origin);
        placed += buildSpiralStair(level, origin);
        placed += buildWindows(level, origin);
        placed += buildTop(level, origin);
        return placed;
    }

    // ── Cross-section membership ────────────────────────────────────────────────

    /** Is (dx,dz) inside the cruciform plan with arm half-width {@code w} and reach {@code L}? */
    private static boolean insideCross(int dx, int dz, int w, int L) {
        int adx = Math.abs(dx), adz = Math.abs(dz);
        return (adz <= w && adx <= L) || (adx <= w && adz <= L);
    }

    private static int wAt(int y) { return lerp(W_BASE, W_TOP, y / (double) SHAFT_H); }
    private static int lAt(int y) { return lerp(L_BASE, L_TOP, y / (double) SHAFT_H); }

    // ── Foundation & plinth ─────────────────────────────────────────────────────

    private static int buildFoundation(ServerLevel level, BlockPos origin) {
        int placed = 0;
        for (int y = -FOUNDATION_D; y < 0; y++) {
            int fr = L_BASE + 1 - (y + FOUNDATION_D);
            fr = Math.max(L_BASE - 1, Math.min(L_BASE + 1, fr));
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

    /** A stepped pedestal at the foot — concentric square rings, widest at the bottom. */
    private static int buildPlinth(ServerLevel level, BlockPos origin) {
        int placed = 0;
        int[] sizes = { L_BASE + 2, L_BASE + 1, L_BASE };
        for (int s = 0; s < sizes.length; s++) {
            int r = sizes[s];
            int y = s; // step up as it narrows
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring only
                    if (set(level, origin.getX() + dx, origin.getY() + y, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
                    // slab nosing on the tread of each step
                    if (set(level, origin.getX() + dx, origin.getY() + y + 1, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM))) placed++;
                }
            }
        }
        return placed;
    }

    // ── Shaft: hollow fluted cruciform shell + string-courses ───────────────────

    private static int buildShaft(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int reach = L_BASE + 2;

        for (int y = 0; y <= SHAFT_H; y++) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            int w = wAt(y), L = lAt(y);
            boolean band = (y % TIER_STEP) < 2 && y > 2 && y < SHAFT_H - 2;

            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    boolean outer = insideCross(dx, dz, w, L);
                    boolean interior = insideCross(dx, dz, w - WALL_T, L - WALL_T);

                    if (outer && !interior) {
                        // Shell wall. Outer skin is fluted (recessed between ribs).
                        boolean surface = !insideCross(dx, dz, w - 1, L - 1);
                        if (!surface || isRib(dx, dz, w, L)) {
                            if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                    wallPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                        } else if (setAir(level, origin.getX() + dx, wy, origin.getZ() + dz)) {
                            placed++; // carve the flute groove
                        }
                    } else if (interior) {
                        // Hollow it out (cleared of any intruding terrain).
                        if (setAir(level, origin.getX() + dx, wy, origin.getZ() + dz)) placed++;
                    } else if (Math.max(Math.abs(dx), Math.abs(dz)) <= L) {
                        // Clear the diagonal notches of terrain.
                        if (setAir(level, origin.getX() + dx, wy, origin.getZ() + dz)) placed++;
                    }

                    // Protruding string-course: a proud ring of band blocks.
                    if (band && insideCross(dx, dz, w + 1, L + 1) && !insideCross(dx, dz, w - 1, L - 1)) {
                        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                bandPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** Is this outer-surface cell on a fluting rib line (kept proud) rather than recessed? */
    private static boolean isRib(int dx, int dz, int w, int L) {
        boolean xFace = !insideCross(dx + 1, dz, w, L) || !insideCross(dx - 1, dz, w, L);
        boolean zFace = !insideCross(dx, dz + 1, w, L) || !insideCross(dx, dz - 1, w, L);
        if (xFace && zFace) return true;                 // arris / corner edge: always kept
        if (xFace) return Math.floorMod(dz, RIB_STEP) == 0;
        if (zFace) return Math.floorMod(dx, RIB_STEP) == 0;
        return true;
    }

    // ── Interior floors ─────────────────────────────────────────────────────────

    private static int buildFloors(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        for (int y = FLOOR_STEP; y < SHAFT_H - 4; y += FLOOR_STEP) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            int w = wAt(y), L = lAt(y);
            for (int dx = -L; dx <= L; dx++) {
                for (int dz = -L; dz <= L; dz++) {
                    if (!insideCross(dx, dz, w - WALL_T, L - WALL_T)) continue;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= SPIRAL_R) continue; // stairwell hole
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())) placed++;
                }
            }
        }
        return placed;
    }

    // ── Central column + spiral stair ───────────────────────────────────────────

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

    private static int buildSpiralStair(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int s = SPIRAL_R;
        final int top = SHAFT_H - 4;
        int y = 1;

        outer:
        while (y < top) {
            for (int x = -s; x <= s; x++) {                 // north side → east
                placed += step(level, origin, x, y, -s, Direction.EAST, maxBuild);
                if (++y >= top) break outer;
            }
            for (int z = -s + 1; z <= s; z++) {             // east side → south
                placed += step(level, origin, s, y, z, Direction.SOUTH, maxBuild);
                if (++y >= top) break outer;
            }
            for (int x = s - 1; x >= -s; x--) {             // south side → west
                placed += step(level, origin, x, y, s, Direction.WEST, maxBuild);
                if (++y >= top) break outer;
            }
            for (int z = s - 1; z >= -s + 1; z--) {         // west side → north
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

    // ── Gothic pointed-arch windows ─────────────────────────────────────────────

    /** Rows of mullioned, pointed-arch windows pierced into the four arm-tip faces. */
    private static int buildWindows(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        for (int y = 18; y < SHAFT_H - 16; y += 22) {
            if (origin.getY() + y + 6 > maxBuild) break;
            int L = lAt(y);
            placed += window(level, origin, y, L, Direction.EAST);
            placed += window(level, origin, y, L, Direction.WEST);
            placed += window(level, origin, y, L, Direction.SOUTH);
            placed += window(level, origin, y, L, Direction.NORTH);
        }
        return placed;
    }

    /** One tall window with a central mullion and a pointed arch, on the given arm tip. */
    private static int window(ServerLevel level, BlockPos origin, int y, int L, Direction face) {
        int placed = 0;
        boolean xAxis = face == Direction.EAST || face == Direction.WEST;
        int sign = (face == Direction.EAST || face == Direction.SOUTH) ? 1 : -1;
        for (int t = -1; t <= 1; t++) {                 // tangential across the face
            for (int h = 0; h <= 4; h++) {              // height of the opening
                boolean arch = h == 4 && t != 0;        // pointed top: cut the upper corners
                boolean mullion = t == 0 && h <= 3;     // central stone bar (tracery)
                for (int d = L - WALL_T; d <= L; d++) { // pierce through the wall depth
                    int dx = xAxis ? sign * d : t;
                    int dz = xAxis ? t : sign * d;
                    int wy = origin.getY() + y + h;
                    if (arch) continue;                 // leave the corner stone (arch shoulder)
                    if (mullion && d >= L - 1) {         // keep the outer mullion stone
                        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState())) placed++;
                        continue;
                    }
                    if (setAir(level, origin.getX() + dx, wy, origin.getZ() + dz)) placed++;
                }
            }
        }
        // A soul-lantern set in the sill for an inner glow.
        int dx0 = xAxis ? sign * (L - WALL_T - 1) : 0;
        int dz0 = xAxis ? 0 : sign * (L - WALL_T - 1);
        if (set(level, origin.getX() + dx0, origin.getY() + y, origin.getZ() + dz0,
                Blocks.SOUL_LANTERN.defaultBlockState())) placed++;
        return placed;
    }

    // ── Crown, gables & horns ───────────────────────────────────────────────────

    private static int buildTop(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;

        // (1) Crown: a chunky square cap that bridges over the diagonal notches, giving the
        //     corner horns a solid base to spring from. Battlemented (toothed) at the top.
        final int crownH = 9;
        for (int hy = 0; hy < crownH; hy++) {
            int wy = origin.getY() + SHAFT_H + hy;
            if (wy > maxBuild) return placed;
            int cs = Math.max(W_TOP, L_TOP - hy / 4);          // square half-size, tapering
            for (int dx = -cs; dx <= cs; dx++) {
                for (int dz = -cs; dz <= cs; dz++) {
                    int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean ring = cheb >= cs - 1;             // 2-thick parapet wall
                    boolean floor = hy == 0;                   // solid platform at the base
                    if (!ring && !floor) continue;
                    boolean merlon = Math.floorMod(dx + dz, 2) == 0;
                    if (hy == crownH - 1 && !merlon) continue; // toothed top edge
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            pillarPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                }
            }
        }

        // (2) Pointed gables over each cardinal arm tip.
        placed += gable(level, origin, Direction.EAST);
        placed += gable(level, origin, Direction.WEST);
        placed += gable(level, origin, Direction.SOUTH);
        placed += gable(level, origin, Direction.NORTH);

        // (3) Four thin, splayed blade-horns at the diagonal corners.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                placed += horn(level, origin, sx, sz);
            }
        }

        // (4) Pinnacle platform + palantír dais between the horns.
        placed += buildPinnacle(level, origin);
        return placed;
    }

    /** A triangular pointed pediment standing on an arm tip. */
    private static int gable(ServerLevel level, BlockPos origin, Direction face) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        boolean xAxis = face == Direction.EAST || face == Direction.WEST;
        int sign = (face == Direction.EAST || face == Direction.SOUTH) ? 1 : -1;
        int d = L_TOP; // sit at the arm tip
        int gh = W_TOP; // gable height = half-width
        for (int h = 0; h <= gh; h++) {
            int half = gh - h; // narrows to a point
            for (int t = -half; t <= half; t++) {
                int dx = xAxis ? sign * d : t;
                int dz = xAxis ? t : sign * d;
                int wy = origin.getY() + SHAFT_H + h;
                if (wy > maxBuild) continue;
                BlockState b = (h == half) ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                if (set(level, origin.getX() + dx, wy, origin.getZ() + dz, b)) placed++;
            }
        }
        return placed;
    }

    /** One thin blade-horn that leans outward along its diagonal and tapers to a barbed point. */
    private static int horn(ServerLevel level, BlockPos origin, int sx, int sz) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int len = HORN_H;
        final int d0 = L_TOP;       // spring from the square crown's corner
        final int lean = 11;        // total outward splay
        for (int hy = 0; hy <= len; hy++) {
            double f = hy / (double) len;
            int out = (int) Math.round(lean * Math.pow(f, 1.3));
            int cx = sx * (d0 + out);
            int cz = sz * (d0 + out);
            int hs = (f < 0.45) ? 1 : 0;                 // chunky base → single-block blade
            int wy = origin.getY() + SHAFT_H + 2 + hy;
            if (wy > maxBuild) break;
            for (int ax = -hs; ax <= hs; ax++) {
                for (int az = -hs; az <= hs; az++) {
                    if (set(level, origin.getX() + cx + ax, wy, origin.getZ() + cz + az,
                            pillarPalette(origin.getX() + cx + ax, wy, origin.getZ() + cz + az))) placed++;
                }
            }
            // Serrated outer edge: a jag every other course on the leading (diagonal) side.
            if (hy % 2 == 0 && f > 0.2) {
                if (set(level, origin.getX() + cx + sx, wy, origin.getZ() + cz + sz,
                        pillarPalette(origin.getX() + cx + sx, wy, origin.getZ() + cz + sz))) placed++;
            }
        }
        return placed;
    }

    private static int buildPinnacle(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int maxBuild = level.getMaxBuildHeight() - 1;
        int wy = origin.getY() + SHAFT_H + 2;
        if (wy > maxBuild) return 0;
        int r = W_TOP - 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                        Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (set(level, origin.getX() + dx, wy + 1, origin.getZ() + dz, Blocks.OBSIDIAN.defaultBlockState())) placed++;
            }
        }
        if (set(level, origin.getX(), wy + 2, origin.getZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState())) placed++;
        return placed;
    }

    // ── Palettes ────────────────────────────────────────────────────────────────

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
