package com.srqingchen.eden;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for Eden Protocol. All gameplay numbers are data-driven/configurable; only a few
 * core tuning values live here for MVP, the rest move to datapack JSON as systems land.
 */
public class EdenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RAID_DURATION_MINUTES = BUILDER
            .comment("Hard time limit per raid, in minutes. Not scaled by difficulty.")
            .defineInRange("raidDurationMinutes", 30, 1, 240);

    public static final ModConfigSpec.IntValue EROSION_MAX = BUILDER
            .comment("Default erosion gauge cap per player (upgradeable via tech/cards).")
            .defineInRange("erosionMax", 100, 1, 100000);

    public static final ModConfigSpec.DoubleValue MIASMA_CHANCE = BUILDER
            .comment("Chance (0..1) at each miasma interval (first 20 min) to lose 10% of max health.")
            .defineInRange("miasmaChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_NATURAL_REGEN = BUILDER
            .comment("Reserved: disable vanilla natural_health_regeneration so the custom food regen is authoritative (enforced in a later polish step).")
            .define("disableVanillaNaturalRegen", true);

    public static final ModConfigSpec.DoubleValue FAILED_EXTRACT_KEEP_RATIO = BUILDER
            .comment("Fraction of carried salvage liquidated into supply points when a raid fails (wipe / timeout), without a policy.")
            .defineInRange("failedExtractKeepRatio", 0.10, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue INSURED_KEEP_RATIO = BUILDER
            .comment("Failed-raid salvage keep fraction when the player carried an attrition policy (\u635f\u8017\u4fdd\u5355), which is consumed on trigger.")
            .defineInRange("insuredKeepRatio", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue TIPS_ENABLED = BUILDER
            .comment("Periodic \"did you know\" guidance tips in chat (random body colour; key tips cycle a hue).")
            .define("tipsEnabled", true);

    public static final ModConfigSpec.IntValue TIPS_INTERVAL_MINUTES = BUILDER
            .comment("Average minutes between two chat guidance tips (jittered +/-40%% so it never feels metronomic).")
            .defineInRange("tipsIntervalMinutes", 6, 1, 120);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
