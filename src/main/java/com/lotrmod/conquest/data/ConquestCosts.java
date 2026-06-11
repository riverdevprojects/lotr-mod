package com.lotrmod.conquest.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Central tuning for outpost economy: flag construction cost, guard-hire prices, and caps.
 * Kept moderate by design — there is already an ongoing upkeep cost on top of these.
 */
public final class ConquestCosts {

    private ConquestCosts() {}

    /** Maximum guards that may garrison a single outpost. */
    public static final int MAX_GUARDS_PER_OUTPOST = 15;

    /** One-time resource cost to place a flag / build an outpost (deducted from the guild treasury). */
    public static final Map<TreasuryResource, Long> FLAG_COST;
    static {
        EnumMap<TreasuryResource, Long> m = new EnumMap<>(TreasuryResource.class);
        m.put(TreasuryResource.LOGS, 32L);
        m.put(TreasuryResource.COBBLESTONE, 32L);
        m.put(TreasuryResource.IRON, 8L);
        m.put(TreasuryResource.GOLD, 4L);
        FLAG_COST = Collections.unmodifiableMap(m);
    }

    /** Cost in gold to permanently hire one guard. */
    public static final long GUARD_HIRE_GOLD = 5L;
    /** Alternative cost in silver to permanently hire one guard. */
    public static final long GUARD_HIRE_SILVER = 5L;

    public static Map<TreasuryResource, Long> guardHireCost(TreasuryResource currency) {
        EnumMap<TreasuryResource, Long> m = new EnumMap<>(TreasuryResource.class);
        m.put(currency, currency == TreasuryResource.SILVER ? GUARD_HIRE_SILVER : GUARD_HIRE_GOLD);
        return m;
    }
}
