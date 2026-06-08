package com.lotrmod.conquest.registry;

import com.lotrmod.LOTRMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ConquestItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(LOTRMod.MODID);

    // Block items
    public static final DeferredItem<BlockItem> CLAIM_BANNER_BASE =
        ITEMS.register("claim_banner_base", () ->
            new BlockItem(ConquestBlocks.CLAIM_BANNER_BASE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> WAR_BANNER =
        ITEMS.register("war_banner", () ->
            new BlockItem(ConquestBlocks.WAR_BANNER.get(), new Item.Properties()));

    /**
     * Stub silver ingot. Flagged for worldgen integration later — no ore is registered here.
     * Treasury and upkeep reference this item; actual worldgen ore can be added separately.
     */
    public static final DeferredItem<Item> SILVER_INGOT =
        ITEMS.register("silver_ingot", () -> new Item(new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
