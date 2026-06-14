package com.lotrmod.command;

import com.lotrmod.structure.OrthancBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug/build commands for LOTR structures.
 *
 * <pre>
 * /structure debug orthanc   — raise the black tower of Isengard at your feet
 * </pre>
 *
 * Requires op level 2. The structure is generated procedurally by {@link OrthancBuilder}.
 */
public class StructureCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structure")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("orthanc")
                                .executes(StructureCommand::buildOrthanc))));
    }

    private static int buildOrthanc(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[Structure] This command must be run by a player."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();

        source.sendSuccess(() -> Component.literal(
                "[Structure] Raising Orthanc at " + origin.toShortString() + " — this may take a moment..."), true);

        long startNanos = System.nanoTime();
        int placed = OrthancBuilder.build(level, origin);
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;

        int totalHeight = OrthancBuilder.SHAFT_H + OrthancBuilder.HORN_H;
        source.sendSuccess(() -> Component.literal(
                "[Structure] Orthanc complete: " + placed + " blocks, ~" + totalHeight
                        + " blocks tall, in " + ms + "ms. Fly up to behold it."), true);
        return placed > 0 ? 1 : 0;
    }
}
