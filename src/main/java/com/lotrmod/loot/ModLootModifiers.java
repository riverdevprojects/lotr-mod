package com.lotrmod.loot;

import com.lotrmod.LOTRMod;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registers the global loot modifier serializers for the diamond/netherite scrub (§2.3). */
public final class ModLootModifiers {
    private ModLootModifiers() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, LOTRMod.MODID);

    public static final Supplier<MapCodec<ReplaceScrubbedGearModifier>> REPLACE_SCRUBBED_GEAR =
        SERIALIZERS.register("replace_scrubbed_gear", () -> ReplaceScrubbedGearModifier.CODEC);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
