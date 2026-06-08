package com.lotrmod.conquest.data;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum TreasuryResource {
    BREAD("Bread") {
        @Override public Item vanillaItem() { return Items.BREAD; }
    },
    COBBLESTONE("Cobblestone") {
        @Override public Item vanillaItem() { return Items.COBBLESTONE; }
    },
    LOGS("Logs") {
        @Override public Item vanillaItem() { return Items.OAK_LOG; }
    },
    GOLD("Gold") {
        @Override public Item vanillaItem() { return Items.GOLD_INGOT; }
    },
    IRON("Iron") {
        @Override public Item vanillaItem() { return Items.IRON_INGOT; }
    },
    SILVER("Silver") {
        @Override public Item vanillaItem() { return null; } // stub — silver item registered in ConquestItems
    };

    private final String displayName;

    TreasuryResource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    public abstract Item vanillaItem();

    /** Upkeep cost table keyed by banner count bracket. Index = bracket (0..6). */
    public static int[] upkeepCosts() {
        return new int[]{0, 4, 8, 16, 32, 64, 128, 256};
    }

    /** Returns bracket index 0-6 for a banner count (index 0 = 0 banners, unused). */
    public static int upkeepBracket(int bannerCount) {
        if (bannerCount <= 0)  return 0;
        if (bannerCount <= 4)  return 1;
        if (bannerCount <= 9)  return 2;
        if (bannerCount <= 19) return 3;
        if (bannerCount <= 29) return 4;
        if (bannerCount <= 49) return 5;
        if (bannerCount <= 79) return 6;
        return 7;
    }

    /** Per-resource cost for the given bracket (same for all resources per spec). */
    public static int upkeepCostForBracket(int bracket) {
        int[] table = {0, 4, 8, 16, 32, 64, 128, 256};
        return bracket < table.length ? table[bracket] : 256;
    }
}
