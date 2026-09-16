package com.srqingchen.eden.client.gui;

import com.srqingchen.eden.client.gui.search.JechCompat;
import com.srqingchen.eden.network.BuyPayload;
import com.srqingchen.eden.network.ClientShopData;
import com.srqingchen.eden.system.ShopCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The ark requisition terminal GUI: a code-drawn, list-style shop paid for with supply points.
 * <p>Mirrors the author's {@code entity_modifier} picker pattern (26.1.2 API): a search {@link EditBox}
 * filters the catalog via {@link JechCompat} (pinyin-aware when "Just Enough Characters" is installed),
 * rows are hand-drawn in {@link #extractRenderState} and hand-hit-tested in {@link #mouseClicked}. Buying
 * is server-authoritative: a click sends {@link BuyPayload}; the server deducts points, hands over items
 * and replies with a balance refresh. No vanilla container/menu is involved, which keeps the shared
 * supply-point counter economy intact.
 */
@OnlyIn(Dist.CLIENT)
public class ShopScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 66;

    /** One shop row: the catalog key plus cached display data. */
    private static class Entry {
        final String key;
        final ItemStack icon;
        final String displayName;
        final String registryName;
        final int baseCost;
        /** Today's live price (base x fluctuation), from the server-pushed market state. */
        final int liveCost;

        Entry(String key, ShopCatalog.Entry e) {
            this.key = key;
            this.icon = new ItemStack(e.item().get());
            this.baseCost = e.cost();
            Float f = ClientShopData.fluctuation.get(key);
            float mult = f == null ? 1.0f : 1.0f + f;
            this.liveCost = Math.max(1, Math.round(this.baseCost * mult));
            Identifier id = BuiltInRegistries.ITEM.getKey(e.item().get());
            this.registryName = id != null ? id.toString() : "?";
            String name;
            try {
                name = this.icon.getHoverName().getString();
            } catch (Exception ex) {
                name = this.registryName;
            }
            this.displayName = name;
        }
    }

    private EditBox searchBox;
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private int scrollOffset = 0;

    public ShopScreen() {
        super(Component.translatable("eden.shop.title"));
    }

    /** Client entry point (called from the network handler): seed the market state and open the screen. */
    public static void open(int balance, java.util.Map<String, Float> fluctuation, String shortageId) {
        ClientShopData.balance = balance;
        ClientShopData.updateMarket(fluctuation, shortageId);
        Minecraft.getInstance().setScreen(new ShopScreen());
    }

    @Override
    protected void init() {
        super.init();
        buildEntries();
        applyFilter();

        searchBox = new EditBox(this.font, this.width / 2 - 130, 22, 260, 18, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setValue("");
        searchBox.setResponder(text -> {
            scrollOffset = 0;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("eden.shop.close"), b -> this.onClose())
                .pos(this.width / 2 - 50, this.height - 26).size(100, 20).build());
    }

    private void buildEntries() {
        allEntries.clear();
        for (String key : ShopCatalog.keys()) {
            ShopCatalog.Entry e = ShopCatalog.get(key);
            if (e != null) {
                allEntries.add(new Entry(key, e));
            }
        }
    }

    private void applyFilter() {
        String query = searchBox != null ? searchBox.getValue().trim() : "";
        filtered.clear();
        if (query.isEmpty()) {
            filtered.addAll(allEntries);
            return;
        }
        for (Entry entry : allEntries) {
            if (JechCompat.matches(entry.displayName, query)
                    || JechCompat.matches(entry.registryName, query)
                    || JechCompat.matches(entry.key, query)) {
                filtered.add(entry);
            }
        }
    }

    private int listLeft() { return this.width / 2 - 170; }
    private int listRight() { return this.width / 2 + 170; }
    private int listBottom() { return this.height - 32; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);

        int left = listLeft();
        int right = listRight();
        int bottom = listBottom();

        // Balance (left) and result count + pinyin status (right), on the row between search box and list.
        String balance = Component.translatable("eden.shop.balance", ClientShopData.balance).getString();
        graphics.text(this.font, balance, left, 44, 0xFF88FF88);
        // Today's shortage good (salvage pays +50%), drawn under the balance line.
        if (!ClientShopData.shortageId.isEmpty()) {
            try {
                Identifier sid = Identifier.parse(ClientShopData.shortageId);
                var item = BuiltInRegistries.ITEM.getOptional(sid).orElse(null);
                if (item != null) {
                    String shortage = Component.translatable("eden.shop.shortage",
                            new ItemStack(item).getHoverName()).getString();
                    graphics.text(this.font, shortage, left, 50, 0xFFFFD966);
                }
            } catch (Exception ignored) {
            }
        }
        String hint = filtered.size() + " / " + allEntries.size()
                + (JechCompat.isLoaded() ? "  " + Component.translatable("eden.shop.pinyin_on").getString() : "");
        graphics.text(this.font, hint, right - this.font.width(hint), 44, 0xFF888888);

        // List background.
        graphics.fill(left, LIST_TOP, right, bottom, 0x40000000);

        int visible = (bottom - LIST_TOP) / ROW_HEIGHT;
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= filtered.size()) break;
            Entry entry = filtered.get(idx);
            int y = LIST_TOP + i * ROW_HEIGHT;

            boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hover) {
                graphics.fill(left, y, right, y + ROW_HEIGHT, 0x40FFFFFF);
            }

            try {
                graphics.item(entry.icon, left + 4, y + 4);
            } catch (Exception ignored) {
                // Some items may fail to render; keep the row usable.
            }

            graphics.text(this.font, entry.displayName, left + 26, y + 3, 0xFFFFFFFF);
            boolean afford = ClientShopData.balance >= entry.liveCost;
            // Live price + a trend marker vs. the base cost (▲ red = pricier, ▼ green = cheaper).
            String price = Component.translatable("eden.shop.price", entry.liveCost).getString();
            graphics.text(this.font, price, left + 26, y + 13, afford ? 0xFF77DD77 : 0xFFDD6666);
            String trend = entry.liveCost > entry.baseCost ? " \u25B2" : entry.liveCost < entry.baseCost ? " \u25BC" : "";
            if (!trend.isEmpty()) {
                graphics.text(this.font, trend, left + 26 + this.font.width(price), y + 13,
                        entry.liveCost > entry.baseCost ? 0xFFDD6666 : 0xFF77DD77);
            }
            if (hover) {
                String buy = afford ? Component.translatable("eden.shop.buy_hint").getString()
                        : Component.translatable("eden.shop.cannot_afford").getString();
                graphics.text(this.font, buy, right - this.font.width(buy) - 6, y + 8, afford ? 0xFFFFFF66 : 0xFF999999);
            }
        }

        if (filtered.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("eden.shop.no_result"),
                    this.width / 2, (LIST_TOP + bottom) / 2, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (super.mouseClicked(event, isDoubleClick)) return true;

        int left = listLeft();
        int right = listRight();
        if (mouseX >= left && mouseX < right && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int row = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < filtered.size()) {
                Entry entry = filtered.get(idx);
                // Server-authoritative purchase of a single unit; balance refresh comes back via payload.
                ClientPacketDistributor.sendToServer(new BuyPayload(entry.key, 1));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = (listBottom() - LIST_TOP) / ROW_HEIGHT;
        int maxScroll = Math.max(0, filtered.size() - visible);
        scrollOffset -= (int) scrollY * 3;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
