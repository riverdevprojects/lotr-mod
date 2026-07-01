package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import com.lotrmod.block.ModBlocks;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all LOTR mod items
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRMod.MODID);

    // Wood type items storage
    public static final Map<String, WoodTypeItems> WOOD_TYPE_ITEMS = new HashMap<>();

    // ==================== WOOD TYPE ITEMS ====================
    // For each wood type: sign, hanging_sign, boat, chest_boat
    static {
        registerWoodTypeItems("beech");
        registerWoodTypeItems("white_beech");
        registerWoodTypeItems("oak");
        registerWoodTypeItems("birch");
        registerWoodTypeItems("pine");
        registerWoodTypeItems("elm");
        registerWoodTypeItems("maple");
        registerWoodTypeItems("ash");
        registerWoodTypeItems("hazel");
        registerWoodTypeItems("yew");
        registerWoodTypeItems("rowan");
        registerWoodTypeItems("willow");
        registerWoodTypeItems("olive");
        registerWoodTypeItems("frozen_pine");
        registerWoodTypeItems("deadwood");
        registerWoodTypeItems("burnt_wood");
        registerWoodTypeItems("spruce");
        registerWoodTypeItems("aspen");
        registerWoodTypeItems("holly");
        registerWoodTypeItems("poplar");
        registerWoodTypeItems("laurel");
        registerWoodTypeItems("mallorn");
    }

    // ==================== SPECIAL ITEMS ====================
    public static final DeferredItem<Item> VOLCANIC_ASH = ITEMS.register("volcanic_ash",
            () -> new Item(new Item.Properties()));

    // ==================== BLOCK ITEMS ====================
    // These are automatically created for most blocks, but we register them explicitly for control

    private static void registerWoodTypeItems(String name) {
        WoodTypeItems items = new WoodTypeItems(name);
        WOOD_TYPE_ITEMS.put(name, items);
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);

        // Register block items for all blocks
        registerBlockItems();
    }

    private static void registerBlockItems() {
        // Register block items for wood types
        for (var entry : ModBlocks.WOOD_TYPES.entrySet()) {
            String name = entry.getKey();
            var blocks = entry.getValue();

            ITEMS.register(name + "_log", () -> new BlockItem(blocks.log.get(), new Item.Properties()));
            ITEMS.register(name + "_wood", () -> new BlockItem(blocks.wood.get(), new Item.Properties()));
            ITEMS.register("stripped_" + name + "_log", () -> new BlockItem(blocks.strippedLog.get(), new Item.Properties()));
            ITEMS.register("stripped_" + name + "_wood", () -> new BlockItem(blocks.strippedWood.get(), new Item.Properties()));
            ITEMS.register(name + "_planks", () -> new BlockItem(blocks.planks.get(), new Item.Properties()));
            ITEMS.register(name + "_stairs", () -> new BlockItem(blocks.stairs.get(), new Item.Properties()));
            ITEMS.register(name + "_slab", () -> new BlockItem(blocks.slab.get(), new Item.Properties()));
            ITEMS.register(name + "_fence", () -> new BlockItem(blocks.fence.get(), new Item.Properties()));
            ITEMS.register(name + "_fence_gate", () -> new BlockItem(blocks.fenceGate.get(), new Item.Properties()));
            ITEMS.register(name + "_door", () -> new BlockItem(blocks.door.get(), new Item.Properties()));
            ITEMS.register(name + "_trapdoor", () -> new BlockItem(blocks.trapdoor.get(), new Item.Properties()));
            ITEMS.register(name + "_pressure_plate", () -> new BlockItem(blocks.pressurePlate.get(), new Item.Properties()));
            ITEMS.register(name + "_button", () -> new BlockItem(blocks.button.get(), new Item.Properties()));
            ITEMS.register(name + "_sapling", () -> new BlockItem(blocks.sapling.get(), new Item.Properties()));
        }

        // Register block items for stone types
        for (var entry : ModBlocks.STONE_TYPES.entrySet()) {
            String name = entry.getKey();
            var blocks = entry.getValue();

            ITEMS.register(name.equals("stone") ? "lotr_stone" : name, () -> new BlockItem(blocks.stone.get(), new Item.Properties()));
            ITEMS.register(name + "_cobblestone", () -> new BlockItem(blocks.cobblestone.get(), new Item.Properties()));
            ITEMS.register("polished_" + name, () -> new BlockItem(blocks.polished.get(), new Item.Properties()));
            ITEMS.register(name + "_bricks", () -> new BlockItem(blocks.bricks.get(), new Item.Properties()));
            ITEMS.register("chiseled_" + name + "_bricks", () -> new BlockItem(blocks.chiseledBricks.get(), new Item.Properties()));
            ITEMS.register(name + "_stairs", () -> new BlockItem(blocks.stairs.get(), new Item.Properties()));
            ITEMS.register(name + "_slab", () -> new BlockItem(blocks.slab.get(), new Item.Properties()));
            ITEMS.register(name + "_wall", () -> new BlockItem(blocks.wall.get(), new Item.Properties()));
        }

        // Register other block items
        // REMOVED: lotr_dirt, lotr_coarse_dirt, lotr_sand, lotr_grass_block, meadow_grass_block, lotr_mud, lotr_packed_mud
        ITEMS.register("cracked_mud", () -> new BlockItem(ModBlocks.CRACKED_MUD.get(), new Item.Properties()));
        ITEMS.register("frozen_dirt", () -> new BlockItem(ModBlocks.FROZEN_DIRT.get(), new Item.Properties()));
        ITEMS.register("silt", () -> new BlockItem(ModBlocks.SILT.get(), new Item.Properties()));
        ITEMS.register("lotr_red_sand", () -> new BlockItem(ModBlocks.RED_SAND.get(), new Item.Properties()));
        ITEMS.register("dry_grass_block", () -> new BlockItem(ModBlocks.DRY_GRASS_BLOCK.get(), new Item.Properties()));
        ITEMS.register("tall_grass_block", () -> new BlockItem(ModBlocks.TALL_GRASS_BLOCK.get(), new Item.Properties()));
        ITEMS.register("dry_tall_grass_block", () -> new BlockItem(ModBlocks.DRY_TALL_GRASS_BLOCK.get(), new Item.Properties()));

        // Plants
        ITEMS.register("lotr_fern", () -> new BlockItem(ModBlocks.FERN.get(), new Item.Properties()));
        ITEMS.register("lotr_large_fern", () -> new BlockItem(ModBlocks.LARGE_FERN.get(), new Item.Properties()));
        ITEMS.register("shrubs", () -> new BlockItem(ModBlocks.SHRUBS.get(), new Item.Properties()));
        ITEMS.register("reeds", () -> new BlockItem(ModBlocks.REEDS.get(), new Item.Properties()));
        ITEMS.register("cattails", () -> new BlockItem(ModBlocks.CATTAILS.get(), new Item.Properties()));
        ITEMS.register("tumbleweed", () -> new BlockItem(ModBlocks.TUMBLEWEED.get(), new Item.Properties()));
        ITEMS.register("lotr_vines", () -> new BlockItem(ModBlocks.VINES.get(), new Item.Properties()));

        // Leaves
        ITEMS.register("beech_leaves", () -> new BlockItem(ModBlocks.BEECH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("white_beech_leaves", () -> new BlockItem(ModBlocks.WHITE_BEECH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("lotr_birch_leaves", () -> new BlockItem(ModBlocks.BIRCH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("lotr_oak_leaves", () -> new BlockItem(ModBlocks.OAK_LEAVES.get(), new Item.Properties()));
        ITEMS.register("pine_leaves", () -> new BlockItem(ModBlocks.PINE_LEAVES.get(), new Item.Properties()));
        ITEMS.register("maple_leaves", () -> new BlockItem(ModBlocks.MAPLE_LEAVES.get(), new Item.Properties()));
        ITEMS.register("ash_leaves", () -> new BlockItem(ModBlocks.ASH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("hazel_leaves", () -> new BlockItem(ModBlocks.HAZEL_LEAVES.get(), new Item.Properties()));
        ITEMS.register("yew_leaves", () -> new BlockItem(ModBlocks.YEW_LEAVES.get(), new Item.Properties()));
        ITEMS.register("rowan_leaves", () -> new BlockItem(ModBlocks.ROWAN_LEAVES.get(), new Item.Properties()));
        ITEMS.register("willow_leaves", () -> new BlockItem(ModBlocks.WILLOW_LEAVES.get(), new Item.Properties()));
        ITEMS.register("olive_leaves", () -> new BlockItem(ModBlocks.OLIVE_LEAVES.get(), new Item.Properties()));
        ITEMS.register("dead_leaves", () -> new BlockItem(ModBlocks.DEAD_LEAVES.get(), new Item.Properties()));
        ITEMS.register("frozen_leaves", () -> new BlockItem(ModBlocks.FROZEN_LEAVES.get(), new Item.Properties()));
        ITEMS.register("lotr_acacia_leaves", () -> new BlockItem(ModBlocks.ACACIA_LEAVES.get(), new Item.Properties()));
        ITEMS.register("elm_leaves", () -> new BlockItem(ModBlocks.ELM_LEAVES.get(), new Item.Properties()));
        ITEMS.register("lotr_spruce_leaves", () -> new BlockItem(ModBlocks.SPRUCE_LEAVES.get(), new Item.Properties()));
        ITEMS.register("aspen_leaves", () -> new BlockItem(ModBlocks.ASPEN_LEAVES.get(), new Item.Properties()));
        ITEMS.register("holly_leaves", () -> new BlockItem(ModBlocks.HOLLY_LEAVES.get(), new Item.Properties()));
        ITEMS.register("poplar_leaves", () -> new BlockItem(ModBlocks.POPLAR_LEAVES.get(), new Item.Properties()));
        ITEMS.register("laurel_leaves", () -> new BlockItem(ModBlocks.LAUREL_LEAVES.get(), new Item.Properties()));
        ITEMS.register("mallorn_leaves", () -> new BlockItem(ModBlocks.MALLORN_LEAVES.get(), new Item.Properties()));
        ITEMS.register("flowering_beech_leaves", () -> new BlockItem(ModBlocks.FLOWERING_BEECH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("flowering_white_beech_leaves", () -> new BlockItem(ModBlocks.FLOWERING_WHITE_BEECH_LEAVES.get(), new Item.Properties()));
        ITEMS.register("flowering_maple_leaves", () -> new BlockItem(ModBlocks.FLOWERING_MAPLE_LEAVES.get(), new Item.Properties()));
        ITEMS.register("flowering_rowan_leaves", () -> new BlockItem(ModBlocks.FLOWERING_ROWAN_LEAVES.get(), new Item.Properties()));
        ITEMS.register("flowering_olive_leaves", () -> new BlockItem(ModBlocks.FLOWERING_OLIVE_LEAVES.get(), new Item.Properties()));

        // Flowers
        ITEMS.register("meadow_buttercup", () -> new BlockItem(ModBlocks.MEADOW_BUTTERCUP.get(), new Item.Properties()));
        ITEMS.register("oxeye_daisy_tall", () -> new BlockItem(ModBlocks.OXEYE_DAISY_TALL.get(), new Item.Properties()));
        ITEMS.register("wild_chamomile", () -> new BlockItem(ModBlocks.WILD_CHAMOMILE.get(), new Item.Properties()));
        ITEMS.register("cornflower_blue", () -> new BlockItem(ModBlocks.CORNFLOWER_BLUE.get(), new Item.Properties()));
        ITEMS.register("meadow_clover", () -> new BlockItem(ModBlocks.MEADOW_CLOVER.get(), new Item.Properties()));
        ITEMS.register("wood_anemone", () -> new BlockItem(ModBlocks.WOOD_ANEMONE.get(), new Item.Properties()));
        ITEMS.register("lotr_lily_of_the_valley", () -> new BlockItem(ModBlocks.LILY_OF_THE_VALLEY.get(), new Item.Properties()));
        ITEMS.register("foxglove", () -> new BlockItem(ModBlocks.FOXGLOVE.get(), new Item.Properties()));
        ITEMS.register("primrose", () -> new BlockItem(ModBlocks.PRIMROSE.get(), new Item.Properties()));
        ITEMS.register("wild_violet", () -> new BlockItem(ModBlocks.WILD_VIOLET.get(), new Item.Properties()));
        ITEMS.register("marsh_marigold", () -> new BlockItem(ModBlocks.MARSH_MARIGOLD.get(), new Item.Properties()));
        ITEMS.register("water_forget_me_not", () -> new BlockItem(ModBlocks.WATER_FORGET_ME_NOT.get(), new Item.Properties()));
        ITEMS.register("reed_iris", () -> new BlockItem(ModBlocks.REED_IRIS.get(), new Item.Properties()));
        ITEMS.register("scarlet_poppy", () -> new BlockItem(ModBlocks.SCARLET_POPPY.get(), new Item.Properties()));
        ITEMS.register("desert_starflower", () -> new BlockItem(ModBlocks.DESERT_STARFLOWER.get(), new Item.Properties()));
        ITEMS.register("sun_thistle", () -> new BlockItem(ModBlocks.SUN_THISTLE.get(), new Item.Properties()));
        ITEMS.register("golden_lorien_bloom", () -> new BlockItem(ModBlocks.GOLDEN_LORIEN_BLOOM.get(), new Item.Properties()));
        ITEMS.register("silver_nightflower", () -> new BlockItem(ModBlocks.SILVER_NIGHTFLOWER.get(), new Item.Properties()));

        // Special
        ITEMS.register("hobbit_door", () -> new BlockItem(ModBlocks.HOBBIT_DOOR.get(), new Item.Properties()));
        ITEMS.register("volcanic_ash_block", () -> new BlockItem(ModBlocks.VOLCANIC_ASH_BLOCK.get(), new Item.Properties()));
        ITEMS.register("riverbed_silt", () -> new BlockItem(ModBlocks.RIVERBED_SILT.get(), new Item.Properties()));
    }

    /**
     * Helper class to store sign, hanging_sign, boat, and chest_boat items for a wood type
     */
    public static class WoodTypeItems {
        // Note: Signs, hanging signs, boats, and chest boats require custom implementations
        // For now, we'll create placeholder items
        public final DeferredItem<Item> sign;
        public final DeferredItem<Item> hangingSign;
        public final DeferredItem<Item> boat;
        public final DeferredItem<Item> chestBoat;

        public WoodTypeItems(String name) {
            // Placeholder items - these would normally be SignItem, HangingSignItem, BoatItem, etc.
            this.sign = ITEMS.register(name + "_sign", () -> new Item(new Item.Properties()));
            this.hangingSign = ITEMS.register(name + "_hanging_sign", () -> new Item(new Item.Properties()));
            this.boat = ITEMS.register(name + "_boat", () -> new Item(new Item.Properties()));
            this.chestBoat = ITEMS.register(name + "_chest_boat", () -> new Item(new Item.Properties()));
        }
    }
}
