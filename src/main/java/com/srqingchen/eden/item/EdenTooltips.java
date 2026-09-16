package com.srqingchen.eden.item;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds one explanatory line to the hover tooltip of every Eden item that has a matching
 * {@code eden.tooltip.<path>} translation: cards explain their passive, tools explain their use, salvage
 * materials explain that they settle into supply points. Fully data-driven - drop a lang key in and the
 * tooltip appears, no per-item code. Fired from {@link ItemTooltipEvent} (game bus, common side).
 */
public final class EdenTooltips {
    private EdenTooltips() {}

    public static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !EdenProtocol.MODID.equals(id.getNamespace())) {
            return;
        }
        String key = "eden.tooltip." + id.getPath();
        if (Language.getInstance().has(key)) {
            event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (item instanceof CardItem card) {
            int star = CardItem.starOf(event.getItemStack());
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < star; i++) {
                stars.append('\u2605');
            }
            event.getToolTip().add(Component.literal(stars + " ")
                    .append(Component.translatable("eden.tooltip.card_star",
                            String.format(java.util.Locale.ROOT, "%.2f", CardItem.starMultiplier(star))))
                    .withStyle(card.quality().color));
        }
    }
}
