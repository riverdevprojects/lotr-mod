package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Armor materials for the Materials GDD §7 roster. On 1.21.1, {@link ArmorMaterial} is a
 * registry object, so each material is registered into {@code minecraft:armor_material} and
 * referenced as a {@link Holder}.
 *
 * <p>Render layers point at existing vanilla armor textures as placeholders (art is out of
 * scope); the defense/toughness/knockback/enchantability numbers are the real GDD values.
 * The §7 mithril movement bonus + unbounded durability is GDD step 7 (out of this milestone).
 */
public final class ModArmorMaterials {
    private ModArmorMaterials() {}

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, LOTRMod.MODID);

    // defense: helmet / chestplate / leggings / boots ; body defaults to chestplate value
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> COPPER =
        register("copper", 1, 4, 3, 1, 0.0F, 0.0F, 12, "gold",
            () -> Ingredient.of(ModTags_items("copper")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BRONZE =
        register("bronze", 2, 5, 4, 1, 0.0F, 0.0F, 10, "gold",
            () -> Ingredient.of(cIngot("bronze")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SILVER =
        register("silver", 2, 5, 4, 2, 0.0F, 0.0F, 22, "iron",
            () -> Ingredient.of(ModTags_items("silver")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STAR_IRON =
        register("star_iron", 3, 6, 5, 2, 1.0F, 0.0F, 16, "diamond",
            () -> Ingredient.of(ModTags_items("star_iron")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STEEL =
        register("steel", 3, 7, 5, 2, 1.5F, 0.0F, 12, "iron",
            () -> Ingredient.of(cIngot("steel")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GALVORN =
        register("galvorn", 4, 9, 7, 3, 3.5F, 0.15F, 8, "netherite",
            () -> Ingredient.of(cIngot("galvorn")));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MITHRIL =
        register("mithril", 4, 8, 6, 3, 4.0F, 0.0F, 25, "diamond",
            () -> Ingredient.of(cIngot("mithril")));

    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> cIngot(String metal) {
        return net.minecraft.tags.TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal));
    }

    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ModTags_items(String material) {
        return net.minecraft.tags.TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(LOTRMod.MODID, material + "_tool_materials"));
    }

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> register(
            String name, int helmet, int chest, int legs, int boots,
            float toughness, float knockbackResistance, int enchantmentValue,
            String vanillaLayer, Supplier<Ingredient> repair) {
        return ARMOR_MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.CHESTPLATE, chest);
            defense.put(ArmorItem.Type.LEGGINGS, legs);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.BODY, chest);
            return new ArmorMaterial(
                defense,
                enchantmentValue,
                SoundEvents.ARMOR_EQUIP_IRON,
                repair,
                List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace(vanillaLayer))),
                toughness,
                knockbackResistance);
        });
    }

    public static void register(IEventBus eventBus) {
        ARMOR_MATERIALS.register(eventBus);
    }
}
