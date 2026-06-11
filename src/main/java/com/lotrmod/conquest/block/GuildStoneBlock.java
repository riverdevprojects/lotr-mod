package com.lotrmod.conquest.block;

import com.lotrmod.conquest.ConquestConfig;
import com.lotrmod.conquest.command.GuildCommand;
import com.lotrmod.conquest.data.Guild;
import com.lotrmod.conquest.data.GuildSavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;

/**
 * Guild Stone — a craftable block any player can place and right-click to open the guild menu.
 * Guild masters/officers additionally get resource-deposit options. Primary in-world access to
 * the guild UI (the /guilddebug ui command remains an admin tool).
 */
public class GuildStoneBlock extends Block {

    public static final MapCodec<GuildStoneBlock> CODEC = simpleCodec(GuildStoneBlock::new);

    @Override
    public MapCodec<GuildStoneBlock> codec() { return CODEC; }

    public GuildStoneBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public GuildStoneBlock() {
        this(BlockBehaviour.Properties.of().strength(3.0f, 9.0f).requiresCorrectToolForDrops());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!ConquestConfig.SERVER.enabled.get()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        GuildSavedData data = GuildSavedData.get(sp.getServer());
        Guild guild = data.getGuildForPlayer(sp.getUUID());
        if (guild == null) {
            sp.sendSystemMessage(Component.literal(
                "[Guild] You are not in a guild. Use /guild create <name> <tag> to found one."));
            return InteractionResult.CONSUME;
        }

        // Open the guild info screen (reuses the existing client screen).
        PacketDistributor.sendToPlayer(sp, GuildCommand.buildPacket(guild, data, sp));

        // Guild master / officers get deposit options.
        if (guild.canManage(sp.getUUID())) {
            sp.sendSystemMessage(Component.literal("[Guild] Deposit everything you carry into the treasury:"));
            Component row1 = Component.literal("[Guild] ")
                .append(depositButton("Bread", "bread"))
                .append(Component.literal(" "))
                .append(depositButton("Cobblestone", "cobblestone"))
                .append(Component.literal(" "))
                .append(depositButton("Logs", "logs"));
            Component row2 = Component.literal("[Guild] ")
                .append(depositButton("Gold", "gold"))
                .append(Component.literal(" "))
                .append(depositButton("Iron", "iron"))
                .append(Component.literal(" "))
                .append(depositButton("Silver", "silver"));
            sp.sendSystemMessage(row1);
            sp.sendSystemMessage(row2);
        }
        return InteractionResult.CONSUME;
    }

    private static Component depositButton(String label, String resource) {
        // 2147483647 = deposit all the player has (the command clamps to inventory contents).
        return Component.literal("[+ " + label + "]").withStyle(s -> s
            .withColor(net.minecraft.ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/guild treasury deposit " + resource + " 2147483647")));
    }
}
