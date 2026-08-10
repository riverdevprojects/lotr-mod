package com.lotrmod.block;

import com.lotrmod.LOTRMod;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry for all LOTR mod blocks.
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTRMod.MODID);

    public static final DeferredBlock<ConnectedPanelBlock> DAUB = BLOCKS.register("daub",
            () -> new ConnectedPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUD_BRICKS)));

    public static final DeferredBlock<HobbitDoorBlock> HOBBIT_DOOR = BLOCKS.register("hobbit_door",
            () -> new HobbitDoorBlock());

    public static final DeferredBlock<RotatedPillarBlock> WOOD_BEAM_OAK = BLOCKS.register("wood_beam_oak",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)));

    public static final DeferredBlock<RotatedPillarBlock> WOOD_BEAM_SPRUCE = BLOCKS.register("wood_beam_spruce",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LOG)));

    public static final DeferredBlock<RotatedPillarBlock> WOOD_BEAM_ACACIA = BLOCKS.register("wood_beam_acacia",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ACACIA_LOG)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
