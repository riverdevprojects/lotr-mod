package com.lotrmod.conquest;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ConquestConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final ConquestConfig SERVER;

    static {
        Pair<ConquestConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ConquestConfig::new);
        SERVER_SPEC = pair.getRight();
        SERVER      = pair.getLeft();
    }

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.IntValue maxGuildNameLength;
    public final ModConfigSpec.IntValue maxGuildTagLength;

    private ConquestConfig(ModConfigSpec.Builder builder) {
        builder.push("conquest");

        enabled = builder
            .comment("Set to false to disable the entire land-claiming and conquest system.")
            .define("enabled", true);

        maxGuildNameLength = builder
            .comment("Maximum character length for a guild name.")
            .defineInRange("maxGuildNameLength", 32, 3, 64);

        maxGuildTagLength = builder
            .comment("Maximum character length for a guild tag.")
            .defineInRange("maxGuildTagLength", 5, 2, 8);

        builder.pop();
    }
}
