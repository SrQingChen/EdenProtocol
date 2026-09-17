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

    private final ChroniclePayload data;

    public ChronicleScreen(ChroniclePayload data) {
        super(Component.translatable("eden.chronicle.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        if (data.advanceReady()) {
            this.addRenderableWidget(Button.builder(advanceButtonLabel(), b -> {
                        ClientPacketDistributor.sendToServer(new AdvanceVotePayload());
                        this.onClose();   // re-open the wall to see the fresh vote state
                    })
                    .pos(this.width / 2 - 110, this.height - 26).size(140, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.translatable("eden.launchpad.close"), b -> this.onClose())
                .pos(this.width / 2 + 40, this.height - 26).size(80, 20).build());
    }

    private Component advanceButtonLabel() {
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
        int cx = this.width / 2;
        int y = 10;
        graphics.centeredText(this.font, this.title, cx, y, 0xFFFFFFFF);
        y += 14;

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
                data.seasonIndex(), data.contractsDone(), data.contractsTotal()).getString();
        graphics.centeredText(this.font, season, cx, y, 0xFF9AD8FF);
        y += 12;
        if (data.voteActive()) {
            graphics.centeredText(this.font, Component.translatable("eden.season.vote.status",
                    data.voteYes(), data.voteNeed()), cx, y, 0xFFFFD966);
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
}
