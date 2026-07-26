package com.lotrmod.worldgen.placement;

import com.lotrmod.worldgen.Region;
import com.lotrmod.worldgen.RegionMapLoader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Region gate for ore placement (Materials GDD §5.2). Samples the PNG region map at the candidate
 * position and discards it unless the region is in {@code regions}.
 *
 * <p>The GDD offers two ways to gate: a {@code BiomeModifier} filtered by region ID, or a placement
 * modifier that samples the region map. This is the latter, which the doc prefers because a region
 * can span multiple biomes and it "keeps gating in one system" — the biome modifier then adds each
 * ore feature everywhere, and this decides where it actually lands.
 *
 * <p>{@code density} is the §5.2 multiplier that lets cases like Iron Hills ×2.5 and Ered Mithrin ×3
 * reuse one feature file instead of duplicating it. A value above 1 emits the position multiple
 * times (fractional part probabilistically); below 1 it probabilistically drops it.
 *
 * <p>Note {@link RegionMapLoader#getRegion} returns {@link Region#OCEAN} when the map has not
 * loaded, so a load failure makes region-gated ores stop generating rather than generate everywhere.
 * That is the intended direction to fail: absent ore is far easier to notice than ore in the wrong
 * region, and §5's whole premise is that an ore's absence is meaningful.
 *
 * <p><b>Mithril is deliberately not generated yet.</b> §5.1 gates it to Moria, and Moria is not one
 * of the region map's 29 regions — it is to be added later. Rather than gate it to the whole Misty
 * Mountains range (which would contradict pillar 5, where mithril's confinement to Moria is called
 * the most important worldgen decision in the doc), {@code ore_mithril}'s configured and placed
 * features are kept in the codebase but left out of the {@code add_lotr_ores} biome modifier, the
 * same way the conquest system is kept but unwired. Once a MORIA region exists, add
 * {@code lotrmod:ore_mithril} to that biome modifier and set its region list. The mithril ore block,
 * items, loot table, and tags are all live in the meantime.
 */
public class InRegionPlacement extends PlacementModifier {

    /** Regions are written in the JSON as lowercase enum names, e.g. {@code "misty_mountains"}. */
    private static final Codec<Region> REGION_CODEC = Codec.STRING.comapFlatMap(
        name -> {
            try {
                return DataResult.success(Region.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown Middle-earth region: " + name);
            }
        },
        region -> region.name().toLowerCase(Locale.ROOT));

    public static final MapCodec<InRegionPlacement> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        REGION_CODEC.listOf().fieldOf("regions").forGetter(p -> List.copyOf(p.regions)),
        Codec.floatRange(0.0F, 16.0F).optionalFieldOf("density", 1.0F).forGetter(p -> p.density)
    ).apply(inst, InRegionPlacement::new));

    private final Set<Region> regions;
    private final float density;

    public InRegionPlacement(List<Region> regions, float density) {
        this.regions = Set.copyOf(regions);
        this.density = density;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        if (!regions.contains(RegionMapLoader.getRegion(pos.getX(), pos.getZ()))) {
            return Stream.empty();
        }
        int copies = Mth.floor(density);
        if (random.nextFloat() < density - copies) {
            copies++;
        }
        return switch (copies) {
            case 0 -> Stream.empty();
            case 1 -> Stream.of(pos);
            default -> Stream.generate(() -> pos).limit(copies);
        };
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifiers.IN_REGION.get();
    }
}
