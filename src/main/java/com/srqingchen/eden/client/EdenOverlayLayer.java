package com.srqingchen.eden.client;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * In-raid screen overlays (§16 屏幕叠加), drawn as translucent washes ON TOP of everything:
 * <ul>
 *   <li><b>Erosion vignette</b> - the screen edges stain purple as the erosion level climbs; at high
 *       levels a heartbeat thuds (client-local sound, slow then quick).</li>
 *   <li><b>Low-health pulse</b> - below 6 HP a red vignette breathes with a vanilla-hurt cadence.</li>
 * </ul>
 * Pure ambience: no gameplay effect, everything reads the synced {@link ClientRaidData}.
 */
public class EdenOverlayLayer implements GuiLayer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "raid_overlay");

    /** Edge thickness of the vignettes, in scaled pixels. */
    private static final int EDGE = 26;
    /** Heartbeat cadence in ticks per beat, per erosion level (level 3+ starts the pulse). */
    private static final int[] BEAT_INTERVAL = {40, 40, 34, 26, 20, 16, 12, 10, 8, 8, 6};
    private static long nextBeatTick = -1L;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientRaidData.inRaid) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;

        // Erosion vignette: alpha ramps with the erosion LEVEL (not the raw gauge) so it reads in stages.
        int level = ClientRaidData.erosionLevel;
        if (level > 0) {
            int alpha = Math.min(0x66, 0x14 * level);
            drawVignette(g, w, h, (alpha << 24) | 0x6A0DAD);
            heartbeat(mc, level, now);
        }

        // Low-health pulse: red breath below 6 HP.
        float health = mc.player.getHealth();
        if (health <= 6.0f && mc.player.isAlive()) {
            float phase = (now % 20L) / 20f;                    // one breath per second
            float breath = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
            int alpha = (int) (0x30 + 0x50 * breath * (1.0 - health / 6.0f));
            drawVignette(g, w, h, (alpha << 24) | 0xFF2B2B);
        }
    }

    private static void drawVignette(GuiGraphicsExtractor g, int w, int h, int color) {
        g.fill(0, 0, w, EDGE, color);
        g.fill(0, h - EDGE, w, h, color);
        g.fill(0, EDGE, EDGE, h - EDGE, color);
        g.fill(w - EDGE, EDGE, w, h - EDGE, color);
    }

    /** The erosion heartbeat: a quiet warden-thud whose cadence quickens with the level. */
    private static void heartbeat(Minecraft mc, int level, long now) {
        if (level < 3 || mc.level == null) {
            nextBeatTick = -1L;
            return;
        }
        if (nextBeatTick < 0L) {
            nextBeatTick = now + 40L;
            return;
        }
        if (now < nextBeatTick) {
            return;
        }
        int idx = Math.min(BEAT_INTERVAL.length - 1, level);
        nextBeatTick = now + BEAT_INTERVAL[idx];
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.55f, 1.0f, false);
    }
}
