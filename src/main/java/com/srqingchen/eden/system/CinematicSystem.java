package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.network.PlayCinematicPayload;
import com.srqingchen.eden.registry.EdenSounds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Predicate;

/**
 * Event-triggered cinematics (v3 沉浸模块): a tiny server-side dispatcher that sends
 * {@link PlayCinematicPayload} to a filtered player group. Videos/audio are OPTIONAL assets -
 * dropping {@code intro.ecine + intro.ogg} into assets makes the intro live; nothing ships by
 * default and the client no-ops on missing files, so triggers can be wired anywhere safely.
 * <p>Built-in triggers: first join per world (intro), first raid-dimension entry per world
 * (raid_enter). Season-change is a public API ({@link #playSeasonChange}) ready for the coming
 * season system to call. Config: {@code cinematicsEnabled} master switch.
 */
public final class CinematicSystem {
    private CinematicSystem() {}

    private static final Identifier VIDEO_INTRO = Identifier.fromNamespaceAndPath("eden", "intro");
    private static final Identifier VIDEO_RAID = Identifier.fromNamespaceAndPath("eden", "raid_enter");
    private static final Identifier VIDEO_SEASON = Identifier.fromNamespaceAndPath("eden", "season_change");

    /** ~2.5s after first join, once the world has streamed in, the intro rolls. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.isSpectator()) {
            return;
        }
        if (!EdenConfig.CINEMATICS_ENABLED.get()
                || sp.getPersistentData().getBooleanOr("eden_cine_intro", false)) {
            return;
        }
        sp.getPersistentData().putBoolean("eden_cine_intro", true);
        schedule(sp.level().getServer(), 50, () -> playTo(sp, VIDEO_INTRO, EdenSounds.CINEMATIC_INTRO.getId()));
    }

    /** First entry into the raid overworld per world: the drop-in cinematic. */
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!EdenConfig.CINEMATICS_ENABLED.get() || !event.getTo().equals(EdenDimensions.RAID_OVERWORLD)) {
            return;
        }
        if (sp.getPersistentData().getBooleanOr("eden_cine_raid", false)) {
            return;
        }
        sp.getPersistentData().putBoolean("eden_cine_raid", true);
        schedule(sp.level().getServer(), 30, () -> playTo(sp, VIDEO_RAID, EdenSounds.CINEMATIC_RAID.getId()));
    }

    /** Season rollover cinematic for every logged-in player (called by the future season system). */
    public static void playSeasonChange(MinecraftServer server) {
        if (!EdenConfig.CINEMATICS_ENABLED.get()) {
            return;
        }
        playToGroup(server, p -> !p.isSpectator(), VIDEO_SEASON, EdenSounds.CINEMATIC_SEASON.getId());
    }

    /** Public API: play a video to every player matching the filter (videos are optional assets). */
    public static void playToGroup(MinecraftServer server, Predicate<ServerPlayer> filter,
                                   Identifier video, Identifier sound) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (filter.test(sp)) {
                playTo(sp, video, sound);
            }
        }
    }

    private static void playTo(ServerPlayer sp, Identifier video, Identifier sound) {
        PacketDistributor.sendToPlayer(sp, new PlayCinematicPayload(video, sound));
    }

    private static void schedule(MinecraftServer server, int delayTicks, Runnable task) {
        new Thread(() -> {
            try {
                Thread.sleep(delayTicks * 50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            server.execute(task);
        }, "Eden-Cinematic-Dispatch").start();
    }
}
