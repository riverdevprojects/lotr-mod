package com.lotrmod.structure;

import com.lotrmod.block.ModBlocks;
import com.lotrmod.block.RoundDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Procedural generator for a hobbit-hole (smial) dug into a grassy mound, fronted by a plaster
 * facade with a working round {@link RoundDoorBlock}, flanking round windows, lantern sconces, a
 * smoking chimney, a winding path and a flowery garden.
 *
 * <p>Everything is generated in a local frame relative to the door:
 * <ul>
 *   <li><b>rt</b> — left/right across the facade (the door's right is {@code facing.getClockWise()}),</li>
 *   <li><b>up</b> — height above the threshold,</li>
 *   <li><b>bk</b> — depth into the hill (bk &gt; 0 is inside, bk &lt; 0 is the garden in front).</li>
 * </ul>
 */
public final class HobbitHoleBuilder {

    private HobbitHoleBuilder() {}

    // Mound (half-ellipsoid, front face cut flat at bk = 0).
    static final int MOUND_RX = 12;   // half-width
    static final int MOUND_RY = 8;    // height
    static final int MOUND_RZ = 12;   // depth
    static final int MOUND_CB = 8;    // depth of the dome centre behind the facade

    // Interior room.
    static final int ROOM_HALF_W = 5; // rt extent
    static final int ROOM_DEPTH = 8;  // bk extent
    static final int ROOM_HEIGHT = 4; // up extent

    static final int FACADE_R = 6;    // radius of the plaster facade disc around the door

    public static int build(ServerLevel level, BlockPos frontCenter, Direction facing) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        Ctx ctx = new Ctx(level, frontCenter, right, back);
        int placed = 0;

        placed += clearArea(ctx);
        placed += buildMound(ctx);
        placed += carveInterior(ctx);
        placed += buildFacade(ctx);
        placed += placeDoor(ctx, facing);
        placed += buildWindows(ctx);
        placed += buildSconces(ctx);
        placed += interiorDetails(ctx, facing);
        placed += buildChimney(ctx);
        placed += buildGarden(ctx, facing);
        return placed;
    }

    // ── Terrain clearing ─────────────────────────────────────────────────────────

    private static int clearArea(Ctx c) {
        int placed = 0;
        for (int bk = -8; bk <= MOUND_CB + MOUND_RZ; bk++) {
            for (int rt = -MOUND_RX - 2; rt <= MOUND_RX + 2; rt++) {
                for (int up = 1; up <= MOUND_RY + 3; up++) {
                    if (c.air(rt, up, bk)) placed++;
                }
            }
        }
        return placed;
    }

    // ── Grassy mound ─────────────────────────────────────────────────────────────

    private static int buildMound(Ctx c) {
        int placed = 0;
        for (int bk = 0; bk <= MOUND_CB + MOUND_RZ; bk++) {
            for (int rt = -MOUND_RX; rt <= MOUND_RX; rt++) {
                for (int up = 0; up <= MOUND_RY; up++) {
                    if (!inMound(rt, up, bk)) continue;
                    boolean surface = !inMound(rt, up + 1, bk)
                            || !inMound(rt + 1, up, bk) || !inMound(rt - 1, up, bk)
                            || !inMound(rt, up, bk + 1);
                    BlockState block = surface
                            ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : (c.hash(rt, up, bk) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState()
                                                            : Blocks.DIRT.defaultBlockState());
                    if (c.set(rt, up, bk, block)) placed++;
                }
            }
        }
        // A little foundation under the threshold so it never floats.
        for (int bk = -2; bk <= ROOM_DEPTH; bk++)
            for (int rt = -ROOM_HALF_W - 1; rt <= ROOM_HALF_W + 1; rt++)
                if (c.set(rt, -1, bk, Blocks.DIRT.defaultBlockState())) placed++;
        return placed;
    }

    /** Half-ellipsoid mound, flat-cut at the facade (bk = 0). */
    private static boolean inMound(int rt, int up, int bk) {
        if (bk < 0) return false;
        double e = sq(rt / (double) MOUND_RX) + sq(up / (double) MOUND_RY) + sq((bk - MOUND_CB) / (double) MOUND_RZ);
        return e <= 1.0;
    }

    // ── Hollow interior room ─────────────────────────────────────────────────────

    private static int carveInterior(Ctx c) {
        int placed = 0;
        for (int bk = 1; bk <= ROOM_DEPTH; bk++) {
            for (int rt = -ROOM_HALF_W; rt <= ROOM_HALF_W; rt++) {
                // Wooden floor one block below the threshold, so the room floor is level with it.
                if (inRoom(rt, 0, bk) && c.set(rt, -1, bk, floorPlank(c, rt, bk))) placed++;
                for (int up = 0; up <= ROOM_HEIGHT; up++) {
                    if (!inRoom(rt, up, bk)) continue;
                    boolean wall = !inRoom(rt + 1, up, bk) || !inRoom(rt - 1, up, bk)
                            || !inRoom(rt, up, bk + 1) || !inRoom(rt, up + 1, bk);
                    if (wall) {
                        if (c.set(rt, up, bk, plaster(c, rt, up, bk))) placed++;
                    } else if (c.air(rt, up, bk)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** A rounded (barrel-vaulted) room cross-section. */
    private static boolean inRoom(int rt, int up, int bk) {
        if (bk < 1 || bk > ROOM_DEPTH) return false;
        if (up < 0) return false;
        // vaulted: width shrinks toward the ceiling
        double e = sq(rt / (double) ROOM_HALF_W) + sq((up - 1) / (double) ROOM_HEIGHT);
        return up <= 1 ? Math.abs(rt) <= ROOM_HALF_W : e <= 1.05;
    }

    // ── Facade ───────────────────────────────────────────────────────────────────

    private static int buildFacade(Ctx c) {
        int placed = 0;
        for (int rt = -FACADE_R; rt <= FACADE_R; rt++) {
            for (int up = 0; up <= FACADE_R + 2; up++) {
                double d = Math.sqrt(rt * rt + (up - 2) * (up - 2));
                if (d > FACADE_R + 0.5) continue;           // round plaster disc
                if (isDoorCell(rt, up)) continue;            // leave the doorway open
                boolean rim = d > FACADE_R - 1.0;            // stone rim around the plaster
                BlockState s = rim ? trim(c, rt, up) : plaster(c, rt, up, 0);
                if (c.set(rt, up, 0, s)) placed++;
                // a thin inner reveal so the facade has depth
                if (c.set(rt, up, 1, plaster(c, rt, up, 1))) placed++;
            }
        }
        // Round timber frame hugging the doorway.
        placed += doorFrame(c);
        return placed;
    }

    private static boolean isDoorCell(int rt, int up) {
        return rt >= -1 && rt <= 1 && up >= 0 && up <= 2;
    }

    /** A ring of stripped-log trim that rounds off the square doorway into a circle. */
    private static int doorFrame(Ctx c) {
        int placed = 0;
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        int[][] ring = {
            {-2, 0}, {-2, 1}, {-2, 2}, {2, 0}, {2, 1}, {2, 2},     // sides
            {-1, 3}, {0, 3}, {1, 3}, {-1, -1}, {0, -1}, {1, -1},   // top & threshold
            {-2, 3}, {2, 3}, {-2, -1}, {2, -1}                     // shoulders (rounding)
        };
        for (int[] p : ring) {
            if (c.set(p[0], p[1], 0, log)) placed++;
        }
        // Keystone + threshold accents.
        if (c.set(0, 4, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState())) placed++;
        return placed;
    }

    // ── Round door ───────────────────────────────────────────────────────────────

    private static int placeDoor(Ctx c, Direction facing) {
        int placed = 0;
        BlockPos anchor = c.pos(0, 0, 0); // bottom-centre of the 3×3 (col=1,row=0)
        Direction right = facing.getClockWise();
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                BlockPos p = anchor.relative(right, col - 1).above(row);
                BlockState s = ModBlocks.HOBBIT_DOOR.get().defaultBlockState()
                        .setValue(RoundDoorBlock.FACING, facing)
                        .setValue(RoundDoorBlock.OPEN, false)
                        .setValue(RoundDoorBlock.COL, col)
                        .setValue(RoundDoorBlock.ROW, row);
                if (c.level.setBlock(p, s, 3)) placed++;
            }
        }
        return placed;
    }

    // ── Round windows ────────────────────────────────────────────────────────────

    private static int buildWindows(Ctx c) {
        int placed = 0;
        for (int side : new int[]{-1, 1}) {
            int cx = side * 4, cy = 2;
            for (int dr = -1; dr <= 1; dr++) {
                for (int du = -1; du <= 1; du++) {
                    int rt = cx + dr, up = cy + du;
                    boolean corner = Math.abs(dr) == 1 && Math.abs(du) == 1;
                    if (corner) {
                        if (c.set(rt, up, 0, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState())) placed++;
                    } else {
                        if (c.set(rt, up, 0, Blocks.GLASS_PANE.defaultBlockState())) placed++;
                        if (c.air(rt, up, 1)) placed++; // let a little light in
                    }
                }
            }
            // window box with flowers + a sill
            if (c.set(cx, cy - 2, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, c.back.getOpposite()))) placed++;
        }
        return placed;
    }

    // ── Lantern sconces beside the door ──────────────────────────────────────────

    private static int buildSconces(Ctx c) {
        int placed = 0;
        for (int side : new int[]{-1, 1}) {
            int rt = side * 3;
            if (c.set(rt, 2, -1, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 3, -1, Blocks.LANTERN.defaultBlockState())) placed++;
        }
        return placed;
    }

    // ── Interior furnishing ──────────────────────────────────────────────────────

    private static int interiorDetails(Ctx c, Direction facing) {
        int placed = 0;
        // A round rug on the floor.
        for (int bk = 3; bk <= 5; bk++)
            for (int rt = -2; rt <= 2; rt++)
                if (Math.abs(rt) + Math.abs(bk - 4) <= 2 && c.set(rt, 0, bk, Blocks.RED_WOOL.defaultBlockState())) placed++;

        // Hearth at the back wall with a lit campfire (vents up the chimney).
        if (c.set(-1, 0, ROOM_DEPTH, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(1, 0, ROOM_DEPTH, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(0, 1, ROOM_DEPTH, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(0, 0, ROOM_DEPTH, Blocks.CAMPFIRE.defaultBlockState())) placed++;

        // A table (a fence post + pressure plate) flanked by stair chairs that FACE the table.
        if (c.set(-3, 0, 4, Blocks.OAK_FENCE.defaultBlockState())) placed++;
        if (c.set(-3, 1, 4, Blocks.OAK_PRESSURE_PLATE.defaultBlockState())) placed++;
        Direction right = facing.getClockWise();
        // chair to the left of the table faces right, toward it; chair to the right faces left.
        if (c.set(-4, 0, 4, stair(Blocks.OAK_STAIRS, right, Half.BOTTOM))) placed++;
        if (c.set(-2, 0, 4, stair(Blocks.OAK_STAIRS, right.getOpposite(), Half.BOTTOM))) placed++;

        // Bookshelves + a barrel pantry along a wall.
        if (c.set(3, 0, 6, Blocks.BOOKSHELF.defaultBlockState())) placed++;
        if (c.set(3, 1, 6, Blocks.BOOKSHELF.defaultBlockState())) placed++;
        if (c.set(4, 0, 5, Blocks.BARREL.defaultBlockState())) placed++;

        // Cosy hanging lanterns from the vaulted ceiling.
        if (c.set(-3, ROOM_HEIGHT, 6, Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true))) placed++;
        if (c.set(3, ROOM_HEIGHT, 3, Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true))) placed++;
        return placed;
    }

    // ── Chimney ──────────────────────────────────────────────────────────────────

    private static int buildChimney(Ctx c) {
        int placed = 0;
        int rt = 0, bk = ROOM_DEPTH;
        // shaft of bricks up through the mound (flue rising from the hearth)
        for (int up = 1; up <= MOUND_RY + 1; up++) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int db = -1; db <= 1; db++) {
                    boolean core = dr == 0 && db == 0;
                    BlockState s = core ? Blocks.AIR.defaultBlockState() : Blocks.BRICKS.defaultBlockState();
                    if (core) { if (c.air(rt, up, bk)) placed++; }
                    else if (c.set(rt + dr, up, bk + db, s)) placed++;
                }
            }
        }
        // chimney pot + a wisp of smoke
        int top = MOUND_RY + 2;
        if (c.set(rt, top, bk, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(rt, top + 1, bk, Blocks.CAMPFIRE.defaultBlockState())) placed++;
        return placed;
    }

    // ── Garden & path ────────────────────────────────────────────────────────────

    private static int buildGarden(Ctx c, Direction facing) {
        int placed = 0;
        // A winding dirt path out from the threshold.
        for (int bk = -1; bk >= -7; bk--) {
            int sway = (bk % 4 == 0) ? 1 : 0;
            for (int rt = -1; rt <= 1; rt++) {
                if (c.set(rt + sway, -1, bk, Blocks.DIRT_PATH.defaultBlockState())) placed++;
                if (c.air(rt + sway, 0, bk)) placed++;
            }
        }
        // Lantern posts flanking the path entrance.
        for (int side : new int[]{-1, 1}) {
            int rt = side * 3, bk = -3;
            if (c.set(rt, 0, bk, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 1, bk, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 2, bk, Blocks.LANTERN.defaultBlockState())) placed++;
        }
        // Scatter flowers and shrubs over the mound's grassy slopes and the garden.
        BlockState[] flowers = {
            Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState()
        };
        for (int bk = -7; bk <= MOUND_CB + MOUND_RZ; bk++) {
            for (int rt = -MOUND_RX; rt <= MOUND_RX; rt++) {
                int gy = grassTop(c, rt, bk);
                if (gy == Integer.MIN_VALUE) continue;
                int h = c.hash(rt, 31, bk);
                if (h % 7 == 0) {
                    if (c.set(rt, gy + 1, bk, flowers[(h >> 3) % flowers.length])) placed++;
                } else if (h % 11 == 0) {
                    if (c.set(rt, gy + 1, bk, Blocks.SHORT_GRASS.defaultBlockState())) placed++;
                }
            }
        }
        return placed;
    }

    /** Height (up) of the grass surface at this column, or MIN_VALUE if none/over the facade. */
    private static int grassTop(Ctx c, int rt, int bk) {
        for (int up = MOUND_RY + 1; up >= 0; up--) {
            if (c.level.getBlockState(c.pos(rt, up, bk)).is(Blocks.GRASS_BLOCK)) return up;
        }
        return Integer.MIN_VALUE;
    }

    // ── Palettes ─────────────────────────────────────────────────────────────────

    private static BlockState plaster(Ctx c, int rt, int up, int bk) {
        int r = c.hash(rt, up, bk) % 100;
        if (r < 70) return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        if (r < 90) return Blocks.CUT_SANDSTONE.defaultBlockState();
        return Blocks.SANDSTONE.defaultBlockState();
    }

    private static BlockState trim(Ctx c, int rt, int up) {
        int r = c.hash(rt, up, 7) % 100;
        return r < 80 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    }

    private static BlockState floorPlank(Ctx c, int rt, int bk) {
        return (c.hash(rt, 0, bk) % 4 == 0) ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : Blocks.OAK_PLANKS.defaultBlockState();
    }

    private static BlockState stair(net.minecraft.world.level.block.Block block, Direction facing, Half half) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static double sq(double v) { return v * v; }

    // ── Local-frame context ──────────────────────────────────────────────────────

    private static final class Ctx {
        final ServerLevel level;
        final BlockPos fc;
        final Direction right, back;

        Ctx(ServerLevel level, BlockPos fc, Direction right, Direction back) {
            this.level = level; this.fc = fc; this.right = right; this.back = back;
        }

        BlockPos pos(int rt, int up, int bk) {
            return new BlockPos(
                    fc.getX() + right.getStepX() * rt + back.getStepX() * bk,
                    fc.getY() + up,
                    fc.getZ() + right.getStepZ() * rt + back.getStepZ() * bk);
        }

        boolean set(int rt, int up, int bk, BlockState state) {
            return level.setBlock(pos(rt, up, bk), state, 2);
        }

        boolean air(int rt, int up, int bk) {
            BlockPos p = pos(rt, up, bk);
            if (level.getBlockState(p).isAir()) return false;
            return level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }

        int hash(int x, int y, int z) {
            int h = x * 73856093;
            h ^= y * 19349663;
            h ^= z * 83492791;
            h ^= (h >>> 13);
            return Math.floorMod(h, 1_000_000);
        }
    }
}
