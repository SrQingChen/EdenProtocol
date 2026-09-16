package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.util.EdenMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Periodic "你知道吗 / did you know" guidance tips in chat (onboarding clarity, 功能清单 §1 引导):
 * <ul>
 *   <li>Every few minutes (config {@code tipsIntervalMinutes}, jittered ±40% so it never feels
 *       metronomic) one random tip is announced to everyone who is actually playing.</li>
 *   <li>Normal tips get a random pastel body colour per send.</li>
 *   <li>Tips marked {@link Tip#important} (keys, erosion, extraction...) are bold and their colour
 *       CYCLES through the hue wheel send after send - chat lines cannot animate once rendered, so
 *       the "dynamic colour" reads as an ever-shifting rainbow across appearances.</li>
 *   <li>Each player also gets ONE random starter tip ~40s after their first login of the session,
 *       when they are actually looking at the screen.</li>
 * </ul>
 * Tip bodies live in the lang files ({@code eden.tip.1} ... {@code eden.tip.N}); adding content is
 * purely a translation-file change plus one entry here.
 */
public final class TipsSystem {
    private TipsSystem() {}

    /** One guidance tip: its lang key and whether it is a "key mechanic" worth rainbow-flagging. */
    private record Tip(String key, boolean important) {}

    private static final List<Tip> TIPS = List.of(
            new Tip("eden.tip.keys", true),          // R/Tab/J key bindings
            new Tip("eden.tip.erosion", true),       // erosion clock pressure
            new Tip("eden.tip.extraction", true),    // register + charge the return pod
            new Tip("eden.tip.purifier", false),     // purifier item
            new Tip("eden.tip.market", false),       // daily price fluctuation
            new Tip("eden.tip.market_lock", false),  // market frozen while a crew raids
            new Tip("eden.tip.scanner", false),      // scanner reveals hidden affixes
            new Tip("eden.tip.cards", true),         // curios card slots + curse actives
            new Tip("eden.tip.talents", false),      // J opens the talent map
            new Tip("eden.tip.convert", false),      // supply -> talent points
            new Tip("eden.tip.oasis", false),        // hope oasis
            new Tip("eden.tip.core", false),         // pollution cores -2%
            new Tip("eden.tip.dragon", false),       // tainted dragon bounty
            new Tip("eden.tip.revive", false),       // cooperative revive
            new Tip("eden.tip.insurance", false),    // attrition policy
            new Tip("eden.tip.perfect", false),      // flawless / speed bonus
            new Tip("eden.tip.solo", false),         // solo compensation
            new Tip("eden.tip.locker", false),       // personal locker
            new Tip("eden.tip.chronicle", false),    // chronicle wall campaign panel
            new Tip("eden.tip.spectate", false),     // /eden spectate
            new Tip("eden.tip.difficulty", false),   // difficulty tiers + unlock gates
            new Tip("eden.tip.paradise", false),     // paradise victory
            new Tip("eden.tip.greed", false),        // curse cards: power at a price
            new Tip("eden.tip.crew", false)          // crew scaling
    );

    /** Curated pastel-ish palette for normal tips (readable on the dark chat background). */
    private static final int[] PASTELS = {
            0xFF9AD8FF, 0xFFA8E6CF, 0xFFFFD3B6, 0xFFC9B6FF, 0xFFFFB7D5,
            0xFFB8E0FF, 0xFFD4F0C0, 0xFFE8C9A0, 0xFF9FE8DF, 0xFFF0C9E8
    };

    private static final RandomSource RANDOM = RandomSource.create();

    /** Server tick when the next global tip fires (set on first tick, then rescheduled). */
    private static long nextTipAt = -1;
    /** Hue phase for important tips - advanced on every important send, so colours drift. */
    private static float rainbowHue = 0f;
    /** Players who already got their per-session starter tip. */
    private static final Set<UUID> greeted = new HashSet<>();
    /** Pending per-player starter tips: (uuid, dueTick) resolved from the login event. */
    private static final List<Object[]> pendingGreetings = new ArrayList<>();
    /** The last tip index sent globally, so consecutive repeats are avoided. */
    private static int lastIndex = -1;

    // ---------- schedule ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (!EdenConfig.TIPS_ENABLED.get()) {
            return;
        }
        if (nextTipAt < 0) {
            nextTipAt = now + firstDelay();
        }
        // per-session starter tips
        pendingGreetings.removeIf(entry -> {
            if (now < (long) entry[1]) {
                return false;
            }
            if (server.getPlayerList().getPlayer((UUID) entry[0]) instanceof ServerPlayer sp) {
                sp.sendSystemMessage(build(randomTip()));
            }
            return true;
        });
        if (now >= nextTipAt) {
            announce(server);
            nextTipAt = now + interval();
        }
    }

    /** Schedule ONE random starter tip shortly after a player's first login this session. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!EdenConfig.TIPS_ENABLED.get() || !(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (sp.isSpectator() || !greeted.add(sp.getUUID())) {
            return;
        }
        long due = sp.level().getServer().getTickCount() + 20L * 40;   // ~40s in
        pendingGreetings.add(new Object[]{sp.getUUID(), due});
    }

    /** Send one tip now to every actively playing player. */
    private static void announce(MinecraftServer server) {
        Tip tip = randomTip();
        MutableComponent msg = build(tip);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.isSpectator()) {
                sp.sendSystemMessage(msg);
            }
        }
    }

    private static Tip randomTip() {
        int i = RANDOM.nextInt(TIPS.size());
        if (i == lastIndex) {
            i = (i + 1) % TIPS.size();   // never the same tip twice in a row
        }
        lastIndex = i;
        return TIPS.get(i);
    }

    /** [净土协议] 你知道吗？ prefix + colour-coded body. */
    private static MutableComponent build(Tip tip) {
        MutableComponent tag;
        if (tip.important()) {
            tag = Component.literal("[你知道吗？！] ").withStyle(nextRainbow().withBold(true));
        } else {
            tag = Component.literal("[你知道吗？] ")
                    .withStyle(ChatFormatting.AQUA).withStyle(ChatFormatting.BOLD);
        }
        MutableComponent body = Component.translatable(tip.key());
        if (tip.important()) {
            body.withStyle(nextRainbow().withBold(true));
        } else {
            body.withStyle(Style.EMPTY.withColor(PASTELS[RANDOM.nextInt(PASTELS.length)]));
        }
        return tag.append(body);
    }

    /** Walk the hue wheel a step per important send (chat cannot animate, so this reads as dynamic). */
    private static Style nextRainbow() {
        rainbowHue = (rainbowHue + 0.19f) % 1f;
        // Full-saturation rainbow is harsh on the eyes; keep saturation/value temperate.
        return Style.EMPTY.withColor(TextColor.fromRgb(0xFF000000 | Mth.hsvToRgb(rainbowHue, 0.72f, 1.0f)));
    }

    // ---------- timing ----------

    private static long firstDelay() {
        return 20L * 60 * 3;   // first global tip 3 minutes after boot
    }

    /** interval ±40% jitter, in ticks. */
    private static long interval() {
        int minutes = EdenConfig.TIPS_INTERVAL_MINUTES.get();
        double jitter = 0.6 + RANDOM.nextDouble() * 0.8;   // [0.6, 1.4]
        return (long) (20L * 60 * minutes * jitter);
    }

    /** Server-stopping cleanup so a restart re-greets everyone once. */
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        greeted.clear();
        pendingGreetings.clear();
        nextTipAt = -1;
    }
}
