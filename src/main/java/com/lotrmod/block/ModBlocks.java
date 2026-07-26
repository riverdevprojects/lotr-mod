package com.lotrmod.block;

import com.lotrmod.LOTRMod;
import com.lotrmod.item.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Block registry for the Materials &amp; Metallurgy set (Materials GDD §4.1).
 *
 * <p>Every block registered here also registers a matching {@link net.minecraft.world.item.BlockItem}
 * into {@link ModItems#ITEMS}, so it appears in the creative tab automatically.
 *
 * <p>Textures/models are placeholder-generated (see the generated assets under
 * {@code assets/lotrmod}); art is out of scope for this milestone.
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTRMod.MODID);

    // --- Ores -------------------------------------------------------------
    public static final DeferredBlock<Block> TIN_ORE =
        registerBlock("tin_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredBlock<Block> DEEPSLATE_TIN_ORE =
        registerBlock("deepslate_tin_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    public static final DeferredBlock<Block> SILVER_ORE =
        registerBlock("silver_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE =
        registerBlock("deepslate_silver_ore", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    // Deepslate-only; harder than diamond ore. Steel-tier gate is applied via block tags (§6.1).
    public static final DeferredBlock<Block> MITHRIL_ORE =
        registerBlock("mithril_ore", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE).strength(5.0F, 6.0F)));

    // --- Meteorite core (§5.4) -------------------------------------------
    public static final DeferredBlock<Block> STAR_IRON_BLOCK =
        registerBlock("star_iron_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()));

    // --- Raw storage blocks ----------------------------------------------
    public static final DeferredBlock<Block> RAW_TIN_BLOCK =
        registerBlock("raw_tin_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK)));
    public static final DeferredBlock<Block> RAW_SILVER_BLOCK =
        registerBlock("raw_silver_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK)));

    // --- Metal storage blocks --------------------------------------------
    public static final DeferredBlock<Block> TIN_BLOCK =
        registerBlock("tin_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> SILVER_BLOCK =
        registerBlock("silver_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> BRONZE_BLOCK =
        registerBlock("bronze_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> STEEL_BLOCK =
        registerBlock("steel_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6.0F, 7.0F)));
    public static final DeferredBlock<Block> MITHRIL_BLOCK =
        registerBlock("mithril_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6.0F, 9.0F)));
    public static final DeferredBlock<Block> STAR_IRON_INGOT_BLOCK =
        registerBlock("star_iron_ingot_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6.0F, 7.0F)));
    public static final DeferredBlock<Block> GALVORN_BLOCK =
        registerBlock("galvorn_block", () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(7.0F, 10.0F)));

    private static DeferredBlock<Block> registerBlock(String name, Supplier<Block> block) {
        DeferredBlock<Block> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.registerSimpleBlockItem(name, registered);
        return registered;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
