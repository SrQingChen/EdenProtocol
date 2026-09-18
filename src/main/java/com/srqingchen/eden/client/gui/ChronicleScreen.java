package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.network.AdvanceVotePayload;
import com.srqingchen.eden.network.ChroniclePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The chronicle wall / campaign panel screen (§13/§14, self-made per the v1 decision): the Duststar
 * gauge, stage, tallies, objectives ("what lowers the gauge next") and the highlight reel, drawn as a
 * code-only board. The wall block pushes a {@link ChroniclePayload}; the screen is a static picture of
 * it (re-open to refresh).
 * <p>Season block (S1 批A): a season/contracts status line, and once >= 3/5 contracts are banked the
 *「推进赛季」button - a server-wide majority vote (first click opens a 30s window, every click counts,
 * more than half of the online players advances the season).
 */
@OnlyIn(Dist.CLIENT)
public class ChronicleScreen extends Screen {

    private static final int PAGE_CAMPAIGN = 0, PAGE_RESERVE = 1;

    private final ChroniclePayload data;
    private int page = PAGE_CAMPAIGN;

    public ChronicleScreen(ChroniclePayload data) {
        super(Component.translatable("eden.chronicle.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(pageTabLabel(PAGE_CAMPAIGN), b -> switchPage(PAGE_CAMPAIGN))
                .pos(this.width / 2 - 124, 22).size(120, 16).build());
        this.addRenderableWidget(Button.builder(pageTabLabel(PAGE_RESERVE), b -> switchPage(PAGE_RESERVE))
                .pos(this.width / 2 + 4, 22).size(120, 16).build());
        if (data.finaleUnlocked() && !data.seasonCompleted()) {
            // Gate already open (3/5 passed the vote): the button is a direct arena entrance,
            // for the first gathering and every re-entry after a wipe.
            this.addRenderableWidget(Button.builder(
                            Component.translatable("eden.finale.enter_button"), b -> {
                        ClientPacketDistributor.sendToServer(new AdvanceVotePayload());
                        this.onClose();
                    })
                    .pos(this.width / 2 - 110, this.height - 26).size(140, 20).build());
        } else if (data.advanceReady()) {
            this.addRenderableWidget(Button.builder(advanceButtonLabel(), b -> {
                        ClientPacketDistributor.sendToServer(new AdvanceVotePayload());
                        this.onClose();   // re-open the wall to see the fresh vote state
                    })
                    .pos(this.width / 2 - 110, this.height - 26).size(140, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.translatable("eden.launchpad.close"), b -> this.onClose())
                .pos(this.width / 2 + 40, this.height - 26).size(80, 20).build());
    }

    private Component pageTabLabel(int which) {
        return Component.literal((page == which ? "▶ " : "   ")
                + Component.translatable(which == PAGE_CAMPAIGN
                        ? "eden.chronicle.tab_campaign" : "eden.chronicle.tab_reserve").getString());
    }

    private void switchPage(int which) {
        this.page = which;
        rebuildWidgets();
    }

    private Component advanceButtonLabel() {
        if (data.seasonCompleted()) {
            return data.voteActive()
                    ? Component.translatable("eden.season.rerun.button_active", data.voteYes(), data.voteNeed())
                    : Component.translatable("eden.season.rerun.button");
        }
        return data.voteActive()
                ? Component.translatable("eden.season.vote.button_active", data.voteYes(), data.voteNeed())
                : Component.translatable("eden.season.vote.button");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.page == PAGE_RESERVE) {
            renderReserve(graphics);
            return;
        }
        int cx = this.width / 2;
        int y = 40;
        graphics.centeredText(this.font, this.title, cx, 8, 0xFFFFFFFF);

        // The pollution gauge (inverted: full bar = clean world).
        int barW = 260;
        int barX = cx - barW / 2;
        float clean = 1.0f - data.pollution() / 100.0f;
        graphics.fill(barX, y, barX + barW, y + 12, 0xAA000000);
        int cleanW = (int) ((barW - 2) * clean);
        if (cleanW > 0) {
            graphics.fill(barX + 1, y + 1, barX + 1 + cleanW, y + 11, 0xFF7FE0A8);
        }
        graphics.outline(barX, y, barW, 12, 0xFF666666);
        String gauge = Component.translatable("eden.chronicle.gauge", data.pollution(), data.stage()).getString();
        graphics.centeredText(this.font, gauge, cx, y + 15, data.stage() == 0 ? 0xFF7FE0A8 : 0xFFDD88FF);
        y += 32;

        // Tallies.
        String stats = Component.translatable("eden.chronicle.stats", data.totalRaids(),
                data.successfulExtracts(), data.coresDestroyed(), data.dragonsSlain()).getString();
        graphics.centeredText(this.font, stats, cx, y, 0xFFAAAAAA);
        y += 12;
        String supply = Component.translatable("eden.chronicle.supply", data.supplyPoints()).getString();
        graphics.centeredText(this.font, supply, cx, y, 0xFF88FF88);
        y += 16;

        // Current objective line: what the crew should be doing at this stage.
        String objectiveKey = data.stage() == 0 ? "eden.chronicle.objective.victory"
                : "eden.chronicle.objective." + data.stage();
        graphics.centeredText(this.font, Component.translatable(objectiveKey), cx, y, 0xFFFFD966);
        y += 16;

        // Season status (S1): season index + contracts done/total; vote progress while a window is open.
        String season = Component.translatable("eden.season.chronicle",
                Component.translatable(data.seasonTitleKey()),
                data.contractsDone(), data.contractsTotal()).getString();
        graphics.centeredText(this.font, season, cx, y, 0xFF9AD8FF);
        y += 12;
        if (data.voteActive()) {
            graphics.centeredText(this.font, Component.translatable("eden.season.vote.status",
                    data.voteYes(), data.voteNeed()), cx, y, 0xFFFFD966);
            y += 12;
        }
        if (data.seasonCompleted()) {
            graphics.centeredText(this.font, Component.translatable("eden.finale.done_line"), cx, y, 0xFF7FE0A8);
            y += 12;
        }
        if (!data.endingsSeen().isEmpty()) {
            StringBuilder gallery = new StringBuilder();
            for (String e : data.endingsSeen()) {
                if (!gallery.isEmpty()) {
                    gallery.append("  ");
                }
                gallery.append("\u2713 ").append(Component.translatable(
                        "eden.finale.end." + e + ".title").getString());
            }
            graphics.centeredText(this.font, Component.translatable("eden.finale.gallery",
                    gallery.toString()), cx, y, 0xFFFFD24A);
            y += 12;
        }

        if (data.stage() != 0) {
            String unlockHint = Component.translatable("eden.chronicle.next_unlock",
                    Math.max(1, data.stage() + 1)).getString();
            graphics.centeredText(this.font, unlockHint, cx, y, 0xFF888888);
            y += 16;
        }

        // Highlight reel.
        graphics.centeredText(this.font, Component.translatable("eden.chronicle.highlights"), cx, y, 0xFFAA66FF);
        y += 12;
        int n = Math.min(data.highlightKeys().size(), data.highlightPlayers().size());
        for (int i = 0; i < n && y < this.height - 34; i++) {
            Component line = Component.translatable(data.highlightKeys().get(i),
                    data.highlightPlayers().get(i), data.highlightValues().get(i));
            graphics.centeredText(this.font, line, cx, y, 0xFFCCCCCC);
            y += 11;
        }
    }

    /**
     * 「协议余量」 page (S1 批C): three UNLABELLED bars, the raw numbers, a dark star - and nothing
     * else. Any tooltip that explained them would defeat the page.
     */
    private void renderReserve(GuiGraphicsExtractor graphics) {
        int cx = this.width / 2;
        graphics.centeredText(this.font, this.title, cx, 8, 0xFFFFFFFF);
        graphics.centeredText(this.font, "\u2726", cx, 42, 0xFF5A4A7A);   // the dark star itself
        int barW = 200;
        int barX = cx - barW / 2;
        int y = 60;
        for (float v : new float[]{data.meterPurity(), data.meterSymbiosis(), data.meterArchive()}) {
            graphics.fill(barX, y, barX + barW, y + 10, 0xAA000000);
            int w = (int) ((barW - 2) * Math.max(0f, Math.min(100f, v)) / 100f);
            if (w > 0) {
                graphics.fill(barX + 1, y + 1, barX + 1 + w, y + 9, 0xFF6E5A9E);
            }
            graphics.outline(barX, y, barW, 10, 0xFF44445A);
            graphics.text(this.font, String.valueOf((int) v), barX + barW + 8, y + 1, 0xFF9A8AC0);
            y += 18;
        }
        // The one number that IS explained: how many of the drafters' pages the crew has found.
        String pages = Component.translatable("eden.chronicle.pages",
                data.pagesPurity() + data.pagesSymbiosis() + data.pagesArchive(),
                data.pagesPurity(), data.pagesSymbiosis(), data.pagesArchive()).getString();
        graphics.centeredText(this.font, pages, cx, y + 8, 0xFF7A7A98);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
