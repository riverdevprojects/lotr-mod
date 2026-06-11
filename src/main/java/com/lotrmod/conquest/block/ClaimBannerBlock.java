package com.lotrmod.conquest.block;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.ConquestCosts;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import com.lotrmod.conquest.registry.ConquestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimBannerBlock extends BaseEntityBlock {

    public static final MapCodec<ClaimBannerBlock> CODEC = simpleCodec(ClaimBannerBlock::new);

    /** Transient: which outpost flag a player last opened the menu on, so menu commands know the target. */
    public static final Map<UUID, BlockPos> OPEN_OUTPOST = new HashMap<>();

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

            // The chunk the flag sits in must be unclaimed: you can't plant inside your own land
            // (flag stacking) or inside another guild's land.
            ChunkPos centerChunk = new ChunkPos(pos);
            Guild centerOwner = data.getChunkOwner(centerChunk);
            if (centerOwner != null) {
                level.removeBlock(pos, false);
                player.getInventory().placeItemBackInInventory(new ItemStack(this));
                player.sendSystemMessage(Component.literal(centerOwner.id.equals(guild.id)
                    ? "[Conquest] This land is already claimed by your guild."
                    : "[Conquest] This land is already claimed by '" + centerOwner.name + "'."));
                return;
            }

            // Claim only the chunks of the 9x9 footprint that are still unowned. This keeps the
            // "others can't claim" boundary identical to the "others can't build" (protection)
            // boundary — both are exactly the set of chunks a guild actually owns.
            Set<ChunkPos> chunks = new HashSet<>();
            for (ChunkPos cp : getClaimChunks(pos)) {
                if (data.getChunkOwner(cp) == null) chunks.add(cp);
            }

            // Outpost/flag placement consumes guild treasury resources.
            if (!guild.canAfford(ConquestCosts.FLAG_COST)) {
                level.removeBlock(pos, false);
                // Give the banner item back — the placement was rejected for lack of funds, not rules.
                player.getInventory().placeItemBackInInventory(new ItemStack(this));
                player.sendSystemMessage(Component.literal(
                    "[Conquest] Your guild cannot afford this flag. Cost: 32 logs, 32 cobblestone, 8 iron, 4 gold."));
                return;
            }
            guild.charge(ConquestCosts.FLAG_COST);

            // Valid — attach guild id to block entity and start auto-build
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ClaimBannerBlockEntity bannerBE) {
                bannerBE.initialize(guild.id, chunks);
                bannerBE.addInvested(ConquestCosts.FLAG_COST); // tracked for refund on abandon
                guild.addBanner(pos, chunks);
                data.refreshChunkIndex(guild);
                data.setDirty();
                player.sendSystemMessage(Component.literal(
                    "[Conquest] Outpost founded! Claiming " + chunks.size() + " chunks for " + guild.name + "."));
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
                // War capture: if an enemy destroyed this flag, auto-plant a captured flag for them.
                if (bannerBE.capturedBy != null) {
                    plantCapturedFlag((ServerLevel) level, pos, bannerBE.capturedBy,
                        new HashSet<>(bannerBE.claimedChunks), guild != null ? guild.name : "the enemy");
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Plants a captured flag for the attacking guild over the freed claim, consuming no resources.
     * Deferred one tick so it runs after the destroyed flag has been fully removed.
     */
    private static void plantCapturedFlag(ServerLevel level, BlockPos pos, UUID attackerId,
                                          Set<ChunkPos> capturedChunks, String defenderName) {
        level.getServer().execute(() -> {
            GuildSavedData data = GuildSavedData.get(level.getServer());
            Guild attacker = data.getGuild(attackerId);
            if (attacker == null) return;

            // Only take chunks that are now unclaimed (freed by the destroyed flag).
            Set<ChunkPos> claimable = new HashSet<>();
            for (ChunkPos cp : capturedChunks) {
                if (data.getChunkOwner(cp) == null) claimable.add(cp);
            }
            if (claimable.isEmpty()) return;

            level.setBlock(pos, ConquestBlocks.CLAIM_BANNER_BASE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof ClaimBannerBlockEntity newBe) {
                newBe.initialize(attacker.id, claimable); // free capture — no resources invested
            }
            attacker.addBanner(pos, claimable);
            data.refreshChunkIndex(attacker);
            data.setDirty();

            Component msg = Component.literal("[Conquest] Your guild captured " + claimable.size()
                + " chunks from '" + defenderName + "'! A captured flag now flies at "
                + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ".");
            for (UUID uuid : attacker.memberUUIDs) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(msg);
            }
        });
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        GuildSavedData data = GuildSavedData.get(sp.getServer());
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ClaimBannerBlockEntity bannerBE) || bannerBE.guildId == null) return InteractionResult.PASS;
        Guild guild = data.getGuild(bannerBE.guildId);
        if (guild == null || !guild.isMember(sp.getUUID())) return InteractionResult.PASS;

        // Open the real outpost GUI (hire guards / abandon).
        OPEN_OUTPOST.put(sp.getUUID(), pos); // also lets the /guild outpost commands target this flag
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
            com.lotrmod.conquest.network.S2COutpostScreenPacket.build(pos, bannerBE, guild, guild.canManage(sp.getUUID())));
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ConquestBlockEntities.CLAIM_BANNER.get(),
            (lvl, pos, st, be) -> ClaimBannerBlockEntity.serverTick((ServerLevel) lvl, pos, st, be));
    }

    /**
     * Returns the 9×9 ChunkPos set (81 chunks) centred on the chunk containing pos.
     */
    public static Set<ChunkPos> getClaimChunks(BlockPos pos) {
        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;
        Set<ChunkPos> chunks = new HashSet<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                chunks.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        return chunks;
    }
}
