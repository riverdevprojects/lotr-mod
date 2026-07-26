package com.lotrmod.event;

import com.lotrmod.LOTRMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Diamond &amp; netherite scrub (Materials GDD §2). Three of the four scrub vectors live here:
 * creative-tab removal (§2.3 step 2) and villager-trade removal (§2.3 step 4). Recipes (step 1)
 * are disabled by datapack overrides under {@code data/minecraft/recipe}; loot tables (step 3)
 * are handled by {@link com.lotrmod.loot.ModLootModifiers} — a global loot modifier, which cannot
 * "miss" a table the way per-file overrides can (§2.3).
 *
 * <p>Netherite is cut entirely (option A, §2.2): there is no Nether in this mod's cosmology, so
 * netherite items become dead — they are pulled from creative here and swapped out of loot by the
 * GLM. Note the vanilla Nether dimension is still reachable, so §2.2's "remove Ancient Debris
 * worldgen" is done with datapack overrides of the two vanilla <em>configured</em> features
 * ({@code data/minecraft/worldgen/configured_feature/ore_ancient_debris_{large,small}.json}, set to
 * {@code size: 0}) — overriding the configured features rather than every placed feature that
 * references them means no placement can be missed.
 */
public final class ScrubHandlers {
    private ScrubHandlers() {}

    /** Items removed from creative and (via the GLM) from loot. */
    public static final Set<Item> SCRUBBED = Set.of(
        Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
        Items.DIAMOND_HORSE_ARMOR,
        Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
        Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
        Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.NETHERITE_BLOCK, Items.ANCIENT_DEBRIS,
        Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

    /**
     * Removes scrubbed items from every creative tab (mod-bus event).
     *
     * <p>{@code getParentEntries()} and {@code getSearchEntries()} hand back
     * {@code ObjectSortedSets.unmodifiable} views documented as "purely for querying" — mutating them
     * throws {@link UnsupportedOperationException}. Removal has to go through
     * {@link BuildCreativeModeTabContentsEvent#remove}, and the doomed stacks must be collected first
     * so the backing set is not modified while it is being iterated.
     */
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        List<ItemStack> doomed = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (SCRUBBED.contains(stack.getItem())) {
                doomed.add(stack);
            }
        }
        for (ItemStack stack : event.getSearchEntries()) {
            if (SCRUBBED.contains(stack.getItem())) {
                doomed.add(stack);
            }
        }
        for (ItemStack stack : doomed) {
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /**
     * Strips villager trades that hand out diamond/netherite gear (armorer/toolsmith/weaponsmith).
     * Trade outputs are resolved by evaluating the listing; evaluation is guarded because some
     * listings touch the (here absent) trader entity — those are left untouched rather than risk
     * an exception.
     */
    public static void onVillagerTrades(VillagerTradesEvent event) {
        RandomSource random = RandomSource.create();
        RegistryAccess access = event.getRegistryAccess();
        for (List<VillagerTrades.ItemListing> listings : event.getTrades().values()) {
            listings.removeIf(listing -> producesScrubbedItem(listing, random, access));
        }
    }

    private static boolean producesScrubbedItem(VillagerTrades.ItemListing listing, RandomSource random, RegistryAccess access) {
        try {
            MerchantOffer offer = listing.getOffer(null, random);
            if (offer == null) return false;
            ItemStack result = offer.getResult();
            return SCRUBBED.contains(result.getItem());
        } catch (Throwable t) {
            LOTRMod.LOGGER.debug("Skipped uninspectable villager trade during diamond scrub: {}", t.toString());
            return false;
        }
    }
}
