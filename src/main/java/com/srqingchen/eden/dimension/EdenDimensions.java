package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Dimension keys. The dimensions themselves are datapack-registered under
 * {@code data/eden/dimension/*.json} (LevelStem), reusing the vanilla overworld dimension type and
 * worldgen presets so we avoid the refactored dimension_type "attributes" format.
 * <p>The per-raid "fresh random seed" rebuild layers on top of this
 * (NeoForge supports a {@code neoforge:seed_override} on LevelStems).
 */
public class EdenDimensions {
    /** Safe hub: flat platform, initial spawn, shops, launch pad. */
    public static final ResourceKey<Level> ARK = key("ark");
    /** Polluted raid world (overworld-like). */
    public static final ResourceKey<Level> RAID_OVERWORLD = key("raid_overworld");
    /** Polluted raid nether (tainted netherrack; unlocked at campaign stage 3). */
    public static final ResourceKey<Level> RAID_NETHER = key("raid_nether");
    /** Polluted raid end (tainted endstone + the dragon hunt; unlocked at campaign stage 4). */
    public static final ResourceKey<Level> RAID_END = key("raid_end");
    /** Paradise: the purified reward world, unlocked when the campaign pollution hits zero. */
    public static final ResourceKey<Level> PARADISE = key("paradise");

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(EdenProtocol.MODID, name));
    }
}
