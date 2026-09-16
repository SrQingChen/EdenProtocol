package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom particle types (§16 自定义粒子贴图): pollution spores, taint mist, energy arcs and the
 * hope-glow. All are plain {@link SimpleParticleType}s rendered from hand-drawn 8x8 sprites via
 * {@code client/EdenCustomParticle}; the per-particle json files under {@code assets/eden/particles}
 * bind each type to its sprite.
 */
public class EdenParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, EdenProtocol.MODID);

    /** 污染孢子 - purple motes drifting off tainted ground. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POLLUTION_SPORE =
            PARTICLES.register("pollution_spore", () -> new SimpleParticleType(false));
    /** 浊雾 - dark violet haze around cores and thick taint. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TAINT_MIST =
            PARTICLES.register("taint_mist", () -> new SimpleParticleType(false));
    /** 能量电弧 - cyan-white sparks crawling the return pod's hull. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENERGY_ARC =
            PARTICLES.register("energy_arc", () -> new SimpleParticleType(false));
    /** 希望萤光 - warm golden-green motes over the hope oasis. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOPE_GLOW =
            PARTICLES.register("hope_glow", () -> new SimpleParticleType(false));
}
