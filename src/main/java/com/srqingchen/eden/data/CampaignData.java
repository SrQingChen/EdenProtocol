package com.srqingchen.eden.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-wide shared campaign progress (the ark and the Duststar campaign are shared by all players).
 * Holds the pooled supply points, the world-purification campaign (pollution % -> stage -> unlocks, see
 * {@link CampaignSystem}) and the chronicle highlights the wall renders.
 * <p>Also carries the ark MARKET state (功能清单 §19.3): the per-item buy-price fluctuations re-rolled
 * each in-game day and the daily "shortage good" whose salvage value pays +50%.
 * <p>26.1.2 {@link SavedData} is Codec-driven: a no-arg constructor + a {@link Codec}, registered via
 * a {@link SavedDataType} and fetched with {@code getDataStorage().computeIfAbsent(TYPE)}. Stored on the
 * always-loaded overworld so the pool survives across dimensions and restarts.
 */
public class CampaignData extends SavedData {

    /** One chronicle wall line: a translatable key + the hero's name + a numeric value. */
    public record Highlight(String key, String player, int value) {}

    public static final Codec<Highlight> HIGHLIGHT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("key").forGetter(Highlight::key),
            Codec.STRING.fieldOf("player").forGetter(Highlight::player),
            Codec.INT.optionalFieldOf("value", 0).forGetter(Highlight::value)
    ).apply(inst, Highlight::new));

    /**
     * Daily market state, grouped so the root codec stays under DFU's 16-field limit. Saved by older
     * versions with these keys FLAT at the root parses here as "absent" (defaults) - the market simply
     * re-rolls on the next in-game day, which it does anyway.
     */
    public record MarketState(long day, String shortage, Map<String, Float> fluctuation) {
        public static final Codec<MarketState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.optionalFieldOf("day", -1L).forGetter(MarketState::day),
                Codec.STRING.optionalFieldOf("shortage", "").forGetter(MarketState::shortage),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("fluctuation", Map.of())
                        .forGetter(MarketState::fluctuation)
        ).apply(inst, MarketState::new));
    }

    /**
     * 三值温度计 + 残页发现记录 (S1 批C), same grouping treatment. New data - no legacy shape exists.
     */
    public record ProtocolState(float purity, float symbiosis, float archive, List<String> pagesFound) {
        public static final Codec<ProtocolState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("purity", 0.0f).forGetter(ProtocolState::purity),
                Codec.FLOAT.optionalFieldOf("symbiosis", 0.0f).forGetter(ProtocolState::symbiosis),
                Codec.FLOAT.optionalFieldOf("archive", 0.0f).forGetter(ProtocolState::archive),
                Codec.STRING.listOf().optionalFieldOf("pages_found", List.of()).forGetter(ProtocolState::pagesFound)
        ).apply(inst, ProtocolState::new));
    }

    /**
     * Season-registry &amp; finale state (批 D). {@code seasonId} is the active season definition's
     * registry id; blank on pre-批D saves and resolved through the legacy {@code season_index}.
     * {@code finaleUnlocked} = the advance vote passed and the arena challenge stays open until
     * beaten; {@code seasonCompleted} = the finale ceremony has played out; {@code endingsSeen}
     * collects ending ids across multi-playthrough cycles (the chronicle's ending gallery).
     */
    public record SeasonState(String seasonId, boolean finaleUnlocked, boolean seasonCompleted,
                              List<String> endingsSeen) {
        public static final Codec<SeasonState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("season_id", "").forGetter(SeasonState::seasonId),
                Codec.BOOL.optionalFieldOf("finale_unlocked", false).forGetter(SeasonState::finaleUnlocked),
                Codec.BOOL.optionalFieldOf("season_completed", false).forGetter(SeasonState::seasonCompleted),
                Codec.STRING.listOf().optionalFieldOf("endings_seen", List.of()).forGetter(SeasonState::endingsSeen)
        ).apply(inst, SeasonState::new));
    }

    public static final Codec<CampaignData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("supply_points").forGetter(d -> d.supplyPoints),
            MarketState.CODEC.optionalFieldOf("market", new MarketState(-1L, "", Map.of()))
                    .forGetter(d -> new MarketState(d.marketDay, d.marketShortage, d.marketFluctuation)),
            Codec.FLOAT.optionalFieldOf("pollution", 100.0f).forGetter(d -> d.pollution),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(d -> d.stage),
            Codec.INT.optionalFieldOf("total_raids", 0).forGetter(d -> d.totalRaids),
            Codec.INT.optionalFieldOf("successful_extracts", 0).forGetter(d -> d.successfulExtracts),
            Codec.INT.optionalFieldOf("cores_destroyed", 0).forGetter(d -> d.coresDestroyed),
            Codec.INT.optionalFieldOf("dragons_slain", 0).forGetter(d -> d.dragonsSlain),
            Codec.BOOL.optionalFieldOf("paradise_unlocked", false).forGetter(d -> d.paradiseUnlocked),
            HIGHLIGHT_CODEC.listOf().optionalFieldOf("highlights", List.of()).forGetter(d -> d.highlights),
            Codec.INT.optionalFieldOf("season_index", 1).forGetter(d -> d.seasonIndex),
            Codec.STRING.listOf().optionalFieldOf("contracts_done", List.of()).forGetter(d -> d.contractsDone),
            Codec.LONG.optionalFieldOf("season_week_stamp", -1L).forGetter(d -> d.seasonWeekStamp),
            Codec.INT.optionalFieldOf("seasons_advanced", 0).forGetter(d -> d.seasonsAdvanced),
            ProtocolState.CODEC.optionalFieldOf("protocol", new ProtocolState(0f, 0f, 0f, List.of()))
                    .forGetter(d -> new ProtocolState(d.protocolPurity, d.protocolSymbiosis,
                            d.protocolArchive, d.pagesFound)),
            SeasonState.CODEC.optionalFieldOf("season_state", new SeasonState("", false, false, List.of()))
                    .forGetter(d -> new SeasonState(d.seasonId, d.finaleUnlocked, d.seasonCompleted, d.endingsSeen))
    ).apply(inst, CampaignData::new));

    public static final SavedDataType<CampaignData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "campaign"), CampaignData::new, CODEC);

    /** Cap on remembered chronicle highlights (newest first, oldest trimmed). */
    public static final int MAX_HIGHLIGHTS = 12;

    private int supplyPoints;
    /** The in-game day the market was last rolled for (game time / 24000). */
    private long marketDay;
    /** Item registry id of today's shortage good ("" = none / not yet rolled). */
    private String marketShortage;
    /** Shop key -> buy-price fluctuation in [-0.3, +0.3]. */
    private Map<String, Float> marketFluctuation = new HashMap<>();

    // ---------- Duststar purification campaign (§14) ----------
    /** World pollution percent, 100 (fully tainted) -> 0 (purified). */
    private float pollution = 100.0f;
    /** Current campaign stage 1-5 (0 = victory / fully purified). Derived from pollution, stored for rewards. */
    private int stage = 1;
    private int totalRaids;
    private int successfulExtracts;
    private int coresDestroyed;
    private int dragonsSlain;
    /** Paradise (eden:paradise) is open for visits once the campaign reaches 0% pollution. */
    private boolean paradiseUnlocked;
    /** Season system (S1 矿洞季): 1-based season number, per-season finished contract ids, weekly-focus state. */
    private int seasonIndex = 1;
    private List<String> contractsDone = new ArrayList<>();
    private long seasonWeekStamp = -1L;
    private int seasonsAdvanced = 0;
    /** Chronicle wall highlights, newest first. */
    private List<Highlight> highlights = new ArrayList<>();
    /** 三值温度计 (S1 批C): server-wide 0-100 counters, deliberately UNEXPLAINED to players. */
    private float protocolPurity;
    private float protocolSymbiosis;
    private float protocolArchive;
    /** Discovered《三位起草人》fragment pages, "kind:page" (kind = purity/symbiosis/archive), shared server-wide. */
    private List<String> pagesFound = new ArrayList<>();
    /** 批 D season-registry + finale state (see {@link SeasonState}). */
    private String seasonId = "";
    private boolean finaleUnlocked;
    private boolean seasonCompleted;
    private List<String> endingsSeen = new ArrayList<>();

    public CampaignData() {
        this.supplyPoints = 0;
        this.marketDay = -1L;
        this.marketShortage = "";
    }

    @SuppressWarnings("unused")   // built by the CODEC via the grouped MarketState / ProtocolState
    private CampaignData(int supplyPoints, MarketState market, float pollution, int stage,
                         int totalRaids, int successfulExtracts, int coresDestroyed, int dragonsSlain,
                         boolean paradiseUnlocked, List<Highlight> highlights, int seasonIndex,
                         List<String> contractsDone, long seasonWeekStamp, int seasonsAdvanced,
                         ProtocolState protocol, SeasonState seasonState) {
        this.seasonIndex = seasonIndex;
        this.contractsDone = new ArrayList<>(contractsDone);
        this.seasonWeekStamp = seasonWeekStamp;
        this.seasonsAdvanced = seasonsAdvanced;
        this.supplyPoints = supplyPoints;
        this.marketDay = market.day();
        this.marketShortage = market.shortage();
        this.marketFluctuation = new HashMap<>(market.fluctuation());
        this.pollution = pollution;
        this.stage = stage;
        this.totalRaids = totalRaids;
        this.successfulExtracts = successfulExtracts;
        this.coresDestroyed = coresDestroyed;
        this.dragonsSlain = dragonsSlain;
        this.paradiseUnlocked = paradiseUnlocked;
        this.highlights = new ArrayList<>(highlights);
        this.protocolPurity = protocol.purity();
        this.protocolSymbiosis = protocol.symbiosis();
        this.protocolArchive = protocol.archive();
        this.pagesFound = new ArrayList<>(protocol.pagesFound());
        this.seasonId = seasonState.seasonId();
        this.finaleUnlocked = seasonState.finaleUnlocked();
        this.seasonCompleted = seasonState.seasonCompleted();
        this.endingsSeen = new ArrayList<>(seasonState.endingsSeen());
    }

    public int getSupplyPoints() {
        return this.supplyPoints;
    }

    public void addSupplyPoints(int amount) {
        this.supplyPoints = Math.max(0, this.supplyPoints + amount);
        setDirty();
    }

    /** Spend from the shared pool; returns false if insufficient. Used by the ark shop in a later milestone. */
    public boolean spendSupplyPoints(int amount) {
        if (this.supplyPoints < amount) {
            return false;
        }
        this.supplyPoints -= amount;
        setDirty();
        return true;
    }

    // ---------- market state (§19.3) ----------

    public long marketDay() {
        return this.marketDay;
    }

    public void setMarketDay(long day) {
        this.marketDay = day;
        setDirty();
    }

    public String marketShortage() {
        return this.marketShortage;
    }

    public void setMarketShortage(String itemId) {
        this.marketShortage = itemId;
        setDirty();
    }

    public Map<String, Float> marketFluctuation() {
        return this.marketFluctuation;
    }

    public void setMarketFluctuation(Map<String, Float> fluctuation) {
        this.marketFluctuation = new HashMap<>(fluctuation);
        setDirty();
    }

    // ---------- purification campaign (§14) ----------

    public float pollution() {
        return this.pollution;
    }

    /** Direct pollution write (clamped 0..100); callers fire stage transitions via {@link CampaignSystem}. */
    public void setPollution(float value) {
        this.pollution = Math.max(0.0f, Math.min(100.0f, value));
        setDirty();
    }

    public int stage() {
        return this.stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
        setDirty();
    }

    public int totalRaids() {
        return this.totalRaids;
    }

    public void addTotalRaid() {
        this.totalRaids++;
        setDirty();
    }

    public int successfulExtracts() {
        return this.successfulExtracts;
    }

    public void addSuccessfulExtract() {
        this.successfulExtracts++;
        setDirty();
    }

    public int coresDestroyed() {
        return this.coresDestroyed;
    }

    public void addCoreDestroyed() {
        this.coresDestroyed++;
        setDirty();
    }

    public int dragonsSlain() {
        return this.dragonsSlain;
    }

    public void addDragonSlain() {
        this.dragonsSlain++;
        setDirty();
    }

    public boolean paradiseUnlocked() {
        return this.paradiseUnlocked;
    }

    public void setParadiseUnlocked(boolean unlocked) {
        this.paradiseUnlocked = unlocked;
        setDirty();
    }

    public int seasonIndex() {
        return this.seasonIndex;
    }

    public List<String> contractsDone() {
        return this.contractsDone;
    }

    public boolean isContractDone(String id) {
        return this.contractsDone.contains(id);
    }

    public void markContractDone(String id) {
        if (!this.contractsDone.contains(id)) {
            this.contractsDone.add(id);
            setDirty();
        }
    }

    public long seasonWeekStamp() {
        return this.seasonWeekStamp;
    }

    public void setSeasonWeek(long stamp) {
        this.seasonWeekStamp = stamp;
        setDirty();
    }

    public int seasonsAdvanced() {
        return this.seasonsAdvanced;
    }

    // ---------- 批 D season registry + finale state ----------

    /** Registry id of the active season definition ("" = not yet resolved; legacy saves). */
    public String seasonId() {
        return this.seasonId;
    }

    /** Admin override of the display index (see /eden season set); id routing goes through the registry. */
    public void setSeasonIndex(int index) {
        this.seasonIndex = Math.max(1, index);
        setDirty();
    }

    public void setSeasonId(String id) {
        if (!this.seasonId.equals(id)) {
            this.seasonId = id;
            setDirty();
        }
    }

    /** The advance vote passed: the arena challenge is open until beaten (multi-retry by design). */
    public boolean finaleUnlocked() {
        return this.finaleUnlocked;
    }

    public void setFinaleUnlocked(boolean unlocked) {
        this.finaleUnlocked = unlocked;
        setDirty();
    }

    /** This season's finale ceremony has played (paradise open, ending banked). */
    public boolean seasonCompleted() {
        return this.seasonCompleted;
    }

    public void setSeasonCompleted(boolean completed) {
        this.seasonCompleted = completed;
        setDirty();
    }

    /** Ending ids witnessed across ALL cycles (the chronicle's ending gallery). */
    public List<String> endingsSeen() {
        return this.endingsSeen;
    }

    public void markEndingSeen(String endingId) {
        if (!this.endingsSeen.contains(endingId)) {
            this.endingsSeen.add(endingId);
            setDirty();
        }
    }

    /**
     * Multi-playthrough cycle reset (重开赛季): season-layer progress starts over — contracts,
     * finale gate, fragment archive, thermometers and the campaign — while meta progression
     * (supply points, seasons-advanced history, ending gallery) stays.
     */
    public void resetSeasonCycle() {
        this.contractsDone = new ArrayList<>();
        this.finaleUnlocked = false;
        this.seasonCompleted = false;
        this.pagesFound = new ArrayList<>();
        this.protocolPurity = 0f;
        this.protocolSymbiosis = 0f;
        this.protocolArchive = 0f;
        this.pollution = 100.0f;
        this.stage = 1;
        this.totalRaids = 0;
        this.successfulExtracts = 0;
        this.coresDestroyed = 0;
        this.dragonsSlain = 0;
        this.paradiseUnlocked = false;
        this.highlights = new ArrayList<>();
        setDirty();
    }

    /** Season advance (finale complete → next season mod, or S0 re-run): campaign resets, meta stays. */
    public void advanceSeason() {
        this.seasonIndex++;
        this.seasonsAdvanced++;
        resetSeasonCycle();
    }

    // ---------- 三值温度计 + 残页 (S1 批C) ----------

    public float protocolPurity() {
        return this.protocolPurity;
    }

    public float protocolSymbiosis() {
        return this.protocolSymbiosis;
    }

    public float protocolArchive() {
        return this.protocolArchive;
    }

    public void addProtocolPurity(float amount) {
        this.protocolPurity = Math.max(0f, Math.min(100f, this.protocolPurity + amount));
        setDirty();
    }

    public void addProtocolSymbiosis(float amount) {
        this.protocolSymbiosis = Math.max(0f, Math.min(100f, this.protocolSymbiosis + amount));
        setDirty();
    }

    public void addProtocolArchive(float amount) {
        this.protocolArchive = Math.max(0f, Math.min(100f, this.protocolArchive + amount));
        setDirty();
    }

    public List<String> pagesFound() {
        return this.pagesFound;
    }

    /** Count of discovered pages for one fragment kind (purity/symbiosis/archive), 0-6. */
    public int pagesFound(String kind) {
        int n = 0;
        for (String p : this.pagesFound) {
            if (p.startsWith(kind + ":")) {
                n++;
            }
        }
        return n;
    }

    /** Bank a fragment page server-wide; true when it was the first copy (caller pays the reward). */
    public boolean discoverPage(String kind, int page) {
        String id = kind + ":" + page;
        if (this.pagesFound.contains(id)) {
            return false;
        }
        this.pagesFound.add(id);
        setDirty();
        return true;
    }

    public List<Highlight> highlights() {
        return this.highlights;
    }

    /** Remember a chronicle highlight (newest first; the oldest is trimmed past {@link #MAX_HIGHLIGHTS}). */
    public void addHighlight(String key, String player, int value) {
        this.highlights.add(0, new Highlight(key, player, value));
        if (this.highlights.size() > MAX_HIGHLIGHTS) {
            this.highlights.subList(MAX_HIGHLIGHTS, this.highlights.size()).clear();
        }
        setDirty();
    }

    /** Fetch (or create) the shared campaign data from the always-loaded overworld. */
    public static CampaignData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
