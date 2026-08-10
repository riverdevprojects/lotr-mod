package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import com.lotrmod.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry for the LOTR mod.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRMod.MODID);

    public static final DeferredItem<BlockItem> DAUB = ITEMS.registerSimpleBlockItem(ModBlocks.DAUB);
    public static final DeferredItem<BlockItem> HOBBIT_DOOR = ITEMS.registerSimpleBlockItem(ModBlocks.HOBBIT_DOOR);
    public static final DeferredItem<BlockItem> WOOD_BEAM_OAK = ITEMS.registerSimpleBlockItem(ModBlocks.WOOD_BEAM_OAK);
    public static final DeferredItem<BlockItem> WOOD_BEAM_SPRUCE = ITEMS.registerSimpleBlockItem(ModBlocks.WOOD_BEAM_SPRUCE);
    public static final DeferredItem<BlockItem> WOOD_BEAM_ACACIA = ITEMS.registerSimpleBlockItem(ModBlocks.WOOD_BEAM_ACACIA);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
