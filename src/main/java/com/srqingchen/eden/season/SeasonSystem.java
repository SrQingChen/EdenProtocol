package com.srqingchen.eden.season;

import com.srqingchen.eden.system.RaidWorldFeatures;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Season & expedition-contract core (S1 矿洞季, 批 A). A season is a themed content package; each
 * ships FIVE contracts (委托) that any class can complete - the class changes HOW, never WHETHER.
 * At launch a player may carry at most ONE; the contract only pays out when the run ends in a
 * SUCCESSFUL extraction. Three of five done unlocks the server-wide season advance (campaign resets,
 * meta progression stays).
 * <p>Progress tracking is per-run session state (static map with login/logout cleanup, mirroring
 * CurseCardSystem) so the persisted RaidState schema stays untouched in batch A.
 */
public final class SeasonSystem {
    private SeasonSystem() {}

    /** One contract: a goal type, a numeric target and the supply-point reward on payout. */
    public record Contract(String id, Goal goal, int target, int rewardSupply) {

        public String nameKey() {
            return "eden.season.contract." + id;
        }

        public String descKey() {
            return "eden.season.contract." + id + ".desc";
        }
    }

    /** Goal types - each wired to concrete tracking events (see the tick/break/death hooks). */
    public enum Goal {
        /** Walk N blocks of natural cave passages (y<0, sky-occluded, sampled). */
        SURVEY,
        /** Mine N deepslate-era ore blocks (y<0). */
        MINE,
        /** Spend one full game night inside a mineshaft or trial chamber. */
        LODGE,
        /** Extract with a settlement worth >= target supply points. */
        HAUL,
        /** Kill an ecology boss or three lair-guard waves. */
        PURGE
    }

    /** S0 矿洞季 contracts (profession-agnostic by design); owned by the season definition. */
    public static final List<Contract> CAVE_CONTRACTS = List.of(
            new Contract("survey", Goal.SURVEY, 300, 60),
            new Contract("miner", Goal.MINE, 96, 50),
            new Contract("lodge", Goal.LODGE, 1, 55),
            new Contract("haul", Goal.HAUL, 60, 50),
            new Contract("purge", Goal.PURGE, 3, 65));

    // ---------- per-run session state ----------

    /** Live run state: the carried contract + rolling progress counters. */
    public static final class Run {
        public Contract contract;
        public int surveyBlocks;
        public int minedOres;
        public int purgeKills;
        public int lodgeTicks;
        /** 批C temperature feeding: tainted blocks mined this run (symbiosis "clean hands" check). */
        public int taintedMined;
        /** 批C: visited the hope oasis at least once this run. */
        public boolean visitedOasis;
    }

    private static final java.util.Map<java.util.UUID, Run> RUNS = new java.util.HashMap<>();

    public static Run run(ServerPlayer sp) {
        return RUNS.computeIfAbsent(sp.getUUID(), k -> new Run());
    }

    /** Choose the contract to carry (at most one; null/empty id clears it). */
    public static void selectContract(ServerPlayer sp, String id) {
        Run r = run(sp);
        List<Contract> pool = currentContracts(sp.level().getServer());
        r.contract = pool == null ? null : pool.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
        if (r.contract != null) {
            EdenMessages.overlay(sp, Type.INFO, "eden.season.msg.selected",
                    net.minecraft.network.chat.Component.translatable(r.contract.nameKey()));
        } else {
            EdenMessages.overlay(sp, Type.INFO, "eden.season.msg.cleared");
        }
    }

    public static void onLogout(java.util.UUID id) {
        RUNS.remove(id);
    }

    /** Weekly focus contract index (rotates every real week, payout x1.5). */
    public static int weeklyFocusIndex(MinecraftServer server) {
        long week = System.currentTimeMillis() / (1000L * 60 * 60 * 24 * 7);
        var data = com.srqingchen.eden.data.CampaignData.get(server);
        if (data.seasonWeekStamp() != week) {
            data.setSeasonWeek(week);
        }
        List<Contract> pool = currentContracts(server);
        int n = pool == null ? 0 : pool.size();
        return n == 0 ? 0 : (int) (week % n);
    }

    // ---------- payout (called from SettlementService on a successful extraction) ----------

    /** Judge the carried contract against the run's counters; payout + broadcast on success. */
    public static void onSuccessfulExtract(ServerPlayer sp, int earnedSupply) {
        Run r = RUNS.get(sp.getUUID());
        MinecraftServer server = sp.level().getServer();
        if (r == null || r.contract == null || server == null) {
            return;
        }
        var data = com.srqingchen.eden.data.CampaignData.get(server);
        if (data.isContractDone(r.contract.id())) {
            return;   // already banked this season
        }
        boolean done = switch (r.contract.goal()) {
            case SURVEY -> r.surveyBlocks >= r.contract.target();
            case MINE -> r.minedOres >= r.contract.target();
            case LODGE -> r.lodgeTicks >= r.contract.target();
            case HAUL -> earnedSupply >= r.contract.target();
            case PURGE -> r.purgeKills >= r.contract.target();
        };
        if (!done) {
            EdenMessages.overlay(sp, Type.WARNING, "eden.season.msg.missed",
                    net.minecraft.network.chat.Component.translatable(r.contract.nameKey()));
            return;
        }
        List<Contract> pool = currentContracts(server);
        int focusIdx = weeklyFocusIndex(server);
        boolean focus = pool != null && focusIdx < pool.size()
                && pool.get(focusIdx).id().equals(r.contract.id());
        int pay = Math.round(r.contract.rewardSupply() * (focus ? 1.5f : 1f));
        data.markContractDone(r.contract.id());
        data.addSupplyPoints(pay);
        EdenMessages.send(sp, Type.SUCCESS, "eden.season.msg.completed",
                net.minecraft.network.chat.Component.translatable(r.contract.nameKey()), pay);
        // Server-wide milestone broadcast when the crew reaches the advance threshold (3/5).
        long doneCount = data.contractsDone().size();
        if (doneCount == 3) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                EdenMessages.send(p, Type.SPECIAL, "eden.season.msg.threshold");
            }
        }
    }

/** Season advance gate: the active season's threshold (3-of-5 by default); a COMPLETED season
     * always re-opens the gate for the multi-playthrough re-run vote. */
    public static boolean canAdvance(MinecraftServer server) {
        var data = com.srqingchen.eden.data.CampaignData.get(server);
        if (data.seasonCompleted()) {
            return true;
        }
        SeasonDefinition season = Seasons.current(server);
        int need = season == null ? 3 : season.advanceThreshold();
        return data.contractsDone().size() >= need;
    }

    /** Multi-playthrough re-run: same season from the top; endings/meta stay banked. */
    public static void rerunSeason(MinecraftServer server) {
        var data = com.srqingchen.eden.data.CampaignData.get(server);
        data.resetSeasonCycle();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, "eden.season.rerun",
                    net.minecraft.network.chat.Component.translatable(
                            Seasons.current(server).titleKey()));
        }
        com.srqingchen.eden.system.CinematicSystem.playSeasonChange(server);
    }

    /** Advance the season (called from the chronicle-wall button / admin command). */
    public static void advanceSeason(MinecraftServer server) {
        if (!canAdvance(server)) {
            return;
        }
        var data = com.srqingchen.eden.data.CampaignData.get(server);
        data.advanceSeason();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, "eden.season.msg.advanced", data.seasonIndex());
        }
        com.srqingchen.eden.system.CinematicSystem.playSeasonChange(server);
    }

    // ---------- server-wide advance vote (S1 批A 编年史推进按钮) ----------

    /** Vote lifetime: first click opens the window, majority (> half of online) advances. */
    private static final long VOTE_WINDOW_MS = 30_000L;
    private static long voteDeadline = 0L;
    private static final java.util.Set<java.util.UUID> votes = new java.util.HashSet<>();

    /** Live vote state for the chronicle payload (also lazily expires a finished window). */
    public static boolean[] voteState(MinecraftServer server) {
        boolean active = System.currentTimeMillis() <= voteDeadline;
        if (!active && voteDeadline != 0L) {
            votes.clear();
            voteDeadline = 0L;
        }
        int need = server.getPlayerList().getPlayerCount() / 2 + 1;
        return new boolean[]{active, votes.size() >= need};
    }

    public static int voteYesCount() {
        return votes.size();
    }

    /** One click on the chronicle wall's advance button: open or join the vote; majority advances. */
    public static void castAdvanceVote(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        if (server == null || !canAdvance(server)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > voteDeadline) {
            votes.clear();
            voteDeadline = now + VOTE_WINDOW_MS;
            votes.add(sp.getUUID());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                EdenMessages.send(p, Type.SPECIAL, "eden.season.vote.open", sp.getName().getString());
            }
        } else if (votes.add(sp.getUUID())) {
            EdenMessages.send(sp, Type.INFO, "eden.season.vote.counted");
        }
        int need = server.getPlayerList().getPlayerCount() / 2 + 1;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.INFO, "eden.season.vote.progress", votes.size(), need);
        }
        if (votes.size() >= need) {
            votes.clear();
            voteDeadline = 0L;
            // 批 D: a passed vote opens the finale gate permanently (the season turns over only
            // when the arena's apex boss falls); on a COMPLETED season the same vote re-runs it
            // (multi-playthrough: campaign starts over, meta progression + ending gallery stay).
            if (com.srqingchen.eden.data.CampaignData.get(server).seasonCompleted()) {
                rerunSeason(server);
            } else {
                com.srqingchen.eden.system.FinaleSystem.onVotePassed(server);
            }
        }
    }

    /** The contract id the player currently carries ("" = none) - for the launch pad payload. */
    public static String currentContractId(ServerPlayer sp) {
        Run r = RUNS.get(sp.getUUID());
        return r != null && r.contract != null ? r.contract.id() : "";
    }

    /** Contracts of the CURRENT season, straight from the active season definition (批 D). */
    public static List<Contract> currentContracts(MinecraftServer server) {
        SeasonDefinition season = Seasons.current(server);
        return season == null ? List.of() : season.contracts();
    }

    /** Convenience for HUD/GUI: is the player mid-raid in a raid level. */
    public static boolean inRaid(ServerPlayer sp) {
        return sp.level() instanceof net.minecraft.server.level.ServerLevel sl
                && RaidWorldFeatures.isRaidLevel(sl);
    }
}
