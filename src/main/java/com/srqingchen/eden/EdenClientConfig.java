package com.srqingchen.eden;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side config: HUD presentation tuning (§16 GUI 缩放). The scale multiplies the whole raid HUD
 * (erosion gauge, affix strip, extraction pointer) around the screen corners; 1.0 = current layout.
 */
public class EdenClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HUD_SCALE = BUILDER
            .comment("Global scale of the in-raid HUD (0.5 - 2.0). 1.0 = default layout.")
            .defineInRange("hudScale", 1.0, 0.5, 2.0);

    public static final ModConfigSpec.IntValue HUD_MARGIN_X = BUILDER
            .comment("Horizontal margin (px, unscaled) of the raid HUD from the screen edge.")
            .defineInRange("hudMarginX", 8, 0, 512);

    public static final ModConfigSpec.IntValue HUD_MARGIN_Y = BUILDER
            .comment("Vertical margin (px, unscaled) of the raid HUD from the screen edge.")
            .defineInRange("hudMarginY", 8, 0, 512);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
