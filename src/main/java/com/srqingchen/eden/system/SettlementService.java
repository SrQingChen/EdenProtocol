package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Converts a returning player's salvaged materials into the shared supply-point pool.
 * <p>Scans the vanilla player inventory (main + armor + offhand via the {@link Inventory} Container
 * view). Equipment, functional items and Curios cards are never touched because they are absent from the
 * {@link SalvageTable}. Sophisticated Backpacks (soft dependency, guarded by {@code ModList.isLoaded})
 * are swept through {@link BackpackCompat}: a backpack counts as equipment, so on a successful extraction
 * the body and its installed upgrades are kept while only the salvageable CONTENTS are liquidated.
 * <p>Per-unit values come from the {@link MarketSystem} (today's shortage good pays +50%). The earned
 * total is then scaled by every applicable bonus: gathering talents (+5/+15/+25%), the greed curse card
 * (+50%), the greed-card「饕餮」sacrificial burn (+200%) and the greed's-tail raid gamble (x2).
 */
public class SettlementService {

    /**
     * Settle one returning player: convert all salvage in their inventory into supply points.
     *
     * @return the supply points this player contributed
     */
    public static int settle(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return 0;
        }
        CampaignData campaign = CampaignData.get(server);
        boolean sbLoaded = ModList.get().isLoaded("sophisticatedbackpacks");
        int earned = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int unit = MarketSystem.salvageValue(campaign, stack.getItem());
            if (unit > 0) {
                earned += unit * stack.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            } else if (sbLoaded && BackpackCompat.isBackpack(stack)) {
                // Backpack body + installed upgrades are equipment and are kept; only liquidate its contents.
                earned += BackpackCompat.processContents(stack, s -> MarketSystem.salvageValue(campaign, s.getItem()), true);
            }
        }

        earned = Math.round(earned * settlementMultiplier(player));

        CampaignData data = CampaignData.get(server);
        if (earned > 0) {
            data.addSupplyPoints(earned);
        }
        applyExtractionBonuses(player, earned);
        EdenMessages.send(player, Type.SUCCESS, "eden.msg.settled", earned, data.getSupplyPoints());
        com.srqingchen.eden.season.SeasonSystem.onSuccessfulExtract(player, earned);
        return earned;
    }

    /** Speedrun window (§12 完美撤离): extract within this many minutes for the quick-extract bonus. */
    private static final float SPEEDRUN_MINUTES = 10.0f;
    private static final float FLAWLESS_BONUS = 0.30f;
    private static final float SPEEDRUN_BONUS = 0.30f;
    private static final float SOLO_BONUS = 0.20f;

    /**
     * Extraction bonuses (§12 完美撤离 + §9 单人补偿), paid as extra points on top of the haul:
     * flawless (no damage all raid) +30%, quick extract (inside 10 min) +30%, entered alone +20%.
     * A flawless extraction with salvage also earns a server-wide chronicle highlight.
     */
    private static void applyExtractionBonuses(ServerPlayer player, int earned) {
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        float bonus = 0f;
        boolean flawless = !state.tookDamage;
        boolean quick = (player.level().getGameTime() - state.raidStartTick) / 1200f <= SPEEDRUN_MINUTES;
        if (flawless) {
            bonus += FLAWLESS_BONUS;
            EdenMessages.send(player, Type.SPECIAL, "eden.msg.extract_flawless");
        }
        if (quick) {
            bonus += SPEEDRUN_BONUS;
            EdenMessages.send(player, Type.SPECIAL, "eden.msg.extract_speedrun");
        }
        if (state.soloRun) {
            bonus += SOLO_BONUS;
            EdenMessages.send(player, Type.INFO, "eden.msg.extract_solo_bonus");
        }
        CampaignData campaign = CampaignData.get(server);
        campaign.addSuccessfulExtract();
        if (bonus > 0f && earned > 0) {
            int extra = Math.round(earned * bonus);
            campaign.addSupplyPoints(extra);
            EdenMessages.send(player, Type.SUCCESS, "eden.msg.extract_bonus", extra);
        }
        if (flawless && earned > 0) {
            campaign.addHighlight("eden.chronicle.entry.extract", player.getName().getString(),
                    (int) ((FLAWLESS_BONUS + (quick ? SPEEDRUN_BONUS : 0f)) * 100f));
        }
    }

    /**
     * The full settlement bonus multiplier for this player right now: gathering talents, the greed curse
     * card, an active「饕餮」burn, and the greed's-tail flag (x2 on success, per §19.5).
     */
    public static float settlementMultiplier(ServerPlayer player) {
        float mult = 1f;
        if (TalentSystem.hasMech(player, "uni_gath_supply")) mult += 0.05f;
        if (TalentSystem.hasMech(player, "scv_supply")) mult += 0.15f;
        if (TalentSystem.hasMech(player, "scv_convert")) mult += 0.25f;
        if (CurioCards.isEquipped(player, (CardItem) EdenItems.CARD_GREED.get())) mult += 0.50f;
        if ("greed".equals(CurseCardSystem.activeBurnId(player))) mult += 2.00f;
        if (player.getData(EdenAttachments.RAID_STATE).greedTail) mult += 1.00f;
        return mult;
    }
}
