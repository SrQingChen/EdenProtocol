package com.srqingchen.eden.item;

import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Server-side helpers for reading / mutating the cards a player has equipped in their Curios
 * {@code eden:card} slots (size 3). Centralised here because several systems query equipped cards:
 * the erosion-growth multipliers ({@code ErosionSystem}), settlement bonuses
 * ({@code SettlementService} / {@code FailureRetention}) and the card actives
 * ({@code CurseCardSystem}).
 */
public final class CurioCards {
    private CurioCards() {}

    /** All equipped card stacks, in slot order (empty slots skipped). */
    public static List<ItemStack> equippedCards(ServerPlayer sp) {
        List<ItemStack> out = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(sp);
        if (handler == null) {
            return out;
        }
        IItemHandlerModifiable equipped = handler.getEquippedCurios();
        for (int i = 0; i < equipped.getSlots(); i++) {
            ItemStack stack = equipped.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CardItem) {
                out.add(stack);
            }
        }
        return out;
    }

    /** True when the player currently wears the given card item in any card slot. */
    public static boolean isEquipped(ServerPlayer sp, CardItem card) {
        for (ItemStack stack : equippedCards(sp)) {
            if (stack.getItem() == card) {
                return true;
            }
        }
        return false;
    }

    /** The equipped stack whose card's activeId equals the given id (for actives / sacrificial burn). */
    @Nullable
    public static ItemStack equippedWithActive(ServerPlayer sp, String activeId) {
        for (ItemStack stack : equippedCards(sp)) {
            if (((CardItem) stack.getItem()).activeId() != null
                    && ((CardItem) stack.getItem()).activeId().equals(activeId)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Remove (destroy) one equipped card stack by active id - the "sacrificial burn". Returns true when a
     * stack was found and burned.
     */
    public static boolean burnEquipped(ServerPlayer sp, String activeId) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(sp);
        if (handler == null) {
            return false;
        }
        IItemHandlerModifiable equipped = handler.getEquippedCurios();
        for (int i = 0; i < equipped.getSlots(); i++) {
            ItemStack stack = equipped.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CardItem card
                    && activeId.equals(card.activeId())) {
                equipped.setStackInSlot(i, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    // ---------- card passives other systems query ----------

    /**
     * Multiplier on natural erosion accumulation from equipped cards: curse cards speed it up
     * (greed x1.5, resonance x1.4, glass x1.3) while the Purity (净光) card slows it (x0.7). Multipliers
     * stack multiplicatively, so a triple-curse load-out bleeds erosion fast - the curse trade-off.
     */
    public static float erosionGrowthMultiplier(ServerPlayer sp) {
        float mult = 1.0f;
        for (ItemStack stack : equippedCards(sp)) {
            CardItem card = (CardItem) stack.getItem();
            if (card == EdenItems.CARD_PURITY.get()) {
                mult *= 0.7f;
                continue;
            }
            String id = card.activeId();
            if (id == null) {
                continue;
            }
            mult *= switch (id) {
                case "greed" -> 1.5f;
                case "resonance" -> 1.4f;
                case "glass" -> 1.3f;
                default -> 1.0f;
            };
        }
        return mult;
    }

    // ---------- greed passive: lure polluted mobs ----------

    private static final int LURE_INTERVAL = 100;   // every 5s
    private static final double LURE_RANGE = 32.0;
    private static final float LURE_CHANCE = 0.5f;

    /**
     * Greed's "attracts polluted mobs" passive: periodically pull nearby mobs onto the wearer. Runs only
     * inside a raid (the ark hub stays peaceful).
     */
    public static void greedLureTick(ServerPlayer sp, boolean hasGreed) {
        if (!hasGreed || sp.tickCount % LURE_INTERVAL != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) sp.level();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(sp.blockPosition()).inflate(LURE_RANGE));
        for (Mob mob : mobs) {
            if (mob.isAlive() && sp.getRandom().nextFloat() < LURE_CHANCE) {
                mob.setTarget(sp);
            }
        }
    }

    /** Convenience predicate: any equipped card passes. */
    public static boolean anyEquipped(ServerPlayer sp, Predicate<CardItem> test) {
        for (ItemStack stack : equippedCards(sp)) {
            if (test.test((CardItem) stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
