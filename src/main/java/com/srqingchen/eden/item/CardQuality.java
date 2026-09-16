package com.srqingchen.eden.item;

import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Card quality tiers - the dialectic "pure ladder" (COMMON..LEGENDARY) vs the "tainted" face (CURSE).
 * Quality drives the card NAME colour and the card-pack roll distribution (per-difficulty weights live in
 * {@code DifficultyConfigData}). {@link #ORDER} is the fixed order shared by the config weight lists, the
 * card pool and the editor, so it must never be reordered without migrating saved weights.
 */
public enum CardQuality {
    COMMON("common", ChatFormatting.WHITE),
    RARE("rare", ChatFormatting.BLUE),
    EPIC("epic", ChatFormatting.LIGHT_PURPLE),
    LEGENDARY("legendary", ChatFormatting.GOLD),
    CURSE("curse", ChatFormatting.RED);

    public static final List<CardQuality> ORDER = List.of(values());

    public final String id;
    public final ChatFormatting color;

    CardQuality(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }
}
