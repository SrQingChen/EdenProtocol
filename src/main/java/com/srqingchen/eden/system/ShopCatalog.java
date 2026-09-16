package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.network.OpenShopPayload;
import com.srqingchen.eden.network.ShopBalancePayload;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The ark requisition catalog: what supply points can buy. Base costs are design defaults; the LIVE
 * buy price is the base scaled by the daily market fluctuation ({@link MarketSystem}, ±30% per in-game
 * day, locked while a crew is inside the raid world). Surfaced through the GUI shop screen and the
 * {@code /eden buy} command.
 */
public final class ShopCatalog {
    private ShopCatalog() {}

    public record Entry(DeferredItem<? extends Item> item, int cost) {}

    private static final Map<String, Entry> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("cell", new Entry(EdenItems.EDEN_CELL, 30));
        CATALOG.put("locator", new Entry(EdenItems.LOCATOR, 40));
        CATALOG.put("pack", new Entry(EdenItems.CARD_PACK, 60));
        CATALOG.put("ingot", new Entry(EdenItems.TAINTED_INGOT, 15));
        CATALOG.put("crystal", new Entry(EdenItems.TAINT_CRYSTAL, 10));
        CATALOG.put("insurance", new Entry(EdenItems.INSURANCE, 50));
        // Erosion relief + raid gamble goods (§19.5).
        CATALOG.put("purifier", new Entry(EdenItems.PURIFIER, 35));
        CATALOG.put("hunter", new Entry(EdenItems.HUNTER_BEACON_T1, 80));
        CATALOG.put("tail", new Entry(EdenItems.GREED_TAIL, 60));
        // Intel tool (§11): situation reads + extraction bearings + affix peels.
        CATALOG.put("scanner", new Entry(EdenItems.SCANNER, 45));
    }

    public static Entry get(String key) {
        return CATALOG.get(key.toLowerCase());
    }

    public static Set<String> keys() {
        return CATALOG.keySet();
    }

    /** The LIVE buy price of a key right now (base price scaled by today's fluctuation). */
    public static int livePrice(MinecraftServer server, String key) {
        Entry entry = get(key);
        if (entry == null) {
            return 0;
        }
        CampaignData data = CampaignData.get(server);
        return MarketSystem.buyPrice(data, key, entry.cost());
    }

    /** Send the player the shop menu (current points + price list) in chat. */
    public static void sendMenu(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        EdenMessages.send(player, Type.INFO, "eden.msg.shop_points", data.getSupplyPoints());
        StringBuilder list = new StringBuilder();
        for (Map.Entry<String, Entry> e : CATALOG.entrySet()) {
            list.append(e.getKey()).append('=').append(livePrice(server, e.getKey())).append("  ");
        }
        EdenMessages.send(player, Type.INFO, "eden.msg.shop_buy_hint", list.toString());
    }

    /** Open the client shop screen for this player, seeded with their balance + today's market state. */
    public static void openShop(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        PacketDistributor.sendToPlayer(player, new OpenShopPayload(
                data.getSupplyPoints(), new HashMap<>(data.marketFluctuation()), data.marketShortage()));
    }

    /** Push the current balance to the player's client (refreshes an open shop screen after a purchase). */
    public static void sendBalance(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ShopBalancePayload(balanceOf(player)));
    }

    private static int balanceOf(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null ? CampaignData.get(server).getSupplyPoints() : 0;
    }

    /**
     * Purchase {@code count} of {@code key} from the shared supply-point pool at today's live price.
     * Items are handed out one at a time (dropped at the player's feet if the inventory is full) so any
     * count respects stack limits. Returns the points spent, or 0 on failure (unknown key / insufficient
     * points).
     */
    public static int buy(ServerPlayer player, String key, int count) {
        Entry entry = get(key);
        if (entry == null) {
            EdenMessages.send(player, Type.WARNING, "eden.msg.shop_unknown", key);
            return 0;
        }
        int qty = Math.max(1, count);
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return 0;
        }
        CampaignData data = CampaignData.get(server);
        int unit = MarketSystem.buyPrice(data, key, entry.cost());
        int total = unit * qty;
        if (!data.spendSupplyPoints(total)) {
            EdenMessages.send(player, Type.WARNING, "eden.msg.shop_insufficient", total, data.getSupplyPoints());
            return 0;
        }
        ItemStack proto = new ItemStack(entry.item().get());
        String name = proto.getHoverName().getString();
        for (int i = 0; i < qty; i++) {
            ItemStack stack = proto.copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        EdenMessages.send(player, Type.SUCCESS, "eden.msg.shop_bought", qty, name, total, data.getSupplyPoints());
        return total;
    }
}
