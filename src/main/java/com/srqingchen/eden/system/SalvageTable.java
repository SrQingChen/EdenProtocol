package com.srqingchen.eden.system;

import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Salvage -&gt; supply-point conversion values (MVP defaults; moves to datapack/config in v1). Only
 * materials/research items convert. Functional items (locator, eden_cell, insurance, card_pack) and
 * equipment/cards are deliberately absent, so settlement never consumes them.
 * <p>The map is built lazily on first use because {@code DeferredItem.get()} is only valid after
 * registration completes.
 */
public final class SalvageTable {
    private SalvageTable() {}

    private static Map<Item, Integer> values = null;

    private static Map<Item, Integer> values() {
        if (values == null) {
            Map<Item, Integer> m = new HashMap<>();
            m.put(EdenItems.ESSENCE.get(), 2);
            m.put(EdenItems.TAINT_CRYSTAL.get(), 3);
            m.put(EdenItems.TAINTED_ORE.get(), 4);
            m.put(EdenItems.TAINTED_INGOT.get(), 6);
            m.put(EdenItems.SAMPLE_FLORA.get(), 5);
            m.put(EdenItems.SAMPLE_FAUNA.get(), 5);
            m.put(EdenItems.SAMPLE_MINERAL.get(), 5);
            m.put(EdenItems.RELIC_SHARD.get(), 12);
            m.put(EdenItems.SALVAGE_TECH.get(), 8);
            m.put(EdenItems.SALVAGE_ARTIFACT.get(), 25);
            values = m;
        }
        return values;
    }

    /** Supply-point value of a single item, or 0 if it is not salvage. */
    public static int valueOf(Item item) {
        return values().getOrDefault(item, 0);
    }

    public static boolean isSalvage(Item item) {
        return values().containsKey(item);
    }

    /** All salvageable items - the pool the daily "shortage good" rolls from. */
    public static java.util.Set<Item> salvageItems() {
        return values().keySet();
    }
}
