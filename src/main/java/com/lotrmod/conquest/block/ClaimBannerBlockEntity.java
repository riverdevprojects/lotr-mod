package com.lotrmod.conquest.block;

import com.lotrmod.conquest.data.ConquestCosts;
import com.lotrmod.conquest.data.TreasuryResource;
import com.lotrmod.conquest.entity.GuardEntity;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import com.lotrmod.conquest.registry.ConquestEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    public UUID guildId = null;
    public Set<ChunkPos> claimedChunks = new HashSet<>();

    /** Transient: the guild that just destroyed this flag in war, to auto-plant a captured flag. */
    public UUID capturedBy = null;

    private boolean fullyBuilt = false;
    private int buildTick = 0;

    private boolean guardsSpawned = false;
    /** Current garrison size for this outpost (starter + hired). Capped at {@link ConquestCosts#MAX_GUARDS_PER_OUTPOST}. */
    private int guardCount = 0;

    /** Resources invested in this outpost (construction + hires), refunded on abandon. */
    private final EnumMap<TreasuryResource, Long> invested = new EnumMap<>(TreasuryResource.class);

    public ClaimBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ConquestBlockEntities.CLAIM_BANNER.get(), pos, state);
    }

    public void initialize(UUID guildId, Set<ChunkPos> chunks) {
        this.guildId = guildId;
        this.claimedChunks = new HashSet<>(chunks);
        this.fullyBuilt = false;
        this.buildTick = 0;
        this.guardsSpawned = false;
        this.guardCount = 0;
        this.invested.clear();
        setChanged();
    }

    public boolean isFullyBuilt() { return fullyBuilt; }
    public int getGuardCount()    { return guardCount; }

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
            be.tickGuards(level, pos);
            return;
        }

        be.buildTick++;
        if (be.buildTick == 3) {
            be.buildOutpostShell(level, pos);
        } else if (be.buildTick == 6 && !level.getBlockState(pos.above()).is(ConquestBlocks.CLAIM_BANNER_POLE.get())) {
            level.setBlock(pos.above(), ConquestBlocks.CLAIM_BANNER_POLE.get().defaultBlockState(), 3);
        } else if (be.buildTick == 9 && !level.getBlockState(pos.above(2)).is(ConquestBlocks.CLAIM_BANNER_TOP.get())) {
            level.setBlock(pos.above(2), ConquestBlocks.CLAIM_BANNER_TOP.get().defaultBlockState(), 3);
            be.fullyBuilt = true;
            be.setChanged();
        }
    }

    /**
     * Builds the grander outpost around the flag: a stone-brick foundation pad, four corner
     * pillars topped with lanterns, and a central interaction table beside the banner.
     * Above-ground decoration only replaces air/replaceable blocks so it doesn't gouge terrain.
     */
    private void buildOutpostShell(ServerLevel level, BlockPos pos) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();

        // 5x5 foundation one block below the flag.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), bricks, 3);
            }
        }

        // Corner pillars, 3 high, each capped with a lantern.
        int[][] corners = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
        for (int[] c : corners) {
            for (int dy = 0; dy <= 2; dy++) {
                placeIfReplaceable(level, pos.offset(c[0], dy, c[1]), bricks);
            }
            placeIfReplaceable(level, pos.offset(c[0], 3, c[1]), Blocks.LANTERN.defaultBlockState());
        }

        // Central interaction table (decorative companion to the flag menu).
        placeIfReplaceable(level, pos.offset(1, 0, 0), Blocks.FLETCHING_TABLE.defaultBlockState());

        setChanged();
    }

    private static void placeIfReplaceable(ServerLevel level, BlockPos at, BlockState toPlace) {
        if (level.getBlockState(at).canBeReplaced()) {
            level.setBlock(at, toPlace, 3);
        }
    }

    private void tickGuards(ServerLevel level, BlockPos pos) {
        if (!guardsSpawned && guildId != null) {
            for (int i = 0; i < STARTER_GUARDS; i++) {
                if (spawnOneGuard(level, pos) != null) guardCount++;
            }
            guardsSpawned = true;
            setChanged();
        }
    }

    /** Spawns a single guard for this outpost at a safe nearby tile. Returns null on failure. */
    public GuardEntity spawnOneGuard(ServerLevel level, BlockPos pos) {
        GuardEntity guard = ConquestEntities.GUILD_GUARD.get().create(level);
        if (guard == null) return null;
        guard.setGuildId(guildId);
        guard.setHomePos(pos);
        BlockPos spawn = findSafeSpawn(level, pos);
        guard.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        level.addFreshEntity(guard);
        return guard;
    }

    /** Hires one more guard if below the cap. Returns true on success. */
    public boolean hireGuard(ServerLevel level) {
        if (guardCount >= ConquestCosts.MAX_GUARDS_PER_OUTPOST) return false;
        if (spawnOneGuard(level, getBlockPos()) == null) return false;
        guardCount++;
        setChanged();
        return true;
    }

    /** Removes every guard belonging to this outpost (matched by guild + home position). */
    public void despawnGuards(ServerLevel level) {
        BlockPos home = getBlockPos();
        List<GuardEntity> guards = level.getEntitiesOfClass(GuardEntity.class,
            new AABB(home).inflate(96.0),
            g -> guildId != null && guildId.equals(g.getGuildId())
                && home.equals(g.getHomePos()));
        for (GuardEntity g : guards) g.discard();
        guardCount = 0;
        setChanged();
    }

    /**
     * Finds a spawn position clear of solid blocks near the banner so guards don't suffocate
     * inside the banner column. Searches an outward ring of positions for one with two blocks
     * of headroom on solid ground; falls back to directly beside the banner.
     */
    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos banner) {
        // Skip the banner column itself (base + pole + top occupy x,z of banner).
        int[][] ring = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        for (int[] off : ring) {
            // Allow a little vertical search so guards land on uneven ground.
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos candidate = banner.offset(off[0], dy, off[1]);
                if (isStandable(level, candidate)) return candidate;
            }
        }
        // Fallback: directly above the ground next to the banner.
        return banner.offset(1, 0, 0);
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
        tag.putInt("buildTick", buildTick);
        tag.putBoolean("guardsSpawned", guardsSpawned);
        tag.putInt("guardCount", guardCount);
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
        buildTick     = tag.getInt("buildTick");
        guardsSpawned = tag.getBoolean("guardsSpawned");
        guardCount    = tag.getInt("guardCount");
        claimedChunks.clear();
        for (long l : tag.getLongArray("chunks")) claimedChunks.add(new ChunkPos(l));

        invested.clear();
        CompoundTag inv = tag.getCompound("invested");
        for (TreasuryResource r : TreasuryResource.values()) {
            if (inv.contains(r.name())) invested.put(r, inv.getLong(r.name()));
        }
    }
}
