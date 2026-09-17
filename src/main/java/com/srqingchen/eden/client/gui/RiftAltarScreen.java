package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.block.RiftAltarMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 裂隙祭坛 screen (v2, vanilla-style): a plain {@link AbstractContainerScreen} over
 * {@link RiftAltarMenu}. Vanilla renders the slots, items, carried stack, quick-craft and tooltips;
 * this class only paints the panel background, the two recipe labels and the cost hint. All the
 * interaction problems of the old hand-hit-tested screen are gone by construction.
 */
@OnlyIn(Dist.CLIENT)
public class RiftAltarScreen extends AbstractContainerScreen<RiftAltarMenu> {

    private static final int PANEL = 0xFF1A1A26, PANEL_EDGE = 0xFF4A4A5E;

    public RiftAltarScreen(RiftAltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 196);
        this.titleLabelX = 8;
        this.inventoryLabelY = 96;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        // Recipe panels: fuse row + ascend row.
        for (int ry : new int[]{16, 48}) {
            g.fill(x + 12, y + ry, x + this.imageWidth - 12, y + ry + 26, 0xFF14141E);
            g.fill(x + 12, y + ry, x + this.imageWidth - 12, y + ry + 1, PANEL_EDGE);
            g.fill(x + 12, y + ry + 25, x + this.imageWidth - 12, y + ry + 26, PANEL_EDGE);
            g.fill(x + 12, y + ry, x + 13, y + ry + 26, PANEL_EDGE);
            g.fill(x + this.imageWidth - 13, y + ry, x + this.imageWidth - 12, y + ry + 26, PANEL_EDGE);
        }
        // Recipe arrows.
        arrow(g, x + 84, y + 24);
        arrow(g, x + 84, y + 56);
        g.text(this.font, "+", x + 46, y + 61, 0xFF8A8A9A);
    }

    private void arrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 8, x + 18, y + 10, 0xFF5A5A6E);
        g.fill(x + 13, y + 5, x + 15, y + 8, 0xFF5A5A6E);
        g.fill(x + 13, y + 10, x + 15, y + 13, 0xFF5A5A6E);
        g.fill(x + 15, y + 6, x + 17, y + 7, 0xFF5A5A6E);
        g.fill(x + 15, y + 11, x + 17, y + 12, 0xFF5A5A6E);
        g.fill(x + 17, y + 7, x + 18, y + 8, 0xFF5A5A6E);
        g.fill(x + 17, y + 10, x + 18, y + 11, 0xFF5A5A6E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // super drew slots+items; labels go on top.
        g.text(this.font, this.title, this.leftPos + this.titleLabelX, this.topPos + this.titleLabelY, 0xFFFFFFFF);
        g.text(this.font, Component.translatable("eden.forge.fuse.title"),
                this.leftPos + 14, this.topPos + 6, 0xFFFFD24A);
        g.text(this.font, Component.translatable("eden.forge.ascend.title", RiftAltarMenu.ascendCost()),
                this.leftPos + 14, this.topPos + 38, 0xFFB46BC9);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
