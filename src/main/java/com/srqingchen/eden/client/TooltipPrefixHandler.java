package com.srqingchen.eden.client;

import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Set;

/**
 * Difficulty-prefix tooltip handler (client, {@link ItemTooltipEvent} on the game bus). While the
 * local player is in a raid, every item's display name is prefixed with the difficulty's taint tier
 * ("轻度污染的" ... "浊潮吞噬的"), per CATEGORY (weapon / tool / armor / block / food / generic variants
 * via {@code eden.prefix.<difficulty>.<category>}, falling back to the generic key), tinted by tier
 * (deeper tiers sink to darker purples), plus an italic lore line describing the tier. Pure display
 * layer: the item id, mechanics, stacking and recipes are untouched.
 * <p>26.x note: armor/melee are data-driven (no ArmorItem/SwordItem classes), so categories read the
 * EQUIPPABLE component and the vanilla tool tags instead.
 */
public class TooltipPrefixHandler {
    /** Difficulty ids that have a taint prefix (the ark/safe context has none). */
    private static final Set<String> PREFIXED = Set.of("scout", "salvage", "purge", "abyss", "endgame");

    private static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("swords"));
    private static final TagKey<Item> PICKAXES = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("pickaxes"));
    private static final TagKey<Item> AXES = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("axes"));
    private static final TagKey<Item> SHOVELS = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("shovels"));
    private static final TagKey<Item> HOES = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("hoes"));

    public static void onTooltip(ItemTooltipEvent event) {
        if (!ClientRaidData.inRaid || !PREFIXED.contains(ClientRaidData.difficulty)) {
            return;
        }
        List<Component> tip = event.getToolTip();
        if (tip.isEmpty()) {
            return;
        }
        String difficulty = ClientRaidData.difficulty;
        Component prefix = Component.translatable("eden.prefix." + difficulty + "." + categoryOf(event.getItemStack()))
                .withStyle(ChatFormatting.DARK_PURPLE);
        Component original = tip.get(0);
        tip.set(0, Component.empty().append(prefix).append(Component.literal(" ")).append(original));
        tip.add(1, Component.translatable("eden.prefix.lore." + difficulty)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static String categoryOf(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot() != null
                && switch (equippable.slot()) {
                    case HEAD, CHEST, LEGS, FEET, BODY -> true;
                    default -> false;
                }) {
            return "armor";
        }
        if (stack.is(SWORDS)) {
            return "weapon";
        }
        if (stack.is(PICKAXES) || stack.is(AXES) || stack.is(SHOVELS) || stack.is(HOES)) {
            return "tool";
        }
        if (stack.getItem() instanceof BlockItem) {
            return "block";
        }
        if (stack.has(DataComponents.FOOD)) {
            return "food";
        }
        return "generic";
    }
}
