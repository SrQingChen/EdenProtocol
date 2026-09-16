package com.srqingchen.eden.item;

import com.srqingchen.eden.registry.EdenDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CurioAttributeModifiers;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A card: an equippable curio (eden:card slot) whose passive is expressed as vanilla attribute modifiers,
 * so it applies automatically while equipped with zero tick code. Each card has a fixed {@link CardQuality}
 * (drives the name colour); each STACK carries a star level (1-5, {@link EdenDataComponents#CARD_STAR}) that
 * scales the modifier amounts and shows as ★N appended to the quality-coloured name.
 * <p>Curse cards may additionally declare an ACTIVE skill ({@code activeId}, resolved by
 * {@code CurseCardSystem}): a short-press one-shot burst on a per-card cooldown, and a long-press
 * "sacrificial burn" that destroys the card in exchange for a powerful state with a ramping cost.
 */
public class CardItem extends Item implements ICurioItem {
    /** One attribute modifier entry: attribute + amount + operation. */
    public record Modifier(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {}

    private final String modifierIdSuffix;
    private final CardQuality quality;
    private final List<Modifier> modifiers;
    /** Active-skill id ("bloodthirst", "greed", ...), or null when the card has no active. */
    private final String activeId;

    public CardItem(Properties properties, String modifierIdSuffix, CardQuality quality, List<Modifier> modifiers) {
        this(properties, modifierIdSuffix, quality, modifiers, null);
    }

    public CardItem(Properties properties, String modifierIdSuffix, CardQuality quality, List<Modifier> modifiers,
                    String activeId) {
        super(properties);
        this.modifierIdSuffix = modifierIdSuffix;
        this.quality = quality;
        this.modifiers = modifiers;
        this.activeId = activeId;
    }

    public CardQuality quality() {
        return this.quality;
    }

    public List<Modifier> modifiers() {
        return this.modifiers;
    }

    /** The active-skill id, or null if this card has no active ability. */
    @Nullable
    public String activeId() {
        return this.activeId;
    }

    /** True when the equipped stack's card provides a card active (short/long press). */
    public boolean hasActive() {
        return this.activeId != null;
    }

    /** Star level carried on a stack (defaults to 1 when the component is absent). */
    public static int starOf(ItemStack stack) {
        return Math.max(1, stack.getOrDefault(EdenDataComponents.CARD_STAR.get(), 1));
    }

    /** Star 1..5 -> attribute multiplier 1.0 .. 2.0 (linear, +0.25 per star above 1). */
    public static double starMultiplier(int star) {
        return 1.0 + 0.25 * (Math.max(1, star) - 1);
    }

    @Override
    public Component getName(ItemStack stack) {
        int star = starOf(stack);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < star; i++) {
            stars.append('\u2605');
        }
        return Component.translatable(this.getDescriptionId())
                .append(Component.literal(" " + stars))
                .withStyle(this.quality.color);
    }

    /**
     * Curios 15 sources a curio's attributes from {@link CurioAttributeModifiers}: first the
     * {@code curios:attribute_modifiers} data component, and - when that component is absent, as here - this method via
     * the item capability ({@code ItemizedCurioCapability.getDefaultCurioAttributeModifiers}). The equip/apply flow
     * ({@code ICurioItem.forEachModifier}) never consults the old
     * {@code getAttributeModifiers(SlotContext, Identifier, ItemStack)} multimap hook, so the star scaling has to be
     * expressed here. {@code CurioAttributeModifiers.builder().build()} sets {@code showInTooltip=true}, so the
     * resulting bonuses also render on the card's tooltip.
     */
    @Override
    public CurioAttributeModifiers getDefaultCurioAttributeModifiers(ItemStack stack) {
        double mult = starMultiplier(starOf(stack));
        CurioAttributeModifiers.Builder builder = CurioAttributeModifiers.builder();
        for (int i = 0; i < this.modifiers.size(); i++) {
            Modifier m = this.modifiers.get(i);
            builder.addModifier(m.attribute(), new AttributeModifier(
                    Identifier.fromNamespaceAndPath("eden", this.modifierIdSuffix + "_" + i),
                    m.amount() * mult, m.operation()));
        }
        return builder.build();
    }
}
