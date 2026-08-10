package com.lotrmod.block;

import net.minecraft.util.StringRepresentable;

/**
 * The 13 connected-texture tiles a {@link ConnectedPanelBlock} can show on its visible face,
 * matching the {@code _base/_top/_bottom/_left/_right/...} texture suffixes 1:1.
 */
public enum WallVariant implements StringRepresentable {
    BASE("base"),
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right"),
    TOP_LEFT("top_left"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right"),
    TOP_LEFT_INV("top_left_inv"),
    TOP_RIGHT_INV("top_right_inv"),
    BOTTOM_LEFT_INV("bottom_left_inv"),
    BOTTOM_RIGHT_INV("bottom_right_inv");

    private final String name;

    WallVariant(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
