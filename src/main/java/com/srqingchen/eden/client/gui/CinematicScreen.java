package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.client.cinematic.CinematicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Fullscreen cinematic overlay (v3 沉浸模块). Blocks all input; hold SPACE for ~0.8s to skip (a
 * filling bar shows the hold), auto-closes when the video ends. Videos are optional assets - if the
 * file is absent the screen never opens, so wiring a trigger anywhere is always safe.
 */
@OnlyIn(Dist.CLIENT)
public class CinematicScreen extends Screen {

    /** Long-press window in render ticks (20 tps on the screen ticker = 0.8s). */
    private static final int SKIP_HOLD_TICKS = 16;
    private final CinematicPlayer player;
    private int spaceHeld = 0;

    private CinematicScreen(CinematicPlayer player) {
        super(Component.translatable("eden.cinematic.title"));
        this.player = player;
    }

    /** Open for a video id; silently does nothing when the asset is missing. */
    public static void open(Identifier video, Identifier sound) {
        CinematicPlayer p = CinematicPlayer.play(video, sound);
        if (p != null) {
            Minecraft.getInstance().setScreen(new CinematicScreen(p));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.player.isFinished()) {
            this.onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.player.render(g, this.width, this.height);
        int cx = this.width / 2, y = this.height - 26;
        g.centeredText(this.font, Component.translatable("eden.cinematic.skip").getString(),
                cx, y, 0xB8FFFFFF);
        // hold-to-skip progress bar
        int half = 8;
        int filled = Math.round(half * 2 * Math.min(1f, this.spaceHeld / (float) SKIP_HOLD_TICKS));
        g.fill(cx - half, y - 22, cx + half, y - 21, 0x55FFFFFF);
        if (filled > 0) {
            g.fill(cx - half, y - 22, cx - half + filled, y - 21, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 32) {   // SPACE
            this.spaceHeld++;
            if (this.spaceHeld >= SKIP_HOLD_TICKS) {
                this.onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        this.player.stop();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
