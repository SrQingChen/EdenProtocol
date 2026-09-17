package com.srqingchen.eden.block;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CardQuality;
import com.srqingchen.eden.registry.EdenDataComponents;
import com.srqingchen.eden.system.RiftForge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 裂隙祭坛 menu (v2, vanilla-style rewrite per user feedback): a real
 * {@link AbstractContainerMenu} served through a {@link net.minecraft.world.MenuProvider}, so every
 * vanilla slot interaction - click, drag, shift-click, quick-craft - works out of the box and the
 * old code-drawn staging screen (whose clicks never landed) is retired.
 * <p>Two recipes side by side. FUSE: three same-quality PURE cards (slots 0-2) -> one rolled card
 * of the next tier in the result slot (3); the result shown is exactly the card you get. ASCEND: one
 * card (4) + one boss material (5) + {@link RiftForge#ASCEND_SUPPLY_COST} supply points -> the same
 * card at +1 star (result 6). Result previews recompute on every input change (server side, synced
 * to the client); taking a result consumes its inputs. Anything staged when the menu closes is
 * handed back (never eaten).
 */
public class RiftAltarMenu extends AbstractContainerMenu {

    /** Layout inside a 176x196 image: fuse row y=24, ascend row y=56, player inventory below. */
    private static final int FUSE_Y = 24, ASCEND_Y = 56;
    private static final int RESULT_X = 116;
    /** First player-inventory slot index in this menu (7 recipe slots come first). */
    private static final int FIRST_INV_SLOT = 7;

    private final Player owner;
    private final SimpleContainer fuseInputs = new SimpleContainer(3);
    private final SimpleContainer fuseResult = new SimpleContainer(1);
    private final SimpleContainer ascendInputs = new SimpleContainer(2);   // [0]=card [1]=material
    private final SimpleContainer ascendResult = new SimpleContainer(1);

    public RiftAltarMenu(int id, Inventory playerInv) {
        super(com.srqingchen.eden.registry.EdenMenus.RIFT_ALTAR.get(), id);
        this.owner = playerInv.player;

        for (int i = 0; i < 3; i++) {
            this.addSlot(new Slot(this.fuseInputs, i, 26 + i * 18, FUSE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof CardItem c && c.quality() != CardQuality.CURSE;
                }
            });
        }
        this.addSlot(new Slot(this.fuseResult, 0, RESULT_X, FUSE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;   // output only
            }

            @Override
            public void onTake(Player player, ItemStack taken) {
                for (int i = 0; i < 3; i++) {
                    RiftAltarMenu.this.fuseInputs.getItem(i).shrink(1);
                }
                RiftAltarMenu.this.slotsChanged(RiftAltarMenu.this.fuseInputs);
            }
        });
        this.addSlot(new Slot(this.ascendInputs, 0, 26, ASCEND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CardItem;
            }
        });
        this.addSlot(new Slot(this.ascendInputs, 1, 62, ASCEND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RiftForge.isBossMaterial(stack.getItem());
            }
        });
        this.addSlot(new Slot(this.ascendResult, 0, RESULT_X, ASCEND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack taken) {
                RiftAltarMenu.this.ascendInputs.getItem(0).shrink(1);
                RiftAltarMenu.this.ascendInputs.getItem(1).shrink(1);
                if (player.level() instanceof ServerLevel sl && sl.getServer() != null) {
                    CampaignData.get(sl.getServer()).spendSupplyPoints(RiftForge.ASCEND_SUPPLY_COST);
                }
                RiftAltarMenu.this.slotsChanged(RiftAltarMenu.this.ascendInputs);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 106 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 164));
        }
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        if (!(this.owner.level() instanceof ServerLevel level)) {
            return;   // client side: result slots arrive synced from the server
        }
        this.fuseResult.setItem(0, RiftForge.rollFuse(
                this.fuseInputs.getItem(0), this.fuseInputs.getItem(1), this.fuseInputs.getItem(2), level.getRandom()));
        boolean afford = level.getServer() == null
                || CampaignData.get(level.getServer()).getSupplyPoints() >= RiftForge.ASCEND_SUPPLY_COST;
        this.ascendResult.setItem(0, afford
                ? RiftForge.rollAscend(this.ascendInputs.getItem(0), this.ascendInputs.getItem(1))
                : ItemStack.EMPTY);
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < FIRST_INV_SLOT) {
            // Recipe / result slot -> player inventory (the vanilla tail fires onTake, consuming inputs).
            if (!this.moveItemStackTo(stack, FIRST_INV_SLOT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stageFromInventory(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    /** Shift-click staging from the inventory: pure cards fill fuse first, then any card, then materials. */
    private boolean stageFromInventory(ItemStack stack) {
        if (stack.getItem() instanceof CardItem c && c.quality() != CardQuality.CURSE) {
            for (int i = 0; i < 3; i++) {
                if (this.fuseInputs.getItem(i).isEmpty()) {
                    this.fuseInputs.setItem(i, stack.split(1));
                    this.slotsChanged(this.fuseInputs);
                    return true;
                }
            }
        }
        if (stack.getItem() instanceof CardItem && this.ascendInputs.getItem(0).isEmpty()) {
            this.ascendInputs.setItem(0, stack.split(1));
            this.slotsChanged(this.ascendInputs);
            return true;
        }
        if (RiftForge.isBossMaterial(stack.getItem()) && this.ascendInputs.getItem(1).isEmpty()) {
            this.ascendInputs.setItem(1, stack.split(1));
            this.slotsChanged(this.ascendInputs);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Anything still staged goes back to the player (dropped at their feet when full).
        this.clearContainer(player, this.fuseInputs);
        this.clearContainer(player, this.ascendInputs);
        this.fuseResult.setItem(0, ItemStack.EMPTY);
        this.ascendResult.setItem(0, ItemStack.EMPTY);
    }

    /** The ascend supply cost, for the client screen's label. */
    public static int ascendCost() {
        return RiftForge.ASCEND_SUPPLY_COST;
    }

    /** Client helper: the card star component the menu's ascend result would set (preview tooltip). */
    public static ItemStack previewStar(ItemStack card) {
        int star = Math.min(5, CardItem.starOf(card) + 1);
        ItemStack copy = card.copy();
        copy.set(EdenDataComponents.CARD_STAR.get(), star);
        return copy;
    }
}
