package com.lotrmod.conquest.event;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.lotrmod.conquest.entity.GuardEntity;
import com.lotrmod.conquest.registry.ConquestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimProtectionHandler {

    /** Rate-limit denial messages: playerUUID → last denied tick. */
    private static final Map<UUID, Long> lastDeniedTick = new HashMap<>();
    private static final long DENY_COOLDOWN_TICKS = 40L;

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        BlockPos pos = event.getPos();
        GuildSavedData data = GuildSavedData.get(sl.getServer());
        Guild owner = data.getChunkOwner(new ChunkPos(pos));
        if (owner == null) return; // unclaimed — allow

        boolean isFlag = isClaimBanner(event.getState());

        if (isFlag) {
            if (owner.isMember(sp.getUUID())) {
                // A guild may never destroy its own flag by mining; only the master/officer
                // may remove it (the sanctioned "abandon outpost" path). Regular members are blocked.
                if (!owner.canManage(sp.getUUID())) {
                    event.setCanceled(true);
                    sendMessage(sp, "You cannot destroy your own guild's flag. Only the guild master or an officer can remove it.");
                }
                return;
            }
            // Non-member: an enemy flag is only mineable while at war with the owning guild.
            Guild breakerGuild = data.getGuildForPlayer(sp.getUUID());
            boolean atWar = breakerGuild != null && owner.wars.containsKey(breakerGuild.id);
            if (!atWar) {
                event.setCanceled(true);
                sendMessage(sp, "You can only destroy this flag while your guild is at war with '" + owner.name + "'.");
            } else {
                // Attacking an enemy flag during war is hostile — rouse the defenders.
                aggroNearbyGuards(sl, sp, pos);
                // Destroying the base captures the land: mark it so a captured flag is auto-planted.
                if (event.getState().is(ConquestBlocks.CLAIM_BANNER_BASE.get())
                    && sl.getBlockEntity(pos) instanceof com.lotrmod.conquest.block.ClaimBannerBlockEntity be) {
                    be.capturedBy = breakerGuild.id;
                }
            }
            return;
        }

        // Any non-flag block in claimed territory is protected from non-members, regardless of war.
        if (owner.isMember(sp.getUUID())) return;
        event.setCanceled(true);
        sendDenied(sp);
        aggroNearbyGuards(sl, sp, pos);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer sp)) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;
        if (isDenied(sl, sp, event.getPos())) {
            event.setCanceled(true);
            sendDenied(sp);
            aggroNearbyGuards(sl, sp, event.getPos());
        }
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!ConquestConfig.SERVER.enabled.get()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        BlockPos pos = event.getPos();
        if (isDenied(sl, sp, pos)) {
            // Only cancel if the clicked block is a container or guild object
            var state = sl.getBlockState(pos);
            if (isContainer(state) || isGuildBlock(state)) {
                event.setCanceled(true);
                sendDenied(sp);
            }
        } else {
            // Player is allowed — increment development score if they're a member
            GuildSavedData data = GuildSavedData.get(sl.getServer());
            Guild guild = data.getChunkOwner(new ChunkPos(pos));
            if (guild != null && guild.isMember(sp.getUUID())) {
                triggerGuardAggroIfHostile(sl, sp, pos, data);
            }
        }
    }

    private boolean isDenied(ServerLevel level, ServerPlayer player, BlockPos pos) {
        GuildSavedData data = GuildSavedData.get(level.getServer());
        Guild owner = data.getChunkOwner(new ChunkPos(pos));
        if (owner == null) return false;              // unclaimed — allow
        if (owner.isMember(player.getUUID())) return false; // member — allow
        return true;
    }

    private void sendDenied(ServerPlayer player) {
        sendMessage(player, "This land is claimed. You cannot interact here.");
    }

    /** Rate-limited "[Conquest]" message to avoid chat spam from repeated denials. */
    private void sendMessage(ServerPlayer player, String text) {
        long now = player.level().getGameTime();
        long last = lastDeniedTick.getOrDefault(player.getUUID(), 0L);
        if (now - last < DENY_COOLDOWN_TICKS) return;
        lastDeniedTick.put(player.getUUID(), now);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Conquest] " + text));
    }

    /** Trigger guards when a non-member does something hostile in a claimed chunk. */
    private void triggerGuardAggroIfHostile(ServerLevel level, ServerPlayer player, BlockPos pos, GuildSavedData data) {
        // No-op for member actions — hostile action guard aggro is handled in break/place events
    }

    /** Called by break/place events on denied players to aggro nearby guards. */
    public static void aggroNearbyGuards(ServerLevel level, ServerPlayer intruder, BlockPos pos) {
        GuildSavedData data = GuildSavedData.get(level.getServer());
        Guild owner = data.getChunkOwner(new ChunkPos(pos));
        if (owner == null) return;

        double range = 24.0;
        level.getEntitiesOfClass(GuardEntity.class,
            new net.minecraft.world.phys.AABB(pos).inflate(range), g -> {
                UUID guildId = g.getGuildId();
                return guildId != null && guildId.equals(owner.id);
            }).forEach(guard -> guard.aggroOn(intruder));
    }

    private boolean isContainer(net.minecraft.world.level.block.state.BlockState state) {
        return state.hasBlockEntity() &&
            (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.HopperBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.DropperBlock ||
             state.getBlock() instanceof net.minecraft.world.level.block.DispenserBlock);
    }

    private boolean isGuildBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(ConquestBlocks.CLAIM_BANNER_BASE.get()) ||
               state.is(ConquestBlocks.CLAIM_BANNER_POLE.get()) ||
               state.is(ConquestBlocks.CLAIM_BANNER_TOP.get()) ||
               state.is(ConquestBlocks.WAR_BANNER.get());
    }

    /** True for the claim-banner (flag) structure blocks — NOT the war banner. */
    private boolean isClaimBanner(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(ConquestBlocks.CLAIM_BANNER_BASE.get()) ||
               state.is(ConquestBlocks.CLAIM_BANNER_POLE.get()) ||
               state.is(ConquestBlocks.CLAIM_BANNER_TOP.get());
    }
}
