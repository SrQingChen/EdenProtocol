package com.srqingchen.eden.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side mirror of the shop state: the local player's supply-point balance (updated by
 * {@link OpenShopPayload} on open and {@link ShopBalancePayload} after each purchase) plus today's
 * market state (per-key buy fluctuation, shortage-good item id) for the price/trend display (§19.3).
 * Plain data holder (no client-only Minecraft types) so it is safe to touch from common network code.
 */
public class ClientShopData {
    public static int balance = 0;
    /** Shop key -> buy-price fluctuation (-0.3..0.3); absent key = base price. */
    public static Map<String, Float> fluctuation = new HashMap<>();
    /** Today's shortage-good item id ("" = none); its salvage value pays +50%. */
    public static String shortageId = "";

    public static void updateMarket(Map<String, Float> fluct, String shortage) {
        fluctuation = new HashMap<>(fluct);
        shortageId = shortage == null ? "" : shortage;
    }
}
