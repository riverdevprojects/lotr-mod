package com.lotrmod.worldgen.placement;

import com.lotrmod.LOTRMod;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Placement modifier types added by the mod (Materials GDD §5.2). */
public final class ModPlacementModifiers {
    private ModPlacementModifiers() {}

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
        DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, LOTRMod.MODID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<InRegionPlacement>> IN_REGION =
        PLACEMENT_MODIFIERS.register("in_region", () -> () -> InRegionPlacement.CODEC);

    public static void register(IEventBus modEventBus) {
        PLACEMENT_MODIFIERS.register(modEventBus);
    }
}
