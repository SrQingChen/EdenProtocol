package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.effect.ErosionEffect;
import com.srqingchen.eden.effect.PollutionEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom MobEffects. {@code DeferredHolder<MobEffect, MobEffect>} implements {@code Holder<MobEffect>},
 * so it can be passed straight into {@code new MobEffectInstance(holder, duration, amplifier)}.
 */
public class EdenEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, EdenProtocol.MODID);

    /** Personal erosion status marker (amplifier mirrors the erosion level). */
    public static final DeferredHolder<MobEffect, MobEffect> EROSION =
            MOB_EFFECTS.register("erosion", ErosionEffect::new);

    /** Pollution DoT/slow from tainted fluid and pollution clouds. */
    public static final DeferredHolder<MobEffect, MobEffect> POLLUTION =
            MOB_EFFECTS.register("pollution", PollutionEffect::new);
}
