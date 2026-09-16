package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Duststar purification campaign (功能清单 §14): the world's pollution percent is the single
 * campaign gauge. Cores destroyed in expeditions, dragon kills and successful extractions push it down;
 * crossing a threshold advances the stage, which broadcasts server-wide, pays a stage reward into the
 * shared supply pool and unlocks the next difficulty tier (and, later, the deeper raid dimensions).
 * Reaching 0% crowns the server: paradise unlocks and the ark receives a paradise gate.
 * <p>Stage bands: 100-81 = 1, 80-61 = 2, 60-41 = 3, 40-21 = 4, 20-0+ = 5, exactly 0 = victory.
 */
public final class CampaignSystem {
    private CampaignSystem() {}

    /** Stage reached when entering each band; 0 is the victory state. */
    public static int stageForPollution(float pollution) {
        if (pollution <= 0.0f) {
            return 0;
        } else if (pollution <= 20.0f) {
            return 5;
        } else if (pollution <= 40.0f) {
            return 4;
        } else if (pollution <= 60.0f) {
            return 3;
        } else if (pollution <= 80.0f) {
            return 2;
        }
        return 1;
    }

    /** The campaign stage a difficulty tier needs (scout is always open). */
    public static int stageRequiredFor(String difficulty) {
        return switch (difficulty) {
            case "salvage" -> 2;
            case "purge" -> 3;
            case "abyss" -> 4;
            case "endgame" -> 5;
            default -> 1;   // scout
        };
    }

    /** True when the current campaign stage unlocks this difficulty for the launch pad. */
    public static boolean isDifficultyUnlocked(CampaignData data, String difficulty) {
        return data.stage() >= stageRequiredFor(difficulty) || data.stage() == 0;
    }

    /** Supply points paid into the shared pool when a stage is first reached (index = stage). */
    private static final int[] STAGE_REWARD = {200, 0, 30, 50, 80, 120};

    /**
     * Reduce the world pollution and process any stage transition (broadcast, reward, unlocks, victory).
     *
     * @param hero            the player whose deed pushed the gauge (chronicle highlight; may be null)
     * @param highlightKey    chronicle line key for this deed, or null for no highlight
     * @param highlightValue  numeric value shown in the chronicle line (e.g. cores destroyed)
     */
    public static void reducePollution(MinecraftServer server, float amount, ServerPlayer hero,
                                       String highlightKey, int highlightValue) {
        if (amount <= 0.0f) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        if (data.pollution() <= 0.0f) {
            return;   // already fully purified
        }
        data.setPollution(data.pollution() - amount);
        if (hero != null && highlightKey != null) {
            data.addHighlight(highlightKey, hero.getName().getString(), highlightValue);
        }
        int newStage = stageForPollution(data.pollution());
        if (newStage < data.stage()) {
            // One or more bands crossed: fire each intermediate stage-up so nothing is skipped.
            for (int s = data.stage() - 1; s >= Math.max(0, newStage); s--) {
                announceStage(server, data, s);
            }
        }
        EdenProtocol.LOGGER.info("[Eden] campaign pollution now {}% (stage {})", data.pollution(), data.stage());
    }

    /** Broadcast a stage arrival, pay its reward and unlock its content (victory at stage 0). */
    private static void announceStage(MinecraftServer server, CampaignData data, int stage) {
        data.setStage(stage);
        int reward = STAGE_REWARD[stage];
        if (reward > 0) {
            data.addSupplyPoints(reward);
        }
        String unlocked = stageUnlocks(stage);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (stage == 0) {
                EdenMessages.send(sp, Type.SPECIAL, "eden.campaign.victory");
                EdenMessages.send(sp, Type.SUCCESS, "eden.campaign.stage_reward", reward);
            } else {
                EdenMessages.send(sp, Type.SPECIAL, "eden.campaign.stage_up", stage, data.pollution());
                EdenMessages.send(sp, Type.SUCCESS, "eden.campaign.stage_reward", reward);
            }
            if (unlocked != null) {
                EdenMessages.send(sp, Type.INFO, "eden.campaign.unlocked", Component.translatable(unlocked));
            }
        }
        data.addHighlight(stage == 0 ? "eden.chronicle.entry.victory" : "eden.chronicle.entry.stage",
                "", stage);
        if (stage == 0 && !data.paradiseUnlocked()) {
            data.setParadiseUnlocked(true);
            ArkFacilities.placeParadiseGate(server);
        }
    }

    /** Lang key of the content unlocked at this stage (null = none to announce). */
    private static String stageUnlocks(int stage) {
        return switch (stage) {
            case 2 -> "eden.campaign.unlock.salvage";
            case 3 -> "eden.campaign.unlock.purge_nether";
            case 4 -> "eden.campaign.unlock.abyss_end";
            case 5 -> "eden.campaign.unlock.endgame";
            case 0 -> "eden.campaign.unlock.paradise";
            default -> null;
        };
    }
}
