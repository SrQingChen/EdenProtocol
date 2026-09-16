package com.srqingchen.eden.system;

import com.entitymodifier.data.ConfigProfileManager;
import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.data.DifficultyConfigData;
import net.minecraft.server.MinecraftServer;

/**
 * Links the raid difficulty to entity_modifier's config profiles. entity_modifier is a hard dependency, so
 * {@link ConfigProfileManager} is called directly (no reflection). On raid start we look up the profile the
 * admin assigned to that difficulty (via the in-game difficulty editor, stored in {@link DifficultyConfigData})
 * and apply it, tuning mobs server-wide. If the assigned profile does not exist, {@code applyProfile} returns
 * false and the raid runs with default mobs.
 * <p>Because a profile is server-wide, this matches the "one shared campaign / one difficulty at a time"
 * design; the ark is a safe hub, so buffing mobs globally is harmless there.
 */
public class DifficultyLinkage {

    public static void applyOnRaidStart(MinecraftServer server, String difficulty) {
        String profile = DifficultyConfigData.get(server).entryFor(difficulty).profile();
        if (profile == null || profile.isEmpty()) {
            return;
        }
        if (ConfigProfileManager.applyProfile(profile)) {
            EdenProtocol.LOGGER.info("[Eden] Applied entity_modifier profile '{}' for difficulty '{}'", profile, difficulty);
        } else {
            EdenProtocol.LOGGER.info("[Eden] entity_modifier profile '{}' (difficulty '{}') not found; raid uses default mobs",
                    profile, difficulty);
        }
    }
}
