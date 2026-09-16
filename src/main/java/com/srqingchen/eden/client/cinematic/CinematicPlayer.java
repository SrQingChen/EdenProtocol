package com.srqingchen.eden.client.cinematic;

import com.mojang.blaze3d.platform.NativeImage;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays one packed .ecine cinematic (v3 沉浸模块): MJPEG frames decoded on a single background thread
 * with the JDK built-in ImageIO (no extra natives, no ffmpeg), ping-pong {@link NativeImage}s
 * uploaded to a {@link DynamicTexture} on the render tick, audio via a vanilla sound slot.
 * <p>Performance profile (design constraints): the video file is STREAMED (never fully loaded), at
 * most one frame is in flight plus two preallocated buffers, the texture is exactly video-sized and
 * both buffers close on stop - steady-state cost is one JPEG decode + one texture upload per frame.
 */
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class CinematicPlayer {

    private final Identifier textureId;
    private final DynamicTexture texture;
    private final NativeImage front, back;
    private final int width, height, fps, frameCount;
    private final long frameNanos;
    private final Thread decoder;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private SimpleSoundInstance sound;
    /** Highest frame index the decoder has painted; the render tick flips it onto the texture. */
    private volatile int decodedFrame = -1;
    private int shownFrame = -1;
    private long startNanos;
    private volatile boolean finished;

    private CinematicPlayer(Identifier video, int width, int height, int fps, int frameCount,
                            DataInputStream in) {
        this.textureId = Identifier.fromNamespaceAndPath(EdenProtocol.MODID,
                "cinematic/" + video.getPath());
        this.width = width;
        this.height = height;
        this.fps = Math.max(1, fps);
        this.frameCount = frameCount;
        this.frameNanos = 1_000_000_000L / this.fps;
        this.front = new NativeImage(width, height, true);
        this.back = new NativeImage(width, height, true);
        this.texture = new DynamicTexture(() -> "eden_cinematic", this.front);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        this.decoder = new Thread(() -> decodeLoop(in), "Eden-Cinematic");
        this.decoder.setDaemon(true);
        this.startNanos = System.nanoTime();
        this.decoder.start();
    }

    /** Open and start playing {@code video}; returns null when the asset is missing or malformed. */
    public static CinematicPlayer play(Identifier video, Identifier sound) {
        Resource resource;
        try {
            resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                    Identifier.fromNamespaceAndPath(video.getNamespace(),
                            "cinematics/" + video.getPath() + ".ecine"));
        } catch (IOException e) {
            EdenProtocol.LOGGER.debug("[Eden] cinematic {} unavailable", video);
            return null;
        }
        try (DataInputStream head = new DataInputStream(resource.open())) {
            byte[] magic = new byte[5];
            head.readFully(magic);
            if (!"ECIN1".equals(new String(magic))) {
                return null;
            }
            int w = head.readInt(), h = head.readInt(), fps = head.readInt(), frames = head.readInt();
            if (w <= 0 || h <= 0 || w > 1920 || h > 1920 || frames <= 0) {
                return null;
            }
            DataInputStream stream = new DataInputStream(resource.open());
            stream.skipBytes(5 + 16);
            CinematicPlayer player = new CinematicPlayer(video, w, h, fps, frames, stream);
            player.startSound(sound);
            return player;
        } catch (IOException e) {
            EdenProtocol.LOGGER.debug("[Eden] cinematic {} unreadable: {}", video, e.toString());
            return null;
        }
    }

    private void startSound(Identifier sound) {
        if (sound == null) {
            return;
        }
        net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(sound).ifPresent(event -> {
            this.sound = SimpleSoundInstance.forMusic(event.value());
            Minecraft.getInstance().getSoundManager().play(this.sound);
        });
    }

    private void decodeLoop(DataInputStream in) {
        try {
            for (int i = 0; i < frameCount && running.get(); i++) {
                // Pace to the wall clock so the decoder never races ahead of presentation.
                long target = (i + 1) * frameNanos - (System.nanoTime() - startNanos) - 2_000_000L;
                while (target > 0 && running.get()) {
                    Thread.sleep(Math.min(10, target / 1_000_000L + 1));
                    target = (i + 1) * frameNanos - (System.nanoTime() - startNanos) - 2_000_000L;
                }
                int len = in.readInt();
                byte[] jpeg = new byte[len];
                in.readFully(jpeg);
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
                if (img != null) {
                    paint(img);
                    decodedFrame = i;
                }
            }
        } catch (Exception e) {
            EdenProtocol.LOGGER.debug("[Eden] cinematic stream ended: {}", e.toString());
        } finally {
            finished = true;
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** ARGB BufferedImage to ABGR NativeImage, row-buffered, no per-pixel allocations. */
    private void paint(BufferedImage img) {
        int w = Math.min(width, img.getWidth()), h = Math.min(height, img.getHeight());
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRaster().getDataElements(0, y, w, 1, row);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
                back.setPixelABGR(x, y, abgr | 0xFF000000);
            }
        }
    }

    /** Render tick: flip freshly decoded frames, upload, draw letterboxed fullscreen. */
    public void render(GuiGraphicsExtractor g, int screenW, int screenH) {
        long due = (System.nanoTime() - startNanos) / frameNanos;
        while (shownFrame < decodedFrame && shownFrame < due) {
            shownFrame++;
        }
        if (shownFrame >= 0 && shownFrame == decodedFrame) {
            texture.setPixels(back);
            texture.upload();
        }
        float scale = Math.min(screenW / (float) width, screenH / (float) height);
        int dw = Math.round(width * scale), dh = Math.round(height * scale);
        int dx = (screenW - dw) / 2, dy = (screenH - dh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, textureId, dx, dy, 0.0F, 0.0F, dw, dh, width, height);
    }

    /** Presentation progress 0..1 (drives the auto-close). */
    public float progress() {
        long total = (long) frameCount * frameNanos;
        return Math.min(1f, (System.nanoTime() - startNanos) / (float) total);
    }

    public boolean isFinished() {
        return finished || progress() >= 1f;
    }

    public void stop() {
        running.set(false);
        try {
            decoder.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (sound != null) {
            sm.stop(sound);
        }
        Minecraft.getInstance().getTextureManager().release(textureId);
        front.close();
        back.close();
    }
}
