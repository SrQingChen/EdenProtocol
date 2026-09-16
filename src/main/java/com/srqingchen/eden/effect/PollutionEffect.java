package com.srqingchen.eden.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Pollution effect applied by tainted fluid / pollution clouds: stacking damage-over-time and slow.
 * Tick logic is handled by the world/pollution system; this class is the registered effect type.
 */
public class PollutionEffect extends MobEffect {
    public PollutionEffect() {
        super(MobEffectCategory.HARMFUL, 0x3B5323);
    }
}
