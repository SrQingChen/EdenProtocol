package com.srqingchen.eden.item;

import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.registry.EdenDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Card-pack roll. Picks a quality by the per-difficulty quality weights, then a card OF that quality
 * (auto-discovered from the item registry, so newly registered {@link CardItem}s are picked up with no code
 * change), then a star level by the per-difficulty star weights. An empty quality tier (e.g. LEGENDARY
 * before any legendary card exists) falls back to any card, so the roll never fails.
 */
public final class CardPool {
    private CardPool() {}

    private static List<CardItem> cache;

    /** All registered cards (lazy cache; the item registry is fixed after mod load). */
    private static List<CardItem> allCards() {
        if (cache == null) {
            List<CardItem> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof CardItem ci) {
                    list.add(ci);
                }
            }
            cache = list;
        }
        return cache;
    }

    /** Roll one card stack (quality -> card -> star) using the given difficulty's distributions. */
    public static ItemStack roll(RandomSource random, DifficultyConfigData.Entry cfg) {
        List<CardItem> all = allCards();
        if (all.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<Integer> qw = validWeights(cfg == null ? null : cfg.qualityWeights(), CardQuality.ORDER.size());
        CardQuality quality = CardQuality.ORDER.get(weightedIndex(random, qw));

        List<CardItem> pool = new ArrayList<>();
        for (CardItem c : all) {
            if (c.quality() == quality) {
                pool.add(c);
            }
        }
        if (pool.isEmpty()) {
            pool = all; // empty tier -> fall back to any card
        }
        CardItem card = pool.get(random.nextInt(pool.size()));

        List<Integer> sw = validWeights(cfg == null ? null : cfg.starWeights(), DifficultyConfigData.STAR_COUNT);
        int star = weightedIndex(random, sw) + 1;

        ItemStack stack = new ItemStack(card);
        stack.set(EdenDataComponents.CARD_STAR.get(), star);
        return stack;
    }

    private static List<Integer> validWeights(List<Integer> weights, int size) {
        if (weights != null && weights.size() == size) {
            return weights;
        }
        List<Integer> ones = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ones.add(1);
        }
        return ones;
    }

    private static int weightedIndex(RandomSource random, List<Integer> weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return random.nextInt(weights.size());
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.size(); i++) {
            roll -= Math.max(0, weights.get(i));
            if (roll < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }
}
