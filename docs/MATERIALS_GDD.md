# Materials & Metallurgy — GDD v0.1

**Status:** Draft — approved for implementation
**Scope:** Ore roster, worldgen, tool/armor tiers, diamond deprecation, basic crafting recipes
**Out of scope (deferred to Forge GDD):** alloying, steel, galvorn, mithril, ithildin, forge block entity

---

## 1. Design pillars

1. **Every ore has one job.** If a material's only justification is "it's another tier," it's cut. Ten ores, five of them vanilla.
2. **Alloys and high tiers are earned at a station, not a table.** The crafting grid handles raw metals. The forge handles everything that requires heat, flux, or a lost recipe.
3. **No single strictly-best endgame item.** Galvorn and mithril answer different questions.
4. **Diamond is a component, not a tier.** See §2.
5. **Region gating is content.** An ore's absence from a region is as meaningful as its presence. Mithril in Moria and nowhere else is the single most important worldgen decision in this doc.

---

## 2. Diamond deprecation

Diamond ceases to be an equipment material. It becomes a **pure crafting component** — high-value, non-wearable, non-swingable.

### 2.1 Items removed from play

| Item | Action |
|---|---|
| `minecraft:diamond_sword` | recipe removed, hidden from creative, scrubbed from loot tables |
| `minecraft:diamond_pickaxe` | same |
| `minecraft:diamond_axe` | same |
| `minecraft:diamond_shovel` | same |
| `minecraft:diamond_hoe` | same |
| `minecraft:diamond_helmet` | same |
| `minecraft:diamond_chestplate` | same |
| `minecraft:diamond_leggings` | same |
| `minecraft:diamond_boots` | same |
| `minecraft:diamond_horse_armor` | removed; replaced by `lotr:steel_horse_armor` |

### 2.2 Dependency warning — netherite

Netherite gear is produced **exclusively** by smithing-upgrading diamond gear. Removing diamond equipment silently orphans the entire netherite chain: netherite ingots, scrap, and the upgrade template become dead items with no valid recipe path.

Decision required before implementation. Three options:

- **(A) Cut netherite entirely.** Cleanest. There is no Nether in this mod's cosmology anyway — no Ancient Debris, no netherite. Recommended.
- **(B) Re-target the smithing template** so netherite upgrades steel gear instead of diamond. Keeps the mechanic, costs a lore justification.
- **(C) Leave orphaned.** Not acceptable — players will find it and file bugs.

**Recommendation: (A).** Remove Ancient Debris worldgen, remove netherite items from creative, scrub from loot tables. Galvorn and mithril occupy that design space already.

### 2.3 Removal mechanics (NeoForge)

Items cannot be truly unregistered without breaking saves. The removal is a four-step scrub:

1. **Recipes** — datapack overrides at `data/minecraft/recipe/<name>.json` containing a condition that never passes. Preferred form:
   ```json
   {
     "neoforge:conditions": [ { "type": "neoforge:false" } ],
     "type": "minecraft:crafting_shaped",
     "pattern": ["XX", "X#", " #"],
     "key": { "X": "minecraft:diamond", "#": "minecraft:stick" },
     "result": { "id": "minecraft:diamond_axe", "count": 1 }
   }
   ```
   The recipe still parses but is never loaded.
2. **Creative tabs** — subscribe to `BuildCreativeModeTabContentsEvent` and remove the entries.
3. **Loot tables** — override every vanilla chest table that rolls diamond gear (`village_*`, `stronghold_*`, `desert_pyramid`, `bastion_*`, `end_city_treasure`, `ruined_portal`, `trial_chambers/*`, `shipwreck_supply`, plus any of ours). Substitute steel gear at the same weight.
4. **Villager trades** — armorer/toolsmith/weaponsmith tiers offering diamond gear need `VillagerTradesEvent` handling.

Step 3 is the one that gets missed. Do a full-text grep of the vanilla loot table dump for `diamond_` before signing this off.

### 2.4 Diamond's new role

Diamond stays valuable — it just stops being wearable.

| Use | Notes |
|---|---|
| Forge construction | Diamond is a required component of the forge multiblock's core. Hard gate: no diamonds, no steel. |
| Grinding wheel / abrasive | Consumable component for processing star-iron and mithril. |
| Diamond dust | Intermediate for mithril refining. Forge-side, spec'd in the Forge GDD. |
| Enchanting table | Unchanged (vanilla). |
| Decoration | Diamond block stays craftable both directions. |
| Trade | High-value dwarf/Erebor trade good. |

This makes diamond *more* interesting than it was, not less. It's now a bottleneck rather than a milestone.

---

## 3. Material roster

| Material | Source | Equipment? | Crafted at |
|---|---|---|---|
| Coal | mined | no | — |
| Copper | mined (vanilla) | tools + armor | crafting table |
| Tin | mined | **no** — bronze feedstock only | — |
| Iron | mined (vanilla) | tools + armor | crafting table |
| Silver | mined | tools + armor | crafting table |
| Gold | mined (vanilla) | **no** — decoration/enchanting/trade | — |
| Diamond | mined (vanilla) | **no** — component only | — |
| Emerald | mined (vanilla) | no — trade currency | — |
| Star-iron | meteorite crater | tools + armor | crafting table |
| **Mithril** | mined, Moria only | tools + armor | **forge** |
| Bronze | alloy | tools + armor | **forge** |
| Steel | alloy | tools + armor | **forge** |
| Galvorn | smithed from star-iron | tools + armor | **forge** |
| Ithildin | mithril alloy | no — block/glyph | **forge** |

Everything in the "forge" column is **out of scope for this document.** This GDD delivers ore, worldgen, stats, and basic-table recipes only.

---

## 4. Registry

Placeholder namespace `lotr:` — replace with the mod's actual ID before implementation.

### 4.1 Blocks

```
lotr:tin_ore
lotr:deepslate_tin_ore
lotr:silver_ore
lotr:deepslate_silver_ore
lotr:mithril_ore              # deepslate only — see §5.3
lotr:star_iron_block          # meteorite core, worldgen-placed
lotr:raw_tin_block
lotr:raw_silver_block
lotr:tin_block
lotr:silver_block
lotr:bronze_block
lotr:steel_block
lotr:mithril_block
lotr:star_iron_ingot_block
lotr:galvorn_block
```

### 4.2 Items

```
lotr:raw_tin           lotr:tin_ingot          lotr:tin_nugget
lotr:raw_silver        lotr:silver_ingot       lotr:silver_nugget
lotr:raw_mithril       lotr:mithril_ingot      lotr:mithril_nugget
lotr:star_iron_chunk   lotr:star_iron_ingot    lotr:star_iron_nugget
lotr:bronze_ingot      lotr:steel_ingot        lotr:galvorn_ingot
lotr:diamond_dust      lotr:steel_horse_armor
```

Tools and armor: `lotr:<material>_{sword,pickaxe,axe,shovel,hoe}` and `lotr:<material>_{helmet,chestplate,leggings,boots}` for `copper`, `silver`, `star_iron`, `bronze`, `steel`, `galvorn`, `mithril`.

---

## 5. Worldgen

### 5.1 Table

| Ore | Y range | Vein size | Count/chunk | Air-exposure discard | Region gate |
|---|---|---|---|---|---|
| Coal | 0 → 190 | 17 | 20 | 0.0 | global |
| Copper | -16 → 112 | 10 | 16 | 0.0 | global |
| Tin | -20 → 90 | 8 | 9 | 0.0 | Ered Luin, Misty Mtns |
| Iron | -24 → 56 | 9 | 12 | 0.0 | global; ×2.5 Iron Hills |
| Silver | -48 → 20 | 6 | 4 | 0.3 | mountain regions |
| Gold | -64 → 32 | 9 | 5 | 0.2 | ×3 Ered Mithrin / Erebor |
| Diamond | -64 → 16 | 8 | 6 | 0.6 | Moria, Erebor |
| Emerald | -16 → 120 | 1 | 60 | 0.0 | Erebor massif only |
| **Mithril** | -64 → -38 | 2 | 1 | 0.88 | **Moria only** |
| **Star-iron** | surface | crater feature | ~1 per 600 chunks | — | global, biased to peaks |

Diamond's count stays at vanilla-ish levels despite no longer being equipment — it's now a forge bottleneck, and starving it starves steel.

### 5.2 Region gating

Gating hooks into the existing PNG region-map system rather than biome tags, so a region can span multiple biomes. Implementation: a `BiomeModifier` filtered by region ID, or a custom placement modifier that samples the region map at the placement position. The latter is preferred — it keeps gating in one system.

The `lotr:in_region` placement modifier should take a list of region IDs and a density multiplier, so the Iron Hills ×2.5 and Ered Mithrin ×3 cases don't need duplicate feature files.

### 5.3 Mithril — depth scaling

Mithril is the one ore that does not use a uniform distribution. Requirements:

- Deepslate variant only. There is no stone-tier mithril ore.
- Density scales **inversely with Y**: near-zero at Y-38, peaking at bedrock.
- Below Y-55, mithril placement raises a regional "delving" value that increases hostile mob spawn rates and spawn-cap in a radius around the player.
- The delving value is persistent per-chunk and does not decay quickly. Mining out a deep mithril seam should make that section of Moria measurably worse to be in.

This is the "delved too greedily, and too deep" mechanic and it is the single most flavor-carrying system in the ore set. Do not ship mithril without it — otherwise it's just deep diamonds.

### 5.4 Star-iron

Star-iron does **not** generate as a vein. It arrives as a surface impact feature:

- A crater of scorched/displaced terrain, 7–15 blocks across.
- 2–5 `lotr:star_iron_block` at the impact point, buried 1–3 blocks deep.
- Higher placement weight on mountain peaks and open plains; suppressed in dense forest and water.
- Visible from a distance. This is a landmark players hunt, not something they trip over while strip-mining.

Optional stretch: rare live meteorite events that place a fresh crater during play, with sound and sky VFX.

### 5.5 Feature JSON

`data/lotr/worldgen/configured_feature/ore_silver.json`

```json
{
  "type": "minecraft:ore",
  "config": {
    "discard_chance_on_air_exposure": 0.3,
    "size": 6,
    "targets": [
      {
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:stone_ore_replaceables"
        },
        "state": { "Name": "lotr:silver_ore" }
      },
      {
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:deepslate_ore_replaceables"
        },
        "state": { "Name": "lotr:deepslate_silver_ore" }
      }
    ]
  }
}
```

`data/lotr/worldgen/placed_feature/ore_silver.json`

```json
{
  "feature": "lotr:ore_silver",
  "placement": [
    { "type": "minecraft:count", "count": 4 },
    { "type": "minecraft:in_square" },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "min_inclusive": { "absolute": -48 },
        "max_inclusive": { "absolute": 20 }
      }
    },
    { "type": "minecraft:biome" }
  ]
}
```

Tin follows the same shape with `size: 8`, `count: 9`, height -20 → 90, discard 0.0.

---

## 6. Tool tiers

| Material | Durability | Speed | Atk bonus | Ench | Mines at |
|---|---|---|---|---|---|
| Copper | 190 | 5.0 | 1.0 | 12 | stone |
| Bronze | 320 | 6.5 | 1.5 | 10 | iron |
| Iron | 250 | 6.0 | 2.0 | 14 | iron |
| Silver | 180 | 7.0 | 1.5 | 22 | iron |
| Steel | 850 | 7.5 | 2.5 | 12 | **steel** |
| Star-iron | 700 | 7.0 | 2.5 | 16 | steel |
| Galvorn | 1900 | 8.5 | 3.5 | 14 | **mithril** |
| Mithril | 2600 | 10.0 | 3.5 | 25 | mithril |

**Silver** is deliberately fragile and fast with very high enchantability — a specialist sidegrade, not a step. Its real value is §6.2.

**Star-iron** sits beside steel rather than above it: faster and far more enchantable, notably less durable. It's the galvorn feedstock, so consuming it in tools is a real cost.

### 6.1 Mining tiers

Two new block tags gate the ladder above iron:

```
lotr:incorrect_for_steel_tool
lotr:incorrect_for_mithril_tool
```

`incorrect_for_steel_tool` must **not** contain `lotr:mithril_ore` — steel picks are what unlock mithril. Without this, mithril is unreachable and the entire top of the tree is dead. This is the single most common way to ship this system broken.

Since diamond tools are gone, obsidian must be re-gated. Move it into steel tier (out of `incorrect_for_steel_tool`, into `incorrect_for_iron_tool`). Verify nothing else in vanilla required diamond-tier mining — ancient debris does, but per §2.2 it's being removed.

### 6.2 Special damage

| Material | Effect |
|---|---|
| Silver | +5 damage vs. undead, wights, wraiths |
| Mithril | +2 damage vs. orcs and trolls |
| Galvorn | ignores 25% of target armor |

### 6.3 Code shape

NeoForge 1.21.x, `ToolMaterial` is a record:

```java
public static final ToolMaterial SILVER = new ToolMaterial(
    LOTRTags.Blocks.INCORRECT_FOR_IRON_TOOL, // incorrectBlocksForDrops
    180,                                      // durability
    7.0F,                                     // speed
    1.5F,                                     // attackDamageBonus
    22,                                       // enchantmentValue
    LOTRTags.Items.SILVER_TOOL_MATERIALS      // repairItems
);
```

**Version caveat:** the `Tier` → `ToolMaterial` migration and the exact constructor signature moved between 1.21.1 and 1.21.5. Confirm against the mappings actually in the project before writing these — the field order above is correct for the record form but is not stable across the whole 1.21 line.

---

## 7. Armor tiers

| Tier | Crafted at | Helm/Chest/Legs/Boots | Toughness | KB res | Ench |
|---|---|---|---|---|---|
| Leather | crafting table | 1/3/2/1 | 0.0 | 0.0 | 15 |
| Copper | crafting table | 1/4/3/1 | 0.0 | 0.0 | 12 |
| Bronze | **forge** | 2/5/4/1 | 0.0 | 0.0 | 10 |
| Iron | crafting table | 2/6/5/2 | 0.0 | 0.0 | 9 |
| Silver | crafting table | 2/5/4/2 | 0.0 | 0.0 | 22 |
| Star-iron | crafting table | 3/6/5/2 | 1.0 | 0.0 | 16 |
| **Steel** | **forge** | 3/7/5/2 | 1.5 | 0.0 | 12 |
| **Galvorn** | **forge** | 4/9/7/3 | 3.5 | 0.15 | 8 |
| **Mithril** | **forge** | 4/8/6/3 | 4.0 | 0.0 | 25 |

Note the deliberate split at the top. **Galvorn** is the raw-number tank: highest defense, knockback resistance, but heavy-feeling and poorly enchantable. **Mithril** is the mobility set: slightly lower defense, highest toughness, best enchantability, and —

**Mithril armor applies no movement penalty and grants +5% movement speed when the full set is worn.** Durability is effectively unbounded (65535 or a no-damage flag). This is what makes it mithril rather than "better steel," and it's why its raw defense numbers are allowed to sit below galvorn's.

Leather and iron armor are craftable at the normal table with vanilla recipes, unchanged. Copper, silver, and star-iron join them. Everything else routes through the forge.

---

## 8. Crafting recipes

**In scope:** basic crafting-table recipes for ores, ingots, blocks, nuggets, and equipment for copper, silver, and star-iron.
**Out of scope:** bronze, steel, galvorn, mithril, ithildin — all forge-gated, specified in the Forge GDD.

### 8.1 Smelting

Every raw metal and ore block needs both a furnace and a blast furnace recipe.

`data/lotr/recipe/silver_ingot_from_smelting_raw_silver.json`

```json
{
  "type": "minecraft:smelting",
  "ingredient": "lotr:raw_silver",
  "result": { "id": "lotr:silver_ingot" },
  "experience": 0.7,
  "cookingtime": 200
}
```

Blast furnace variant: `"type": "minecraft:blasting"`, `"cookingtime": 100`.

Required pairs:

| Input | Output | XP |
|---|---|---|
| `lotr:raw_silver` | `lotr:silver_ingot` | 0.7 |
| `lotr:silver_ore` | `lotr:silver_ingot` | 0.7 |
| `lotr:deepslate_silver_ore` | `lotr:silver_ingot` | 0.7 |
| `lotr:raw_tin` | `lotr:tin_ingot` | 0.5 |
| `lotr:tin_ore` | `lotr:tin_ingot` | 0.5 |
| `lotr:deepslate_tin_ore` | `lotr:tin_ingot` | 0.5 |
| `lotr:star_iron_chunk` | `lotr:star_iron_ingot` | 1.0 |

Mithril does **not** smelt in a furnace. Raw mithril requires the forge. This is intentional — it prevents a lucky deep-mining run from skipping the entire steel gate.

### 8.2 Storage blocks & nuggets

Standard 3×3 / shapeless pairs for every metal. Per metal `M`:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["MMM", "MMM", "MMM"],
  "key": { "M": "lotr:silver_ingot" },
  "result": { "id": "lotr:silver_block", "count": 1 }
}
```

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": ["lotr:silver_block"],
  "result": { "id": "lotr:silver_ingot", "count": 9 }
}
```

Nuggets mirror this at 1:9 against the ingot. Raw blocks (`raw_silver_block`, `raw_tin_block`) get the same treatment against raw items.

### 8.3 Tools

Vanilla patterns. Per material `M` where `M` ∈ {copper, silver, star_iron}:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["MMM", " S ", " S "],
  "key": { "M": "lotr:silver_ingot", "S": "minecraft:stick" },
  "result": { "id": "lotr:silver_pickaxe", "count": 1 }
}
```

| Tool | Pattern |
|---|---|
| Sword | `" M "`, `" M "`, `" S "` |
| Pickaxe | `"MMM"`, `" S "`, `" S "` |
| Axe | `"MM "`, `"MS "`, `" S "` |
| Shovel | `" M "`, `" S "`, `" S "` |
| Hoe | `"MM "`, `" S "`, `" S "` |

15 files (3 materials × 5 tools).

### 8.4 Armor

Vanilla patterns, same three materials:

| Piece | Pattern |
|---|---|
| Helmet | `"MMM"`, `"M M"` |
| Chestplate | `"M M"`, `"MMM"`, `"MMM"` |
| Leggings | `"MMM"`, `"M M"`, `"M M"` |
| Boots | `"M M"`, `"M M"` |

12 files. Copper, silver, star-iron only — bronze, steel, galvorn, and mithril armor **must not have crafting-table recipes.** If any of those four is craftable at a normal table, the forge has no reason to exist.

### 8.5 Smithing / repair

`repairItems` tags per §6.3. Standard anvil and grindstone behavior applies. No smithing-table recipes in this scope — netherite is gone per §2.2, and galvorn/mithril upgrades belong to the forge.

---

## 9. Progression ladder

```
                      crafting table                 forge
                      ─────────────                  ─────
wood ──→ stone ──→ copper ──→ iron ──────────────→ steel ──→ mithril
                      │          │                    ↑          ↑
                 (tin)└──────────┼──→ bronze ─────────┘          │
                                 │    (forge)                    │
                            silver                          diamond
                        (sidegrade,                       (component
                        anti-undead)                       gate, §2.4)

star-iron (meteorite) ──→ galvorn ──→ [terminal, forge, hidden recipe]
```

Two terminal sets, neither strictly better. Diamond gates the forge, the forge gates steel, steel gates mithril.

---

## 10. Tag reference

**Block tags**
```
lotr:incorrect_for_steel_tool
lotr:incorrect_for_mithril_tool
lotr:ores/tin
lotr:ores/silver
lotr:ores/mithril
c:ores          # add all new ores
minecraft:needs_iron_tool        # + tin, silver ore
lotr:needs_steel_tool            # + mithril ore, obsidian
```

**Item tags**
```
c:ingots/tin
c:ingots/silver
c:ingots/bronze
c:ingots/steel
c:ingots/mithril
c:ingots/star_iron
c:raw_materials/tin
c:raw_materials/silver
lotr:silver_tool_materials       # repair
lotr:copper_tool_materials
lotr:star_iron_tool_materials
```

Use the `c:` common convention throughout — it's what other mods will look for if this ever ships alongside anything.

---

## 11. Open questions

1. **Bronze — keep or cut?** It exists to justify tin. If early game is already dense, cutting bronze and tin together is a clean excision that touches nothing else. Decide before writing tin worldgen.
2. **Netherite** — confirm option (A) from §2.2.
3. **Copper tools** — vanilla has copper tools as of 1.21.9+. Confirm the target version; if they exist, extend rather than register duplicates.
4. **Star-iron armor at the crafting table?** Currently yes. Arguable that anything meteoric should need heat. Moving it forge-side would make the forge gate feel earlier and heavier.
5. **Delving value persistence** — per-chunk NBT or a separate region-level save? Affects whether it can be queried cheaply for spawn logic.

---

## 12. Implementation order

1. Diamond scrub (§2) — do this **first**, before any new content, so the removal is testable in isolation.
2. Registry: blocks, items, blockstates, models, lang.
3. Ore worldgen + region gating hook.
4. Tags, including the two new mining tiers.
5. Tool/armor materials and equipment items.
6. Basic crafting recipes (§8).
7. Special damage effects (§6.2) and mithril movement (§7).
8. Delving mechanic (§5.3) — last, it depends on 3 being stable.

Steps 1–6 are a shippable milestone on their own; steps 7–8 are what make it feel like this mod and not a generic ore pack.
