package com.lotrmod.loot;

import com.lotrmod.event.ScrubHandlers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier for the diamond/netherite scrub (§2.3 step 3). Any diamond or netherite
 * equipment rolled by <em>any</em> loot table — vanilla, modded, or ours — is dropped outright, with
 * no substitute. A GLM is used instead of per-table overrides precisely because the doc warns step 3
 * is "the one that gets missed."
 */
public class DropScrubbedGearModifier extends LootModifier {
    public static final MapCodec<DropScrubbedGearModifier> CODEC =
        RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, DropScrubbedGearModifier::new));

    public DropScrubbedGearModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>(generatedLoot.size());
        for (ItemStack stack : generatedLoot) {
            if (!ScrubHandlers.SCRUBBED.contains(stack.getItem())) {
                result.add(stack);
            }
        }
        return result;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
