package com.srqingchen.eden.system;

import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import java.util.function.ToIntFunction;

/**
 * Sophisticated Backpacks soft-dependency bridge. Isolated in its own class so the SB classes are only
 * linked when this class is first touched - callers MUST guard every call with
 * {@code ModList.get().isLoaded("sophisticatedbackpacks")} so this class is never loaded when SB is absent.
 * <p>A backpack's contents live in the wrapper's {@link InventoryHandler}, NOT in the player inventory, so
 * end-of-raid settlement has to reach in here. Per design the backpack item itself (together with its
 * installed upgrade items) counts as equipment: on a successful extraction it is KEPT and only its
 * salvageable CONTENTS are liquidated; on failure the whole backpack is lost and its contents are merely
 * valued (the caller then scales that by the keep-ratio) without being moved.
 */
public final class BackpackCompat {
    private BackpackCompat() {}

    /** True if this stack is a Sophisticated Backpack (its contents live in a wrapper, not the player inventory). */
    public static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BackpackItem;
    }

    /**
     * Walk the backpack's contents. Every salvageable stack contributes {@code value * count} to the total;
     * when {@code clear} is true those slots are also emptied (a successful extraction liquidates the contents
     * into supply points while keeping the backpack). Non-salvageable contents (gear / cards / tools) are never
     * touched. The backpack item and its upgrades are the caller's concern, not handled here.
     *
     * @param salvageValue per-stack supply-point unit value (0 = not salvage), e.g. {@code s -> SalvageTable.valueOf(s.getItem())}
     * @param clear        whether to empty the liquidated slots (true on success, false on failure)
     * @return the total supply-point value of the salvageable contents
     */
    public static int processContents(ItemStack stack, ToIntFunction<ItemStack> salvageValue, boolean clear) {
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(stack);
        InventoryHandler inv = wrapper.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack content = inv.getInternalStack(i);
            if (content.isEmpty()) {
                continue;
            }
            int unit = salvageValue.applyAsInt(content);
            if (unit <= 0) {
                continue;   // gear / cards / tools inside the backpack stay put
            }
            total += unit * content.getCount();
            if (clear) {
                inv.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        return total;
    }
}
