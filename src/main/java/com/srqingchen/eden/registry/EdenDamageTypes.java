package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Damage types are data-driven in 26.1.2 (see {@code data/eden/damage_type/*.json}); these are the
 * code-side {@link ResourceKey} references used to build a {@code DamageSource} at runtime.
 */
public class EdenDamageTypes {
    /** Erosion damage: percentage of max health per tick, ignores armour. */
    public static final ResourceKey<DamageType> EROSION = key("erosion");
    /** Miasma pulse: early-phase periodic health drain. */
    public static final ResourceKey<DamageType> MIASMA = key("miasma");
    /** Collapse: timeout wipe at the 30-minute hard limit. */
    public static final ResourceKey<DamageType> COLLAPSE = key("collapse");
    /**
     * True void damage (curse-card actives): tagged to bypass armour, shield, resistance, effects,
     * enchantments AND the hurt-immunity cooldown, so the follow-up hit lands as pure health loss.
     */
    public static final ResourceKey<DamageType> VOID_TRUE = key("void_true");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(EdenProtocol.MODID, name));
    }
}
