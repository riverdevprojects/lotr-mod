package com.lotrmod.block;

import com.lotrmod.LOTRMod;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all LOTR mod blocks
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTRMod.MODID);

    // Wood type storage
    public static final Map<String, WoodTypeBlocks> WOOD_TYPES = new HashMap<>();

    // Stone type storage
    public static final Map<String, StoneTypeBlocks> STONE_TYPES = new HashMap<>();

    // ==================== WOOD TYPES ====================
    // Each wood type has: log, wood, stripped_log, stripped_wood, planks, stairs, slab,
    // fence, fence_gate, door, trapdoor, pressure_plate, button
    static {
        registerWoodType("beech");
        registerWoodType("white_beech");
        registerWoodType("oak"); // LOTR oak, not vanilla
        registerWoodType("birch"); // LOTR birch
        registerWoodType("pine");
        registerWoodType("elm");
        registerWoodType("maple");
        registerWoodType("ash");
        registerWoodType("hazel");
        registerWoodType("yew");
        registerWoodType("rowan");
        registerWoodType("willow");
        registerWoodType("olive");
        registerWoodType("frozen_pine");
        registerWoodType("deadwood");
        registerWoodType("burnt_wood");
        // Additional Middle-earth trees (custom models/structures)
        registerWoodType("spruce"); // LOTR spruce, not vanilla
        registerWoodType("aspen");
        registerWoodType("holly");
        registerWoodType("poplar"); // white poplar
        registerWoodType("laurel");
        registerWoodType("mallorn"); // Lothlorien golden tree
    }

    // ==================== STONE TYPES ====================
    // Each stone type has: stone, cobblestone, polished, bricks, chiseled_bricks, stairs, slab, wall
    static {
        registerStoneType("stone"); // LOTR stone
        registerStoneType("limestone");
        registerStoneType("calcite");
        registerStoneType("volcanic_stone");
    }

    // ==================== SOILS & GROUND ====================
    // REMOVED: lotr_dirt, lotr_coarse_dirt, lotr_mud, lotr_packed_mud (using vanilla instead)

    public static final DeferredBlock<Block> CRACKED_MUD = BLOCKS.register("cracked_mud",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)));

    public static final DeferredBlock<Block> FROZEN_DIRT = BLOCKS.register("frozen_dirt",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)));

    public static final DeferredBlock<Block> SILT = BLOCKS.register("silt",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)));

    // ==================== SAND ====================
    // REMOVED: lotr_sand (using vanilla instead)

    public static final DeferredBlock<Block> RED_SAND = BLOCKS.register("lotr_red_sand",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_SAND)));

    // ==================== GRASS & SURFACE ====================
    // REMOVED: lotr_grass_block, meadow_grass_block (using vanilla instead)

    public static final DeferredBlock<GrassBlock> DRY_GRASS_BLOCK = BLOCKS.register("dry_grass_block",
            () -> new GrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)));

    public static final DeferredBlock<GrassBlock> TALL_GRASS_BLOCK = BLOCKS.register("tall_grass_block",
            () -> new GrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)));

    public static final DeferredBlock<GrassBlock> DRY_TALL_GRASS_BLOCK = BLOCKS.register("dry_tall_grass_block",
            () -> new GrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)));

    // ==================== PLANTS ====================
    public static final DeferredBlock<Block> FERN = BLOCKS.register("lotr_fern",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FERN)));

    public static final DeferredBlock<Block> LARGE_FERN = BLOCKS.register("lotr_large_fern",
            () -> new DoublePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_FERN)));

    public static final DeferredBlock<Block> SHRUBS = BLOCKS.register("shrubs",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH)));

    public static final DeferredBlock<Block> REEDS = BLOCKS.register("reeds",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));

    public static final DeferredBlock<Block> CATTAILS = BLOCKS.register("cattails",
            () -> new DoublePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));

    // DeadBushBlock so tumbleweeds survive on sand, red sand and coarse dirt
    // (Harad desert/savanna and the eastern steppes) — not just grass/dirt.
    public static final DeferredBlock<Block> TUMBLEWEED = BLOCKS.register("tumbleweed",
            () -> new DeadBushBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH)));

    public static final DeferredBlock<Block> VINES = BLOCKS.register("lotr_vines",
            () -> new VineBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.VINE)));

    // ==================== LEAVES ====================
    // Base leaves (15 types)
    public static final DeferredBlock<LeavesBlock> BEECH_LEAVES = BLOCKS.register("beech_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> WHITE_BEECH_LEAVES = BLOCKS.register("white_beech_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> BIRCH_LEAVES = BLOCKS.register("lotr_birch_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES)));
    public static final DeferredBlock<LeavesBlock> OAK_LEAVES = BLOCKS.register("lotr_oak_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> PINE_LEAVES = BLOCKS.register("pine_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LEAVES)));
    public static final DeferredBlock<LeavesBlock> MAPLE_LEAVES = BLOCKS.register("maple_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> ASH_LEAVES = BLOCKS.register("ash_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> HAZEL_LEAVES = BLOCKS.register("hazel_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> YEW_LEAVES = BLOCKS.register("yew_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> ROWAN_LEAVES = BLOCKS.register("rowan_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> WILLOW_LEAVES = BLOCKS.register("willow_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> OLIVE_LEAVES = BLOCKS.register("olive_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> DEAD_LEAVES = BLOCKS.register("dead_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> FROZEN_LEAVES = BLOCKS.register("frozen_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LEAVES)));
    public static final DeferredBlock<LeavesBlock> ACACIA_LEAVES = BLOCKS.register("lotr_acacia_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ACACIA_LEAVES)));

    // Leaves for the additional Middle-earth trees
    public static final DeferredBlock<LeavesBlock> ELM_LEAVES = BLOCKS.register("elm_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> SPRUCE_LEAVES = BLOCKS.register("lotr_spruce_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LEAVES)));
    public static final DeferredBlock<LeavesBlock> ASPEN_LEAVES = BLOCKS.register("aspen_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES)));
    public static final DeferredBlock<LeavesBlock> HOLLY_LEAVES = BLOCKS.register("holly_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> POPLAR_LEAVES = BLOCKS.register("poplar_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES)));
    public static final DeferredBlock<LeavesBlock> LAUREL_LEAVES = BLOCKS.register("laurel_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_LEAVES)));
    public static final DeferredBlock<LeavesBlock> MALLORN_LEAVES = BLOCKS.register("mallorn_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES)));

    // Flowering variants (5 types)
    public static final DeferredBlock<LeavesBlock> FLOWERING_BEECH_LEAVES = BLOCKS.register("flowering_beech_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA_LEAVES)));
    public static final DeferredBlock<LeavesBlock> FLOWERING_WHITE_BEECH_LEAVES = BLOCKS.register("flowering_white_beech_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA_LEAVES)));
    public static final DeferredBlock<LeavesBlock> FLOWERING_MAPLE_LEAVES = BLOCKS.register("flowering_maple_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA_LEAVES)));
    public static final DeferredBlock<LeavesBlock> FLOWERING_ROWAN_LEAVES = BLOCKS.register("flowering_rowan_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA_LEAVES)));
    public static final DeferredBlock<LeavesBlock> FLOWERING_OLIVE_LEAVES = BLOCKS.register("flowering_olive_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA_LEAVES)));

    // ==================== FLOWERS ====================
    // Using simple Block instead of FlowerBlock to avoid suspicious stew effect requirements
    public static final DeferredBlock<Block> MEADOW_BUTTERCUP = BLOCKS.register("meadow_buttercup",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> OXEYE_DAISY_TALL = BLOCKS.register("oxeye_daisy_tall",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> WILD_CHAMOMILE = BLOCKS.register("wild_chamomile",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> CORNFLOWER_BLUE = BLOCKS.register("cornflower_blue",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CORNFLOWER)));
    public static final DeferredBlock<Block> MEADOW_CLOVER = BLOCKS.register("meadow_clover",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> WOOD_ANEMONE = BLOCKS.register("wood_anemone",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> LILY_OF_THE_VALLEY = BLOCKS.register("lotr_lily_of_the_valley",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LILY_OF_THE_VALLEY)));
    public static final DeferredBlock<Block> FOXGLOVE = BLOCKS.register("foxglove",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ALLIUM)));
    public static final DeferredBlock<Block> PRIMROSE = BLOCKS.register("primrose",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> WILD_VIOLET = BLOCKS.register("wild_violet",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)));
    public static final DeferredBlock<Block> MARSH_MARIGOLD = BLOCKS.register("marsh_marigold",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> WATER_FORGET_ME_NOT = BLOCKS.register("water_forget_me_not",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)));
    public static final DeferredBlock<Block> REED_IRIS = BLOCKS.register("reed_iris",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ORCHID)));
    public static final DeferredBlock<Block> SCARLET_POPPY = BLOCKS.register("scarlet_poppy",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY)));
    public static final DeferredBlock<Block> DESERT_STARFLOWER = BLOCKS.register("desert_starflower",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> SUN_THISTLE = BLOCKS.register("sun_thistle",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> GOLDEN_LORIEN_BLOOM = BLOCKS.register("golden_lorien_bloom",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));
    public static final DeferredBlock<Block> SILVER_NIGHTFLOWER = BLOCKS.register("silver_nightflower",
            () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DANDELION)));

    // ==================== SPECIAL ====================
    public static final DeferredBlock<RoundDoorBlock> HOBBIT_DOOR = BLOCKS.register("hobbit_door",
            () -> new RoundDoorBlock());

    public static final DeferredBlock<Block> VOLCANIC_ASH_BLOCK = BLOCKS.register("volcanic_ash_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)));

    public static final DeferredBlock<Block> RIVERBED_SILT = BLOCKS.register("riverbed_silt",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND)));

    // ==================== HELPER METHODS ====================

    private static void registerWoodType(String name) {
        WoodTypeBlocks blocks = new WoodTypeBlocks(name);
        WOOD_TYPES.put(name, blocks);
    }

    private static void registerStoneType(String name) {
        StoneTypeBlocks blocks = new StoneTypeBlocks(name);
        STONE_TYPES.put(name, blocks);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    /**
     * Helper class to store all blocks for a wood type
     */
    public static class WoodTypeBlocks {
        public final DeferredBlock<RotatedPillarBlock> log;
        public final DeferredBlock<RotatedPillarBlock> wood;
        public final DeferredBlock<RotatedPillarBlock> strippedLog;
        public final DeferredBlock<RotatedPillarBlock> strippedWood;
        public final DeferredBlock<Block> planks;
        public final DeferredBlock<StairBlock> stairs;
        public final DeferredBlock<SlabBlock> slab;
        public final DeferredBlock<FenceBlock> fence;
        public final DeferredBlock<FenceGateBlock> fenceGate;
        public final DeferredBlock<DoorBlock> door;
        public final DeferredBlock<TrapDoorBlock> trapdoor;
        public final DeferredBlock<PressurePlateBlock> pressurePlate;
        public final DeferredBlock<ButtonBlock> button;
        public final DeferredBlock<Block> sapling; // Decorative only, no growth

        public WoodTypeBlocks(String name) {
            BlockSetType blockSetType = BlockSetType.OAK; // Use oak properties as base
            WoodType woodType = WoodType.OAK; // Use oak wood type as base

            this.log = BLOCKS.register(name + "_log",
                    () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)));
            this.wood = BLOCKS.register(name + "_wood",
                    () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD)));
            this.strippedLog = BLOCKS.register("stripped_" + name + "_log",
                    () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG)));
            this.strippedWood = BLOCKS.register("stripped_" + name + "_wood",
                    () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD)));
            this.planks = BLOCKS.register(name + "_planks",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
            this.stairs = BLOCKS.register(name + "_stairs",
                    () -> new StairBlock(Blocks.OAK_PLANKS.defaultBlockState(),
                            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS)));
            this.slab = BLOCKS.register(name + "_slab",
                    () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB)));
            this.fence = BLOCKS.register(name + "_fence",
                    () -> new FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE)));
            this.fenceGate = BLOCKS.register(name + "_fence_gate",
                    () -> new FenceGateBlock(woodType, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE)));
            this.door = BLOCKS.register(name + "_door",
                    () -> new DoorBlock(blockSetType, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR)));
            this.trapdoor = BLOCKS.register(name + "_trapdoor",
                    () -> new TrapDoorBlock(blockSetType, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR)));
            this.pressurePlate = BLOCKS.register(name + "_pressure_plate",
                    () -> new PressurePlateBlock(blockSetType, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE)));
            this.button = BLOCKS.register(name + "_button",
                    () -> new ButtonBlock(blockSetType, 30, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON)));
            this.sapling = BLOCKS.register(name + "_sapling",
                    () -> new TallGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING))); // Decorative only - no growth
        }
    }

    /**
     * Helper class to store all blocks for a stone type
     */
    public static class StoneTypeBlocks {
        public final DeferredBlock<Block> stone;
        public final DeferredBlock<Block> cobblestone;
        public final DeferredBlock<Block> polished;
        public final DeferredBlock<Block> bricks;
        public final DeferredBlock<Block> chiseledBricks;
        public final DeferredBlock<StairBlock> stairs;
        public final DeferredBlock<SlabBlock> slab;
        public final DeferredBlock<WallBlock> wall;

        public StoneTypeBlocks(String name) {
            this.stone = BLOCKS.register(name.equals("stone") ? "lotr_stone" : name,
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));
            this.cobblestone = BLOCKS.register(name + "_cobblestone",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE)));
            this.polished = BLOCKS.register("polished_" + name,
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE)));
            this.bricks = BLOCKS.register(name + "_bricks",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)));
            this.chiseledBricks = BLOCKS.register("chiseled_" + name + "_bricks",
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS)));
            this.stairs = BLOCKS.register(name + "_stairs",
                    () -> new StairBlock(Blocks.STONE.defaultBlockState(),
                            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_STAIRS)));
            this.slab = BLOCKS.register(name + "_slab",
                    () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB)));
            this.wall = BLOCKS.register(name + "_wall",
                    () -> new WallBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE_WALL)));
        }
    }
}
