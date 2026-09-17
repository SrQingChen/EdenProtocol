package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.data.LootInjectionData;
import com.srqingchen.eden.network.LootTablesPayload;
import com.srqingchen.eden.network.SaveTableLootPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The per-table detail editor (user-feedback rework): everything about ONE loot table's injection.
 * Top: the table id + its structure hint + the override mode - 跟随全局 / 强制启用 / 强制禁用 (a forced
 * table injects even off the global prefix path; a disabled one never injects). Middle: the
 * per-difficulty pool (difficulty chips + the weight/min/max/chance column chips + the six item
 * slots), pre-filled with the table's current pools. Save persists the override and live-applies
 * via the background datapack reload; 清除 removes the override entirely.
 */
@OnlyIn(Dist.CLIENT)
public class LootTableEditScreen extends Screen {

    private static final String[] CATALOG = LootEditorScreen.CATALOG;
    private static final int COL_WEIGHT = 0, COL_MIN = 1, COL_MAX = 2, COL_CHANCE = 3;
    private static final String[] COL_KEYS = {
            "eden.editor.loot_col_weight", "eden.editor.loot_col_min", "eden.editor.loot_col_max", "eden.editor.loot_col_chance"};
    private static final int MODE_GLOBAL = 0, MODE_ON = 1, MODE_OFF = 2;

    private final LootTablesPayload.Row row;
    private final Screen parent;
    private final LootEditorScreen.State pools;
    private int mode;
    private int difficulty = 0;
    private int column = COL_WEIGHT;

    private final List<Button> modeBtns = new ArrayList<>();
    private final List<Button> diffBtns = new ArrayList<>();
    private final List<Button> colBtns = new ArrayList<>();
    private final List<Button> itemBtns = new ArrayList<>();
    private final List<Button> minusBtns = new ArrayList<>();
    private final List<Button> plusBtns = new ArrayList<>();
    private Button rollMinMinus, rollMinPlus, rollMaxMinus, rollMaxPlus;

    public LootTableEditScreen(LootTablesPayload.Row row, Screen parent) {
        super(Component.translatable("eden.editor.loot.table_title"));
        this.row = row;
        this.parent = parent;
        this.mode = row.state();
        this.pools = LootEditorScreen.State.fromBlock(row.pools());
    }

    @Override
    protected void init() {
        for (int m = 0; m < 3; m++) {
            final int mm = m;
            Button b = Button.builder(Component.empty(), btn -> { this.mode = mm; refresh(); })
                    .bounds(10 + m * 96, 46, 94, 18).build();
            this.modeBtns.add(b);
            addRenderableWidget(b);
        }
        int n = DifficultyConfigData.DIFFICULTIES.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = Button.builder(Component.empty(), btn -> { this.difficulty = idx; refresh(); })
                    .bounds(10 + i * 50, 68, 48, 16).build();
            this.diffBtns.add(b);
            addRenderableWidget(b);
        }
        this.rollMinMinus = Button.builder(Component.literal("-"), b -> stepRolls(-1, true)).bounds(10, 88, 20, 16).build();
        this.rollMinPlus = Button.builder(Component.literal("+"), b -> stepRolls(1, true)).bounds(66, 88, 20, 16).build();
        this.rollMaxMinus = Button.builder(Component.literal("-"), b -> stepRolls(-1, false)).bounds(102, 88, 20, 16).build();
        this.rollMaxPlus = Button.builder(Component.literal("+"), b -> stepRolls(1, false)).bounds(158, 88, 20, 16).build();
        addRenderableWidget(this.rollMinMinus);
        addRenderableWidget(this.rollMinPlus);
        addRenderableWidget(this.rollMaxMinus);
        addRenderableWidget(this.rollMaxPlus);

        for (int c = 0; c < COL_KEYS.length; c++) {
            final int cc = c;
            Button b = Button.builder(Component.empty(), btn -> { this.column = cc; refresh(); })
                    .bounds(10 + c * 62, 108, 60, 16).build();
            this.colBtns.add(b);
            addRenderableWidget(b);
        }
        for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
            final int slot = k;
            Button item = Button.builder(Component.empty(), btn -> cycleItem(slot))
                    .bounds(10, 128 + k * 17, 128, 16).build();
            Button minus = Button.builder(Component.literal("-"), btn -> adjust(slot, -1))
                    .bounds(142, 128 + k * 17, 20, 16).build();
            Button plus = Button.builder(Component.literal("+"), btn -> adjust(slot, +1))
                    .bounds(240, 128 + k * 17, 20, 16).build();
            this.itemBtns.add(item);
            this.minusBtns.add(minus);
            this.plusBtns.add(plus);
            addRenderableWidget(item);
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.save_table"), b -> save())
                .bounds(10, 6, 120, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.clear_table"), b -> {
                    this.mode = MODE_GLOBAL;
                    save();
                })
                .bounds(136, 6, 100, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.back"), b -> this.onClose())
                .bounds(this.width - 90, 6, 80, 16).build());
        refresh();
    }

    private void stepRolls(int dir, boolean min) {
        if (min) {
            pools.rollsMin[difficulty] = Math.max(0, Math.min(pools.rollsMax[difficulty], pools.rollsMin[difficulty] + dir));
        } else {
            pools.rollsMax[difficulty] = Math.max(pools.rollsMin[difficulty], Math.min(8, pools.rollsMax[difficulty] + dir));
        }
        refresh();
    }

    private void cycleItem(int slot) {
        String current = pools.items[difficulty][slot];
        int idx = -1;
        for (int i = 0; i < CATALOG.length; i++) {
            if (CATALOG[i].equals(current)) {
                idx = i;
                break;
            }
        }
        pools.items[difficulty][slot] = CATALOG[(idx + 1 + CATALOG.length) % CATALOG.length];
        refresh();
    }

    private void adjust(int slot, int dir) {
        switch (this.column) {
            case COL_WEIGHT -> pools.weight[difficulty][slot] = Math.max(0, Math.min(999, pools.weight[difficulty][slot] + dir));
            case COL_MIN -> pools.min[difficulty][slot] = Math.max(1, Math.min(pools.max[difficulty][slot], pools.min[difficulty][slot] + dir));
            case COL_MAX -> pools.max[difficulty][slot] = Math.max(pools.min[difficulty][slot], Math.min(64, pools.max[difficulty][slot] + dir));
            case COL_CHANCE -> pools.chance[difficulty][slot] = Math.max(0.0f, Math.min(1.0f,
                    Math.round((pools.chance[difficulty][slot] + dir * 0.05f) * 100.0f) / 100.0f));
            default -> { }
        }
        refresh();
    }

    private void save() {
        var block = pools.toBlockForTable();
        ClientPacketDistributor.sendToServer(new SaveTableLootPayload(row.id(), mode,
                block.rolls(), block.items(), block.weights(), block.min(), block.max(), block.chance()));
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void refresh() {
        String[] modeKeys = {"eden.editor.loot.state_global", "eden.editor.loot.state_on", "eden.editor.loot.state_off"};
        for (int m = 0; m < this.modeBtns.size(); m++) {
            this.modeBtns.get(m).setMessage(Component.literal((mode == m ? "\u25b6 " : "   ")
                    + Component.translatable(modeKeys[m]).getString()));
        }
        for (int i = 0; i < this.diffBtns.size(); i++) {
            String name = Component.translatable("eden.difficulty." + DifficultyConfigData.DIFFICULTIES.get(i)).getString();
            this.diffBtns.get(i).setMessage(Component.literal((i == difficulty ? "\u25b6 " : "   ") + name));
        }
        for (int c = 0; c < this.colBtns.size(); c++) {
            String name = Component.translatable(COL_KEYS[c]).getString();
            this.colBtns.get(c).setMessage(Component.literal((c == column ? "\u25b6" : "") + name));
        }
        for (int k = 0; k < this.itemBtns.size(); k++) {
            String id = pools.items[difficulty][k];
            String label = (id == null || id.isBlank())
                    ? Component.translatable("eden.editor.loot.empty").getString()
                    : id.substring(id.indexOf(':') + 1);
            this.itemBtns.get(k).setMessage(Component.literal(label));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        ex.fill(0, 0, this.width, this.height, 0xDD14141E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ex, mouseX, mouseY, partialTick);
        ex.centeredText(this.font, this.title, this.width / 2, 8, 0xFFE8E8F0);
        String id = row.id();
        String shown = id;
        while (this.font.width(shown) > 250 && shown.length() > 4) {
            shown = shown.substring(0, shown.length() - 4) + "…";
        }
        ex.text(this.font, shown, 244, 6, 0xFFFFE8A0);
        if (!row.hintKey().isEmpty()) {
            ex.text(this.font, Component.translatable(row.hintKey()).getString(), 244, 26, 0xFF8FB8D8);
        }
        ex.text(this.font, Component.translatable("eden.editor.loot_rolls_min").getString(), 32, 93, 0xFFD8D8E4);
        ex.text(this.font, Integer.toString(pools.rollsMin[difficulty]), 92, 93, 0xFFFFFF60);
        ex.text(this.font, Component.translatable("eden.editor.loot_rolls_max").getString(), 124, 93, 0xFFD8D8E4);
        ex.text(this.font, Integer.toString(pools.rollsMax[difficulty]), 184, 93, 0xFFFFFF60);
        for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
            int y = 128 + k * 17 + 4;
            String value = String.format(Locale.ROOT, "W%d  %d-%d  %d%%",
                    pools.weight[difficulty][k], pools.min[difficulty][k], pools.max[difficulty][k],
                    Math.round(pools.chance[difficulty][k] * 100.0f));
            ex.text(this.font, value, 166, y, 0xFFFFFF60);
        }
        ex.text(this.font, Component.translatable("eden.editor.loot.table_hint").getString(), 10,
                this.height - 14, 0xFF7A7A88);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
