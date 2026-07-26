package com.lotrmod.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.Map;
import java.util.Set;

/**
 * Global loot modifier for the diamond/netherite scrub (Materials GDD §2.3 step 3). Any diamond or
 * netherite equipment rolled by <em>any</em> loot table — vanilla, modded, or ours — is swapped for
 * the equivalent {@code lotrmod:steel_*} piece at the same count. A GLM is used instead of
 * per-table overrides precisely because the doc warns step 3 is "the one that gets missed."
 */
public class ReplaceScrubbedGearModifier extends LootModifier {
    public static final MapCodec<ReplaceScrubbedGearModifier> CODEC =
        RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, ReplaceScrubbedGearModifier::new));

    /**
     * Items dropped from loot outright, because no equivalent exists to substitute. The netherite
     * upgrade template drives a mechanic that no longer exists (§2.2 option A cuts netherite), so
     * leaving it in chests would hand players a key to a door that has been bricked up.
     */
    private static final Set<Item> DROPPED = Set.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    /** vanilla scrubbed item -> replacement lotrmod path (steel equivalent). */
    private static final Map<Item, String> REPLACEMENTS = Map.ofEntries(
        Map.entry(Items.DIAMOND_SWORD, "steel_sword"),
        Map.entry(Items.DIAMOND_PICKAXE, "steel_pickaxe"),
        Map.entry(Items.DIAMOND_AXE, "steel_axe"),
        Map.entry(Items.DIAMOND_SHOVEL, "steel_shovel"),
        Map.entry(Items.DIAMOND_HOE, "steel_hoe"),
        Map.entry(Items.DIAMOND_HELMET, "steel_helmet"),
        Map.entry(Items.DIAMOND_CHESTPLATE, "steel_chestplate"),
        Map.entry(Items.DIAMOND_LEGGINGS, "steel_leggings"),
        Map.entry(Items.DIAMOND_BOOTS, "steel_boots"),
        Map.entry(Items.DIAMOND_HORSE_ARMOR, "steel_horse_armor"),
        Map.entry(Items.NETHERITE_SWORD, "steel_sword"),
        Map.entry(Items.NETHERITE_PICKAXE, "steel_pickaxe"),
        Map.entry(Items.NETHERITE_AXE, "steel_axe"),
        Map.entry(Items.NETHERITE_SHOVEL, "steel_shovel"),
        Map.entry(Items.NETHERITE_HOE, "steel_hoe"),
        Map.entry(Items.NETHERITE_HELMET, "steel_helmet"),
        Map.entry(Items.NETHERITE_CHESTPLATE, "steel_chestplate"),
        Map.entry(Items.NETHERITE_LEGGINGS, "steel_leggings"),
        Map.entry(Items.NETHERITE_BOOTS, "steel_boots"),
        // Netherite raw materials (§2.2). Bastion treasure and ruined portals roll these, and the
        // vanilla Nether still generates, so they need substituting rather than only hiding from
        // creative. Steel is the replacement for the same reason §2.3 substitutes steel gear.
        Map.entry(Items.NETHERITE_INGOT, "steel_ingot"),
        Map.entry(Items.NETHERITE_SCRAP, "steel_ingot"),
        Map.entry(Items.NETHERITE_BLOCK, "steel_block"),
        Map.entry(Items.ANCIENT_DEBRIS, "steel_ingot"));

    public ReplaceScrubbedGearModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>(generatedLoot.size());
        for (ItemStack stack : generatedLoot) {
            if (DROPPED.contains(stack.getItem())) {
                continue;
            }
            String replacementPath = REPLACEMENTS.get(stack.getItem());
            if (replacementPath == null) {
                result.add(stack);
                continue;
            }
            Item replacement = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("lotrmod", replacementPath));
            // A missing replacement would silently delete the drop, so keep the original instead.
            result.add(replacement == Items.AIR ? stack : new ItemStack(replacement, stack.getCount()));
        }
        return result;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
