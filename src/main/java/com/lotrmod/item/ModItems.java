package com.lotrmod.item;

import com.lotrmod.LOTRMod;
import net.minecraft.core.Holder;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry for the Materials &amp; Metallurgy set (Materials GDD §4.2, §6, §7).
 *
 * <p>Block items are registered from {@link com.lotrmod.block.ModBlocks}. Tools/armor are
 * registered for every equipment material in the roster (copper, silver, star-iron, bronze,
 * steel, galvorn, mithril); the forge-gated four have no crafting-table recipe (§8), so in this
 * milestone they are creative-only.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRMod.MODID);

    // --- Raw materials, ingots, nuggets, dust (§4.2) ----------------------
    public static final DeferredItem<Item> RAW_TIN         = simple("raw_tin");
    public static final DeferredItem<Item> TIN_INGOT       = simple("tin_ingot");
    public static final DeferredItem<Item> TIN_NUGGET      = simple("tin_nugget");
    public static final DeferredItem<Item> RAW_SILVER      = simple("raw_silver");
    public static final DeferredItem<Item> SILVER_INGOT    = simple("silver_ingot");
    public static final DeferredItem<Item> SILVER_NUGGET   = simple("silver_nugget");
    public static final DeferredItem<Item> RAW_MITHRIL     = simple("raw_mithril");
    public static final DeferredItem<Item> MITHRIL_INGOT   = simple("mithril_ingot");
    public static final DeferredItem<Item> MITHRIL_NUGGET  = simple("mithril_nugget");
    public static final DeferredItem<Item> STAR_IRON_CHUNK = simple("star_iron_chunk");
    public static final DeferredItem<Item> STAR_IRON_INGOT = simple("star_iron_ingot");
    public static final DeferredItem<Item> STAR_IRON_NUGGET = simple("star_iron_nugget");
    public static final DeferredItem<Item> BRONZE_INGOT    = simple("bronze_ingot");
    public static final DeferredItem<Item> STEEL_INGOT     = simple("steel_ingot");
    public static final DeferredItem<Item> GALVORN_INGOT   = simple("galvorn_ingot");
    public static final DeferredItem<Item> DIAMOND_DUST    = simple("diamond_dust");

    // --- Horse armor (replaces diamond_horse_armor via the loot scrub, §2.1) ---
    public static final DeferredItem<Item> STEEL_HORSE_ARMOR = ITEMS.register("steel_horse_armor",
        () -> new AnimalArmorItem(ModArmorMaterials.STEEL, AnimalArmorItem.BodyType.EQUESTRIAN, false,
            new Item.Properties().stacksTo(1)));

    // --- Equipment sets (§6 tools, §7 armor) ------------------------------
    static {
        registerTools("copper", ModToolTiers.COPPER);
        registerTools("silver", ModToolTiers.SILVER);
        registerTools("star_iron", ModToolTiers.STAR_IRON);
        registerTools("bronze", ModToolTiers.BRONZE);
        registerTools("steel", ModToolTiers.STEEL);
        registerTools("galvorn", ModToolTiers.GALVORN);
        registerTools("mithril", ModToolTiers.MITHRIL);

        registerArmor("copper", ModArmorMaterials.COPPER, 11);
        registerArmor("silver", ModArmorMaterials.SILVER, 10);
        registerArmor("star_iron", ModArmorMaterials.STAR_IRON, 22);
        registerArmor("bronze", ModArmorMaterials.BRONZE, 16);
        registerArmor("steel", ModArmorMaterials.STEEL, 30);
        registerArmor("galvorn", ModArmorMaterials.GALVORN, 40);
        registerArmor("mithril", ModArmorMaterials.MITHRIL, 45);
    }

    private static DeferredItem<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static void registerTools(String prefix, Tier tier) {
        ITEMS.register(prefix + "_sword",
            () -> new SwordItem(tier, new Item.Properties().attributes(SwordItem.createAttributes(tier, 3, -2.4F))));
        ITEMS.register(prefix + "_pickaxe",
            () -> new PickaxeItem(tier, new Item.Properties().attributes(DiggerItem.createAttributes(tier, 1.0F, -2.8F))));
        ITEMS.register(prefix + "_axe",
            () -> new AxeItem(tier, new Item.Properties().attributes(DiggerItem.createAttributes(tier, 6.0F, -3.1F))));
        ITEMS.register(prefix + "_shovel",
            () -> new ShovelItem(tier, new Item.Properties().attributes(DiggerItem.createAttributes(tier, 1.5F, -3.0F))));
        ITEMS.register(prefix + "_hoe",
            () -> new HoeItem(tier, new Item.Properties().attributes(DiggerItem.createAttributes(tier, 0.0F, -3.0F))));
    }

    private static void registerArmor(String prefix, Holder<ArmorMaterial> material, int durabilityMultiplier) {
        registerArmorPiece(prefix + "_helmet", material, ArmorItem.Type.HELMET, durabilityMultiplier);
        registerArmorPiece(prefix + "_chestplate", material, ArmorItem.Type.CHESTPLATE, durabilityMultiplier);
        registerArmorPiece(prefix + "_leggings", material, ArmorItem.Type.LEGGINGS, durabilityMultiplier);
        registerArmorPiece(prefix + "_boots", material, ArmorItem.Type.BOOTS, durabilityMultiplier);
    }

    private static void registerArmorPiece(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, int mult) {
        ITEMS.register(name,
            () -> new ArmorItem(material, type, new Item.Properties().durability(type.getDurability(mult))));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
