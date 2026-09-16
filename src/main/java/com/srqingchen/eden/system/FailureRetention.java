package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Failed-expedition retention - the "损耗保单" (attrition policy) mechanic.
 * <p>When a player really dies inside a raid, the whole inventory is about to drop into a world that gets
 * wiped on the next launch. So we intercept {@link LivingDropsEvent} (which for players carries the full
 * dropped inventory and only fires on a real, non-cancelled death - a downed/revived player never reaches
 * it): equipment / cards / tools (anything absent from {@link SalvageTable}) are KEPT and handed back on
 * respawn, while salvageable materials are liquidated into supply points at a keep-ratio
 * (10% base, 35% if the player carried an attrition policy, which is consumed).
 */
public final class FailureRetention {
    private FailureRetention() {}

    /** Gear waiting to be handed back, keyed by player UUID (death -&gt; respawn is same-session). */
    private static final java.util.Map<UUID, Pending> PENDING = new HashMap<>();

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid) {
            return; // died outside an expedition -> normal vanilla drops
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        CampaignData campaign = CampaignData.get(server);

        boolean sbLoaded = ModList.get().isLoaded("sophisticatedbackpacks");
        boolean insured = false;
        int salvageValue = 0;
        List<ItemStack> gear = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(EdenItems.INSURANCE.get())) {
                insured = true;          // the policy is consumed to boost the keep-ratio
                continue;
            }
            if (sbLoaded && BackpackCompat.isBackpack(stack)) {
                // A backpack is equipment the player chose to risk: on failure the body + installed upgrades are
                // LOST (not added to gear); only its salvageable contents are valued, then scaled by the ratio below.
                salvageValue += BackpackCompat.processContents(stack, s -> MarketSystem.salvageValue(campaign, s.getItem()), false);
                continue;
            }
            int unit = MarketSystem.salvageValue(campaign, stack.getItem());
            if (unit > 0) {
                salvageValue += unit * stack.getCount();
            } else {
                gear.add(stack.copy());  // equipment / cards / tools / relics -> kept
            }
        }
        event.setCanceled(true);          // void the drops; the raid world is wiped anyway

        // The greed curse card pays +50% on salvaged value even in failure (its passive).
        if (CurioCards.isEquipped(player, (CardItem) EdenItems.CARD_GREED.get())) {
            salvageValue = Math.round(salvageValue * 1.5f);
        }
        // Greed's tail (§19.5): the failed gamble halves the keep-ratio on top of everything else.
        double ratio = insured ? EdenConfig.INSURED_KEEP_RATIO.get() : EdenConfig.FAILED_EXTRACT_KEEP_RATIO.get();
        if (state.greedTail) {
            ratio *= 0.5;
        }
        int points = (int) Math.round(salvageValue * ratio);
        if (points > 0) {
            campaign.addSupplyPoints(points);
        }
        PENDING.put(player.getUUID(), new Pending(gear, points, insured, ratio));
    }

    /** Hand the kept gear back after respawn and report the liquidation. */
    public static void onRespawn(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        for (ItemStack stack : pending.gear()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        EdenMessages.send(player, EdenMessages.Type.WARNING, "eden.msg.failed_keep",
                pending.points(), (int) Math.round(pending.ratio() * 100.0));
    }

    private record Pending(List<ItemStack> gear, int points, boolean insured, double ratio) {}
}
