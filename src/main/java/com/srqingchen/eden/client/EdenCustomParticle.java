package com.srqingchen.eden.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.RisingParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The four custom particles (§16), one shared class parameterised by motion/decay profile:
 * <ul>
 *   <li><b>pollution_spore</b> - slow rise, long life, gentle fade (tainted ground off-gas).</li>
 *   <li><b>taint_mist</b> - sluggish drift, big soft quad (haze around cores).</li>
 *   <li><b>energy_arc</b> - quick, short-lived, shrinks fast (pod hull sparks).</li>
 *   <li><b>hope_glow</b> - floating bob with a warm glow that breathes out (oasis motes).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class EdenCustomParticle {

    private EdenCustomParticle() {}

    private static class Custom extends RisingParticle {
        private final boolean shrink;
        private final float baseSize;

        Custom(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
               TextureAtlasSprite sprite, float size, int lifetime, boolean shrink) {
            super(level, x, y, z, vx, vy, vz, sprite);
            this.baseSize = size;
            this.quadSize = size;
            this.lifetime = lifetime;
            this.shrink = shrink;
            this.hasPhysics = false;
        }

        @Override
        public SingleQuadParticle.Layer getLayer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }

        @Override
        public float getQuadSize(float partialTick) {
            if (!this.shrink) {
                return this.baseSize;
            }
            float t = (this.age + partialTick) / this.lifetime;
            return this.baseSize * (1.0f - t * t);   // ease-out shrink
        }
    }

    /** 污染孢子: slow rise, ~3s life. */
    public static ParticleProvider<SimpleParticleType> spore(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz, random) ->
                sized(level, x, y, z, vx * 0.2, 0.03 + random.nextDouble() * 0.03, vz * 0.2,
                        sprites, random, 0.10f, 55 + random.nextInt(25), true);
    }

    /** 浊雾: sluggish big soft quad, ~2.5s life. */
    public static ParticleProvider<SimpleParticleType> mist(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz, random) ->
                sized(level, x, y, z, (random.nextDouble() - 0.5) * 0.015, 0.012, (random.nextDouble() - 0.5) * 0.015,
                        sprites, random, 0.22f, 45 + random.nextInt(30), false);
    }

    /** 能量电弧: quick, short, shrinks fast. */
    public static ParticleProvider<SimpleParticleType> arc(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz, random) ->
                sized(level, x, y, z, vx * 0.6 + (random.nextDouble() - 0.5) * 0.08,
                                vy * 0.6 + (random.nextDouble() - 0.5) * 0.08,
                                vz * 0.6 + (random.nextDouble() - 0.5) * 0.08,
                        sprites, random, 0.07f, 8 + random.nextInt(6), true);
    }

    /** 希望萤光: gentle float, breathing fade, ~4s life. */
    public static ParticleProvider<SimpleParticleType> hope(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz, random) ->
                sized(level, x, y, z, (random.nextDouble() - 0.5) * 0.01,
                                0.015 + Math.sin(random.nextDouble() * Math.PI) * 0.02,
                                (random.nextDouble() - 0.5) * 0.01,
                        sprites, random, 0.11f, 70 + random.nextInt(30), true);
    }

    private static Particle sized(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites,
                                  RandomSource random, float size, int lifetime, boolean shrink) {
        Custom p = new Custom(level, x, y, z, vx, vy, vz, sprites.get(random), size, lifetime, shrink);
        return p;
    }
}
