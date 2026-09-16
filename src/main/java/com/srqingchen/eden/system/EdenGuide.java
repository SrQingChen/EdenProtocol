package com.srqingchen.eden.system;

import com.srqingchen.eden.util.EdenMessages;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Guide-book handout (帕秋莉手册接入, soft dependency). The book itself lives entirely in the data
 * pack ({@code assets/eden/patchouli_books/manual/}); this class only gives a copy to each player
 * once per world on their first ark arrival, when Patchouli is installed. All Patchouli classes are
 * confined to {@link Hooks} so a server without the mod never loads them.
 */
public final class EdenGuide {
    private EdenGuide() {}

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.isSpectator()) {
            return;
        }
        if (sp.getPersistentData().getBooleanOr("eden_guide_given", false)) {
            return;
        }
        // Only burn the once-flag once the book actually lands in the inventory: without Patchouli
        // we stay quiet AND un-flagged, so installing the mod later still hands the book out.
        if (!net.neoforged.fml.ModList.get().isLoaded("patchouli")) {
            return;
        }
        Hooks.give(sp);
    }

    /** Patchouli-touching code, only class-loaded behind the ModList guard. */
    private static final class Hooks {
        static void give(ServerPlayer sp) {
            var stack = vazkii.patchouli.api.PatchouliAPI.get().getBookStack(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("eden", "manual"));
            if (!stack.isEmpty() && sp.getInventory().add(stack)) {
                sp.getPersistentData().putBoolean("eden_guide_given", true);
                EdenMessages.send(sp, EdenMessages.Type.INFO, "eden.guide.received");
            }
        }
    }
}
