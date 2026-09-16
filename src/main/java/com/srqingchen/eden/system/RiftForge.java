package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CardQuality;
import com.srqingchen.eden.registry.EdenDataComponents;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 裂隙祭坛 forging (功能清单 §8 v2 卡牌锻造): the server-side recipe validation for the rift altar.
 * Two recipes, both executed against concrete INVENTORY SLOTS named by the client (re-validated here):
 * <ul>
 *   <li><b>FUSE 融合</b> - any three same-quality PURE cards fuse into one random card of the next
 *   quality (star 1-2). Three legendaries (top tier) fuse into one random legendary that keeps the
 *   highest source star +1. Duplicates stop being dead weight.</li>
 *   <li><b>ASCEND 升星</b> - one boss material (孢子囊/浊鳞/掘凿爪) + supply points raise ONE card's
 *   star by +1 (max 5). The ecology loop feeds directly into card power.</li>
 * </ul>
 */
public final class RiftForge {
    private RiftForge() {}

    /** Supply-point cost of one ascend (shared campaign pool sink). */
    public static final int ASCEND_SUPPLY_COST = 25;
    /** Star cap (matches CardPool's 1-5 star roll range). */
    private static final int STAR_MAX = 5;
    /** Pure (non-curse) quality order as registered in CardQuality.ORDER. */
    private static final List<CardQuality> PURE = CardQuality.ORDER.stream()
            .filter(q -> q != CardQuality.CURSE).toList();

    /** FUSE: three same-quality pure cards -> one rolled card of the next tier. */
    public static boolean fuse(ServerPlayer p, int slotA, int slotB, int slotC) {
        if (!validSlot(p, slotA) || !validSlot(p, slotB) || !validSlot(p, slotC)) {
            return false;
        }
        if (slotA == slotB || slotA == slotC || slotB == slotC) {
            return false;
        }
        ItemStack a = p.getInventory().getItem(slotA);
        ItemStack b = p.getInventory().getItem(slotB);
        ItemStack c = p.getInventory().getItem(slotC);
        if (!(a.getItem() instanceof CardItem ca) || !(b.getItem() instanceof CardItem cb)
                || !(c.getItem() instanceof CardItem cc)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.forge.msg.need_cards");
            return false;
        }
        CardQuality q = ca.quality();
        if (q == CardQuality.CURSE || cb.quality() != q || cc.quality() != q) {
            EdenMessages.overlay(p, Type.WARNING, "eden.forge.msg.need_same_quality");
            return false;
        }
        int bestStar = Math.max(CardItem.starOf(a), Math.max(CardItem.starOf(b), CardItem.starOf(c)));
        a.shrink(1);
        b.shrink(1);
        c.shrink(1);
        RandomSource rand = p.level().getRandom();
        int tier = PURE.indexOf(q);
        ItemStack result;
        if (tier >= 0 && tier < PURE.size() - 1) {
            result = rollOfQuality(rand, PURE.get(tier + 1), 1 + rand.nextInt(2));
        } else {
            // Top tier in: a legendary that carries the best source star +1.
            result = rollOfQuality(rand, PURE.get(PURE.size() - 1), Math.min(STAR_MAX, bestStar + 1));
        }
        if (!result.isEmpty() && !p.getInventory().add(result)) {
            p.drop(result, false);
        }
        EdenMessages.overlay(p, Type.SUCCESS, "eden.forge.msg.fused", result.getHoverName());
        return true;
    }

    /** ASCEND: one boss material + supply points -> +1 star on one card (max 5). */
    public static boolean ascend(ServerPlayer p, int cardSlot, int materialSlot) {
        if (!validSlot(p, cardSlot) || !validSlot(p, materialSlot) || cardSlot == materialSlot) {
            return false;
        }
        ItemStack card = p.getInventory().getItem(cardSlot);
        ItemStack material = p.getInventory().getItem(materialSlot);
        if (!(card.getItem() instanceof CardItem)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.forge.msg.need_card");
            return false;
        }
        if (!isBossMaterial(material.getItem())) {
            EdenMessages.overlay(p, Type.WARNING, "eden.forge.msg.need_material");
            return false;
        }
        int star = CardItem.starOf(card);
        if (star >= STAR_MAX) {
            EdenMessages.overlay(p, Type.WARNING, "eden.forge.msg.star_max");
            return false;
        }
        CampaignData campaign = CampaignData.get(p.level().getServer());
        if (!campaign.spendSupplyPoints(ASCEND_SUPPLY_COST)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.talent.msg.no_supply",
                    ASCEND_SUPPLY_COST, campaign.getSupplyPoints());
            return false;
        }
        material.shrink(1);
        card.set(EdenDataComponents.CARD_STAR.get(), star + 1);
        EdenMessages.overlay(p, Type.SUCCESS, "eden.forge.msg.ascended", card.getHoverName(), star + 1);
        return true;
    }

    /** Random card of one exact quality with a fixed star (registry auto-discovery, like CardPool). */
    private static ItemStack rollOfQuality(RandomSource rand, CardQuality quality, int star) {
        List<CardItem> pool = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof CardItem ci && ci.quality() == quality) {
                pool.add(ci);
            }
        }
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(pool.get(rand.nextInt(pool.size())));
        stack.set(EdenDataComponents.CARD_STAR.get(), Math.max(1, Math.min(STAR_MAX, star)));
        return stack;
    }

    private static boolean validSlot(ServerPlayer p, int slot) {
        return slot >= 0 && slot < 36 && !p.getInventory().getItem(slot).isEmpty();
    }

    public static boolean isBossMaterial(Item item) {
        return item == EdenItems.SPORE_SAC.get() || item == EdenItems.TAINTED_SCALE.get()
                || item == EdenItems.EXCAVATOR_CLAW.get();
    }
}
