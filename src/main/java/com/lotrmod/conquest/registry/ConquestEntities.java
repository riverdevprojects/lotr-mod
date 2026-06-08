package com.lotrmod.conquest.registry;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.entity.GuardEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ConquestEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, LOTRMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuardEntity>> GUILD_GUARD =
        ENTITIES.register("guild_guard", () ->
            EntityType.Builder.<GuardEntity>of(GuardEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(64)
                .build("lotrmod:guild_guard"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
