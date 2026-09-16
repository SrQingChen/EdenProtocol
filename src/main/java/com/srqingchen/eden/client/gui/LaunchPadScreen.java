package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.network.ChooseClassPayload;
import com.srqingchen.eden.network.LaunchRaidPayload;
import com.srqingchen.eden.network.OpenLaunchPadPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The launch pad screen (§3/§9/§15): pick difficulty, destination and class, then launch. Pure code
 * (vanilla buttons + a drawn header showing the campaign gauge); every unlock the server sent is
 * honoured visually and re-validated server-side on {@link LaunchRaidPayload}. Class buttons double as
 * the pad-side quick class switch (the talent screen's J map remains the deep editor).
 */
@OnlyIn(Dist.CLIENT)
public class LaunchPadScreen extends Screen {

    private final OpenLaunchPadPayload data;
    private int difficultyIndex = 0;
    private int dimensionIndex = 0;

    private static final String[] CLASS_IDS = {"engineer", "prospector", "medic", "vanguard", "scavenger"};

    public LaunchPadScreen(OpenLaunchPadPayload data) {
        super(Component.translatable("eden.launchpad.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        int cols = OpenLaunchPadPayload.DIFFICULTIES.size();
        int bw = 92;
        int gap = 4;
        int rowW = cols * bw + (cols - 1) * gap;
        int x0 = this.width / 2 - rowW / 2;
        int diffY = 52;
        for (int i = 0; i < cols; i++) {
            final int idx = i;
            boolean unlocked = data.difficultyUnlocked().get(i);
            this.addRenderableWidget(Button.builder(difficultyLabel(i, unlocked), b -> {
                        if (!unlocked) {
                            return;
                        }
                        difficultyIndex = idx;
                        relabel();
                    })
                    .pos(x0 + i * (bw + gap), diffY).size(bw, 20).build());
        }
        int dimY = 94;
        for (int i = 0; i < OpenLaunchPadPayload.DIMENSIONS.size(); i++) {
            final int idx = i;
            boolean unlocked = data.dimensionUnlocked().get(i);
            this.addRenderableWidget(Button.builder(dimensionLabel(i, unlocked), b -> {
                        if (!unlocked) {
                            return;
                        }
                        dimensionIndex = idx;
                        relabel();
                    })
                    .pos(x0 + i * (bw + gap), dimY).size(bw, 20).build());
        }
        // Class quick-switch row: current class highlighted; clicking switches immediately (free at the pad).
        int clsY = 136;
        for (int i = 0; i < CLASS_IDS.length; i++) {
            final String cls = CLASS_IDS[i];
            this.addRenderableWidget(Button.builder(classLabel(cls), b -> {
                        ClientPacketDistributor.sendToServer(new ChooseClassPayload(cls));
                        // Optimistic highlight; the server's authoritative sync follows via TalentSync.
                    })
                    .pos(x0 + i * (bw + gap), clsY).size(bw, 20).build());
        }
        int launchY = 170;
        this.addRenderableWidget(Button.builder(Component.translatable("eden.launchpad.launch"), b -> launch())
                .pos(this.width / 2 - 75, launchY).size(150, 22).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eden.launchpad.close"), b -> this.onClose())
                .pos(this.width / 2 - 40, this.height - 26).size(80, 20).build());
    }

    private Component difficultyLabel(int i, boolean unlocked) {
        String id = OpenLaunchPadPayload.DIFFICULTIES.get(i);
        String name = Component.translatable("eden.difficulty." + id).getString();
        if (!unlocked) {
            return Component.literal("🔒 " + name);
        }
        return difficultyIndex == i ? Component.literal("▶ " + name) : Component.literal(name);
    }

    private Component dimensionLabel(int i, boolean unlocked) {
        String id = OpenLaunchPadPayload.DIMENSIONS.get(i);
        String name = Component.translatable("eden.dimension." + id).getString();
        if (!unlocked) {
            return Component.literal("🔒 " + name);
        }
        return dimensionIndex == i ? Component.literal("▶ " + name) : Component.literal(name);
    }

    private Component classLabel(String cls) {
        String name = Component.translatable("eden.class." + cls).getString();
        return cls.equals(data.currentClass()) ? Component.literal("◆ " + name) : Component.literal(name);
    }

    private void relabel() {
        // Rebuild the whole widget layout so labels refresh (simple + rare).
        rebuildWidgets();
    }

    private void launch() {
        ClientPacketDistributor.sendToServer(new LaunchRaidPayload(
                OpenLaunchPadPayload.DIFFICULTIES.get(difficultyIndex),
                OpenLaunchPadPayload.DIMENSIONS.get(dimensionIndex)));
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        String gauge = Component.translatable("eden.launchpad.campaign", data.stage(), data.pollution()).getString();
        graphics.centeredText(this.font, gauge, this.width / 2, 22, 0xFFAA66FF);
        graphics.centeredText(this.font, Component.translatable("eden.launchpad.difficulty_row"),
                this.width / 2, 42, 0xFFAAAAAA);
        graphics.centeredText(this.font, Component.translatable("eden.launchpad.dimension_row"),
                this.width / 2, 84, 0xFFAAAAAA);
        graphics.centeredText(this.font, Component.translatable("eden.launchpad.class_row"),
                this.width / 2, 126, 0xFFAAAAAA);
        String hint = Component.translatable("eden.launchpad.class_hint").getString();
        graphics.centeredText(this.font, hint, this.width / 2, this.height - 48, 0xFF666666);
    }
}
