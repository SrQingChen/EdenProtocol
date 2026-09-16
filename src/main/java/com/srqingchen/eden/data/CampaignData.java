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

    public static final Codec<CampaignData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("supply_points").forGetter(d -> d.supplyPoints),
            Codec.LONG.optionalFieldOf("market_day", -1L).forGetter(d -> d.marketDay),
            Codec.STRING.optionalFieldOf("market_shortage", "").forGetter(d -> d.marketShortage),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("market_fluctuation", Map.of())
                    .forGetter(d -> d.marketFluctuation),
            Codec.FLOAT.optionalFieldOf("pollution", 100.0f).forGetter(d -> d.pollution),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(d -> d.stage),
            Codec.INT.optionalFieldOf("total_raids", 0).forGetter(d -> d.totalRaids),
            Codec.INT.optionalFieldOf("successful_extracts", 0).forGetter(d -> d.successfulExtracts),
            Codec.INT.optionalFieldOf("cores_destroyed", 0).forGetter(d -> d.coresDestroyed),
            Codec.INT.optionalFieldOf("dragons_slain", 0).forGetter(d -> d.dragonsSlain),
            Codec.BOOL.optionalFieldOf("paradise_unlocked", false).forGetter(d -> d.paradiseUnlocked),
            HIGHLIGHT_CODEC.listOf().optionalFieldOf("highlights", List.of()).forGetter(d -> d.highlights)
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
    /** Chronicle wall highlights, newest first. */
    private List<Highlight> highlights = new ArrayList<>();

    public CampaignData() {
        this.supplyPoints = 0;
        this.marketDay = -1L;
        this.marketShortage = "";
    }

    private CampaignData(int supplyPoints, long marketDay, String marketShortage,
                         Map<String, Float> marketFluctuation, float pollution, int stage,
                         int totalRaids, int successfulExtracts, int coresDestroyed, int dragonsSlain,
                         boolean paradiseUnlocked, List<Highlight> highlights) {
        this.supplyPoints = supplyPoints;
        this.marketDay = marketDay;
        this.marketShortage = marketShortage;
        this.marketFluctuation = new HashMap<>(marketFluctuation);
        this.pollution = pollution;
        this.stage = stage;
        this.totalRaids = totalRaids;
        this.successfulExtracts = successfulExtracts;
        this.coresDestroyed = coresDestroyed;
        this.dragonsSlain = dragonsSlain;
        this.paradiseUnlocked = paradiseUnlocked;
        this.highlights = new ArrayList<>(highlights);
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
