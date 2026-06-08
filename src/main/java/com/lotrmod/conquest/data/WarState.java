package com.lotrmod.conquest.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class WarState {
    public static final long WAR_EXPIRY_ONLINE_DAYS = 50L;

    public final UUID opponentGuildId;
    /** Online-day when we last placed a war banner (or declared war). */
    public long lastBannerByUsDay;
    /** Online-day when they last placed a war banner (or war was declared). */
    public long lastBannerByThemDay;

    public WarState(UUID opponentGuildId, long currentOnlineDay) {
        this.opponentGuildId = opponentGuildId;
        this.lastBannerByUsDay = currentOnlineDay;
        this.lastBannerByThemDay = currentOnlineDay;
    }

    private WarState(UUID opponentGuildId, long byUs, long byThem) {
        this.opponentGuildId = opponentGuildId;
        this.lastBannerByUsDay = byUs;
        this.lastBannerByThemDay = byThem;
    }

    public long lastActivityDay() {
        return Math.max(lastBannerByUsDay, lastBannerByThemDay);
    }

    public boolean isExpired(long currentOnlineDay) {
        return (currentOnlineDay - lastActivityDay()) >= WAR_EXPIRY_ONLINE_DAYS;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("opponent", opponentGuildId);
        tag.putLong("byUs", lastBannerByUsDay);
        tag.putLong("byThem", lastBannerByThemDay);
        return tag;
    }

    public static WarState load(CompoundTag tag) {
        return new WarState(
            tag.getUUID("opponent"),
            tag.getLong("byUs"),
            tag.getLong("byThem")
        );
    }
}
