package com.lotrmod.conquest.registry;

import com.lotrmod.LOTRMod;
import com.lotrmod.conquest.block.ClaimBannerBlock;
import com.lotrmod.conquest.block.WarBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ConquestBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(LOTRMod.MODID);

    /** Claim banner: base block (has block entity, handles claiming logic). */
    public static final DeferredBlock<ClaimBannerBlock> CLAIM_BANNER_BASE =
        BLOCKS.register("claim_banner_base", ClaimBannerBlock::new);

    /** Claim banner: middle pole — placeholder block. */
    public static final DeferredBlock<Block> CLAIM_BANNER_POLE =
        BLOCKS.register("claim_banner_pole", () -> new Block(
            BlockBehaviour.Properties.of().strength(2.0f).noOcclusion()));

    /** Claim banner: top decoration — placeholder block. */
    public static final DeferredBlock<Block> CLAIM_BANNER_TOP =
        BLOCKS.register("claim_banner_top", () -> new Block(
            BlockBehaviour.Properties.of().strength(2.0f).noOcclusion()));

    /** War banner: placed in enemy territory to start a siege. */
    public static final DeferredBlock<WarBannerBlock> WAR_BANNER =
        BLOCKS.register("war_banner", WarBannerBlock::new);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
