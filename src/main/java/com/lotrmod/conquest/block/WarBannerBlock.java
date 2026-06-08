package com.lotrmod.conquest.block;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.data.WarState;
import com.lotrmod.conquest.registry.ConquestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.UUID;

public class WarBannerBlock extends BaseEntityBlock {

    public static final MapCodec<WarBannerBlock> CODEC = simpleCodec(WarBannerBlock::new);

    @Override
    public MapCodec<WarBannerBlock> codec() { return CODEC; }

    public WarBannerBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public WarBannerBlock() {
        this(BlockBehaviour.Properties.of()
            .strength(2.0f, 4.0f)
            .requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarBannerBlockEntity(pos, state);
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
                return;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            GuildSavedData data = GuildSavedData.get(player.getServer());

            Guild attacker = data.getGuildForPlayer(player.getUUID());
            if (attacker == null) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] You are not in a guild."));
                return;
            }
            if (!attacker.canManage(player.getUUID())) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] Only officers and the guild master can place war banners."));
                return;
            }

            ChunkPos cp = new ChunkPos(pos);
            Guild defender = data.getChunkOwner(cp);
            if (defender == null) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] War banners must be placed inside enemy claimed territory."));
                return;
            }
            if (defender.id.equals(attacker.id)) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] You cannot place a war banner in your own territory."));
                return;
            }
            if (!attacker.wars.containsKey(defender.id)) {
                level.removeBlock(pos, false);
                player.sendSystemMessage(Component.literal("[Conquest] You are not at war with '" + defender.name + "'. Declare war first."));
                return;
            }

            // Valid — initialize the block entity siege
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WarBannerBlockEntity warBE) {
                long startTick = data.onlineTicks;
                warBE.initialize(attacker.id, defender.id, cp, startTick);

                // Update war state last-banner day
                WarState ws = attacker.wars.get(defender.id);
                ws.lastBannerByUsDay = data.onlineDay();
                WarState wsDefender = defender.wars.get(attacker.id);
                if (wsDefender != null) wsDefender.lastBannerByThemDay = data.onlineDay();

                data.setDirty();

                // Notify defender
                String msg = "[WAR] The guild '" + attacker.name + "' has placed a war banner in your territory at " +
                    pos.getX() + "," + pos.getY() + "," + pos.getZ() + "! You have 1 in-game day to destroy it!";
                for (UUID uuid : defender.memberUUIDs) {
                    ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(uuid);
                    if (target != null) target.sendSystemMessage(Component.literal(msg));
                }

                player.sendSystemMessage(Component.literal("[Conquest] War banner placed in " + defender.name + "'s territory. Siege begins!"));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WarBannerBlockEntity warBE && warBE.isActive()) {
                // Siege failed — notify attacker guild
                GuildSavedData data = GuildSavedData.get(((ServerLevel) level).getServer());
                Guild attacker = data.getGuild(warBE.attackerGuildId);
                Guild defender = data.getGuild(warBE.defenderGuildId);
                String aName = attacker != null ? attacker.name : "?";
                String dName = defender != null ? defender.name : "?";

                String msg = "[WAR] The siege banner of '" + aName + "' was destroyed by '" + dName + "'! Siege failed.";
                if (attacker != null) {
                    for (UUID uuid : attacker.memberUUIDs) {
                        ServerPlayer p = ((ServerLevel) level).getServer().getPlayerList().getPlayer(uuid);
                        if (p != null) p.sendSystemMessage(Component.literal(msg));
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ConquestBlockEntities.WAR_BANNER.get(),
            (lvl, pos, st, be) -> WarBannerBlockEntity.serverTick((ServerLevel) lvl, pos, st, be));
    }
}
