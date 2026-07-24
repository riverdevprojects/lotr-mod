package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry for the LOTR mod.
 *
 * All custom blocks and items were removed; the mod currently registers no items.
 * The empty registry is kept so the creative tab and registration wiring remain in
 * place for future content.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRMod.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
