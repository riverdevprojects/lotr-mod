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
 * <p>Orthanc is built as four great pillars of black stone, fused together for most of
 * their height and splitting near the summit into four sharp horns, with a flat pinnacle
 * platform between them (where the palantír once sat). The shaft tapers as it rises and is
 * covered in vertical fluting; a battered talus skirts the base, an overhanging cornice
 * crowns the shaft, and crenellated horns claw at the sky.
 *
 * <p>The whole thing is generated cell-by-cell from a small set of geometry rules so that
 * it can be raised anywhere in the world with a single command. Every stair used —
 * the talus skirt, the cornice eave, the internal spiral stair and the horn barbs — is
 * placed with an explicit, deliberately-chosen {@code facing} so the geometry reads
 * correctly from the ground.
 *
 * <h2>Stair facing convention (important!)</h2>
 * In Minecraft a stair's {@code facing} is the direction of its <em>full-height</em> side;
 * you climb a bottom stair <em>toward</em> its facing. We rely on this throughout:
 * <ul>
 *   <li>Talus skirt — bottom stairs facing <em>inward</em> (toward the tower) so the slope
 *       rises from the ground up to the wall: a battered fortress base.</li>
 *   <li>Cornice eave — top-half stairs facing <em>inward</em> so the overhang juts out and
 *       slopes back up under the parapet.</li>
 *   <li>Spiral stair — each step faces the direction of travel (= the direction you ascend).</li>
 * </ul>
 */
public final class OrthancBuilder {

    private OrthancBuilder() {}

    // ── Geometry ────────────────────────────────────────────────────────────────
    /** Half-width (Chebyshev radius) of the shaft at its foot. */
    static final int R_BASE = 11;
    /** Half-width of the shaft where it meets the pinnacle. */
    static final int R_TOP = 6;
    /** Height of the fused shaft (y = 0 .. SHAFT_H). */
    public static final int SHAFT_H = 96;
    /** Height of the four horns above the shaft. */
    public static final int HORN_H = 40;
    /** How far below the foot the foundation is sunk. */
    static final int FOUNDATION_D = 6;
    /** Spacing of the vertical fluting ribs around the shaft. */
    static final int RIB_STEP = 3;
    /** Vertical spacing of window slits. */
    static final int WINDOW_STEP = 16;
    /** Chebyshev radius of the internal spiral stair path (wraps the central column). */
    static final int SPIRAL_R = 3;

    // ── Classification codes for a shaft cell ───────────────────────────────────
    private static final int NONE = 0;
    private static final int WALL = 1;
    private static final int RIB = 2;
    private static final int CORNER = 3;

    /**
     * Raise Orthanc with its foot centred on {@code origin}.
     *
     * @return the number of blocks placed.
     */
    public static int build(ServerLevel level, BlockPos origin) {
        int placed = 0;
        final int ox = origin.getX();
        final int oy = origin.getY();
        final int oz = origin.getZ();
        final int maxBuild = level.getMaxBuildHeight() - 1;
        final int reach = R_BASE + 4; // horizontal clearing/footprint radius

        // ── Foundation: a solid stepped plinth sunk into the ground ─────────────
        for (int y = -FOUNDATION_D; y < 0; y++) {
            int fr = R_BASE + 2 - (y + FOUNDATION_D); // wider at the very bottom, stepping in as it rises
            fr = Math.max(R_BASE, Math.min(R_BASE + 2, fr));
            for (int dx = -fr; dx <= fr; dx++) {
                for (int dz = -fr; dz <= fr; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > fr) continue;
                    if (set(level, ox + dx, oy + y, oz + dz, foundationPalette(ox + dx, oy + y, oz + dz))) placed++;
                }
            }
        }

        // ── Shaft: tapering, fluted, four-cornered body ─────────────────────────
        for (int y = 0; y <= SHAFT_H; y++) {
            int wy = oy + y;
            if (wy > maxBuild) break;
            int r = radiusAt(y);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int code = classify(dx, dz, r);
                    if (code != NONE) {
                        BlockState state = (code == WALL)
                                ? wallPalette(ox + dx, wy, oz + dz)
                                : pillarPalette(ox + dx, wy, oz + dz);
                        if (set(level, ox + dx, wy, oz + dz, state)) placed++;
                    } else if (Math.max(Math.abs(dx), Math.abs(dz)) <= r + 1) {
                        // carve the interior / silhouette clean of any intruding terrain
                        if (setAir(level, ox + dx, wy, oz + dz)) placed++;
                    }
                }
            }
        }

        // ── Central column (spiral stair core + structural spine) ───────────────
        for (int y = 0; y <= SHAFT_H; y++) {
            int wy = oy + y;
            if (wy > maxBuild) break;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (set(level, ox + dx, wy, oz + dz, pillarPalette(ox + dx, wy, oz + dz))) placed++;
                }
            }
        }

        placed += buildSpiralStair(level, origin, maxBuild);
        placed += buildFloors(level, origin, maxBuild);
        placed += buildHorns(level, origin, maxBuild);
        placed += buildPinnacle(level, origin, maxBuild);
        placed += buildTalus(level, origin);
        placed += buildCornice(level, origin, maxBuild);
        placed += buildWindows(level, origin, maxBuild);
        placed += buildDoorway(level, origin);

        return placed;
    }

    // ── Shaft cell classification ───────────────────────────────────────────────

    /** Half-width of the shaft at height {@code y}, tapering linearly. */
    private static int radiusAt(int y) {
        double t = y / (double) SHAFT_H;
        return (int) Math.round(R_BASE + (R_TOP - R_BASE) * t);
    }

    /**
     * Decide what a cell at offset (dx,dz) is, given the shaft half-width {@code r}.
     * Produces a 2-thick fluted ring with four robust corner pillars and proud ribs.
     */
    private static int classify(int dx, int dz, int r) {
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        int cheb = Math.max(adx, adz);

        // Corner pillars: thickened blocks at the four diagonal corners, proud by one.
        boolean nearCorner = adx >= r - 1 && adz >= r - 1;
        if (nearCorner && cheb <= r + 1) return CORNER;

        // Two-block-thick wall ring.
        if (cheb == r || cheb == r - 1) return WALL;

        // Vertical fluting: ribs standing one block proud of the wall, every RIB_STEP.
        if (cheb == r + 1) {
            if (adx > adz) {
                if (Math.floorMod(dz, RIB_STEP) == 0) return RIB;
            } else if (adz > adx) {
                if (Math.floorMod(dx, RIB_STEP) == 0) return RIB;
            }
        }
        return NONE;
    }

    // ── Internal spiral staircase ───────────────────────────────────────────────

    /**
     * A continuous square-helix stair wrapping the central column. Each step faces the
     * direction of travel, so you genuinely climb it the right way round.
     */
    private static int buildSpiralStair(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        final int s = SPIRAL_R;
        final int top = SHAFT_H - 4;
        int y = 1;

        outer:
        while (y < top) {
            // North side, travelling west → east (facing EAST).
            for (int x = -s; x <= s; x++) {
                placed += step(level, origin, x, y, -s, Direction.EAST, maxBuild);
                if (++y >= top) break outer;
            }
            // East side, travelling north → south (facing SOUTH).
            for (int z = -s + 1; z <= s; z++) {
                placed += step(level, origin, s, y, z, Direction.SOUTH, maxBuild);
                if (++y >= top) break outer;
            }
            // South side, travelling east → west (facing WEST).
            for (int x = s - 1; x >= -s; x--) {
                placed += step(level, origin, x, y, s, Direction.WEST, maxBuild);
                if (++y >= top) break outer;
            }
            // West side, travelling south → north (facing NORTH).
            for (int z = s - 1; z >= -s + 1; z--) {
                placed += step(level, origin, -s, y, z, Direction.NORTH, maxBuild);
                if (++y >= top) break outer;
            }
        }
        return placed;
    }

    /** Place one spiral step (a stair facing its travel direction) plus its support. */
    private static int step(ServerLevel level, BlockPos origin, int dx, int y, int dz, Direction travel, int maxBuild) {
        int wy = origin.getY() + y;
        if (wy > maxBuild) return 0;
        int placed = 0;
        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                stair(Blocks.POLISHED_BLACKSTONE_STAIRS, travel, Half.BOTTOM))) placed++;
        // Solid tread support directly beneath each step.
        if (set(level, origin.getX() + dx, wy - 1, origin.getZ() + dz,
                Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
        return placed;
    }

    // ── Interior floors / landings ──────────────────────────────────────────────

    private static int buildFloors(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        for (int y = 24; y < SHAFT_H; y += 24) {
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            int r = radiusAt(y);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > r - 1) continue;
                    // Leave the spiral shaft open and the central column intact.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= SPIRAL_R) continue;
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())) placed++;
                }
            }
        }
        return placed;
    }

    // ── Horns ───────────────────────────────────────────────────────────────────

    /** The four sharp horns: tapering pillars that splay outward as they rise to a point. */
    private static int buildHorns(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        for (int hy = 0; hy <= HORN_H; hy++) {
            int y = SHAFT_H + hy;
            int wy = origin.getY() + y;
            if (wy > maxBuild) break;
            double t = hy / (double) HORN_H;
            int hs = (int) Math.round(2 * (1 - t));               // half-size 2 → 0
            int lean = (int) Math.round(6 * Math.pow(t, 1.3));    // splay outward near the top
            int base = R_TOP + lean;

            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = sx * base;
                    int cz = sz * base;
                    for (int dx = cx - hs; dx <= cx + hs; dx++) {
                        for (int dz = cz - hs; dz <= cz + hs; dz++) {
                            if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                                    pillarPalette(origin.getX() + dx, wy, origin.getZ() + dz))) placed++;
                        }
                    }
                    // A claw-like barb on the outer face, two below the tip.
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

    /** The flat summit between the horns: floor, crenellated parapet and central dais. */
    private static int buildPinnacle(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        int wy = origin.getY() + SHAFT_H;
        if (wy > maxBuild) return 0;
        int r = R_TOP;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb > r) continue;
                if (cheb == r) {
                    // Crenellated parapet: merlons every other block, raised one higher.
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())) placed++;
                    if (Math.floorMod(dx + dz, 2) == 0 && wy + 1 <= maxBuild) {
                        if (set(level, origin.getX() + dx, wy + 1, origin.getZ() + dz,
                                Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState())) placed++;
                    }
                } else {
                    // Floor.
                    if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                            Blocks.POLISHED_BLACKSTONE.defaultBlockState())) placed++;
                }
            }
        }

        // Central dais — the seat of the palantír.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (set(level, origin.getX() + dx, wy + 1, origin.getZ() + dz,
                        Blocks.OBSIDIAN.defaultBlockState())) placed++;
            }
        }
        if (set(level, origin.getX(), wy + 2, origin.getZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState())) placed++;
        return placed;
    }

    // ── Battered base (talus) ───────────────────────────────────────────────────

    /** A skirt of stairs around the foot, facing inward so the slope leans against the wall. */
    private static int buildTalus(ServerLevel level, BlockPos origin) {
        int placed = 0;
        int r = R_BASE + 1; // one ring outside the foot of the shaft
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb != r) continue;
                Direction inward = inwardFace(dx, dz);
                if (inward == null) continue; // skip exact diagonal corners
                if (set(level, origin.getX() + dx, origin.getY(), origin.getZ() + dz,
                        stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, inward, Half.BOTTOM))) placed++;
                // Solid support beneath the skirt so it never floats over the terrain.
                if (set(level, origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz,
                        Blocks.BLACKSTONE.defaultBlockState())) placed++;
            }
        }
        return placed;
    }

    // ── Overhanging cornice ─────────────────────────────────────────────────────

    /** An eave of upside-down stairs just below the pinnacle, facing inward to overhang. */
    private static int buildCornice(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        int wy = origin.getY() + SHAFT_H - 1;
        if (wy > maxBuild) return 0;
        int r = R_TOP + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb != r) continue;
                Direction inward = inwardFace(dx, dz);
                if (inward == null) continue;
                if (set(level, origin.getX() + dx, wy, origin.getZ() + dz,
                        stair(Blocks.POLISHED_BLACKSTONE_STAIRS, inward, Half.TOP))) placed++;
            }
        }
        return placed;
    }

    // ── Glowing window slits ────────────────────────────────────────────────────

    /** Narrow window slits on each face, lit from within with a soul lantern. */
    private static int buildWindows(ServerLevel level, BlockPos origin, int maxBuild) {
        int placed = 0;
        for (int y = WINDOW_STEP; y < SHAFT_H - 6; y += WINDOW_STEP) {
            int r = radiusAt(y);
            for (int h = 0; h < 3; h++) {
                int wy = origin.getY() + y + h;
                if (wy > maxBuild) continue;
                // +Z and -Z faces (slit offset to x=±1 to sit between ribs),
                // +X and -X faces (offset to z=±1). dx/dz = 0 is a rib column, so avoid it.
                placed += windowCell(level, origin, 1, wy, r, 0, 1);
                placed += windowCell(level, origin, -1, wy, -r, 0, -1);
                placed += windowCell(level, origin, r, wy, 1, 1, 0);
                placed += windowCell(level, origin, -r, wy, -1, -1, 0);
            }
        }
        return placed;
    }

    /** Carve a single window cell: dark glass on the outer face, soul lantern set behind it. */
    private static int windowCell(ServerLevel level, BlockPos origin, int dx, int wy, int dz, int nx, int nz) {
        int placed = 0;
        if (set(level, origin.getX() + dx, wy, origin.getZ() + dz, Blocks.BLACK_STAINED_GLASS.defaultBlockState())) placed++;
        // One block inward (opposite the outward normal): the lantern.
        if (set(level, origin.getX() + dx - nx, wy, origin.getZ() + dz - nz, Blocks.SOUL_LANTERN.defaultBlockState())) placed++;
        return placed;
    }

    // ── Great doors ─────────────────────────────────────────────────────────────

    /** A tall arched gateway through the south face, framed in chiselled and gilded stone. */
    private static int buildDoorway(ServerLevel level, BlockPos origin) {
        int placed = 0;
        int r = radiusAt(0); // foot half-width
        // The south wall sits around dz = +r (.. r+1 ribs). Punch an opening and frame it.
        for (int dz = r - 1; dz <= r + 1; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int y = 0; y <= 6; y++) {
                    boolean opening = Math.abs(dx) <= 1 && (y <= 4 || (y == 5 && dx == 0));
                    int wx = origin.getX() + dx;
                    int wy = origin.getY() + y;
                    int wz = origin.getZ() + dz;
                    if (opening) {
                        if (setAir(level, wx, wy, wz)) placed++;
                    } else if (Math.abs(dx) == 2 || y == 6 || (y == 5 && Math.abs(dx) == 1)) {
                        // Frame: chiselled jambs and lintel with gilded keystone.
                        BlockState frame = (y >= 5 && dx == 0)
                                ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                                : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                        if (dz == r) { // only frame the visible outer skin
                            if (set(level, wx, wy, wz, frame)) placed++;
                        }
                    }
                }
            }
        }
        // A short threshold step out of the door.
        for (int dx = -1; dx <= 1; dx++) {
            if (set(level, origin.getX() + dx, origin.getY() - 1, origin.getZ() + r + 1,
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
        if (r < 68) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (r < 86) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 96) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private static BlockState foundationPalette(int x, int y, int z) {
        int r = Math.floorMod(hash(x, y, z), 100);
        if (r < 55) return Blocks.BLACKSTONE.defaultBlockState();
        if (r < 80) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 95) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Build a straight stair block state with the given facing and half. */
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
        return null; // exact diagonal — ambiguous, skip
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
