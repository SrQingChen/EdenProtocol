package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.network.LootTablesPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The per-table loot editor's LIST screen (user-feedback rework): every chest loot table in the
 * registry in one scrollable, searchable list - id, the structure that uses it (localised, when
 * known) and the table's injection state (跟随全局 / ✓已启用 / ✗已禁用). Select a row and「修改…」
 * opens {@link LootTableEditScreen} for THAT table's detailed editing;「全局规则…」opens the
 * global-rules screen (prefix / namespace / default pools);「刷新」re-requests the list.
 */
@OnlyIn(Dist.CLIENT)
public class LootTableListScreen extends Screen {

    private static final int ROW_H = 14, LIST_Y = 58, COL_STATE_X = 240;

    private final List<LootTablesPayload.Row> rows;
    private final List<LootTablesPayload.Row> filtered = new ArrayList<>();
    private final Screen parent;
    private EditBox searchBox;
    private int selected = -1;
    private int scroll;

    public LootTableListScreen(LootTablesPayload payload, Screen parent) {
        super(Component.translatable("eden.editor.loot.list_title"));
        this.rows = payload.rows();
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 130, 30, 200, 18,
                Component.translatable("eden.editor.loot.search"));
        this.searchBox.setMaxLength(40);
        this.searchBox.setResponder(t -> applyFilter());
        addRenderableWidget(this.searchBox);
        setInitialFocus(this.searchBox);

        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.edit"), b -> openEditor())
                .bounds(10, 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.global_rules"), b -> {
                    // carry nothing: the global screen keeps its own state via the parent editor chain
                    this.minecraft.setScreen(new LootEditorScreen(globalState(), this));
                })
                .bounds(this.width - 190, 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.refresh"), b -> refresh())
                .bounds(this.width - 94, 28, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.shop.close"), b -> this.onClose())
                .bounds(this.width / 2 - 40, this.height - 24, 80, 18).build());
        applyFilter();
    }

    /** The global rules state is owned by the difficulty editor chain; opening fresh defaults here. */
    private LootEditorScreen.State globalState() {
        return LootEditorScreen.State.freshDefaults();
    }

    private void refresh() {
        com.srqingchen.eden.network.RequestLootTablesPayload req =
                new com.srqingchen.eden.network.RequestLootTablesPayload();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(req);
        // the reply re-opens this screen through ClientHooks
    }

    private void applyFilter() {
        String q = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (LootTablesPayload.Row r : this.rows) {
            if (q.isEmpty() || r.id().toLowerCase(Locale.ROOT).contains(q)
                    || Component.translatable(r.hintKey()).getString().toLowerCase(Locale.ROOT).contains(q)) {
                this.filtered.add(r);
            }
        }
        this.selected = Math.min(this.selected, this.filtered.size() - 1);
        this.scroll = 0;
    }

    private void openEditor() {
        if (this.selected < 0 || this.selected >= this.filtered.size()) {
            return;
        }
        this.minecraft.setScreen(new LootTableEditScreen(this.filtered.get(this.selected), this));
    }

    private int listBottom() {
        return this.height - 32;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - LIST_Y) / ROW_H);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDoubleClick) {
        if (super.mouseClicked(event, isDoubleClick)) {
            return true;
        }
        double mx = event.x(), my = event.y();
        int x1 = 10, x2 = this.width - 10;
        if (mx >= x1 && mx <= x2 && my >= LIST_Y && my < listBottom()) {
            int idx = (int) ((my - LIST_Y) / ROW_H) + this.scroll;
            if (idx >= 0 && idx < this.filtered.size()) {
                this.selected = idx;
                // double-click jumps straight into the editor
                if (isDoubleClick) {
                    openEditor();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, this.filtered.size() - visibleRows());
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xDD14141E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 10, 0xFFE8E8F0);
        g.text(this.font, Component.translatable("eden.editor.loot.count", this.filtered.size()).getString(),
                this.width - 10 - this.font.width(Component.translatable(
                        "eden.editor.loot.count", this.filtered.size()).getString()), 10, 0xFF9A9AA8);

        int x1 = 10, x2 = this.width - 10;
        for (int i = 0; i < visibleRows() && this.scroll + i < this.filtered.size(); i++) {
            LootTablesPayload.Row r = this.filtered.get(this.scroll + i);
            int y = LIST_Y + i * ROW_H;
            boolean sel = this.scroll + i == this.selected;
            g.fill(x1, y, x2, y + ROW_H - 1, sel ? 0xFF2E3A52 : (this.scroll + i) % 2 == 0 ? 0xFF181822 : 0xFF141420);
            // id, trimmed
            String id = r.id();
            String shown = id;
            while (this.font.width(shown) > 215 && shown.length() > 4) {
                shown = shown.substring(0, shown.length() - 4) + "…";
            }
            g.text(this.font, shown, x1 + 4, y + 3, r.state() == 0 ? 0xFFD8D8E4 : 0xFFFFE8A0);
            // structure hint
            String hint = r.hintKey().isEmpty() ? "" : Component.translatable(r.hintKey()).getString();
            if (!hint.isEmpty()) {
                g.text(this.font, hint, x1 + 230, y + 3, 0xFF8FB8D8);
            }
            // state marker on the right
            String mark = r.state() == 1 ? "\u2713 " + Component.translatable("eden.editor.loot.state_on").getString()
                    : r.state() == 2 ? "\u2717 " + Component.translatable("eden.editor.loot.state_off").getString()
                    : Component.translatable("eden.editor.loot.state_global").getString();
            int mw = this.font.width(mark);
            g.text(this.font, mark, x2 - mw - 4, y + 3,
                    r.state() == 1 ? 0xFF7FE0A8 : r.state() == 2 ? 0xFFE08A8A : 0xFF7A7A88);
        }
        // scroll indicator
        int max = Math.max(0, this.filtered.size() - visibleRows());
        if (max > 0) {
            g.text(this.font, (this.scroll + 1) + "/" + (max + 1), x2 - 30, listBottom() + 4, 0xFF7A7A88);
        }
        g.text(this.font, Component.translatable("eden.editor.loot.list_hint").getString(), 10,
                this.height - 40, 0xFF7A7A88);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
