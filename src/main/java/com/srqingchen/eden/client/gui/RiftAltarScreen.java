package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CardQuality;
import com.srqingchen.eden.network.RiftForgePayload;
import com.srqingchen.eden.system.RiftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 裂隙祭坛 forge screen (v2), drawn entirely in code like the shop/talent screens. Two recipes on
 * top - FUSE (three same-quality cards) and ASCEND (one card + one boss material + supply) - and the
 * player's main inventory grid below. Click slots to stage them (staged slots are highlighted; the
 * recipe panel shows what is staged), then click FORGE to fire the server-bound request; the server
 * re-validates everything and answers with an overlay message.
 */
@OnlyIn(Dist.CLIENT)
public class RiftAltarScreen extends Screen {

    private static final int CELL = 18, GRID_COLS = 9, GRID_ROWS = 4;   // 36 = hotbar(0-8) + main(9-35)
    private static final int COL_BG = 0xF60E0E18, COL_PANEL = 0xFF1A1A24, COL_BORDER = 0xFF5A5A6E;
    private static final int COL_STAGED = 0xFF3FBF5F, COL_CARD = 0xFF4C8FD6, COL_MAT = 0xFFB46BC9;

    private final int supplyBalance;
    /** Staged slot indexes: fuse = [a,b,c], ascend = [card, material]. -1 = empty. */
    private int[] fuse = {-1, -1, -1};
    private int ascendCard = -1, ascendMat = -1;
    private final List<Integer> invCards = new ArrayList<>();
    private final List<Integer> invMats = new ArrayList<>();

    public RiftAltarScreen(int supplyBalance) {
        super(Component.translatable("eden.forge.title"));
        this.supplyBalance = supplyBalance;
    }

    @Override
    protected void init() {
        super.init();
        rescan();
    }

    private void rescan() {
        this.invCards.clear();
        this.invMats.clear();
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < Math.min(GRID_ROWS * GRID_COLS, inv.getContainerSize()); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CardItem) {
                this.invCards.add(i);
            } else if (RiftForge.isBossMaterial(s.getItem())) {
                this.invMats.add(i);
            }
        }
    }

    // ---------- layout ----------

    private int gridX() { return (this.width - GRID_COLS * CELL) / 2; }
    private int gridY() { return this.height - GRID_ROWS * CELL - 26; }
    private int panelY() { return 58; }
    private int panelH() { return gridY() - panelY() - 18; }
    private int fuseX() { return this.width / 2 - 220; }
    private int ascendX() { return this.width / 2 + 20; }
    private int panelW() { return 200; }

    // ---------- rendering ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF20B0B12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(this.font, this.title.getString(), 12, 6, 0xFFFFFFFF);
        String bal = Component.translatable("eden.forge.balance", this.supplyBalance).getString();
        g.text(this.font, bal, this.width - this.font.width(bal) - 12, 6, 0xFF88FF88);

        drawFusePanel(g, mouseX, mouseY);
        drawAscendPanel(g, mouseX, mouseY);
        drawInventory(g, mouseX, mouseY);

        g.text(this.font, Component.translatable("eden.forge.hint").getString(), 12,
                this.height - 14, 0xFF7A7A88);
    }

    private void drawFusePanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = fuseX(), y = panelY(), w = panelW(), h = panelH();
        g.fill(x, y, x + w, y + h, COL_PANEL);
        border(g, x, y, x + w, y + h, COL_BORDER);
        g.centeredText(this.font, Component.translatable("eden.forge.fuse.title").getString(),
                x + w / 2, y + 8, 0xFFFFD24A);
        g.text(this.font, fit(Component.translatable("eden.forge.fuse.desc").getString(), w - 12),
                x + 6, y + 22, 0xFFC8C8D2);
        // three staging slots + arrow + result
        int sy = y + 38;
        for (int i = 0; i < 3; i++) {
            int sx = x + 16 + i * (CELL + 8);
            boolean staged = this.fuse[i] >= 0;
            g.fill(sx, sy, sx + CELL, sy + CELL, staged ? 0x663FBF5F : 0xFF10101A);
            border(g, sx, sy, sx + CELL, sy + CELL, staged ? COL_STAGED : COL_BORDER);
            if (staged) {
                ItemStack s = stackAt(this.fuse[i]);
                g.item(s, sx + 1, sy + 1);
            }
        }
        int qx = x + 16 + 3 * (CELL + 8) + 10;
        hline(g, qx, qx + 18, sy + CELL / 2, 0xFF5A5A6E);
        g.text(this.font, ">", qx + 5, sy + CELL / 2 - 8, 0xFF5A5A6E);
        int rx = qx + 26;
        g.fill(rx, sy, rx + CELL + 4, sy + CELL, 0xFF10101A);
        border(g, rx, sy, rx + CELL + 4, sy + CELL, fuseReady() ? COL_STAGED : COL_BORDER);
        g.centeredText(this.font, "?", rx + (CELL + 4) / 2, sy + 5, 0xFF9A9AA6);
        // forge button
        boolean ready = fuseReady();
        int by = y + h - 26;
        g.fill(x + 16, by, x + w - 16, by + 18, ready ? 0xFF2E6B3F : 0xFF26262F);
        border(g, x + 16, by, x + w - 16, by + 18, ready ? COL_STAGED : COL_BORDER);
        g.centeredText(this.font, Component.translatable("eden.forge.fuse.button").getString(),
                (x * 2 + w) / 2, by + 5, ready ? 0xFFE8FFE8 : 0xFF6E6E7A);
    }

    private void drawAscendPanel(GuiGraphicsExtractor g, int mx, int my) {
        int x = ascendX(), y = panelY(), w = panelW(), h = panelH();
        g.fill(x, y, x + w, y + h, COL_PANEL);
        border(g, x, y, x + w, y + h, COL_BORDER);
        g.centeredText(this.font, Component.translatable("eden.forge.ascend.title").getString(),
                x + w / 2, y + 8, 0xFFB46BC9);
        g.text(this.font, fit(Component.translatable("eden.forge.ascend.desc", RiftForge.ASCEND_SUPPLY_COST)
                .getString(), w - 12), x + 6, y + 22, 0xFFC8C8D2);
        int sy = y + 38;
        // card + material + star
        g.fill(x + 16, sy, x + 16 + CELL, sy + CELL, this.ascendCard >= 0 ? 0x664C8FD6 : 0xFF10101A);
        border(g, x + 16, sy, x + 16 + CELL, sy + CELL, this.ascendCard >= 0 ? COL_CARD : COL_BORDER);
        if (this.ascendCard >= 0) {
            g.item(stackAt(this.ascendCard), x + 17, sy + 1);
        }
        g.text(this.font, "+", x + 16 + CELL + 6, sy + 5, 0xFF5A5A6E);
        int mmx = x + 16 + CELL + 20;
        g.fill(mmx, sy, mmx + CELL, sy + CELL, this.ascendMat >= 0 ? 0x66B46BC9 : 0xFF10101A);
        border(g, mmx, sy, mmx + CELL, sy + CELL, this.ascendMat >= 0 ? COL_MAT : COL_BORDER);
        if (this.ascendMat >= 0) {
            g.item(stackAt(this.ascendMat), mmx + 1, sy + 1);
        }
        String star = this.ascendCard >= 0
                ? "★".repeat(Math.max(1, CardItem.starOf(stackAt(this.ascendCard)))) + " → +1★"
                : "★";
        g.text(this.font, star, mmx + CELL + 10, sy + 5, 0xFFE8C97A);
        boolean ready = ascendReady();
        int by = y + h - 26;
        g.fill(x + 16, by, x + w - 16, by + 18, ready ? 0xFF5A3E6B : 0xFF26262F);
        border(g, x + 16, by, x + w - 16, by + 18, ready ? COL_MAT : COL_BORDER);
        g.centeredText(this.font, Component.translatable("eden.forge.ascend.button").getString(),
                (x * 2 + w) / 2, by + 5, ready ? 0xFFF6E8FF : 0xFF6E6E7A);
    }

    private void drawInventory(GuiGraphicsExtractor g, int mx, int my) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        int x0 = gridX(), y0 = gridY();
        g.centeredText(this.font, Component.translatable("eden.forge.inventory").getString(),
                this.width / 2, y0 - 12, 0xFF9A9AA6);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slot = row * GRID_COLS + col;
                int x = x0 + col * CELL, y = y0 + row * CELL;
                ItemStack s = inv.getItem(slot);
                g.fill(x, y, x + CELL - 1, y + CELL - 1, staged(slot) ? 0x663FBF5F : 0xFF14141E);
                border(g, x, y, x + CELL - 1, y + CELL - 1, staged(slot) ? COL_STAGED : 0xFF262630);
                if (!s.isEmpty()) {
                    g.item(s, x + 1, y + 1);
                    if (s.getCount() > 1) {
                        g.text(this.font, String.valueOf(s.getCount()), x + CELL - 2 - this.font.width(String.valueOf(s.getCount())),
                                y + CELL - 10, 0xFFFFFFFF);
                    }
                }
            }
        }
    }

    private boolean staged(int slot) {
        return fuse[0] == slot || fuse[1] == slot || fuse[2] == slot
                || ascendCard == slot || ascendMat == slot;
    }

    private ItemStack stackAt(int slot) {
        return Minecraft.getInstance().player.getInventory().getItem(slot);
    }

    private boolean fuseReady() {
        if (fuse[0] < 0 || fuse[1] < 0 || fuse[2] < 0) {
            return false;
        }
        ItemStack a = stackAt(fuse[0]), b = stackAt(fuse[1]), c = stackAt(fuse[2]);
        if (!(a.getItem() instanceof CardItem ca) || !(b.getItem() instanceof CardItem cb)
                || !(c.getItem() instanceof CardItem cc)) {
            return false;
        }
        return ca.quality() != CardQuality.CURSE && ca.quality() == cb.quality() && cb.quality() == cc.quality();
    }

    private boolean ascendReady() {
        return this.ascendCard >= 0 && this.ascendMat >= 0
                && stackAt(this.ascendCard).getItem() instanceof CardItem
                && CardItem.starOf(stackAt(this.ascendCard)) < 5
                && this.supplyBalance >= RiftForge.ASCEND_SUPPLY_COST;
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x(), my = event.y();
        if (super.mouseClicked(event, isDoubleClick)) {
            return true;
        }
        // inventory grid clicks stage / unstage
        int x0 = gridX(), y0 = gridY();
        int col = ((int) mx - x0) / CELL, row = ((int) my - y0) / CELL;
        if (col >= 0 && col < GRID_COLS && row >= 0 && row < GRID_ROWS
                && mx >= x0 && my >= y0 && mx < x0 + GRID_COLS * CELL && my < y0 + GRID_ROWS * CELL) {
            int slot = row * GRID_COLS + col;
            ItemStack s = stackAt(slot);
            if (s.getItem() instanceof CardItem || RiftForge.isBossMaterial(s.getItem())) {
                stage(slot);
            }
            return true;
        }
        // forge buttons
        if (fuseReady() && inRect(mx, my, fuseX() + 16, panelY() + panelH() - 26, fuseX() + panelW() - 16,
                panelY() + panelH() - 8)) {
            ClientPacketDistributor.sendToServer(new RiftForgePayload("fuse", fuse[0], fuse[1], fuse[2]));
            clearStaging();
            return true;
        }
        if (ascendReady() && inRect(mx, my, ascendX() + 16, panelY() + panelH() - 26, ascendX() + panelW() - 16,
                panelY() + panelH() - 8)) {
            ClientPacketDistributor.sendToServer(new RiftForgePayload("ascend", ascendCard, ascendMat, -1));
            clearStaging();
            return true;
        }
        return false;
    }

    /** Clicking a card/material slot stages it into the first free hole of its recipe; click staged = unstage. */
    private void stage(int slot) {
        if (staged(slot)) {
            for (int i = 0; i < 3; i++) {
                if (fuse[i] == slot) {
                    fuse[i] = -1;
                }
            }
            if (ascendCard == slot) {
                ascendCard = -1;
            }
            if (ascendMat == slot) {
                ascendMat = -1;
            }
            return;
        }
        if (stackAt(slot).getItem() instanceof CardItem) {
            for (int i = 0; i < 3; i++) {
                if (fuse[i] < 0) {
                    fuse[i] = slot;
                    return;
                }
            }
            if (ascendCard < 0) {
                ascendCard = slot;
                return;
            }
        } else {
            if (ascendMat < 0) {
                ascendMat = slot;
            }
        }
    }

    private void clearStaging() {
        fuse = new int[]{-1, -1, -1};
        ascendCard = -1;
        ascendMat = -1;
    }

    private static boolean inRect(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    // ---------- helpers (same fill-based idioms as TalentScreen) ----------

    private static void hline(GuiGraphicsExtractor g, int x1, int x2, int y, int col) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, col);
    }

    private static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int col) {
        g.fill(x1, y1, x2, y1 + 1, col);
        g.fill(x1, y2 - 1, x2, y2, col);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, col);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, col);
    }

    private String fit(String s, int maxW) {
        if (maxW <= 0 || this.font.width(s) <= maxW) {
            return s;
        }
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i)) > maxW) {
            i--;
        }
        return s.substring(0, i);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
