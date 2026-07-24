package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Single creative-mode tab for the mod. It is populated automatically from the item
 * registry, so any items registered in the future show up without extra wiring.
 * The mod currently registers no items, so the tab is empty for now.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LOTRMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOTR_TAB =
        TABS.register("lotr", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.lotrmod.lotr"))
            .icon(() -> new ItemStack(Items.GRASS_BLOCK))
            .displayItems((params, output) -> {
                for (var holder : ModItems.ITEMS.getEntries()) output.accept(holder.get());
            })
            .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
