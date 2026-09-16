package com.srqingchen.eden.item;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;

/**
 * A STATUS card (功能清单 §8 "状态卡"): besides any attribute passives, its {@code curioTick} keeps a set
 * of (usually ambient) mob effects permanently refreshed on the wearer while equipped - a few seconds of
 * duration re-applied every tick, so it never flickers and disappears the moment the card is unequipped.
 * Effects are applied ambient + without particles to keep the HUD clean.
 */
public class StatusCardItem extends CardItem {

    /** One refreshed effect: the effect holder + amplifier (duration is handled by the refresh). */
    public record Effect(Holder<MobEffect> effect, int amplifier) {}

    private static final int REFRESH_TICKS = 60;
    private final List<Effect> effects;

    public StatusCardItem(Properties properties, String modifierIdSuffix, CardQuality quality,
                          List<Modifier> modifiers, List<Effect> effects) {
        super(properties, modifierIdSuffix, quality, modifiers);
        this.effects = effects;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() == null || slotContext.entity().level().isClientSide()) {
            return;
        }
        // Re-apply well before expiry (every 3s for a 60s window) - cheap and flicker-free.
        if (slotContext.entity().tickCount % REFRESH_TICKS != 0) {
            return;
        }
        for (Effect e : this.effects) {
            slotContext.entity().addEffect(new MobEffectInstance(e.effect(), REFRESH_TICKS + 20, e.amplifier(),
                    true, false, true));
        }
    }
}
