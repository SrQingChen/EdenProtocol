package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.data.LootInjectionData;
import com.srqingchen.eden.network.EditorDataPayload;
import com.srqingchen.eden.network.SaveLootConfigPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Code-driven loot-injection editor (client-only), opened from the difficulty editor's「战利品…」button.
 * Edits the shared {@link State} in place so returning to the parent editor and re-opening keeps changes.
 * <p>Top: master toggle + namespace scope + rule boxes (prefixes / exclusions, comma-separated) and the
 * rolls range for the selected difficulty. Middle: which column the -/+ steppers adjust (weight / count
 * min / count max / chance) and the six per-difficulty item slots - the item button cycles the catalog,
 * an empty slot shows (空) and cycles into the first catalog item. Save sends {@link SaveLootConfigPayload};
 * the server persists it and reloads the datapacks so the new pools go live in seconds.
 */
@OnlyIn(Dist.CLIENT)
public class LootEditorScreen extends Screen {

    /** Curated cycle order for item slots: mod supplies first, then a few vanilla staples. */
    private static final String[] CATALOG = {
            "eden:essence", "eden:taint_crystal", "eden:tainted_ore", "eden:tainted_ingot",
            "eden:salvage_tech", "eden:salvage_artifact", "eden:eden_cell", "eden:card_pack",
            "eden:insurance", "eden:locator", "eden:purifier", "eden:relic_shard",
            "eden:sample_flora", "eden:sample_fauna", "eden:sample_mineral", "eden:scanner",
            "eden:hunter_beacon_t1", "eden:greed_tail", "eden:spore_sac", "eden:tainted_scale",
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:emerald"};

    private static final int COL_WEIGHT = 0, COL_MIN = 1, COL_MAX = 2, COL_CHANCE = 3;
    private static final String[] COL_KEYS = {
            "eden.editor.loot_col_weight", "eden.editor.loot_col_min", "eden.editor.loot_col_max", "eden.editor.loot_col_chance"};

    /** Mutable client-side mirror of {@link LootInjectionData}, shared with the parent editor screen. */
    public static final class State {
        public boolean enabled = true;
        public boolean allNamespaces = true;
        public String prefixes = "chests/";
        public String exclusions = "";
        public final int n = DifficultyConfigData.DIFFICULTIES.size();
        public final int[] rollsMin = new int[n];
        public final int[] rollsMax = new int[n];
        public final String[][] items = new String[n][LootInjectionData.MAX_ITEMS];
        public final int[][] weight = new int[n][LootInjectionData.MAX_ITEMS];
        public final int[][] min = new int[n][LootInjectionData.MAX_ITEMS];
        public final int[][] max = new int[n][LootInjectionData.MAX_ITEMS];
        public final float[][] chance = new float[n][LootInjectionData.MAX_ITEMS];

        public static State fromPayload(EditorDataPayload p) {
            return fromBlock(p.loot());
        }

        public static State fromBlock(com.srqingchen.eden.network.LootPayloadBlock b) {
            State s = new State();
            s.enabled = b.enabled();
            s.allNamespaces = b.allNamespaces();
            s.prefixes = b.prefixes() == null ? "" : b.prefixes();
            s.exclusions = b.exclusions() == null ? "" : b.exclusions();
            for (int i = 0; i < s.n; i++) {
                s.rollsMin[i] = at(b.rolls(), i * 2, 1);
                s.rollsMax[i] = Math.max(s.rollsMin[i], at(b.rolls(), i * 2 + 1, 2));
                int base = i * LootInjectionData.MAX_ITEMS;
                for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
                    String id = at(b.items(), base + k, "");
                    s.items[i][k] = id == null ? "" : id;
                    s.weight[i][k] = Math.max(0, at(b.weights(), base + k, 1));
                    s.min[i][k] = Math.max(1, at(b.min(), base + k, 1));
                    s.max[i][k] = Math.max(s.min[i][k], at(b.max(), base + k, 1));
                    s.chance[i][k] = clamp01(at(b.chance(), base + k, 1.0f));
                }
            }
            return s;
        }

        public SaveLootConfigPayload toPayload() {
            List<Integer> rolls = new ArrayList<>(n * 2);
            List<String> items = new ArrayList<>(n * LootInjectionData.MAX_ITEMS);
            List<Integer> weight = new ArrayList<>(n * LootInjectionData.MAX_ITEMS);
            List<Integer> min = new ArrayList<>(n * LootInjectionData.MAX_ITEMS);
            List<Integer> max = new ArrayList<>(n * LootInjectionData.MAX_ITEMS);
            List<Float> chance = new ArrayList<>(n * LootInjectionData.MAX_ITEMS);
            for (int i = 0; i < n; i++) {
                rolls.add(rollsMin[i]);
                rolls.add(Math.max(rollsMin[i], rollsMax[i]));
                for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
                    items.add(this.items[i][k] == null ? "" : this.items[i][k].trim());
                    weight.add(this.weight[i][k]);
                    min.add(this.min[i][k]);
                    max.add(Math.max(this.min[i][k], this.max[i][k]));
                    chance.add(this.chance[i][k]);
                }
            }
            return new SaveLootConfigPayload(new com.srqingchen.eden.network.LootPayloadBlock(
                    enabled, allNamespaces, prefixes, exclusions, rolls, items, weight, min, max, chance));
        }

        private static int at(List<Integer> l, int i, int def) { return l != null && i < l.size() && l.get(i) != null ? l.get(i) : def; }
        private static float at(List<Float> l, int i, float def) { return l != null && i < l.size() && l.get(i) != null ? l.get(i) : def; }
        private static String at(List<String> l, int i, String def) { return l != null && i < l.size() && l.get(i) != null ? l.get(i) : def; }
        private static float clamp01(float v) { return Math.max(0.0f, Math.min(1.0f, v)); }
    }

    private final State state;
    private final Screen parent;
    private int editing = 0;
    private int column = COL_WEIGHT;

    private Button toggleBtn;
    private Button scopeBtn;
    private final List<Button> diffBtns = new ArrayList<>();
    private final List<Button> colBtns = new ArrayList<>();
    private final List<Button> itemBtns = new ArrayList<>();
    private final List<Button> minusBtns = new ArrayList<>();
    private final List<Button> plusBtns = new ArrayList<>();
    private Button rollMinMinus, rollMinPlus, rollMaxMinus, rollMaxPlus;
    private EditBox prefixBox, exclusionBox;

    public LootEditorScreen(State state, Screen parent) {
        super(Component.translatable("eden.editor.loot.title"));
        this.state = state;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.toggleBtn = Button.builder(Component.empty(), b -> { state.enabled = !state.enabled; refresh(); })
                .bounds(10, 24, 110, 18).build();
        addRenderableWidget(this.toggleBtn);
        this.scopeBtn = Button.builder(Component.empty(), b -> { state.allNamespaces = !state.allNamespaces; refresh(); })
                .bounds(126, 24, 128, 18).build();
        addRenderableWidget(this.scopeBtn);

        int n = state.n;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = Button.builder(Component.empty(), btn -> { this.editing = idx; refresh(); })
                    .bounds(10 + i * 50, 46, 48, 18).build();
            this.diffBtns.add(b);
            addRenderableWidget(b);
        }

        this.prefixBox = new EditBox(this.font, 58, 68, 196, 18, Component.translatable("eden.editor.loot_prefixes"));
        this.prefixBox.setMaxLength(160);
        this.prefixBox.setValue(state.prefixes);
        addRenderableWidget(this.prefixBox);
        this.exclusionBox = new EditBox(this.font, 58, 90, 196, 18, Component.translatable("eden.editor.loot_exclusions"));
        this.exclusionBox.setMaxLength(160);
        this.exclusionBox.setValue(state.exclusions);
        addRenderableWidget(this.exclusionBox);

        this.rollMinMinus = Button.builder(Component.literal("-"), b -> stepRolls(-1, true)).bounds(10, 114, 20, 18).build();
        this.rollMinPlus = Button.builder(Component.literal("+"), b -> stepRolls(1, true)).bounds(66, 114, 20, 18).build();
        this.rollMaxMinus = Button.builder(Component.literal("-"), b -> stepRolls(-1, false)).bounds(102, 114, 20, 18).build();
        this.rollMaxPlus = Button.builder(Component.literal("+"), b -> stepRolls(1, false)).bounds(158, 114, 20, 18).build();
        addRenderableWidget(this.rollMinMinus);
        addRenderableWidget(this.rollMinPlus);
        addRenderableWidget(this.rollMaxMinus);
        addRenderableWidget(this.rollMaxPlus);

        for (int c = 0; c < COL_KEYS.length; c++) {
            final int cc = c;
            Button b = Button.builder(Component.empty(), btn -> { this.column = cc; refresh(); })
                    .bounds(10 + c * 62, 136, 60, 16).build();
            this.colBtns.add(b);
            addRenderableWidget(b);
        }

        for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
            final int slot = k;
            Button item = Button.builder(Component.empty(), btn -> cycleItem(slot))
                    .bounds(10, 156 + k * 17, 128, 16).build();
            Button minus = Button.builder(Component.literal("-"), btn -> adjust(slot, -1))
                    .bounds(142, 156 + k * 17, 20, 16).build();
            Button plus = Button.builder(Component.literal("+"), btn -> adjust(slot, +1))
                    .bounds(240, 156 + k * 17, 20, 16).build();
            this.itemBtns.add(item);
            this.minusBtns.add(minus);
            this.plusBtns.add(plus);
            addRenderableWidget(item);
            addRenderableWidget(minus);
            addRenderableWidget(plus);
        }

        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.save"), b -> save())
                .bounds(10, 6, 110, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("eden.editor.loot.back"), b -> this.onClose())
                .bounds(this.width - 90, 6, 80, 16).build());
        refresh();
    }

    private void stepRolls(int dir, boolean min) {
        if (min) {
            state.rollsMin[editing] = Math.max(0, Math.min(state.rollsMax[editing], state.rollsMin[editing] + dir));
        } else {
            state.rollsMax[editing] = Math.max(state.rollsMin[editing], Math.min(8, state.rollsMax[editing] + dir));
        }
        refresh();
    }

    private void cycleItem(int slot) {
        String current = state.items[editing][slot];
        int idx = -1;
        for (int i = 0; i < CATALOG.length; i++) {
            if (CATALOG[i].equals(current)) {
                idx = i;
                break;
            }
        }
        state.items[editing][slot] = CATALOG[(idx + 1 + CATALOG.length) % CATALOG.length];
        refresh();
    }

    private void adjust(int slot, int dir) {
        switch (this.column) {
            case COL_WEIGHT -> state.weight[editing][slot] = Math.max(0, Math.min(999, state.weight[editing][slot] + dir));
            case COL_MIN -> {
                state.min[editing][slot] = Math.max(1, Math.min(state.max[editing][slot], state.min[editing][slot] + dir));
            }
            case COL_MAX -> {
                state.max[editing][slot] = Math.max(state.min[editing][slot], Math.min(64, state.max[editing][slot] + dir));
            }
            case COL_CHANCE -> {
                state.chance[editing][slot] = Math.max(0.0f, Math.min(1.0f,
                        Math.round((state.chance[editing][slot] + dir * 0.05f) * 100.0f) / 100.0f));
            }
            default -> { }
        }
        refresh();
    }

    private void save() {
        // Pull the rule boxes first - typing does not go through refresh().
        state.prefixes = this.prefixBox.getValue();
        state.exclusions = this.exclusionBox.getValue();
        ClientPacketDistributor.sendToServer(state.toPayload());
        this.onClose();
    }

    @Override
    public void onClose() {
        // Keep box edits even when cancelling: the parent screen shares the same State object.
        if (this.prefixBox != null) {
            state.prefixes = this.prefixBox.getValue();
        }
        if (this.exclusionBox != null) {
            state.exclusions = this.exclusionBox.getValue();
        }
        this.minecraft.setScreen(this.parent);
    }

    private void refresh() {
        this.toggleBtn.setMessage(Component.translatable(state.enabled ? "eden.editor.loot.on" : "eden.editor.loot.off"));
        this.scopeBtn.setMessage(Component.translatable(state.allNamespaces
                ? "eden.editor.loot.scope_all" : "eden.editor.loot.scope_vanilla"));
        for (int i = 0; i < this.diffBtns.size(); i++) {
            String name = Component.translatable("eden.difficulty." + DifficultyConfigData.DIFFICULTIES.get(i)).getString();
            this.diffBtns.get(i).setMessage(Component.literal((i == editing ? "\u25b6 " : "   ") + name));
        }
        for (int c = 0; c < this.colBtns.size(); c++) {
            String name = Component.translatable(COL_KEYS[c]).getString();
            this.colBtns.get(c).setMessage(Component.literal((c == column ? "\u25b6" : "") + name));
        }
        for (int k = 0; k < this.itemBtns.size(); k++) {
            String id = state.items[editing][k];
            String label = (id == null || id.isBlank())
                    ? Component.translatable("eden.editor.loot.empty").getString()
                    : shortName(id);
            this.itemBtns.get(k).setMessage(Component.literal(label));
        }
    }

    private static String shortName(String id) {
        int cut = id.indexOf(':');
        return cut >= 0 && cut + 1 < id.length() ? id.substring(cut + 1) : id;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        ex.fill(0, 0, this.width, this.height, 0xDD14141E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ex, mouseX, mouseY, partialTick);
        ex.centeredText(this.font, Component.translatable("eden.editor.loot.title"), this.width / 2, 8, 0xFFE8E8F0);

        int colHeader = 0xFF9A9AA8;
        ex.text(this.font, Component.translatable("eden.editor.loot_prefixes").getString(), 10, 74, colHeader);
        ex.text(this.font, Component.translatable("eden.editor.loot_exclusions").getString(), 10, 96, colHeader);
        ex.text(this.font, Component.translatable("eden.editor.loot_rolls_min").getString(), 32, 119, 0xFFD8D8E4);
        ex.text(this.font, Integer.toString(state.rollsMin[editing]), 92, 119, 0xFFFFFF60);
        ex.text(this.font, Component.translatable("eden.editor.loot_rolls_max").getString(), 124, 119, 0xFFD8D8E4);
        ex.text(this.font, Integer.toString(state.rollsMax[editing]), 184, 119, 0xFFFFFF60);

        for (int k = 0; k < LootInjectionData.MAX_ITEMS; k++) {
            int y = 156 + k * 17 + 4;
            String value = String.format(Locale.ROOT, "W%d  %d-%d  %d%%",
                    state.weight[editing][k], state.min[editing][k], state.max[editing][k],
                    Math.round(state.chance[editing][k] * 100.0f));
            ex.text(this.font, value, 166, y, 0xFFFFFF60);
        }
        ex.text(this.font, Component.translatable("eden.editor.loot.hint").getString(), 10, this.height - 14, 0xFF7A7A88);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
