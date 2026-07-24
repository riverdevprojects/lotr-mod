package com.lotrmod.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Procedural generator for a richly-detailed hobbit-hole (smial): a grassy mound with a
 * timber-framed wooden facade, a working vanilla oak door in a plank frame, mullioned round windows
 * with shutters and flower boxes, a porch, two smoking chimneys, a fully-furnished interior
 * (dining nook, kitchen, study, bedroom, pantry) and a fenced garden with a vegetable plot,
 * flower beds, lamp posts, a well and an apple tree.
 *
 * <p>Local frame relative to the door: <b>rt</b> = left/right across the facade, <b>up</b> =
 * height above the threshold, <b>bk</b> = depth into the hill (bk &gt; 0 inside, bk &lt; 0 garden).
 */
public final class HobbitHoleBuilder {

    private HobbitHoleBuilder() {}

    static final int MOUND_RX = 14, MOUND_RY = 9, MOUND_RZ = 14, MOUND_CB = 9;
    static final int ROOM_HALF_W = 6, ROOM_DEPTH = 10, ROOM_HEIGHT = 5;
    static final int FACADE_R = 7;

    public static int build(ServerLevel level, BlockPos frontCenter, Direction facing) {
        Ctx c = new Ctx(level, frontCenter, facing.getClockWise(), facing.getOpposite(), facing);
        int placed = 0;
        placed += clearArea(c);
        placed += buildMound(c);
        placed += carveInterior(c);
        placed += ceilingBeams(c);
        placed += buildFacade(c);
        placed += placeDoor(c, facing);
        placed += buildWindows(c);
        placed += buildPorch(c, facing);
        placed += furnishInterior(c, facing);
        placed += buildChimneys(c);
        placed += buildGarden(c, facing);
        return placed;
    }

    // ── Terrain clearing ─────────────────────────────────────────────────────────

    private static int clearArea(Ctx c) {
        int placed = 0;
        for (int bk = -14; bk <= MOUND_CB + MOUND_RZ; bk++)
            for (int rt = -MOUND_RX - 4; rt <= MOUND_RX + 4; rt++)
                for (int up = 1; up <= MOUND_RY + 5; up++)
                    if (c.air(rt, up, bk)) placed++;
        return placed;
    }

    // ── Grassy mound ─────────────────────────────────────────────────────────────

    private static int buildMound(Ctx c) {
        int placed = 0;
        for (int bk = 0; bk <= MOUND_CB + MOUND_RZ; bk++)
            for (int rt = -MOUND_RX; rt <= MOUND_RX; rt++)
                for (int up = 0; up <= MOUND_RY; up++) {
                    if (!inMound(rt, up, bk)) continue;
                    boolean surface = !inMound(rt, up + 1, bk) || !inMound(rt + 1, up, bk)
                            || !inMound(rt - 1, up, bk) || !inMound(rt, up, bk + 1);
                    BlockState s;
                    if (surface) {
                        int h = c.hash(rt, up, bk) % 100;
                        s = h < 75 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : h < 88 ? Blocks.MOSS_BLOCK.defaultBlockState()
                                : h < 96 ? Blocks.PODZOL.defaultBlockState()
                                         : Blocks.COARSE_DIRT.defaultBlockState();
                    } else {
                        s = c.hash(rt, up, bk) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState()
                                                        : Blocks.DIRT.defaultBlockState();
                    }
                    if (c.set(rt, up, bk, s)) placed++;
                }
        for (int bk = -3; bk <= ROOM_DEPTH + 1; bk++)
            for (int rt = -ROOM_HALF_W - 2; rt <= ROOM_HALF_W + 2; rt++)
                if (c.set(rt, -1, bk, Blocks.DIRT.defaultBlockState())) placed++;
        return placed;
    }

    private static boolean inMound(int rt, int up, int bk) {
        if (bk < 0 || up < 0) return false;
        return sq(rt / (double) MOUND_RX) + sq(up / (double) MOUND_RY) + sq((bk - MOUND_CB) / (double) MOUND_RZ) <= 1.0;
    }

    // ── Hollow, wood-panelled interior ───────────────────────────────────────────

    private static int carveInterior(Ctx c) {
        int placed = 0;
        for (int bk = 1; bk <= ROOM_DEPTH; bk++) {
            for (int rt = -ROOM_HALF_W; rt <= ROOM_HALF_W; rt++) {
                if (inRoom(rt, 0, bk) && c.set(rt, -1, bk, floorPlank(c, rt, bk))) placed++;
                for (int up = 0; up <= ROOM_HEIGHT; up++) {
                    if (!inRoom(rt, up, bk)) continue;
                    boolean wall = !inRoom(rt + 1, up, bk) || !inRoom(rt - 1, up, bk)
                            || !inRoom(rt, up, bk + 1) || !inRoom(rt, up + 1, bk);
                    if (wall) {
                        if (c.set(rt, up, bk, wallMat(c, rt, up, bk))) placed++;
                    } else if (c.air(rt, up, bk)) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean inRoom(int rt, int up, int bk) {
        if (bk < 1 || bk > ROOM_DEPTH || up < 0) return false;
        if (up <= 1) return Math.abs(rt) <= ROOM_HALF_W;
        return sq(rt / (double) ROOM_HALF_W) + sq((up - 1) / (double) ROOM_HEIGHT) <= 1.05;
    }

    /** Wainscoted wood walls below, plaster panels framed by vertical timber studs above. */
    private static BlockState wallMat(Ctx c, int rt, int up, int bk) {
        if (up <= 1) return (c.hash(rt, up, bk) % 2 == 0)
                ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        if (Math.floorMod(rt + bk, 3) == 0) return pillar(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);
        return Blocks.WHITE_TERRACOTTA.defaultBlockState();
    }

    /** Exposed wooden beams running across the ceiling. */
    private static int ceilingBeams(Ctx c) {
        int placed = 0;
        Direction.Axis ax = c.right.getAxis();
        for (int bk = 2; bk <= ROOM_DEPTH - 1; bk += 2)
            for (int rt = -ROOM_HALF_W + 1; rt <= ROOM_HALF_W - 1; rt++)
                if (inRoom(rt, ROOM_HEIGHT - 1, bk) && c.set(rt, ROOM_HEIGHT - 1, bk, pillar(Blocks.STRIPPED_DARK_OAK_LOG, ax)))
                    placed++;
        return placed;
    }

    // ── Timber-framed facade ─────────────────────────────────────────────────────

    private static int buildFacade(Ctx c) {
        int placed = 0;
        Direction.Axis beamAx = c.right.getAxis();
        for (int rt = -FACADE_R; rt <= FACADE_R; rt++) {
            for (int up = -1; up <= FACADE_R + 3; up++) {
                double d = Math.sqrt(rt * rt + (up - 2) * (up - 2));
                if (d > FACADE_R + 0.5) continue;
                if (isDoorCell(rt, up)) continue;
                BlockState s;
                if (up <= 0) {
                    s = Blocks.COBBLESTONE.defaultBlockState();               // stone sill course
                } else if (d > FACADE_R - 1.0) {
                    s = pillar(Blocks.DARK_OAK_LOG, Direction.Axis.Y);         // outer timber ring
                } else if (Math.floorMod(rt, 3) == 0) {
                    s = pillar(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);// vertical posts
                } else if (Math.floorMod(up, 3) == 0) {
                    s = pillar(Blocks.STRIPPED_DARK_OAK_LOG, beamAx);          // horizontal beams
                } else {
                    int h = c.hash(rt, up, 0) % 100;
                    s = h < 55 ? Blocks.OAK_PLANKS.defaultBlockState()
                            : h < 80 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                                     : Blocks.WHITE_TERRACOTTA.defaultBlockState();
                }
                if (c.set(rt, up, 0, s)) placed++;
                if (c.set(rt, up, 1, plasterReveal(c, rt, up))) placed++;
            }
        }
        placed += doorFrame(c);
        return placed;
    }

    private static BlockState plasterReveal(Ctx c, int rt, int up) {
        return (c.hash(rt, up, 1) % 3 == 0) ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                : Blocks.OAK_PLANKS.defaultBlockState();
    }

    private static boolean isDoorCell(int rt, int up) {
        return rt >= -1 && rt <= 1 && up >= 0 && up <= 2;
    }

    private static int doorFrame(Ctx c) {
        int placed = 0;
        BlockState log = pillar(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);
        BlockState beam = pillar(Blocks.STRIPPED_DARK_OAK_LOG, c.right.getAxis());
        for (int up = 0; up <= 2; up++) { // jambs
            if (c.set(-2, up, 0, log)) placed++;
            if (c.set(2, up, 0, log)) placed++;
        }
        for (int rt = -2; rt <= 2; rt++) { // lintel + a rounded crown
            if (c.set(rt, 3, 0, beam)) placed++;
        }
        if (c.set(-1, -1, 0, beam)) placed++;
        if (c.set(0, -1, 0, beam)) placed++;
        if (c.set(1, -1, 0, beam)) placed++;
        if (c.set(0, 4, 0, Blocks.DARK_OAK_LOG.defaultBlockState())) placed++;
        return placed;
    }

    // ── Round door ───────────────────────────────────────────────────────────────

    private static int placeDoor(Ctx c, Direction facing) {
        int placed = 0;
        BlockPos anchor = c.pos(0, 0, 0);
        Direction right = facing.getClockWise();
        for (int col = 0; col < 3; col++)
            for (int row = 0; row < 3; row++) {
                BlockPos p = anchor.relative(right, col - 1).above(row);
                BlockState s;
                if (col == 1 && row <= 1) {
                    // Placeholder: a vanilla oak door fills the centre column (lower + upper halves)
                    s = Blocks.OAK_DOOR.defaultBlockState()
                            .setValue(DoorBlock.FACING, facing)
                            .setValue(DoorBlock.HALF, row == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                            .setValue(DoorBlock.OPEN, false)
                            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
                } else {
                    // plank frame around the doorway
                    s = Blocks.OAK_PLANKS.defaultBlockState();
                }
                if (c.level.setBlock(p, s, 3)) placed++;
            }
        return placed;
    }

    // ── Round windows (frame, glass, mullion, shutters, flower box) ───────────────

    private static int buildWindows(Ctx c) {
        int placed = 0;
        placed += roundWindow(c, -5, 2);
        placed += roundWindow(c, 5, 2);
        // a little round attic window above the door
        if (c.set(0, 6, 0, Blocks.GLASS_PANE.defaultBlockState())) placed++;
        for (int[] o : new int[][]{{-1, 6}, {1, 6}, {0, 5}, {0, 7}})
            if (c.set(o[0], o[1], 0, pillar(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y))) placed++;
        return placed;
    }

    private static int roundWindow(Ctx c, int cx, int cy) {
        int placed = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int du = -1; du <= 1; du++) {
                int rt = cx + dr, up = cy + du;
                boolean corner = Math.abs(dr) == 1 && Math.abs(du) == 1;
                if (corner) {
                    if (c.set(rt, up, 0, pillar(Blocks.STRIPPED_OAK_LOG, dr == 0 ? c.right.getAxis() : Direction.Axis.Y))) placed++;
                } else if (dr == 0 && du == 0) {
                    if (c.set(rt, up, 0, Blocks.IRON_BARS.defaultBlockState())) placed++; // cross mullion
                } else {
                    if (c.set(rt, up, 0, Blocks.GLASS_PANE.defaultBlockState())) placed++;
                    if (c.air(rt, up, 1)) placed++;
                }
            }
        // shutters either side
        if (c.set(cx - 2, cy, 0, shutter(c, true))) placed++;
        if (c.set(cx + 2, cy, 0, shutter(c, false))) placed++;
        // flower box on the sill
        if (c.set(cx, cy - 2, -1, trapdoorLedge(c))) placed++;
        BlockState[] box = {Blocks.POPPY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
                Blocks.AZURE_BLUET.defaultBlockState()};
        for (int dr = -1; dr <= 1; dr++)
            if (c.set(cx + dr, cy - 1, -1, box[(dr + 1) % box.length])) placed++;
        return placed;
    }

    private static BlockState shutter(Ctx c, boolean left) {
        Direction f = left ? c.right.getOpposite() : c.right;
        return Blocks.DARK_OAK_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, f);
    }

    private static BlockState trapdoorLedge(Ctx c) {
        return Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, c.facing);
    }

    // ── Porch ────────────────────────────────────────────────────────────────────

    private static int buildPorch(Ctx c, Direction facing) {
        int placed = 0;
        // doorstep
        for (int rt = -1; rt <= 1; rt++) {
            if (c.set(rt, -1, -1, Blocks.COBBLESTONE.defaultBlockState())) placed++;
            if (c.set(rt, 0, -1, stair(Blocks.DARK_OAK_STAIRS, facing, Half.BOTTOM))) placed++;
        }
        // overhanging eave on posts
        for (int side : new int[]{-1, 1}) {
            int rt = side * 3;
            if (c.set(rt, 0, -2, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 1, -2, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 2, -2, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 3, -1, Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true))) placed++;
        }
        Direction.Axis ax = c.right.getAxis();
        for (int rt = -3; rt <= 3; rt++) {
            if (c.set(rt, 4, -2, pillar(Blocks.DARK_OAK_LOG, ax))) placed++;
            if (c.set(rt, 4, -1, stair(Blocks.DARK_OAK_STAIRS, facing, Half.TOP))) placed++;
        }
        // a bench and barrels by the door
        if (c.set(-2, 0, -2, stair(Blocks.SPRUCE_STAIRS, facing, Half.BOTTOM))) placed++;
        if (c.set(-3, 0, -2, stair(Blocks.SPRUCE_STAIRS, facing, Half.BOTTOM))) placed++;
        if (c.set(2, 0, -2, Blocks.BARREL.defaultBlockState())) placed++;
        if (c.set(2, 1, -2, Blocks.LANTERN.defaultBlockState())) placed++;
        if (c.set(3, 0, -2, Blocks.BARREL.defaultBlockState())) placed++;
        return placed;
    }

    // ── Interior furnishing ──────────────────────────────────────────────────────

    private static int furnishInterior(Ctx c, Direction facing) {
        int placed = 0;
        Direction right = facing.getClockWise();
        Direction left = right.getOpposite();
        Direction inward = facing.getOpposite();

        // Entry rug + potted plants flanking the door.
        for (int bk = 1; bk <= 2; bk++)
            for (int rt = -1; rt <= 1; rt++)
                if (c.set(rt, 0, bk, Blocks.BROWN_CARPET.defaultBlockState())) placed++;
        if (c.set(-2, 0, 1, Blocks.POTTED_FERN.defaultBlockState())) placed++;
        if (c.set(2, 0, 1, Blocks.POTTED_AZALEA.defaultBlockState())) placed++;

        // Round rug + dining table with a cake and chairs that face it.
        for (int bk = 3; bk <= 5; bk++)
            for (int rt = -2; rt <= 2; rt++)
                if (Math.abs(rt) + Math.abs(bk - 4) <= 2 && c.set(rt, 0, bk, Blocks.RED_CARPET.defaultBlockState())) placed++;
        if (c.set(0, 0, 4, Blocks.OAK_FENCE.defaultBlockState())) placed++;
        if (c.set(0, 1, 4, Blocks.CAKE.defaultBlockState())) placed++;
        if (c.set(-2, 0, 4, stair(Blocks.OAK_STAIRS, right, Half.BOTTOM))) placed++;
        if (c.set(2, 0, 4, stair(Blocks.OAK_STAIRS, left, Half.BOTTOM))) placed++;
        if (c.set(0, 0, 2, stair(Blocks.OAK_STAIRS, inward, Half.BOTTOM))) placed++;
        if (c.set(0, 0, 6, stair(Blocks.OAK_STAIRS, facing, Half.BOTTOM))) placed++;

        // Hearth + mantle on the left wall, vents the side chimney.
        if (c.set(-ROOM_HALF_W, 0, 7, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(-ROOM_HALF_W + 1, 0, 7, Blocks.CAMPFIRE.defaultBlockState())) placed++;
        if (c.set(-ROOM_HALF_W, 1, 7, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(-ROOM_HALF_W + 1, 2, 7, slab(Blocks.DARK_OAK_SLAB, false))) placed++; // mantle
        if (c.set(-ROOM_HALF_W + 1, 3, 7, candle(c))) placed++;

        // Study nook (right wall): chiseled bookshelves, a desk and a lantern.
        for (int up = 0; up <= 1; up++)
            for (int bk = 3; bk <= 5; bk++)
                if (c.set(ROOM_HALF_W, up, bk, Blocks.CHISELED_BOOKSHELF.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W - 1, 0, 4, stair(Blocks.SPRUCE_STAIRS, right, Half.BOTTOM))) placed++;
        if (c.set(ROOM_HALF_W, 2, 4, candle(c))) placed++;

        // Kitchen (back-right): smoker, furnace, cauldron, barrels, composter, crafting.
        if (c.set(ROOM_HALF_W, 0, 8, lit(Blocks.SMOKER, left))) placed++;
        if (c.set(ROOM_HALF_W, 0, 9, Blocks.FURNACE.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W - 1, 0, 9, Blocks.CRAFTING_TABLE.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W - 2, 0, 9, Blocks.CAULDRON.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W - 3, 0, 9, Blocks.COMPOSTER.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W - 1, 0, 8, Blocks.BARREL.defaultBlockState())) placed++;
        if (c.set(ROOM_HALF_W, 1, 8, slab(Blocks.OAK_SLAB, true))) placed++;
        if (c.set(ROOM_HALF_W - 2, 0, 8, Blocks.HAY_BLOCK.defaultBlockState())) placed++;

        // Bedroom (back-left): bed along the depth, nightstand, chest, rug, potted flower.
        Direction footFace = facing;                              // head toward the front, foot to the back
        BlockPos foot = c.pos(-ROOM_HALF_W + 1, 0, 9);            // (-5,0,9)
        BlockPos head = foot.relative(footFace);                  // (-5,0,8)
        if (c.level.setBlock(foot, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, footFace)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3)) placed++;
        if (c.level.setBlock(head, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, footFace)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3)) placed++;
        if (c.set(-ROOM_HALF_W + 2, 0, 8, Blocks.BARREL.defaultBlockState())) placed++;   // nightstand
        if (c.set(-ROOM_HALF_W + 2, 1, 8, candle(c))) placed++;
        if (c.set(-ROOM_HALF_W + 2, 0, 9, Blocks.CHEST.defaultBlockState())) placed++;
        if (c.set(-ROOM_HALF_W + 3, 0, 8, Blocks.BROWN_CARPET.defaultBlockState())) placed++;
        if (c.set(-ROOM_HALF_W + 3, 0, 9, Blocks.POTTED_POPPY.defaultBlockState())) placed++;

        // Hanging lanterns from the beams.
        if (c.set(-2, ROOM_HEIGHT - 2, 4, hang(c))) placed++;
        if (c.set(2, ROOM_HEIGHT - 2, 6, hang(c))) placed++;
        if (c.set(0, ROOM_HEIGHT - 2, 8, hang(c))) placed++;
        return placed;
    }

    // ── Chimneys ─────────────────────────────────────────────────────────────────

    private static int buildChimneys(Ctx c) {
        // Flues rise from the left-wall hearth and from the kitchen smoker.
        return chimney(c, -ROOM_HALF_W, 7) + chimney(c, ROOM_HALF_W, 8);
    }

    private static int chimney(Ctx c, int rt, int bk) {
        int placed = 0;
        for (int up = 1; up <= MOUND_RY + 1; up++) {
            if (c.air(rt, up, bk)) placed++;                 // the flue, all the way up
            if (up < ROOM_HEIGHT) continue;                  // don't brick into the room below the ceiling
            for (int dr = -1; dr <= 1; dr++)
                for (int db = -1; db <= 1; db++)
                    if (!(dr == 0 && db == 0) && c.set(rt + dr, up, bk + db, brick(c))) placed++;
        }
        int top = MOUND_RY + 2;
        if (c.set(rt, top, bk, Blocks.BRICKS.defaultBlockState())) placed++;
        if (c.set(rt, top + 1, bk, Blocks.CAMPFIRE.defaultBlockState())) placed++;
        return placed;
    }

    // ── Garden ───────────────────────────────────────────────────────────────────

    private static int buildGarden(Ctx c, Direction facing) {
        int placed = 0;
        // winding cobble-and-path walk to the gate
        for (int bk = -1; bk >= -10; bk--) {
            int sway = (int) Math.round(1.5 * Math.sin(bk * 0.6));
            for (int rt = -1; rt <= 1; rt++) {
                BlockState s = (rt == 0) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : (c.hash(rt, 0, bk) % 2 == 0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                if (c.set(rt + sway, -1, bk, s)) placed++;
                if (c.air(rt + sway, 0, bk)) placed++;
            }
        }
        // picket fence around the yard with a gate on the path
        int yard = 9;
        for (int rt = -yard; rt <= yard; rt++) {
            placed += fenceOrGate(c, rt, -10, rt == 0, facing);
        }
        for (int bk = -10; bk <= -1; bk++) {
            placed += fencePost(c, -yard, bk);
            placed += fencePost(c, yard, bk);
        }
        // lamp posts at the gate
        for (int side : new int[]{-1, 1}) {
            int rt = side * 2;
            if (c.set(rt, 0, -10, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 1, -10, Blocks.OAK_FENCE.defaultBlockState())) placed++;
            if (c.set(rt, 2, -10, Blocks.LANTERN.defaultBlockState())) placed++;
        }
        // vegetable plot (left of the path)
        placed += veggiePatch(c, -6, -7);
        // a well (right of the path)
        placed += well(c, 6, -7);
        // an apple tree on the right
        placed += tree(c, 7, -3);
        // flower beds and shrubs over the slopes
        BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(),
                Blocks.RED_TULIP.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState()};
        for (int bk = -10; bk <= MOUND_CB + MOUND_RZ; bk++)
            for (int rt = -MOUND_RX; rt <= MOUND_RX; rt++) {
                int gy = grassTop(c, rt, bk);
                if (gy == Integer.MIN_VALUE) continue;
                int h = c.hash(rt, 31, bk);
                if (h % 5 == 0) { if (c.set(rt, gy + 1, bk, flowers[(h >> 3) % flowers.length])) placed++; }
                else if (h % 9 == 0) { if (c.set(rt, gy + 1, bk, Blocks.SHORT_GRASS.defaultBlockState())) placed++; }
                else if (h % 23 == 0) { if (c.set(rt, gy + 1, bk, Blocks.FERN.defaultBlockState())) placed++; }
            }
        return placed;
    }

    private static int fenceOrGate(Ctx c, int rt, int bk, boolean gate, Direction facing) {
        BlockState s = gate ? Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, facing)
                : Blocks.OAK_FENCE.defaultBlockState();
        return c.set(rt, 0, bk, s) ? 1 : 0;
    }

    private static int fencePost(Ctx c, int rt, int bk) {
        return c.set(rt, 0, bk, Blocks.OAK_FENCE.defaultBlockState()) ? 1 : 0;
    }

    private static int veggiePatch(Ctx c, int rt0, int bk0) {
        int placed = 0;
        for (int dr = -2; dr <= 2; dr++)
            for (int db = -2; db <= 2; db++) {
                boolean water = dr == 0 && db == 0;
                if (c.set(rt0 + dr, -1, bk0 + db, water ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState())) placed++;
                if (!water) {
                    int h = c.hash(rt0 + dr, 7, bk0 + db) % 4;
                    BlockState crop = h == 0 ? mature(Blocks.WHEAT)
                            : h == 1 ? mature(Blocks.CARROTS)
                            : h == 2 ? mature(Blocks.POTATOES)
                                     : Blocks.BEETROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, 3);
                    if (c.set(rt0 + dr, 0, bk0 + db, crop)) placed++;
                }
            }
        // a pumpkin and a hay bale at the corner
        if (c.set(rt0 + 3, 0, bk0 + 2, Blocks.PUMPKIN.defaultBlockState())) placed++;
        if (c.set(rt0 - 3, 0, bk0 - 2, Blocks.HAY_BLOCK.defaultBlockState())) placed++;
        return placed;
    }

    private static int well(Ctx c, int rt0, int bk0) {
        int placed = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int db = -1; db <= 1; db++) {
                boolean rim = Math.abs(dr) == 1 || Math.abs(db) == 1;
                if (rim) { if (c.set(rt0 + dr, 0, bk0 + db, Blocks.COBBLESTONE.defaultBlockState())) placed++; }
                else { if (c.set(rt0 + dr, 0, bk0 + db, Blocks.WATER.defaultBlockState())) placed++;
                       if (c.set(rt0 + dr, -1, bk0 + db, Blocks.COBBLESTONE.defaultBlockState())) placed++; }
            }
        for (int side : new int[]{-1, 1}) {
            for (int up = 1; up <= 2; up++) if (c.set(rt0 + side, up, bk0, Blocks.OAK_FENCE.defaultBlockState())) placed++;
        }
        for (int dr = -1; dr <= 1; dr++) if (c.set(rt0 + dr, 3, bk0, slab(Blocks.DARK_OAK_SLAB, false))) placed++;
        if (c.set(rt0, 2, bk0, Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true))) placed++;
        return placed;
    }

    private static int tree(Ctx c, int rt0, int bk0) {
        int placed = 0;
        int base = grassTop(c, rt0, bk0);
        if (base == Integer.MIN_VALUE) base = -1; // garden ground sits at up = -1
        for (int up = base + 1; up <= base + 5; up++)
            if (c.set(rt0, up, bk0, pillar(Blocks.OAK_LOG, Direction.Axis.Y))) placed++;
        for (int dr = -2; dr <= 2; dr++)
            for (int db = -2; db <= 2; db++)
                for (int du = 4; du <= 6; du++) {
                    if (dr == 0 && db == 0 && du < 6) continue;
                    if (Math.abs(dr) + Math.abs(db) + Math.abs(du - 5) > 3) continue;
                    int h = c.hash(rt0 + dr, du, bk0 + db) % 100;
                    BlockState leaf = h < 12 ? Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)
                            : h < 18 ? appleHint() : oakLeaf();
                    if (c.set(rt0 + dr, base + du, bk0 + db, leaf)) placed++;
                }
        return placed;
    }

    private static int grassTop(Ctx c, int rt, int bk) {
        for (int up = MOUND_RY + 1; up >= 0; up--)
            if (c.level.getBlockState(c.pos(rt, up, bk)).is(Blocks.GRASS_BLOCK)
                    || c.level.getBlockState(c.pos(rt, up, bk)).is(Blocks.MOSS_BLOCK)
                    || c.level.getBlockState(c.pos(rt, up, bk)).is(Blocks.PODZOL)) return up;
        return Integer.MIN_VALUE;
    }

    // ── Small helpers ────────────────────────────────────────────────────────────

    private static BlockState oakLeaf() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    private static BlockState appleHint() {
        return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    private static BlockState mature(Block crop) {
        return crop.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
    }

    private static BlockState candle(Ctx c) {
        return Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true);
    }

    private static BlockState hang(Ctx c) {
        return Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
    }

    private static BlockState lit(Block furnace, Direction facing) {
        return furnace.defaultBlockState()
                .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
    }

    private static BlockState brick(Ctx c) {
        return (c.hash(c.fc.getX(), 5, c.fc.getZ()) % 3 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.BRICKS.defaultBlockState();
    }

    private static BlockState floorPlank(Ctx c, int rt, int bk) {
        int h = c.hash(rt, 0, bk) % 3;
        return h == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : h == 1 ? Blocks.OAK_PLANKS.defaultBlockState()
                         : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static BlockState pillar(Block b, Direction.Axis ax) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, ax);
    }

    private static BlockState stair(Block b, Direction facing, Half half) {
        return b.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
    }

    private static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static double sq(double v) { return v * v; }

    // ── Local-frame context ──────────────────────────────────────────────────────

    private static final class Ctx {
        final ServerLevel level;
        final BlockPos fc;
        final Direction right, back, facing;

        Ctx(ServerLevel level, BlockPos fc, Direction right, Direction back, Direction facing) {
            this.level = level; this.fc = fc; this.right = right; this.back = back; this.facing = facing;
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
