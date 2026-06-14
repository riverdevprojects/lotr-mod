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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
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

    // Palette
    private static final BlockState BRICK    = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED  = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY    = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState POST     = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState PLANK    = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState WINDOW   = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState LANTERN  = Blocks.LANTERN.defaultBlockState();
    private static final BlockState SEA_LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState ROOF_FILL = Blocks.CUT_SANDSTONE.defaultBlockState();
    private static final BlockState SPIRE    = Blocks.SANDSTONE_WALL.defaultBlockState();
    private static final BlockState AIR      = Blocks.AIR.defaultBlockState();

    private static final int HALF = 3;     // 7x7 footprint
    private static final int INNER = 2;    // 5x5 interior
    /** Floor-block Y (relative to ground) the player stands on for each story. */
    private static final int[] FLOOR = {-1, 4, 9}; // ground, middle, top (flag floor)

    /**
     * Raises the grand watchtower for the outpost: a three-storey stone-brick keep with spruce
     * corner posts, barred windows, overhanging eaves, an interior staircase climbing to the top
     * floor where the flag is planted, and a tiered sandstone roof crowned by a lit spire.
     *
     * @param g        ground position (centre of the tower's base, where the banner was placed)
     * @param flagPos  where the relocated flag now stands (top floor centre)
     */
    public static void buildOutpostTower(ServerLevel level, BlockPos g, BlockPos flagPos) {
        // 1. Hollow out the interior so terrain never pokes through, keeping the flag column.
        for (int dy = 0; dy <= 13; dy++) {
            for (int dx = -INNER; dx <= INNER; dx++) {
                for (int dz = -INNER; dz <= INNER; dz++) {
                    if (isFlagColumn(dx, dz, dy)) continue;
                    set(level, g.offset(dx, dy, dz), AIR);
                }
            }
        }

        buildFoundation(level, g);

        // 2. Three storeys of walls.
        for (int story = 0; story < 3; story++) {
            int base = FLOOR[story] + 1;           // first wall course sits on the floor
            buildStoreyWalls(level, g, story, base);
        }

        // 3. Corner posts run the full height for a timber-framed look.
        for (int dy = 0; dy <= 13; dy++) {
            set(level, g.offset( HALF, dy,  HALF), POST);
            set(level, g.offset( HALF, dy, -HALF), POST);
            set(level, g.offset(-HALF, dy,  HALF), POST);
            set(level, g.offset(-HALF, dy, -HALF), POST);
        }

        // 4. Inter-storey floors + overhanging eaves at each floor line and the roof base.
        buildFloor(level, g, 4, -2, +1);   // floor between storey 0 and 1 (stair slot on west, +z)
        buildFloor(level, g, 9, +2, +1);   // floor between storey 1 and 2 (stair slot on east, +z)
        eaves(level, g, 4);
        eaves(level, g, 9);
        eaves(level, g, 14);

        // 5. Interior staircases climbing each storey.
        buildFlight(level, g, 0, -2);      // ground -> middle along west wall
        buildFlight(level, g, 5, +2);      // middle -> top along east wall

        // 6. Tiered sandstone roof above the top floor, plus a lit spire.
        buildRoof(level, g);

        // 7. Interior lighting and the flag's own pole/top decoration.
        set(level, g.offset(INNER, FLOOR[0] + 1, INNER), LANTERN);
        set(level, g.offset(INNER, FLOOR[1] + 1, INNER), LANTERN);
        set(level, g.offset(INNER, FLOOR[2] + 1, INNER), LANTERN);

        set(level, flagPos.above(),  ConquestBlocks.CLAIM_BANNER_POLE.get().defaultBlockState());
        set(level, flagPos.above(2), ConquestBlocks.CLAIM_BANNER_TOP.get().defaultBlockState());
    }

    /** True for the three blocks (base/pole/top) of the planted flag, so the build never overwrites it. */
    private static boolean isFlagColumn(int dx, int dz, int dy) {
        return dx == 0 && dz == 0 && dy >= FLAG_FLOOR_OFFSET && dy <= FLAG_FLOOR_OFFSET + 2;
    }

    private static void buildFoundation(ServerLevel level, BlockPos g) {
        // Solid 7x7 pad the tower sits on.
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                set(level, g.offset(dx, -1, dz), BRICK);
            }
        }
        // Flared plinth one block wider on the four faces, with sea lanterns for the lit base glow.
        for (int d = -HALF; d <= HALF; d++) {
            set(level, g.offset(d, -1,  HALF + 1), BRICK);
            set(level, g.offset(d, -1, -HALF - 1), BRICK);
            set(level, g.offset( HALF + 1, -1, d), BRICK);
            set(level, g.offset(-HALF - 1, -1, d), BRICK);
        }
        set(level, g.offset(0, -1,  HALF + 1), SEA_LAMP);
        set(level, g.offset(0, -1, -HALF - 1), SEA_LAMP);
        set(level, g.offset( HALF + 1, -1, 0), SEA_LAMP);
        set(level, g.offset(-HALF - 1, -1, 0), SEA_LAMP);

        // Lanterns flanking the ground-floor doorway (south face, +z).
        set(level, g.offset(-1, 0, HALF), LANTERN);
        set(level, g.offset( 1, 0, HALF), LANTERN);
    }

    private static void buildStoreyWalls(ServerLevel level, BlockPos g, int story, int base) {
        for (int dy = base; dy <= base + 3; dy++) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                    if (!edge) continue;
                    if (Math.abs(dx) == HALF && Math.abs(dz) == HALF) continue; // corner posts placed separately

                    // Ground-floor doorway: south face centre, two blocks tall.
                    if (story == 0 && dz == HALF && dx == 0 && dy <= base + 1) {
                        continue;
                    }
                    // Barred windows: centre of each face, the middle two courses.
                    if (isWindow(dx, dz, dy, base, story)) {
                        set(level, g.offset(dx, dy, dz), WINDOW);
                        continue;
                    }
                    set(level, g.offset(dx, dy, dz), wallBlock(dx, dy, dz));
                }
            }
        }
        // Chiseled lintel above the doorway.
        if (story == 0) {
            set(level, g.offset(0, base + 2, HALF), CHISELED);
        }
    }

    private static boolean isWindow(int dx, int dz, int dy, int base, int story) {
        boolean midCourse = dy == base + 1 || dy == base + 2;
        if (!midCourse) return false;
        // North / south faces: window at the centre column. (South storey-0 centre is the door.)
        if (Math.abs(dz) == HALF && dx == 0) {
            return !(story == 0 && dz == HALF);
        }
        // East / west faces: window at the centre column.
        return Math.abs(dx) == HALF && dz == 0;
    }

    /** Weathered stone-brick texture chosen deterministically by position. */
    private static BlockState wallBlock(int dx, int dy, int dz) {
        int h = Math.floorMod(dx * 31 + dy * 17 + dz * 13, 12);
        if (h == 0) return MOSSY;
        if (h == 1 || h == 2) return CRACKED;
        return BRICK;
    }

    /**
     * Lays a full floor at height {@code y}, stone-brick rim with a spruce-plank interior, leaving a
     * stairwell slot so the staircase below can emerge.
     *
     * @param slotX  interior x-column occupied by the staircase (its slab is left open)
     * @param slotZEnd  the +z end of the run; the slot covers dz in [-INNER .. slotZEnd]
     */
    private static void buildFloor(ServerLevel level, BlockPos g, int y, int slotX, int slotZEnd) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                // Leave the stairwell open above the flight below.
                if (dx == slotX && dz >= -INNER && dz <= slotZEnd) continue;
                boolean rim = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                if (Math.abs(dx) == HALF && Math.abs(dz) == HALF) continue; // corner post
                set(level, g.offset(dx, y, dz), rim ? BRICK : PLANK);
            }
        }
    }

    /** Overhanging spruce-stair eaves on the four faces at the given floor line. */
    private static void eaves(ServerLevel level, BlockPos g, int y) {
        for (int d = -HALF; d <= HALF; d++) {
            set(level, g.offset(d, y,  HALF + 1), eaveStair(Direction.NORTH));
            set(level, g.offset(d, y, -HALF - 1), eaveStair(Direction.SOUTH));
            set(level, g.offset( HALF + 1, y, d), eaveStair(Direction.WEST));
            set(level, g.offset(-HALF - 1, y, d), eaveStair(Direction.EAST));
        }
    }

    private static BlockState eaveStair(Direction inward) {
        return Blocks.SPRUCE_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, inward)
            .setValue(StairBlock.HALF, Half.TOP);
    }

    /**
     * Builds one straight flight of stone-brick stairs along the interior wall at {@code x = stairX},
     * climbing in +z from {@code baseY} (a rise of five, landing on the floor above).
     */
    private static void buildFlight(ServerLevel level, BlockPos g, int baseY, int stairX) {
        for (int step = 0; step <= 4; step++) {
            BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH) // ascends toward +z
                .setValue(StairBlock.HALF, Half.BOTTOM);
            set(level, g.offset(stairX, baseY + step, -INNER + step), stair);
        }
    }

    /** Tiered, overhanging sandstone roof above the top floor, crowned with a lit spire. */
    private static void buildRoof(ServerLevel level, BlockPos g) {
        // Closed deck over the top storey.
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                set(level, g.offset(dx, 14, dz), ROOF_FILL);
            }
        }
        // Stepped pyramid of sandstone stairs.
        for (int layer = 0; layer <= HALF; layer++) {
            int half = HALF - layer;
            int y = 15 + layer;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                    if (edge && half > 0) {
                        set(level, g.offset(dx, y, dz), roofStair(dx, dz, half));
                    } else {
                        set(level, g.offset(dx, y, dz), ROOF_FILL);
                    }
                }
            }
        }
        // Spire.
        set(level, g.offset(0, 19, 0), SPIRE);
        set(level, g.offset(0, 20, 0), SPIRE);
        set(level, g.offset(0, 21, 0), LANTERN);
    }

    private static BlockState roofStair(int dx, int dz, int half) {
        Direction facing;
        if (Math.abs(dx) >= Math.abs(dz)) {
            facing = dx >= 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, facing)
            .setValue(StairBlock.HALF, Half.BOTTOM);
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
        // Ring just beyond the 7x7 footprint.
        int[][] ring = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4},
            {4, 4}, {4, -4}, {-4, 4}, {-4, -4},
            {5, 0}, {-5, 0}, {0, 5}, {0, -5}
        };
        for (int[] off : ring) {
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos candidate = base.offset(off[0], dy, off[1]);
                if (isStandable(level, candidate)) return candidate;
            }
        }
        return base.offset(4, 0, 0);
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
