package com.lotrmod.conquest.block;

import com.lotrmod.conquest.data.ConquestCosts;
import com.lotrmod.conquest.data.TreasuryResource;
import com.lotrmod.conquest.entity.GuardEntity;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import com.lotrmod.conquest.registry.ConquestEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.HolderLookup;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimBannerBlockEntity extends BlockEntity {

    /** Number of starter guards spawned for free when the outpost finishes building. */
    private static final int STARTER_GUARDS = 2;

    /**
     * How many blocks above the ground the flag is planted. The watchtower rises from the
     * ground where the player places the banner, and the flag itself ends up on the top
     * floor — players climb the interior stairs to reach (and interact with) it.
     */
    public static final int FLAG_FLOOR_OFFSET = 10;

    public UUID guildId = null;
    public Set<ChunkPos> claimedChunks = new HashSet<>();

    /** Transient: the guild that just destroyed this flag in war, to auto-plant a captured flag. */
    public UUID capturedBy = null;

    /** Ground (base) position of the watchtower — guards garrison here, not at the elevated flag. */
    private BlockPos groundPos = null;

    private boolean fullyBuilt = false;

    private boolean guardsSpawned = false;
    /** Current garrison size for this outpost (starter + hired). Capped at {@link ConquestCosts#MAX_GUARDS_PER_OUTPOST}. */
    private int guardCount = 0;

    /** Resources invested in this outpost (construction + hires), refunded on abandon. */
    private final EnumMap<TreasuryResource, Long> invested = new EnumMap<>(TreasuryResource.class);

    public ClaimBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ConquestBlockEntities.CLAIM_BANNER.get(), pos, state);
    }

    public void initialize(UUID guildId, Set<ChunkPos> chunks) {
        initialize(guildId, chunks, getBlockPos());
    }

    /**
     * @param groundPos the base of the tower (where guards garrison). For a real outpost this is
     *                  {@code FLAG_FLOOR_OFFSET} blocks below the flag; for direct/debug placement
     *                  it is simply the flag's own position.
     */
    public void initialize(UUID guildId, Set<ChunkPos> chunks, BlockPos groundPos) {
        this.guildId = guildId;
        this.claimedChunks = new HashSet<>(chunks);
        this.groundPos = groundPos.immutable();
        this.fullyBuilt = true;
        this.guardsSpawned = false;
        this.guardCount = 0;
        this.invested.clear();
        setChanged();
    }

    public boolean isFullyBuilt() { return fullyBuilt; }
    public int getGuardCount()    { return guardCount; }

    /** Ground/base of the tower; falls back to the flag position for legacy/direct placements. */
    private BlockPos groundPos() {
        return groundPos != null ? groundPos : getBlockPos();
    }

    /** Records resources spent on this outpost so they can be refunded when it is abandoned. */
    public void addInvested(Map<TreasuryResource, Long> cost) {
        for (Map.Entry<TreasuryResource, Long> e : cost.entrySet()) {
            invested.merge(e.getKey(), e.getValue(), Long::sum);
        }
        setChanged();
    }

    /** A copy of everything invested in this outpost, for refund. */
    public Map<TreasuryResource, Long> getInvested() {
        return new EnumMap<>(invested);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ClaimBannerBlockEntity be) {
        if (be.fullyBuilt) {
            be.tickGuards(level);
        }
    }

    // ── Tower construction ─────────────────────────────────────────────────────
    //
    // A three-storey stone keep on a flared, lit base: weathered quoined walls, arched glowing
    // windows with sills, projecting stone cornices, overhanging timber galleries with corner
    // lamp-posts, a crenellated parapet, and a tall banded sandstone spire. The flag stands in
    // the centre of the top floor, reached by two interior staircases with railings.

    // ── Palette ──
    private static final BlockState BRICK      = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED    = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY      = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CHISELED   = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState COBBLE     = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState ANDESITE   = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState POST       = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState STRIPPED   = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
    private static final BlockState PLANK      = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState FENCE      = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState BARS       = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState PANE       = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState LANTERN    = Blocks.LANTERN.defaultBlockState();
    private static final BlockState SEA_LAMP   = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState CHAIN      = Blocks.CHAIN.defaultBlockState();
    private static final BlockState BARREL     = Blocks.BARREL.defaultBlockState();
    private static final BlockState BOOKSHELF  = Blocks.BOOKSHELF.defaultBlockState();
    private static final BlockState BRICK_WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState ROOF_FILL  = Blocks.CUT_SANDSTONE.defaultBlockState();
    private static final BlockState ROOF_BAND  = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    private static final BlockState ROOF_TRIM  = Blocks.CHISELED_SANDSTONE.defaultBlockState();
    private static final BlockState SPIRE      = Blocks.SANDSTONE_WALL.defaultBlockState();
    private static final BlockState FINIAL     = Blocks.END_ROD.defaultBlockState();
    private static final BlockState AIR        = Blocks.AIR.defaultBlockState();

    private static final int HALF  = 3;    // 7x7 tower body
    private static final int INNER = 2;    // 5x5 interior
    private static final int OUT   = 4;    // one ring beyond the walls (HALF + 1)
    /** Wall base (first wall course) Y for each storey, relative to the ground placement. */
    private static final int[] STOREY = {0, 5, 10};

    /**
     * Raises the full watchtower around the relocated flag.
     *
     * @param g        ground position (centre of the tower's base, where the banner was placed)
     * @param flagPos  where the relocated flag now stands (top floor centre)
     */
    public static void buildOutpostTower(ServerLevel level, BlockPos g, BlockPos flagPos) {
        clearInterior(level, g);
        buildBase(level, g);

        for (int s = 0; s < 3; s++) {
            buildStoreyWalls(level, g, s);
        }
        buildCornerPosts(level, g);

        // Inter-storey floors with stairwell slots for the flights below.
        buildFloor(level, g, 4, -2, +1);
        buildFloor(level, g, 9, +2, +1);

        // Decorative bands between storeys: stone cornices + overhanging timber galleries.
        cornice(level, g, 4);
        cornice(level, g, 9);
        timberGallery(level, g, 4);
        timberGallery(level, g, 9);

        buildFlight(level, g, 0, -2);   // ground -> middle (west wall)
        buildFlight(level, g, 5, +2);   // middle -> top  (east wall)
        stairRailings(level, g);

        crenellatedParapet(level, g, 14);
        buildRoof(level, g);

        furnishInteriors(level, g);

        // The flag's own pole/top decoration (the base block is already planted at flagPos).
        set(level, flagPos.above(),  ConquestBlocks.CLAIM_BANNER_POLE.get().defaultBlockState());
        set(level, flagPos.above(2), ConquestBlocks.CLAIM_BANNER_TOP.get().defaultBlockState());
    }

    /** True for the three blocks (base/pole/top) of the planted flag, so the build never overwrites it. */
    private static boolean isFlagColumn(int dx, int dz, int dy) {
        return dx == 0 && dz == 0 && dy >= FLAG_FLOOR_OFFSET && dy <= FLAG_FLOOR_OFFSET + 2;
    }

    /** Hollows out the interior so terrain never pokes through, keeping the flag column. */
    private static void clearInterior(ServerLevel level, BlockPos g) {
        for (int dy = 0; dy <= 13; dy++) {
            for (int dx = -INNER; dx <= INNER; dx++) {
                for (int dz = -INNER; dz <= INNER; dz++) {
                    if (isFlagColumn(dx, dz, dy)) continue;
                    put(level, g, dx, dy, dz, AIR);
                }
            }
        }
    }

    /** Flared, lit, stepped foundation the tower stands on, plus the approach to the gateway. */
    private static void buildBase(ServerLevel level, BlockPos g) {
        // Two solid pads for depth on uneven ground (9x9 then 7x7 beneath).
        for (int dx = -OUT; dx <= OUT; dx++) {
            for (int dz = -OUT; dz <= OUT; dz++) {
                put(level, g, dx, -1, dz, foundationBlock(dx, dz));
                if (Math.abs(dx) <= HALF && Math.abs(dz) <= HALF) {
                    put(level, g, dx, -2, dz, foundationBlock(dx, -dz));
                }
            }
        }
        // Battered skirt: stairs flaring out at the base.
        for (int d = -HALF; d <= HALF; d++) {
            put(level, g, d, 0,  OUT, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
            put(level, g, d, 0, -OUT, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
            put(level, g,  OUT, 0, d, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
            put(level, g, -OUT, 0, d, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
        }
        // Lit base glow (the green-gold light in the reference shot).
        put(level, g, 0, -1,  OUT, SEA_LAMP);
        put(level, g, 0, -1, -OUT, SEA_LAMP);
        put(level, g,  OUT, -1, 0, SEA_LAMP);
        put(level, g, -OUT, -1, 0, SEA_LAMP);

        // Approach threshold and path up to the south doorway.
        put(level, g, 0, 0,  OUT, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
        put(level, g, 0, -1, OUT + 1, COBBLE);
        put(level, g, -1, -1, OUT + 1, COBBLE);
        put(level, g, 1, -1, OUT + 1, COBBLE);

        // Lanterns flanking the gateway.
        put(level, g, -2, 1, HALF, LANTERN);
        put(level, g,  2, 1, HALF, LANTERN);
    }

    private static BlockState foundationBlock(int dx, int dz) {
        int h = Math.floorMod(dx * 7 + dz * 13, 5);
        return h == 0 ? COBBLE : h == 1 ? CRACKED : BRICK;
    }

    /** One storey of walls: quoined corners, arched glowing windows, and a ground-floor gateway. */
    private static void buildStoreyWalls(ServerLevel level, BlockPos g, int storey) {
        int base = STOREY[storey];
        boolean top = storey == 2;
        int winTop = top ? base + 3 : base + 2;   // taller windows on the flag floor

        for (int dy = base; dy <= base + 3; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                    if (!edge) continue;
                    if (Math.abs(dx) == HALF && Math.abs(dz) == HALF) continue; // corner posts

                    // Ground gateway: 3-wide, 2-tall arched opening on the south face.
                    if (storey == 0 && dz == HALF) {
                        if (Math.abs(dx) <= 1 && dy <= base + 1) continue;                 // doorway
                        if (dx == 0 && dy == base + 2) continue;                            // arch crown gap
                        if (Math.abs(dx) == 1 && dy == base + 2) {                           // arch haunches
                            put(level, g, dx, dy, dz, stair(Blocks.STONE_BRICK_STAIRS,
                                dx > 0 ? Direction.WEST : Direction.EAST, false));
                            continue;
                        }
                        if (dx == 0 && dy == base + 3) { put(level, g, dx, dy, dz, CHISELED); continue; }
                    }

                    // Windows: centre column of each face — bars (ground) or panes, with a keystone.
                    if (isWindowColumn(dx, dz, storey) && dy >= base + 1 && dy <= winTop) {
                        put(level, g, dx, dy, dz, storey == 0 ? BARS : PANE);
                        continue;
                    }
                    if (isWindowColumn(dx, dz, storey) && dy == winTop + 1 && !top) {
                        put(level, g, dx, dy, dz, CHISELED); // lintel keystone
                        continue;
                    }

                    put(level, g, dx, dy, dz, wallBlock(dx, dy, dz));
                }
            }
        }
        addSills(level, g, storey, base);
    }

    /** True if (dx,dz) is the central window column of a wall face (skipping the south gateway). */
    private static boolean isWindowColumn(int dx, int dz, int storey) {
        if (Math.abs(dz) == HALF && dx == 0) return !(storey == 0 && dz == HALF);
        return Math.abs(dx) == HALF && dz == 0;
    }

    /** A projecting slab sill beneath each upper-storey window. */
    private static void addSills(ServerLevel level, BlockPos g, int storey, int base) {
        if (storey == 0) return; // ground windows sit on the battered skirt
        BlockState sill = slab(Blocks.STONE_BRICK_SLAB, true);
        put(level, g, 0, base,  OUT, sill);
        put(level, g, 0, base, -OUT, sill);
        put(level, g,  OUT, base, 0, sill);
        put(level, g, -OUT, base, 0, sill);
    }

    /** Weathered, quoined stone-brick wall block chosen by position. */
    private static BlockState wallBlock(int dx, int dy, int dz) {
        boolean quoin = (Math.abs(dz) == HALF && Math.abs(dx) == INNER)
                     || (Math.abs(dx) == HALF && Math.abs(dz) == INNER);
        if (quoin) return (dy % 2 == 0) ? CHISELED : BRICK;
        int h = Math.floorMod(dx * 31 + dy * 17 + dz * 13, 12);
        if (h == 0) return MOSSY;
        if (h == 1 || h == 2) return CRACKED;
        return BRICK;
    }

    /** Spruce-log corner posts with stripped-log capitals at each floor line. */
    private static void buildCornerPosts(ServerLevel level, BlockPos g) {
        for (int[] c : CORNERS) {
            for (int dy = 0; dy <= 13; dy++) {
                boolean capital = dy == 0 || dy == 4 || dy == 9 || dy == 13;
                put(level, g, c[0], dy, c[1], capital ? STRIPPED : POST);
            }
        }
    }

    private static final int[][] CORNERS =
        {{HALF, HALF}, {HALF, -HALF}, {-HALF, HALF}, {-HALF, -HALF}};

    /**
     * Lays a full floor at height {@code y}: stone-brick rim, spruce-plank interior with a
     * polished-andesite centre motif, leaving a stairwell slot for the flight below.
     */
    private static void buildFloor(ServerLevel level, BlockPos g, int y, int slotX, int slotZEnd) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                if (dx == slotX && dz >= -INNER && dz <= slotZEnd) continue; // stairwell
                if (Math.abs(dx) == HALF && Math.abs(dz) == HALF) continue;  // corner post
                boolean rim = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                BlockState floor = rim ? BRICK
                    : (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (dx == 0 || dz == 0)) ? ANDESITE
                    : PLANK;
                put(level, g, dx, y, dz, floor);
            }
        }
    }

    /** Projecting stone cornice (corbels + chiseled corners) at a floor line. */
    private static void cornice(ServerLevel level, BlockPos g, int y) {
        corbelRing(level, g, y, OUT);
        for (int[] c : new int[][]{{OUT, OUT}, {OUT, -OUT}, {-OUT, OUT}, {-OUT, -OUT}}) {
            put(level, g, c[0], y, c[1], CHISELED);
        }
    }

    /** Overhanging timber gallery (deck + railing + corner lamp-posts) wrapping a floor line. */
    private static void timberGallery(ServerLevel level, BlockPos g, int y) {
        ring(level, g, y + 1, OUT, slab(Blocks.SPRUCE_SLAB, false)); // deck on the cornice
        ring(level, g, y + 2, OUT, FENCE);                            // railing
        for (int[] c : new int[][]{{OUT, OUT}, {OUT, -OUT}, {-OUT, OUT}, {-OUT, -OUT}}) {
            put(level, g, c[0], y + 2, c[1], STRIPPED);               // newel post
            put(level, g, c[0], y + 3, c[1], LANTERN);                // lamp atop the newel
        }
    }

    /** One straight stone-brick flight climbing +z from {@code baseY} (rise of five). */
    private static void buildFlight(ServerLevel level, BlockPos g, int baseY, int stairX) {
        for (int step = 0; step <= 4; step++) {
            put(level, g, stairX, baseY + step, -INNER + step,
                stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
        }
    }

    /** Fence guard-rails along the open inboard edge of each stairwell so nobody falls in. */
    private static void stairRailings(ServerLevel level, BlockPos g) {
        for (int dz = -INNER; dz <= 0; dz++) put(level, g, -1, 5, dz, FENCE);   // middle-floor well
        for (int dz = -INNER; dz <= 0; dz++) put(level, g,  1, 10, dz, FENCE);  // top-floor well
    }

    /** Crenellated stone parapet wrapping the roof base, with machicolation corbels. */
    private static void crenellatedParapet(ServerLevel level, BlockPos g, int y) {
        corbelRing(level, g, y, OUT);
        ring(level, g, y + 1, OUT, slab(Blocks.STONE_BRICK_SLAB, false)); // walkway
        for (int d = -OUT; d <= OUT; d++) {
            if (Math.floorMod(d, 2) != 0) continue; // crenel gaps
            put(level, g, d, y + 2,  OUT, BRICK);
            put(level, g, d, y + 2, -OUT, BRICK);
            put(level, g,  OUT, y + 2, d, BRICK);
            put(level, g, -OUT, y + 2, d, BRICK);
        }
        // Wall caps on the corner merlons for a finished battlement.
        for (int[] c : new int[][]{{OUT, OUT}, {OUT, -OUT}, {-OUT, OUT}, {-OUT, -OUT}}) {
            put(level, g, c[0], y + 3, c[1], BRICK_WALL);
        }
    }

    /** Tall banded sandstone spire roof above the flag floor, crowned with a lit finial. */
    private static void buildRoof(ServerLevel level, BlockPos g) {
        // Solid deck = ceiling of the flag room.
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                put(level, g, dx, 14, dz, ROOF_FILL);
            }
        }
        // Alternating tapered (stairs) and straight (band) courses for a tall, elegant pitch.
        int[] half     = {3, 3, 2, 2, 1, 1, 0};
        boolean[] taper = {true, false, true, false, true, false, false};
        for (int i = 0; i < half.length; i++) {
            int r = half[i];
            int y = 15 + i;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                    if (r == 0) {
                        put(level, g, dx, y, dz, ROOF_TRIM);
                    } else if (edge && taper[i]) {
                        put(level, g, dx, y, dz, roofStair(dx, dz));
                    } else if (edge) {
                        boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                        put(level, g, dx, y, dz, corner ? ROOF_TRIM : ROOF_BAND);
                    } else {
                        put(level, g, dx, y, dz, ROOF_FILL);
                    }
                }
            }
        }
        // Finial spire.
        put(level, g, 0, 22, 0, SPIRE);
        put(level, g, 0, 23, 0, SPIRE);
        put(level, g, 0, 24, 0, LANTERN);
        put(level, g, 0, 25, 0, FINIAL);
    }

    /** Lights, props and themed furnishings for each interior storey (clear of the stair columns). */
    private static void furnishInteriors(ServerLevel level, BlockPos g) {
        // Hanging lanterns under each ceiling's interior corners.
        int[] ceil = {3, 8, 13};
        for (int floor = 0; floor < 3; floor++) {
            int cy = ceil[floor];
            for (int[] c : new int[][]{{INNER, INNER}, {INNER, -INNER}, {-INNER, INNER}, {-INNER, -INNER}}) {
                put(level, g, c[0], cy, c[1], CHAIN);
                put(level, g, c[0], cy - 1, c[1], hangingLantern());
            }
        }

        // Ground floor — entrance hall: barrels and a small library along the north wall.
        put(level, g,  1, 0, -2, BARREL);
        put(level, g,  1, 1, -2, BARREL);
        put(level, g,  0, 0, -2, BOOKSHELF);
        put(level, g, -1, 0, -2, BOOKSHELF);
        put(level, g, -1, 1, -2, BOOKSHELF);

        // Middle floor — war room: barrel stores and a map table.
        put(level, g, -1, 5, -2, BARREL);
        put(level, g,  1, 5, -2, BARREL);
        put(level, g,  0, 5, -2, STRIPPED);
        put(level, g,  0, 6, -2, slab(Blocks.SPRUCE_SLAB, false));

        // Top floor — the flag hall: a couple of floor lanterns by the north wall.
        put(level, g, -1, 10, -2, LANTERN);
        put(level, g,  1, 10, -2, LANTERN);
    }

    // ── Geometry helpers ──

    /** Places a square ring of {@code state} at radius {@code r} and height {@code y}. */
    private static void ring(ServerLevel level, BlockPos g, int y, int r, BlockState state) {
        for (int d = -r; d <= r; d++) {
            put(level, g, d, y,  r, state);
            put(level, g, d, y, -r, state);
            put(level, g,  r, y, d, state);
            put(level, g, -r, y, d, state);
        }
    }

    /** Ring of upside-down stairs forming corbels/brackets that face inward toward the wall. */
    private static void corbelRing(ServerLevel level, BlockPos g, int y, int r) {
        for (int d = -r; d <= r; d++) {
            put(level, g, d, y,  r, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
            put(level, g, d, y, -r, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
            put(level, g,  r, y, d, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST,  true));
            put(level, g, -r, y, d, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST,  true));
        }
    }

    private static BlockState roofStair(int dx, int dz) {
        Direction facing;
        if (Math.abs(dx) >= Math.abs(dz)) facing = dx >= 0 ? Direction.EAST : Direction.WEST;
        else                              facing = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        return stair(Blocks.SMOOTH_SANDSTONE_STAIRS, facing, false);
    }

    // ── Block-state builders ──

    private static BlockState stair(Block b, Direction facing, boolean top) {
        return b.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
            .setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState()
            .setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
    }

    private static void put(ServerLevel level, BlockPos g, int dx, int dy, int dz, BlockState state) {
        level.setBlock(g.offset(dx, dy, dz), state, 3);
    }

    private static void set(ServerLevel level, BlockPos at, BlockState state) {
        level.setBlock(at, state, 3);
    }

    // ── Guards ─────────────────────────────────────────────────────────────────

    private void tickGuards(ServerLevel level) {
        if (!guardsSpawned && guildId != null) {
            for (int i = 0; i < STARTER_GUARDS; i++) {
                if (spawnOneGuard(level) != null) guardCount++;
            }
            guardsSpawned = true;
            setChanged();
        }
    }

    /** Spawns a single guard garrisoned at the tower's base. Returns null on failure. */
    public GuardEntity spawnOneGuard(ServerLevel level) {
        BlockPos base = groundPos();
        GuardEntity guard = ConquestEntities.GUILD_GUARD.get().create(level);
        if (guard == null) return null;
        guard.setGuildId(guildId);
        guard.setHomePos(base);
        guard.setPersistenceRequired(); // don't despawn — guards (and their gear) are guild property
        BlockPos spawn = findSafeSpawn(level, base);
        guard.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        level.addFreshEntity(guard);
        return guard;
    }

    /** Hires one more guard if below the cap. Returns true on success. */
    public boolean hireGuard(ServerLevel level) {
        if (guardCount >= ConquestCosts.MAX_GUARDS_PER_OUTPOST) return false;
        if (spawnOneGuard(level) == null) return false;
        guardCount++;
        setChanged();
        return true;
    }

    /** Removes every guard belonging to this outpost (matched by guild + home position). */
    public void despawnGuards(ServerLevel level) {
        BlockPos base = groundPos();
        List<GuardEntity> guards = level.getEntitiesOfClass(GuardEntity.class,
            new AABB(base).inflate(96.0),
            g -> guildId != null && guildId.equals(g.getGuildId())
                && base.equals(g.getHomePos()));
        for (GuardEntity g : guards) g.discard();
        guardCount = 0;
        setChanged();
    }

    /**
     * Finds a spawn position clear of solid blocks just outside the tower base so guards garrison
     * around the keep rather than inside its walls. Searches an outward ring for a tile with two
     * blocks of headroom on solid ground; falls back to directly beside the base.
     */
    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos base) {
        int[][] ring = {
            {5, 0}, {-5, 0}, {0, 5}, {0, -5},
            {5, 5}, {5, -5}, {-5, 5}, {-5, -5},
            {6, 0}, {-6, 0}, {0, 6}, {0, -6}
        };
        for (int[] off : ring) {
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos candidate = base.offset(off[0], dy, off[1]);
                if (isStandable(level, candidate)) return candidate;
            }
        }
        return base.offset(5, 0, 0);
    }

    /** Feet + head must be passable and the block below must be solid enough to stand on. */
    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty()) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (guildId != null) tag.putUUID("guildId", guildId);
        tag.putBoolean("fullyBuilt", fullyBuilt);
        tag.putBoolean("guardsSpawned", guardsSpawned);
        tag.putInt("guardCount", guardCount);
        if (groundPos != null) {
            tag.putInt("groundX", groundPos.getX());
            tag.putInt("groundY", groundPos.getY());
            tag.putInt("groundZ", groundPos.getZ());
        }
        long[] chunks = claimedChunks.stream().mapToLong(ChunkPos::toLong).toArray();
        tag.putLongArray("chunks", chunks);

        CompoundTag inv = new CompoundTag();
        for (Map.Entry<TreasuryResource, Long> e : invested.entrySet()) {
            inv.putLong(e.getKey().name(), e.getValue());
        }
        tag.put("invested", inv);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("guildId")) guildId = tag.getUUID("guildId");
        fullyBuilt    = tag.getBoolean("fullyBuilt");
        guardsSpawned = tag.getBoolean("guardsSpawned");
        guardCount    = tag.getInt("guardCount");
        if (tag.contains("groundX")) {
            groundPos = new BlockPos(tag.getInt("groundX"), tag.getInt("groundY"), tag.getInt("groundZ"));
        }
        claimedChunks.clear();
        for (long l : tag.getLongArray("chunks")) claimedChunks.add(new ChunkPos(l));

        invested.clear();
        CompoundTag inv = tag.getCompound("invested");
        for (TreasuryResource r : TreasuryResource.values()) {
            if (inv.contains(r.name())) invested.put(r, inv.getLong(r.name()));
        }
    }
}
