package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ark market fluctuation (功能清单 §19.3):
 * <ul>
 *   <li>Each in-game day every shop entry's BUY price drifts by a random factor within ±30%.</li>
 *   <li>Each day one SALVAGE good becomes the "shortage good": its salvage (settlement) value pays +50%.</li>
 *   <li>While any player is inside the raid world the market is LOCKED - the day rolls over but prices
 *       and the shortage good stay put until a quiet day, so a crew mid-raid never has the rug pulled
 *       under the goods they are carrying.</li>
 * </ul>
 * Prices are server-authoritative: the shop screen merely displays what the server pushed.
 */
public final class MarketSystem {
    private MarketSystem() {}

    /** Buy-price fluctuation bounds (±30%). */
    public static final float MAX_FLUCTUATION = 0.30f;
    /** Shortage-good salvage bonus (+50%). */
    public static final float SHORTAGE_BONUS = 0.50f;
    /** How often the day check runs (every 5 seconds is plenty). */
    private static final int CHECK_INTERVAL = 100;

    // ---------- day rollover ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long day = overworld.getOverworldClockTime() / 24000L;
        CampaignData data = CampaignData.get(server);
        if (data.marketDay() == day) {
            return;
        }
        data.setMarketDay(day);
        if (playersInRaidWorld(server)) {
            return;   // locked: keep today's fluctuation + shortage good untouched
        }
        rollMarket(server, data, overworld.getRandom());
        announceToArk(server, data);
    }

    /** Roll fresh fluctuations for every shop entry and pick a new shortage salvage good. */
    private static void rollMarket(MinecraftServer server, CampaignData data, RandomSource random) {
        Map<String, Float> fluctuation = new HashMap<>();
        for (String key : ShopCatalog.keys()) {
            fluctuation.put(key, (random.nextFloat() * 2f - 1f) * MAX_FLUCTUATION);
        }
        data.setMarketFluctuation(fluctuation);

        List<Item> salvage = new ArrayList<>(SalvageTable.salvageItems());
        if (!salvage.isEmpty()) {
            Item pick = salvage.get(random.nextInt(salvage.size()));
            Identifier id = BuiltInRegistries.ITEM.getKey(pick);
            data.setMarketShortage(id != null ? id.toString() : "");
        }
    }

    private static boolean playersInRaidWorld(MinecraftServer server) {
        // The market freezes while ANY expedition is afoot - overworld, nether or end (§19.3, extended §2).
        for (ResourceKey<Level> dim : List.of(EdenDimensions.RAID_OVERWORLD,
                EdenDimensions.RAID_NETHER, EdenDimensions.RAID_END)) {
            ServerLevel raid = server.getLevel(dim);
            if (raid != null && !raid.players().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A light in-chat market report to players idling on the ark (everyone NOT in a raid world). */
    private static void announceToArk(MinecraftServer server, CampaignData data) {
        String shortageId = data.marketShortage();
        if (shortageId.isEmpty()) {
            return;
        }
        Identifier id = Identifier.parse(shortageId);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dim = sp.level().dimension();
            if (!dim.equals(EdenDimensions.RAID_OVERWORLD)
                    && !dim.equals(EdenDimensions.RAID_NETHER)
                    && !dim.equals(EdenDimensions.RAID_END)) {
                EdenMessages.send(sp, Type.INFO, "eden.market.rolled",
                        new ItemStack(item).getHoverName());
            }
        }
    }

    // ---------- price queries (server-authoritative) ----------

    /** The current buy-price multiplier for a shop key (1.0 = base price). */
    public static float buyMultiplier(CampaignData data, String key) {
        Float f = data.marketFluctuation().get(key);
        return f == null ? 1.0f : 1.0f + f;
    }

    /** The current buy price of a shop entry, rounded to whole points. */
    public static int buyPrice(CampaignData data, String key, int baseCost) {
        return Math.max(1, Math.round(baseCost * buyMultiplier(data, key)));
    }

    /** True when the item is today's shortage good. */
    public static boolean isShortage(CampaignData data, Item item) {
        if (data.marketShortage().isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && data.marketShortage().equals(id.toString());
    }

    /** Salvage-value multiplier for an item (1.5 on the shortage good, else 1). */
    public static float salvageMultiplier(CampaignData data, Item item) {
        return isShortage(data, item) ? 1f + SHORTAGE_BONUS : 1f;
    }

    /** Convenience: the salvage unit value of an item INCLUDING today's shortage bonus. */
    public static int salvageValue(CampaignData data, Item item) {
        int unit = SalvageTable.valueOf(item);
        if (unit <= 0) {
            return 0;
        }
        return Math.round(unit * salvageMultiplier(data, item));
    }
}
