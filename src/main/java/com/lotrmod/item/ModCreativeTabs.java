package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.registry.ConquestItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * Creative-mode tabs for the mod: one for placeable blocks and one for everything else.
 * Both tabs are populated automatically from the item registries, so newly registered
 * items show up without extra wiring.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LOTRMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS_TAB =
        TABS.register("blocks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lotrmod.blocks"))
            .icon(() -> new ItemStack(ConquestItems.GUILD_STONE.get()))
            .displayItems((params, output) ->
                forEachItem(item -> { if (item instanceof BlockItem) output.accept(item); }))
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_TAB =
        TABS.register("items", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lotrmod.items"))
            .icon(() -> new ItemStack(ModItems.VOLCANIC_ASH.get()))
            .displayItems((params, output) ->
                forEachItem(item -> { if (!(item instanceof BlockItem)) output.accept(item); }))
            .build());

    /** Visits every item registered by the mod (block items and standalone items). */
    private static void forEachItem(Consumer<Item> consumer) {
        for (var holder : ModItems.ITEMS.getEntries()) consumer.accept(holder.get());
        for (var holder : ConquestItems.ITEMS.getEntries()) consumer.accept(holder.get());
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
