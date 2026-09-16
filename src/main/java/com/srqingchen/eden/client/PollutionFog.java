package com.srqingchen.eden.client;

import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Light pollution fog inside the raid world (client-only, game bus). Two independent hooks, both gated on
 * {@link ClientRaidData#inRaid} and applied only for {@link FogType#NONE} (open air - water / lava / powder
 * snow keep their vanilla fog): {@link ViewportEvent.RenderFog} pulls the fog planes slightly closer to dent
 * visibility, and {@link ViewportEvent.ComputeFogColor} blends a dim wash of the per-difficulty particle
 * colour over the vanilla fog colour. Deliberately subtle: no screen distortion, no vignette.
 */
public final class PollutionFog {
    private PollutionFog() {}

    /** Blend weight of the pollution tint over the vanilla fog colour (light touch). */
    private static final float COLOR_BLEND = 0.20f;
    /** Fog is a wide background wash, so keep it well darker than the bright particle motes. */
    private static final float COLOR_DARKEN = 0.45f;
    /** Slight pull-in of the far / near fog planes to reduce visibility without blinding the player. */
    private static final float FAR_SCALE = 0.82f;
    private static final float NEAR_SCALE = 0.70f;
    /** Dense-fog affix (revealed): the planes fold in much closer - a genuinely claustrophobic raid. */
    private static final float DENSE_FAR_SCALE = 0.45f;
    private static final float DENSE_NEAR_SCALE = 0.40f;
    private static final float DENSE_COLOR_BLEND = 0.38f;

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!ClientRaidData.inRaid || event.getType() != FogType.NONE) {
            return;
        }
        if (ClientRaidData.hasAffix("dense_fog")) {
            event.scaleFarPlaneDistance(DENSE_FAR_SCALE);
            event.scaleNearPlaneDistance(DENSE_NEAR_SCALE);
            return;
        }
        event.scaleFarPlaneDistance(FAR_SCALE);
        event.scaleNearPlaneDistance(NEAR_SCALE);
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientRaidData.inRaid || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        int c = ClientRaidData.particleColor;
        float pr = (((c >> 16) & 0xFF) / 255.0f) * COLOR_DARKEN;
        float pg = (((c >> 8) & 0xFF) / 255.0f) * COLOR_DARKEN;
        float pb = ((c & 0xFF) / 255.0f) * COLOR_DARKEN;
        float b = ClientRaidData.hasAffix("dense_fog") ? DENSE_COLOR_BLEND : COLOR_BLEND;
        event.setRed(event.getRed() * (1.0f - b) + pr * b);
        event.setGreen(event.getGreen() * (1.0f - b) + pg * b);
        event.setBlue(event.getBlue() * (1.0f - b) + pb * b);
    }
}
