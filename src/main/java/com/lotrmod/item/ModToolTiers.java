package com.lotrmod.item;

import com.lotrmod.ModTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * Tool tiers for the Materials GDD §6 roster, implemented against the 1.21.1 {@link Tier}
 * interface. (The GDD's §6.3 {@code ToolMaterial} record does not exist on 1.21.1 — that
 * migration landed later in the 1.21 line, exactly as the doc's version caveat warns.)
 *
 * <p>Mining gates (§6.1):
 * <ul>
 *   <li>copper → stone tier (vanilla {@code incorrect_for_stone_tool})</li>
 *   <li>silver, bronze → iron tier (vanilla {@code incorrect_for_iron_tool})</li>
 *   <li>steel, star-iron → steel tier ({@code lotrmod:incorrect_for_steel_tool})</li>
 *   <li>galvorn, mithril → mithril tier ({@code lotrmod:incorrect_for_mithril_tool})</li>
 * </ul>
 * {@code incorrect_for_steel_tool} deliberately does NOT contain mithril ore, so steel picks
 * unlock mithril — the single most common way this system ships broken (§6.1).
 */
public final class ModToolTiers {
    private ModToolTiers() {}

    private static TagKey<Item> cIngot(String metal) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal));
    }

    //                                                  uses  speed  atk  ench  incorrect-mine-tag                        repair-item-tag
    public static final Tier COPPER    = new SimpleTier(190,  5.0F,  1.0F, 12, BlockTags.INCORRECT_FOR_STONE_TOOL,        ModTags.Items.COPPER_TOOL_MATERIALS);
    public static final Tier SILVER    = new SimpleTier(180,  7.0F,  1.5F, 22, BlockTags.INCORRECT_FOR_IRON_TOOL,         ModTags.Items.SILVER_TOOL_MATERIALS);
    public static final Tier BRONZE    = new SimpleTier(320,  6.5F,  1.5F, 10, BlockTags.INCORRECT_FOR_IRON_TOOL,         cIngot("bronze"));
    public static final Tier STEEL     = new SimpleTier(850,  7.5F,  2.5F, 12, ModTags.Blocks.INCORRECT_FOR_STEEL_TOOL,   cIngot("steel"));
    public static final Tier STAR_IRON = new SimpleTier(700,  7.0F,  2.5F, 16, ModTags.Blocks.INCORRECT_FOR_STEEL_TOOL,   ModTags.Items.STAR_IRON_TOOL_MATERIALS);
    public static final Tier GALVORN   = new SimpleTier(1900, 8.5F,  3.5F, 14, ModTags.Blocks.INCORRECT_FOR_MITHRIL_TOOL, cIngot("galvorn"));
    public static final Tier MITHRIL   = new SimpleTier(2600, 10.0F, 3.5F, 25, ModTags.Blocks.INCORRECT_FOR_MITHRIL_TOOL, cIngot("mithril"));

    /** Minimal {@link Tier} implementation; repair ingredient resolves lazily from an item tag. */
    private record SimpleTier(int uses, float speed, float attack, int enchant,
                              TagKey<Block> incorrect, TagKey<Item> repairTag) implements Tier {
        @Override public int getUses() { return uses; }
        @Override public float getSpeed() { return speed; }
        @Override public float getAttackDamageBonus() { return attack; }
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrect; }
        @Override public int getEnchantmentValue() { return enchant; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(repairTag); }
    }
}
