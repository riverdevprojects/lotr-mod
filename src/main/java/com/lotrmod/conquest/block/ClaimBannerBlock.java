package com.lotrmod.conquest.block;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class ClaimBannerBlock extends BaseEntityBlock {

    public static final MapCodec<ClaimBannerBlock> CODEC = simpleCodec(ClaimBannerBlock::new);

    @Override
    public MapCodec<ClaimBannerBlock> codec() { return CODEC; }

    public ClaimBannerBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public ClaimBannerBlock() {
        this(BlockBehaviour.Properties.of()
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClaimBannerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            if (!ConquestConfig.SERVER.enabled.get()) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] The conquest system is currently disabled."));
                return;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild guild = data.getGuildForPlayer(player.getUUID());
            if (guild == null) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] You must be in a guild to place a claim banner."));
                return;
            }
            if (!guild.canManage(player.getUUID())) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] Only officers and the guild master can place claim banners."));
                return;
            }

            Set<ChunkPos> chunks = getClaimChunks(pos);
            if (chunks == null) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal(
                    "[Conquest] Invalid placement — the banner base must be within 2 blocks of a chunk-corner intersection (where 4 chunks meet)."));
                return;
            }

            for (ChunkPos cp : chunks) {
                Guild owner = data.getChunkOwner(cp);
                if (owner != null && !owner.id.equals(guild.id)) {
                    level.removeBlock(pos, false);
                    player.sendSystemMessage(Component.literal(
                        "[Conquest] Cannot claim here — chunk " + cp.x + "," + cp.z + " is already claimed by '" + owner.name + "'."));
                    return;
                }
            }

            // Valid — attach guild id to block entity and start auto-build
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ClaimBannerBlockEntity bannerBE) {
                bannerBE.initialize(guild.id, chunks);
                guild.addBanner(pos, chunks);
                data.refreshChunkIndex(guild);
                data.setDirty();
                player.sendSystemMessage(Component.literal(
                    "[Conquest] Claim banner placed! Claiming 9 chunks for " + guild.name + "."));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ClaimBannerBlockEntity bannerBE && bannerBE.guildId != null) {
                GuildSavedData data = GuildSavedData.get(((ServerLevel) level).getServer());
                Guild guild = data.getGuild(bannerBE.guildId);
                if (guild != null) {
                    guild.removeBanner(pos);
                    data.refreshChunkIndex(guild);
                    data.setDirty();
                }
                // Remove pole and top placeholder blocks if present
                if (level.getBlockState(pos.above()).is(ConquestBlocks.CLAIM_BANNER_POLE.get())) {
                    level.removeBlock(pos.above(), false);
                }
                if (level.getBlockState(pos.above(2)).is(ConquestBlocks.CLAIM_BANNER_TOP.get())) {
                    level.removeBlock(pos.above(2), false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ConquestBlockEntities.CLAIM_BANNER.get(),
            (lvl, pos, st, be) -> ClaimBannerBlockEntity.serverTick((ServerLevel) lvl, pos, st, be));
    }

    /**
     * Returns the 3×3 ChunkPos set centred on the nearest chunk-corner intersection,
     * or null if the pos is more than 2 blocks from any corner.
     */
    public static @Nullable Set<ChunkPos> getClaimChunks(BlockPos pos) {
        // Chunk corners are at multiples of 16 in both X and Z
        int nearestCornerX = (int) Math.round(pos.getX() / 16.0) * 16;
        int nearestCornerZ = (int) Math.round(pos.getZ() / 16.0) * 16;

        double dist = Math.sqrt(
            Math.pow(pos.getX() - nearestCornerX, 2) +
            Math.pow(pos.getZ() - nearestCornerZ, 2)
        );
        if (dist > 2.0) return null;

        // The corner sits between chunkX-1 and chunkX (where chunkX = nearestCornerX >> 4)
        int cornerChunkX = nearestCornerX >> 4;
        int cornerChunkZ = nearestCornerZ >> 4;

        Set<ChunkPos> chunks = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add(new ChunkPos(cornerChunkX + dx, cornerChunkZ + dz));
            }
        }
        return chunks;
    }
}
