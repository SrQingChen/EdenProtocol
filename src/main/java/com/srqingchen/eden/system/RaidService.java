package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.dimension.RaidDimensionManager;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared raid lifecycle entry point, used by both the {@code /eden enter} command and the ark launch
 * pad so the two paths stay identical.
 */
public class RaidService {

    /**
     * Send the player into the polluted raid world and start their raid state.
     *
     * @return false if the raid dimension is unavailable (player left untouched)
     */
    public static boolean startRaid(ServerPlayer player, String difficulty) {
        return startRaid(player, difficulty, EdenDimensions.RAID_OVERWORLD);
    }

    /** Dimension-aware entry: overworld / nether / end expeditions share one lifecycle. */
    public static boolean startRaid(ServerPlayer player, String difficulty,
                                    ResourceKey<Level> dimension) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            if (dimension.equals(EdenDimensions.RAID_NETHER)) {
                RaidDimensionManager.prepareFreshNether(server);
            } else if (dimension.equals(EdenDimensions.RAID_END)) {
                RaidDimensionManager.prepareFreshEnd(server);
            } else {
                RaidDimensionManager.prepareFreshRaidWorld(server);
            }
        }
        if (!DimensionManager.teleport(player, dimension, 0.5D, 0.5D)) {
            return false;
        }
        ServerLevel raid = player.level() instanceof ServerLevel sl ? sl : null;
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        state.inRaid = true;
        state.difficulty = difficulty;
        state.raidStartTick = player.level().getGameTime();
        state.erosion = 0f;
        state.erosionLevel = 0;
        state.downed = false;
        state.raidDim = dimension.equals(EdenDimensions.RAID_NETHER) ? "nether"
                : dimension.equals(EdenDimensions.RAID_END) ? "end" : "overworld";
        // Solo compensation (§9): entered alone -> settlement +20% (many systems assume a crew).
        state.soloRun = raid != null && CrewScaler.crewCount(raid) <= 1;
        // Affixes are world-level: a crewmate already inside this raid shares theirs (same world); otherwise
        // this player is opening a fresh expedition (the world was just rebuilt) and rolls 1-3 new affixes.
        state.affixes = inheritOrRollAffixes(server, player, dimension);
        state.revealedAffixes = new ArrayList<>();
        // §11 progressive reveal: only the FIRST affix shows on entry; scanner sweeps, time (8 min each)
        // or the insight card peel the rest.
        if (!state.affixes.isEmpty()) {
            state.revealedAffixes.add(state.affixes.get(0));
        }
        if (server != null) {
            DifficultyLinkage.applyOnRaidStart(server, difficulty);
            CampaignData.get(server).addTotalRaid();
            // Seed evacuation points / oasis / cores into a brand-new overworld expedition (idempotent).
            if (raid != null && dimension.equals(EdenDimensions.RAID_OVERWORLD)) {
                RaidWorldFeatures.seedIfFresh(raid);
            }
            // Fresh end expedition: the polluted dragon holds the island until challenged (打龙).
            if (raid != null && dimension.equals(EdenDimensions.RAID_END)) {
                DragonHunt.spawnDragonIfAbsent(raid);
            }
        }
        EdenNetwork.syncTo(player);
        announceAffixes(player, state.revealedAffixes, state.affixes.size());
        // §19.5 gamble items: consume carried hunter beacons / greed's tails, inherit an active tail slow.
        RaidGambitSystem.consumeOnRaidStart(player);
        if (raid != null) {
            RaidGambitSystem.inheritGreedTail(player, raid);
        }
        return true;
    }

    /** Share the affixes of a crewmate already in this raid, or roll 1-3 fresh ones when opening a new world. */
    private static List<String> inheritOrRollAffixes(MinecraftServer server, ServerPlayer player,
                                                     ResourceKey<Level> dimension) {
        if (server != null) {
            ServerLevel raid = server.getLevel(dimension);
            if (raid != null) {
                for (ServerPlayer other : raid.players()) {
                    if (other == player) {
                        continue;
                    }
                    RaidState s = other.getData(EdenAttachments.RAID_STATE);
                    if (s.inRaid && !s.affixes.isEmpty()) {
                        return new ArrayList<>(s.affixes);
                    }
                }
            }
        }
        return rollAffixes(player);
    }

    /** Roll 1-3 distinct affixes for a brand-new expedition (cave-season affixes only while S1 is live). */
    private static List<String> rollAffixes(ServerPlayer player) {
        RandomSource rand = player.getRandom();
        boolean caveSeason = false;
        if (player.level().getServer() != null) {
            caveSeason = com.srqingchen.eden.season.Seasons.isCurrent(
                    player.level().getServer(), com.srqingchen.eden.season.S0CaveSeason.ID);
        }
        List<RaidAffix> pool = new ArrayList<>();
        for (RaidAffix a : RaidAffix.values()) {
            if (!a.caveSeason || caveSeason) {
                pool.add(a);
            }
        }
        int count = Math.min(pool.size(), 1 + rand.nextInt(3));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(pool.remove(rand.nextInt(pool.size())).id);
        }
        return result;
    }

    /** Reveal this raid's affixes to the arriving player so they can read them and adapt their plan. */
    private static void announceAffixes(ServerPlayer player, List<String> revealed, int total) {
        if (total == 0) {
            return;
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (String id : revealed) {
            RaidAffix a = RaidAffix.byId(id);
            if (a == null) {
                continue;
            }
            if (!first) {
                names.append(Component.literal("  \u2022  "));
            }
            names.append(Component.translatable(a.langName()));
            first = false;
        }
        int hidden = total - revealed.size();
        if (hidden > 0) {
            if (!first) {
                names.append(Component.literal("  \u2022  "));
            }
            names.append(Component.translatable("eden.affix.hidden_count", hidden));
        }
        EdenMessages.send(player, Type.WARNING, "eden.msg.affixes_revealed", names);
    }
}
