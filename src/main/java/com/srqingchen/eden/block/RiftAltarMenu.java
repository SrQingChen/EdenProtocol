package com.srqingchen.eden.block;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CardQuality;
import com.srqingchen.eden.system.RiftForge;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 裂隙祭坛 menu (v3, "truly vanilla" rewrite per user feedback): the altar opens the VANILLA
 * {@link MenuType#GENERIC_9x3} chest menu — the client renders a stock double-chest screen with
 * stock click / drag / shift-click / quick-craft behaviour, and nothing whatsoever needs to be
 * registered on the client (the previous two attempts died exactly there: first a hand-drawn
 * screen whose clicks never landed, then a custom MenuType whose screen registration never took
 * effect in the user's install).
 * <p>How it works: only the SERVER ever constructs this class (via the block's MenuProvider). The
 * client builds a plain vanilla ChestMenu from the wire type and mirrors slot contents; every
 * rule lives in server-side slot overrides, exactly like the rest of this mod's server-
 * authoritative design. Layout (slot index → meaning), first row of the chest:
 * <pre>
 *   [1][2][3] fuse inputs (pure cards, one shared quality)  [4] fuse result
 *   [5] ascend card   [6] ascend boss material              [7] ascend result
 * </pre>
 * Everything else is blocked. Result slots recompute on the input signature (never re-rolled
 * mid-preview); taking a result consumes its inputs; ascending also debits the shared supply
 * pool (affordability is checked in {@code mayPickUp}, which — on the single-threaded server —
 * makes the check-and-spend atomic). Anything still staged when the menu closes is handed back.
 */
public class RiftAltarMenu extends ChestMenu {

    /** Chest rows / total container slots. */
    public static final int ROWS = 3;
    public static final int SIZE = ROWS * 9;

    public static final int FUSE_A = 0, FUSE_B = 1, FUSE_C = 2, FUSE_RESULT = 3;
    public static final int ASC_CARD = 4, ASC_MAT = 5, ASC_RESULT = 6;

    private final Player owner;
    /** Input signatures the current result previews were rolled from ("\0" = never computed). */
    private String fuseSig = "\0";
    private String ascSig = "\0";

    public RiftAltarMenu(int containerId, Inventory playerInv) {
        super(MenuType.GENERIC_9x3, containerId, playerInv, new SimpleContainer(SIZE), ROWS);
        this.owner = playerInv.player;
        // Server-side slot surgery: swap the plain chest slots for rule-carrying ones. The client
        // never sees these (it runs its own vanilla ChestMenu); positions are cosmetic here.
        replace(FUSE_A, new FuseInputSlot(FUSE_A));
        replace(FUSE_B, new FuseInputSlot(FUSE_B));
        replace(FUSE_C, new FuseInputSlot(FUSE_C));
        replace(FUSE_RESULT, new ResultSlot(FUSE_RESULT, true));
        replace(ASC_CARD, new CardAnySlot(ASC_CARD));
        replace(ASC_MAT, new MaterialSlot(ASC_MAT));
        replace(ASC_RESULT, new ResultSlot(ASC_RESULT, false));
        for (int i = ASC_RESULT + 1; i < SIZE; i++) {
            replace(i, new BlockedSlot(i));
        }
    }

    private void replace(int index, Slot slot) {
        slot.index = index;
        this.slots.set(index, slot);
    }

    // ---------- result computation (signature-guarded, never re-rolls a live preview) ----------

    @Override
    public void broadcastChanges() {
        ensureResults();
        super.broadcastChanges();
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        ensureResults();
    }

    private void ensureResults() {
        if (this.owner.level().isClientSide()) {
            return;
        }
        SimpleContainer c = (SimpleContainer) getContainer();
        String fSig = sig(c.getItem(FUSE_A)) + "|" + sig(c.getItem(FUSE_B)) + "|" + sig(c.getItem(FUSE_C));
        if (!fSig.equals(this.fuseSig)) {
            this.fuseSig = fSig;
            c.setItem(FUSE_RESULT, RiftForge.rollFuse(
                    c.getItem(FUSE_A), c.getItem(FUSE_B), c.getItem(FUSE_C), this.owner.level().getRandom()));
        }
        boolean afford = !(this.owner.level() instanceof ServerLevel sl) || sl.getServer() == null
                || CampaignData.get(sl.getServer()).getSupplyPoints() >= RiftForge.ASCEND_SUPPLY_COST;
        String aSig = afford + "|" + sig(c.getItem(ASC_CARD)) + "|" + sig(c.getItem(ASC_MAT));
        if (!aSig.equals(this.ascSig)) {
            this.ascSig = aSig;
            c.setItem(ASC_RESULT, afford
                    ? RiftForge.rollAscend(c.getItem(ASC_CARD), c.getItem(ASC_MAT))
                    : ItemStack.EMPTY);
        }
    }

    private boolean affordAscend() {
        return this.owner.level() instanceof ServerLevel sl && sl.getServer() != null
                && CampaignData.get(sl.getServer()).spendSupplyPoints(RiftForge.ASCEND_SUPPLY_COST);
    }

    private static String sig(ItemStack stack) {
        if (stack.isEmpty()) {
            return "-";
        }
        // Card identity for the forge = item + count + quality + star; materials are just item + count.
        String extra = stack.getItem() instanceof CardItem c
                ? c.quality() + "*" + CardItem.starOf(stack) : "";
        return stack.getItem() + "@" + stack.getCount() + ":" + extra;
    }

    // ---------- interaction routing ----------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == FUSE_RESULT || index == ASC_RESULT) {
            if (!slot.mayPickup(player)) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.setByPlayer(ItemStack.EMPTY);
            slot.onTake(player, original);   // consume the inputs / debit supply
            ensureResults();
            return original;
        }
        if (index >= SIZE) {
            // Shift-click from the inventory stages into the recipe slots (fuse first, then ascend).
            return stage(stack) ? original : ItemStack.EMPTY;
        }
        if (index <= ASC_MAT) {
            // Un-stage an input back to the inventory.
            if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            ensureResults();
            return original;
        }
        return ItemStack.EMPTY;
    }

    /** Route one staged item into the first fitting recipe slot; true when anything moved. */
    private boolean stage(ItemStack stack) {
        SimpleContainer c = (SimpleContainer) getContainer();
        for (int i : new int[]{FUSE_A, FUSE_B, FUSE_C, ASC_CARD}) {
            if (c.getItem(i).isEmpty() && this.slots.get(i).mayPlace(stack)) {
                c.setItem(i, stack.split(1));
                ensureResults();
                return true;
            }
        }
        if (c.getItem(ASC_MAT).isEmpty() && this.slots.get(ASC_MAT).mayPlace(stack)) {
            c.setItem(ASC_MAT, stack.split(1));
            ensureResults();
            return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        SimpleContainer c = (SimpleContainer) getContainer();
        // Previews belong to the menu, not the player; staged INPUTS always go back.
        c.setItem(FUSE_RESULT, ItemStack.EMPTY);
        c.setItem(ASC_RESULT, ItemStack.EMPTY);
        this.clearContainer(player, c);
        super.removed(player);
    }

    // ---------- slot rules (server side only) ----------

    /** Fuse input: a PURE card whose quality matches whatever is already staged in the trio. */
    private final class FuseInputSlot extends Slot {
        FuseInputSlot(int index) {
            super((SimpleContainer) RiftAltarMenu.this.getContainer(), index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof CardItem card) || card.quality() == CardQuality.CURSE) {
                return false;
            }
            SimpleContainer c = (SimpleContainer) RiftAltarMenu.this.getContainer();
            for (int i : new int[]{FUSE_A, FUSE_B, FUSE_C}) {
                if (i == this.index) {
                    continue;
                }
                ItemStack other = c.getItem(i);
                if (other.getItem() instanceof CardItem oc && oc.quality() != card.quality()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Ascend card input: any card (pure or cursed — both can ascend). */
    private final class CardAnySlot extends Slot {
        CardAnySlot(int index) {
            super((SimpleContainer) RiftAltarMenu.this.getContainer(), index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof CardItem;
        }
    }

    /** Ascend material input: one of the three ecology-boss materials. */
    private final class MaterialSlot extends Slot {
        MaterialSlot(int index) {
            super((SimpleContainer) RiftAltarMenu.this.getContainer(), index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RiftForge.isBossMaterial(stack.getItem());
        }
    }

    /** Output-only slot; taking it consumes the recipe's inputs. */
    private final class ResultSlot extends Slot {
        private final boolean fuse;

        ResultSlot(int index, boolean fuse) {
            super((SimpleContainer) RiftAltarMenu.this.getContainer(), index, 0, 0);
            this.fuse = fuse;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack taken) {
            SimpleContainer c = (SimpleContainer) RiftAltarMenu.this.getContainer();
            if (this.fuse) {
                c.getItem(FUSE_A).shrink(1);
                c.getItem(FUSE_B).shrink(1);
                c.getItem(FUSE_C).shrink(1);
                if (player instanceof ServerPlayer) {
                    EdenMessages.overlay((ServerPlayer) player, Type.SUCCESS, "eden.forge.msg.fused", taken.getHoverName());
                }
            } else {
                // Debit the shared pool FIRST; another player's click could have drained it since
                // this preview was computed. If the debit fails, pull the taken stack back out of
                // the cursor - the preview was never real, and the inputs stay staged.
                if (!RiftAltarMenu.this.affordAscend()) {
                    RiftAltarMenu.this.setCarried(ItemStack.EMPTY);
                    if (player instanceof ServerPlayer sp) {
                        EdenMessages.overlay(sp, Type.WARNING, "eden.talent.msg.no_supply",
                                RiftForge.ASCEND_SUPPLY_COST, CampaignData.get(sp.level().getServer()).getSupplyPoints());
                    }
                    RiftAltarMenu.this.ascSig = " ";
                    RiftAltarMenu.this.ensureResults();
                    return;
                }
                c.getItem(ASC_CARD).shrink(1);
                c.getItem(ASC_MAT).shrink(1);
                if (player instanceof ServerPlayer) {
                    EdenMessages.overlay((ServerPlayer) player, Type.SUCCESS,
                            "eden.forge.msg.ascended", taken.getHoverName(), CardItem.starOf(taken));
                }
            }
            RiftAltarMenu.this.fuseSig = "\0";
            RiftAltarMenu.this.ascSig = "\0";
            RiftAltarMenu.this.ensureResults();
        }
    }

    /** Chest slots outside the recipe rows: nothing may ever live here. */
    private final class BlockedSlot extends Slot {
        BlockedSlot(int index) {
            super((SimpleContainer) RiftAltarMenu.this.getContainer(), index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
