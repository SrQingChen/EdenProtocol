package com.srqingchen.eden.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Personal erosion marker. The actual erosion damage (percentage of max health per tick) is driven
 * by the raid tick handler using the {@code eden:erosion} damage type; this effect mainly surfaces
 * the erosion level as a status icon and can carry attribute side-effects later.
 */
public class ErosionEffect extends MobEffect {
    public ErosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }
}
