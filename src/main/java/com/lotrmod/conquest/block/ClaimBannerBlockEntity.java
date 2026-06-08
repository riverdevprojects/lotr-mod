package com.lotrmod.conquest.block;

import com.lotrmod.conquest.entity.GuardEntity;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import com.lotrmod.conquest.registry.ConquestEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimBannerBlockEntity extends BlockEntity {

    public UUID guildId = null;
    public Set<ChunkPos> claimedChunks = new HashSet<>();

    private boolean fullyBuilt = false;
    private int buildTick = 0; // 0 = not started, 1 = pole placed, 2 = top placed

    // Tracks whether guards have been spawned for this banner
    private boolean guardsSpawned = false;

    public ClaimBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ConquestBlockEntities.CLAIM_BANNER.get(), pos, state);
    }

    public void initialize(UUID guildId, Set<ChunkPos> chunks) {
        this.guildId = guildId;
        this.claimedChunks = new HashSet<>(chunks);
        this.fullyBuilt = false;
        this.buildTick = 0;
        this.guardsSpawned = false;
        setChanged();
    }

    public boolean isFullyBuilt() { return fullyBuilt; }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ClaimBannerBlockEntity be) {
        if (be.fullyBuilt) {
            be.tickGuards(level, pos);
            return;
        }

        be.buildTick++;
        if (be.buildTick == 5 && !level.getBlockState(pos.above()).is(ConquestBlocks.CLAIM_BANNER_POLE.get())) {
            // Place pole
            level.setBlock(pos.above(), ConquestBlocks.CLAIM_BANNER_POLE.get().defaultBlockState(), 3);
        } else if (be.buildTick == 10 && !level.getBlockState(pos.above(2)).is(ConquestBlocks.CLAIM_BANNER_TOP.get())) {
            // Place top
            level.setBlock(pos.above(2), ConquestBlocks.CLAIM_BANNER_TOP.get().defaultBlockState(), 3);
            be.fullyBuilt = true;
            be.setChanged();
        }
    }

    private void tickGuards(ServerLevel level, BlockPos pos) {
        if (!guardsSpawned && guildId != null) {
            spawnGuards(level, pos);
            guardsSpawned = true;
            setChanged();
        }
    }

    private void spawnGuards(ServerLevel level, BlockPos pos) {
        // Tier based on development score is in Guild — we spawn a fixed small patrol for now
        // Tier 1: 2 guards. Guards fetch their guildId from the spawning context.
        for (int i = 0; i < 2; i++) {
            GuardEntity guard = ConquestEntities.GUILD_GUARD.get().create(level);
            if (guard != null) {
                guard.setGuildId(guildId);
                guard.setHomePos(pos);
                guard.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
                level.addFreshEntity(guard);
            }
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (guildId != null) tag.putUUID("guildId", guildId);
        tag.putBoolean("fullyBuilt", fullyBuilt);
        tag.putInt("buildTick", buildTick);
        tag.putBoolean("guardsSpawned", guardsSpawned);
        long[] chunks = claimedChunks.stream().mapToLong(ChunkPos::toLong).toArray();
        tag.putLongArray("chunks", chunks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("guildId")) guildId = tag.getUUID("guildId");
        fullyBuilt    = tag.getBoolean("fullyBuilt");
        buildTick     = tag.getInt("buildTick");
        guardsSpawned = tag.getBoolean("guardsSpawned");
        claimedChunks.clear();
        for (long l : tag.getLongArray("chunks")) claimedChunks.add(new ChunkPos(l));
    }
}
