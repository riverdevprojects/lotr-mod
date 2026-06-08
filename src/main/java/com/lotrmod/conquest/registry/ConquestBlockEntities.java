package com.lotrmod.conquest.registry;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.block.ClaimBannerBlockEntity;
import com.lotrmod.conquest.block.WarBannerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ConquestBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LOTRMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClaimBannerBlockEntity>> CLAIM_BANNER =
        BLOCK_ENTITIES.register("claim_banner", () ->
            BlockEntityType.Builder.of(ClaimBannerBlockEntity::new,
                ConquestBlocks.CLAIM_BANNER_BASE.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarBannerBlockEntity>> WAR_BANNER =
        BLOCK_ENTITIES.register("war_banner", () ->
            BlockEntityType.Builder.of(WarBannerBlockEntity::new,
                ConquestBlocks.WAR_BANNER.get())
            .build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
