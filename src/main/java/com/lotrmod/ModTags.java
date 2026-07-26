package com.lotrmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import static net.minecraft.core.registries.Registries.BLOCK;
import static net.minecraft.core.registries.Registries.ITEM;

/**
 * Tag keys referenced from code (Materials GDD §6.1, §10). The backing JSON lives under
 * {@code data/lotrmod/tags} and (for injections into vanilla tags) {@code data/minecraft/tags}.
 */
public final class ModTags {
    private ModTags() {}

    public static final class Blocks {
        private Blocks() {}

        /** Blocks a steel-tier tool cannot mine (must NOT contain mithril ore — see §6.1). */
        public static final TagKey<Block> INCORRECT_FOR_STEEL_TOOL = tag("incorrect_for_steel_tool");
        /** Blocks a mithril-tier tool cannot mine. */
        public static final TagKey<Block> INCORRECT_FOR_MITHRIL_TOOL = tag("incorrect_for_mithril_tool");
        /** Blocks that require at least a steel-tier tool (mithril ore, obsidian). */
        public static final TagKey<Block> NEEDS_STEEL_TOOL = tag("needs_steel_tool");

        private static TagKey<Block> tag(String path) {
            return TagKey.create(BLOCK, ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, path));
        }
    }

    public static final class Items {
        private Items() {}

        public static final TagKey<Item> COPPER_TOOL_MATERIALS = tag("copper_tool_materials");
        public static final TagKey<Item> SILVER_TOOL_MATERIALS = tag("silver_tool_materials");
        public static final TagKey<Item> STAR_IRON_TOOL_MATERIALS = tag("star_iron_tool_materials");

        private static TagKey<Item> tag(String path) {
            return TagKey.create(ITEM, ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, path));
        }
    }
}
