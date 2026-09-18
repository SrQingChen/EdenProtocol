package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.season.SeasonFinale;
import com.srqingchen.eden.season.Seasons;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * The season-finale ceremony (批 D): a staged, server-driven text + sound performance right after
 * the apex boss falls — the three drafters state their case (each citing how many of THEIR pages
 * the crew actually found), the AI reads out the silent thermometer, the ending is adjudicated and
 * revealed, the optional ending video slot rolls, and everyone is pulled home to the ark. The
 * charred page close-up always closes the show: its rune glows — the next season's seed, never
 * explained.
 * <p><b>Endings (4, per design)</b>: three normal endings led by whichever thermometer value the
 * crew's invisible conduct favoured (净土 / 共生 / 存档) plus the hidden 三重奏 (all 18 pages found
 * AND all three values ≥ 60 — the drafters speak together).
 */
public final class FinaleCeremony {
    private FinaleCeremony() {}

    /** One adjudicated ending. */
    public record FinaleEnding(String id, String titleKey) {}

    public static final FinaleEnding PURITY = new FinaleEnding("purity", "eden.finale.end.purity.title");
    public static final FinaleEnding SYMBIOSIS = new FinaleEnding("symbiosis", "eden.finale.end.symbiosis.title");
    public static final FinaleEnding ARCHIVE = new FinaleEnding("archive", "eden.finale.end.archive.title");
    public static final FinaleEnding TRIO = new FinaleEnding("trio", "eden.finale.end.trio.title");

    /** Seconds between ceremony beats. */
    private static final int BEAT_TICKS = 4 * 20;
    private static final int BEATS = 7;

    private static int beat = -1;
    private static FinaleEnding ending = PURITY;

    /** Adjudicate the ending from the whole season's silent record (thermometer + pages). */
    public static FinaleEnding pickEnding(MinecraftServer server) {
        CampaignData data = CampaignData.get(server);
        boolean allPages = data.pagesFound("purity") >= 6
                && data.pagesFound("symbiosis") >= 6
                && data.pagesFound("archive") >= 6;
        if (allPages
                && data.protocolPurity() >= 60.0f
                && data.protocolSymbiosis() >= 60.0f
                && data.protocolArchive() >= 60.0f) {
            return TRIO;
        }
        float p = data.protocolPurity(), s = data.protocolSymbiosis(), a = data.protocolArchive();
        if (p >= s && p >= a) {
            return PURITY;
        }
        return s >= a ? SYMBIOSIS : ARCHIVE;
    }

    /** Begin the ceremony (called by {@link FinaleSystem} the moment the boss falls). */
    public static void play(MinecraftServer server, net.minecraft.server.level.ServerPlayer hero) {
        ending = pickEnding(server);
        beat = 0;
        String name = hero != null ? hero.getName().getString() : "?";
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, "eden.finale.ceremony.start", name);
        }
        boom(server);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (beat < 0 || server.getTickCount() % BEAT_TICKS != 0) {
            return;
        }
        int step = beat++;
        CampaignData data = CampaignData.get(server);
        switch (step) {
            case 0 -> say(server, "eden.finale.su.speech", data.pagesFound("purity"));
            case 1 -> say(server, "eden.finale.rong.speech", data.pagesFound("symbiosis"));
            case 2 -> say(server, "eden.finale.ming.speech", data.pagesFound("archive"));
            case 3 -> {
                say(server, "eden.finale.ai.reading",
                        (int) data.protocolPurity(), (int) data.protocolSymbiosis(), (int) data.protocolArchive());
                boom(server);
            }
            case 4 -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    EdenMessages.send(p, Type.SPECIAL, "eden.finale.end.reveal",
                            net.minecraft.network.chat.Component.translatable(ending.titleKey()));
                    EdenMessages.send(p, Type.INFO, "eden.finale.end." + ending.id() + ".desc");
                }
                boom(server);
            }
            case 5 -> {
                // Optional ending video: rolls when the asset exists, silently skipped otherwise.
                var season = Seasons.current(server);
                if (season != null && season.finale() != null) {
                    String slot = season.finale().videoSlot();
                    CinematicSystem.playToGroup(server, p -> !p.isSpectator(),
                            Identifier.fromNamespaceAndPath("eden", slot),
                            com.srqingchen.eden.registry.EdenSounds.CINEMATIC_SEASON.getId());
                }
                // The charred page, last frame of every season: the rune glows, nothing more.
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    EdenMessages.send(p, Type.SPECIAL, "eden.finale.charred_page");
                }
            }
            default -> close(server);
        }
    }

    private static void say(MinecraftServer server, String key, Object... args) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, key, args);
        }
    }

    private static void boom(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // Per-player instance at their own position: everyone hears their own cue, wherever they stand.
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.2f, 0.8f);
        }
    }

    /** Everyone goes home; the chronicle gains the ending gallery + the re-run (multi-cycle) button. */
    private static void close(MinecraftServer server) {
        beat = -1;
        ServerLevel ark = server.getLevel(EdenDimensions.ARK);
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            if (ark != null) {
                p.teleportTo(ark, 0.5, 66.0, 0.5, java.util.Set.of(), 0.0f, 0.0f, true);
            }
            EdenMessages.send(p, Type.SUCCESS, "eden.finale.completed");
            EdenMessages.send(p, Type.INFO, "eden.finale.rerun_hint");
        }
    }
}
