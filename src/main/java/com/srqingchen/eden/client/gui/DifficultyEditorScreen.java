package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.network.EditorDataPayload;
import com.srqingchen.eden.network.SaveConfigPayload;
import net.minecraft.client.Minecraft;
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
 * Code-driven difficulty editor (client-only), opened by right-clicking the editor item. Left column: pick a
 * raid difficulty. Top: four tabs - {@code [配置&充能] [氛围粒子] [品质概率] [星级概率]} - each showing that
 * difficulty's rows (profile + charge / particle tuning / card-quality weights / card-star weights) as -/+
 * steppers. Nothing changes until Save pushes the whole table back to the server ({@link SaveConfigPayload}),
 * which clamps and persists it. The row buttons are generic; the active tab decides what each row adjusts.
 * <p>Below the difficulty column,「战利品…」opens {@link LootEditorScreen}, which edits the chest-loot
 * injection rules and pools (saved separately and applied via a datapack reload).
 */
@OnlyIn(Dist.CLIENT)
public class DifficultyEditorScreen extends Screen {

    private static final int TAB_CONFIG = 0, TAB_PARTICLE = 1, TAB_QUALITY = 2, TAB_STAR = 3;
    private static final String[] TAB_KEYS = {
            "eden.editor.tab_config", "eden.editor.tab_particle", "eden.editor.tab_quality", "eden.editor.tab_star"};
    private static final int[] TAB_ROWS = {3, 4, DifficultyConfigData.QUALITY_COUNT, DifficultyConfigData.STAR_COUNT};
    private static final String[][] ROW_KEYS = {
            {"eden.editor.charge_base", "eden.editor.charge_cell", "eden.editor.charge_crystal"},
            {"eden.editor.particle_scale", "eden.editor.particle_chance", "eden.editor.particle_density", "eden.editor.particle_speed"},
            {"eden.quality.common", "eden.quality.rare", "eden.quality.epic", "eden.quality.legendary", "eden.quality.curse"},
            {"", "", "", "", ""}}; // star rows are labelled with literal stars

    // Layout (GUI-scaled pixels, fixed like the rest of the mod's screens).
    private static final int LEFT_X = 16, LEFT_W = 116;
    private static final int RIGHT_X = 146, MINUS_X = 214, PLUS_X = 292, VAL_X = 265, BTN_W = 24;
    private static final int TAB_Y = 26, ROW_Y0 = 86, ROW_DY = 22;

    private final List<String> profiles;
    private final String[] selected;
    private final float[] base, cell, crystal, pScale, pChance, pSpeed;
    private final int[] pDensity;
    private final int[][] quality, star;
    /** Chest-loot injection state, edited in the sub-screen and saved through its own payload. */
    private final LootEditorScreen.State loot;

    private final List<Button> difficultyBtns = new ArrayList<>();
    private final List<Button> tabBtns = new ArrayList<>();
    private final List<Button> minusBtns = new ArrayList<>();
    private final List<Button> plusBtns = new ArrayList<>();
    private Button profileBtn;
    private Button lootBtn;
    private int editing = 0, tab = 0;

    public DifficultyEditorScreen(EditorDataPayload payload) {
        this(payload.profiles(), payload.selected(), payload.charge(), payload.particle(),
                payload.density(), payload.quality(), payload.star(), LootEditorScreen.State.fromPayload(payload));
    }

    public DifficultyEditorScreen(List<String> profiles, List<String> selected, List<Float> charge,
                                  List<Float> particle, List<Integer> density, List<Integer> quality, List<Integer> star,
                                  LootEditorScreen.State loot) {
        super(Component.translatable("eden.editor.title"));
        int n = DifficultyConfigData.DIFFICULTIES.size();
        int qc = DifficultyConfigData.QUALITY_COUNT, sc = DifficultyConfigData.STAR_COUNT;
        this.profiles = profiles != null ? profiles : List.of();
        this.selected = new String[n];
        this.base = new float[n]; this.cell = new float[n]; this.crystal = new float[n];
        this.pScale = new float[n]; this.pChance = new float[n]; this.pSpeed = new float[n]; this.pDensity = new int[n];
        this.quality = new int[n][qc]; this.star = new int[n][sc];
        for (int i = 0; i < n; i++) {
            this.selected[i] = str(selected, i, DifficultyConfigData.DIFFICULTIES.get(i));
            int c = i * 3;
            this.base[i] = fl(charge, c, DifficultyConfigData.DEFAULT_BASE);
            this.cell[i] = fl(charge, c + 1, DifficultyConfigData.DEFAULT_CELL);
            this.crystal[i] = fl(charge, c + 2, DifficultyConfigData.DEFAULT_CRYSTAL);
            this.pScale[i] = fl(particle, c, DifficultyConfigData.DEFAULT_PARTICLE_SCALE);
            this.pChance[i] = fl(particle, c + 1, DifficultyConfigData.DEFAULT_PARTICLE_CHANCE);
            this.pSpeed[i] = fl(particle, c + 2, DifficultyConfigData.DEFAULT_PARTICLE_SPEED);
            this.pDensity[i] = integer(density, i, DifficultyConfigData.DEFAULT_PARTICLE_DENSITY);
            for (int q = 0; q < qc; q++) this.quality[i][q] = integer(quality, i * qc + q, 1);
            for (int s = 0; s < sc; s++) this.star[i][s] = integer(star, i * sc + s, 1);
        }
        this.loot = loot;
    }

    @Override
    protected void init() {
        int n = DifficultyConfigData.DIFFICULTIES.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = Button.builder(Component.empty(), btn -> { this.editing = idx; refresh(); })
                    .bounds(LEFT_X, 52 + i * 22, LEFT_W, 18).build();
            this.difficultyBtns.add(b);
            addRenderableWidget(b);
        }
        for (int t = 0; t < TAB_KEYS.length; t++) {
            final int tt = t;
            Button b = Button.builder(Component.empty(), btn -> { this.tab = tt; refresh(); })
                    .bounds(RIGHT_X + t * 62, TAB_Y, 60, 16).build();
            this.tabBtns.add(b);
            addRenderableWidget(b);
        }
        this.profileBtn = Button.builder(Component.empty(), btn -> cycleProfile())
                .bounds(RIGHT_X, 52, 232, 18).build();
        addRenderableWidget(this.profileBtn);
        this.lootBtn = Button.builder(Component.translatable("eden.editor.loot.button"),
                        btn -> Minecraft.getInstance().setScreen(new LootEditorScreen(this.loot, this)))
                .bounds(LEFT_X, 52 + DifficultyConfigData.DIFFICULTIES.size() * 22 + 6, LEFT_W, 18).build();
        addRenderableWidget(this.lootBtn);
        for (int r = 0; r < 5; r++) {
            final int row = r;
            Button minus = Button.builder(Component.literal("-"), btn -> adjust(row, -1))
                    .bounds(MINUS_X, ROW_Y0 + r * ROW_DY, BTN_W, 18).build();
            Button plus = Button.builder(Component.literal("+"), btn -> adjust(row, +1))
                    .bounds(PLUS_X, ROW_Y0 + r * ROW_DY, BTN_W, 18).build();
            this.minusBtns.add(minus);
            this.plusBtns.add(plus);
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.save"), btn -> save())
                .bounds(this.width / 2 - 105, this.height - 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.shop.close"), btn -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 34, 100, 20).build());
        refresh();
    }

    private void adjust(int row, int dir) {
        if (row >= TAB_ROWS[this.tab]) return;
        switch (this.tab) {
            case TAB_CONFIG -> {
                // base efficiency steps coarsely (0.1); the per-fuel bonuses are small, so they step finely (0.05).
                float d = dir * (row == 0 ? 0.1f : 0.05f);
                if (row == 0) this.base[editing] = Math.max(0.0f, round(this.base[editing] + d));
                else if (row == 1) this.cell[editing] = Math.max(0.0f, round(this.cell[editing] + d));
                else this.crystal[editing] = Math.max(0.0f, round(this.crystal[editing] + d));
            }
            case TAB_PARTICLE -> {
                if (row == 0) this.pScale[editing] = Math.max(0.1f, round(this.pScale[editing] + dir * 0.1f));
                else if (row == 1) this.pChance[editing] = clamp01(round3(this.pChance[editing] + dir * 0.005f));
                else if (row == 2) this.pDensity[editing] = Math.max(0, this.pDensity[editing] + dir);
                else this.pSpeed[editing] = Math.max(0.0f, round(this.pSpeed[editing] + dir * 0.1f));
            }
            case TAB_QUALITY -> this.quality[editing][row] = Math.max(0, this.quality[editing][row] + dir);
            case TAB_STAR -> this.star[editing][row] = Math.max(0, this.star[editing][row] + dir);
            default -> { }
        }
    }

    private void cycleProfile() {
        if (this.profiles.isEmpty()) return;
        int idx = this.profiles.indexOf(this.selected[editing]);
        this.selected[editing] = this.profiles.get((idx + 1) % this.profiles.size());
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < this.difficultyBtns.size(); i++) {
            String name = Component.translatable("eden.difficulty." + DifficultyConfigData.DIFFICULTIES.get(i)).getString();
            this.difficultyBtns.get(i).setMessage(Component.literal((i == editing ? "\u25b6 " : "   ") + name));
        }
        for (int t = 0; t < this.tabBtns.size(); t++) {
            String name = Component.translatable(TAB_KEYS[t]).getString();
            this.tabBtns.get(t).setMessage(Component.literal((t == tab ? "\u25b6" : "") + name));
        }
        this.profileBtn.visible = (this.tab == TAB_CONFIG);
        this.profileBtn.setMessage(Component.translatable("eden.editor.profile_value", this.selected[editing]));
        int rows = TAB_ROWS[this.tab];
        for (int r = 0; r < 5; r++) {
            boolean on = r < rows;
            this.minusBtns.get(r).visible = on;
            this.plusBtns.get(r).visible = on;
        }
    }

    private void save() {
        int n = DifficultyConfigData.DIFFICULTIES.size();
        int qc = DifficultyConfigData.QUALITY_COUNT, sc = DifficultyConfigData.STAR_COUNT;
        List<String> sel = new ArrayList<>(n);
        List<Float> chg = new ArrayList<>(n * 3);
        List<Float> part = new ArrayList<>(n * 3);
        List<Integer> dens = new ArrayList<>(n);
        List<Integer> qual = new ArrayList<>(n * qc);
        List<Integer> st = new ArrayList<>(n * sc);
        for (int i = 0; i < n; i++) {
            sel.add(this.selected[i]);
            chg.add(this.base[i]); chg.add(this.cell[i]); chg.add(this.crystal[i]);
            part.add(this.pScale[i]); part.add(this.pChance[i]); part.add(this.pSpeed[i]);
            dens.add(this.pDensity[i]);
            for (int q = 0; q < qc; q++) qual.add(this.quality[i][q]);
            for (int s = 0; s < sc; s++) st.add(this.star[i][s]);
        }
        ClientPacketDistributor.sendToServer(new SaveConfigPayload(sel, chg, part, dens, qual, st));
        this.onClose();
    }

    private String rowValue(int row) {
        switch (this.tab) {
            case TAB_CONFIG -> {
                float v = row == 0 ? base[editing] : (row == 1 ? cell[editing] : crystal[editing]);
                return String.format(Locale.ROOT, "%.2f", v);
            }
            case TAB_PARTICLE -> {
                if (row == 0) return String.format(Locale.ROOT, "%.2f", pScale[editing]);
                if (row == 1) return String.format(Locale.ROOT, "%.3f", pChance[editing]);
                if (row == 2) return Integer.toString(pDensity[editing]);
                return String.format(Locale.ROOT, "%.2f", pSpeed[editing]);
            }
            case TAB_QUALITY -> {
                return Integer.toString(this.quality[editing][row]);
            }
            case TAB_STAR -> {
                return Integer.toString(this.star[editing][row]);
            }
            default -> { return ""; }
        }
    }

    private static float round(float v) { return Math.round(v * 100.0f) / 100.0f; }
    private static float round3(float v) { return Math.round(v * 1000.0f) / 1000.0f; }
    private static float clamp01(float v) { return Math.max(0.0f, Math.min(1.0f, v)); }
    private static String str(List<String> l, int i, String def) { return (l != null && i < l.size()) ? l.get(i) : def; }
    private static float fl(List<Float> l, int i, float def) { return (l != null && i < l.size()) ? l.get(i) : def; }
    private static int integer(List<Integer> l, int i, int def) { return (l != null && i < l.size()) ? l.get(i) : def; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        ex.fill(0, 0, this.width, this.height, 0xDD14141E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ex, mouseX, mouseY, partialTick);
        ex.centeredText(this.font, Component.translatable("eden.editor.title"), this.width / 2, 10, 0xFFE8E8F0);
        ex.text(this.font, Component.translatable("eden.editor.col_difficulty").getString(), LEFT_X, 42, 0xFF9A9AA8);
        if (this.tab == TAB_CONFIG) {
            ex.text(this.font, Component.translatable("eden.editor.charge_header").getString(), RIGHT_X, 76, 0xFF9A9AA8);
        }
        int rows = TAB_ROWS[this.tab];
        for (int r = 0; r < rows; r++) {
            String label = (this.tab == TAB_STAR)
                    ? "\u2605".repeat(r + 1)
                    : Component.translatable(ROW_KEYS[this.tab][r]).getString();
            int y = ROW_Y0 + r * ROW_DY + 5;
            ex.text(this.font, label, RIGHT_X, y, 0xFFD8D8E4);
            ex.centeredText(this.font, Component.literal(rowValue(r)), VAL_X, y, 0xFFFFFF60);
        }
        ex.text(this.font, Component.translatable("eden.editor.hint").getString(), LEFT_X, this.height - 56, 0xFF7A7A88);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
