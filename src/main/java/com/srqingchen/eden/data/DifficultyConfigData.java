package com.srqingchen.eden.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-wide, persistent per-difficulty configuration, edited in-game through the difficulty editor item.
 * Per difficulty it stores: which entity_modifier profile to apply, the return-pod charge-efficiency
 * coefficients (base / per-cell / per-crystal), the ambient pollution-particle tuning (colour / scale / spawn chance / density /
 * drift speed - route A vanilla dust, where scale drives BOTH size and lifetime), and the card-pack roll
 * distributions (per-quality and per-star weights). Stored on the always-loaded overworld so it survives
 * restarts and is shared by the whole server (one difficulty config at a time, matching the co-op design).
 */
public class DifficultyConfigData extends SavedData {

    /** The raid difficulties, in a fixed order shared by the SavedData, the editor GUI and the payloads. */
    public static final List<String> DIFFICULTIES = List.of("scout", "salvage", "purge", "abyss", "endgame");

    /**
     * Charge-efficiency coefficients a/b/c: pod rate = min(cap, (a + cells*b + crystals*c) * registered/crew). Solo
     * with no fuel fills the 12000-charge pool in ~10 min at a=1.0; one cell (b=0.10) shaves it to ~9 min, one
     * crystal (c=0.05) to ~9.5 min. Fuel ONLY raises this rate (capped) - it never adds charge to the pool directly.
     */
    public static final float DEFAULT_BASE = 1.0f;
    public static final float DEFAULT_CELL = 0.10f;
    public static final float DEFAULT_CRYSTAL = 0.05f;

    /** Ambient particle defaults: taint-purple dust; scale drives size AND lifetime; chance ~1/64 per sample. */
    public static final int DEFAULT_PARTICLE_COLOR = 0x9B5CFF;
    public static final float DEFAULT_PARTICLE_SCALE = 1.0f;
    public static final float DEFAULT_PARTICLE_CHANCE = 0.015625f;
    public static final int DEFAULT_PARTICLE_DENSITY = 32;
    public static final float DEFAULT_PARTICLE_SPEED = 1.0f;

    /** Weight-list lengths: 5 qualities (COMMON..CURSE) and 5 star levels (1-5). */
    public static final int QUALITY_COUNT = 5;
    public static final int STAR_COUNT = 5;

    /** One difficulty's full config. The profile name defaults to the difficulty id. */
    public record Entry(String profile,
                        float chargeBase, float chargeCell, float chargeCrystal,
                        int particleColor, float particleScale, float particleChance, int particleDensity, float particleSpeed,
                        List<Integer> qualityWeights, List<Integer> starWeights) {
        // Every field but "profile" is OPTIONAL with a default, so a world saved by an older schema (which lacked the
        // particle / weight fields) still parses instead of throwing "No key ..." and discarding the ENTIRE config on
        // load. Add future fields here as optional so old saves keep loading.
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("profile").forGetter(Entry::profile),
                Codec.FLOAT.optionalFieldOf("charge_base", DEFAULT_BASE).forGetter(Entry::chargeBase),
                Codec.FLOAT.optionalFieldOf("charge_cell", DEFAULT_CELL).forGetter(Entry::chargeCell),
                Codec.FLOAT.optionalFieldOf("charge_crystal", DEFAULT_CRYSTAL).forGetter(Entry::chargeCrystal),
                Codec.INT.optionalFieldOf("particle_color", DEFAULT_PARTICLE_COLOR).forGetter(Entry::particleColor),
                Codec.FLOAT.optionalFieldOf("particle_scale", DEFAULT_PARTICLE_SCALE).forGetter(Entry::particleScale),
                Codec.FLOAT.optionalFieldOf("particle_chance", DEFAULT_PARTICLE_CHANCE).forGetter(Entry::particleChance),
                Codec.INT.optionalFieldOf("particle_density", DEFAULT_PARTICLE_DENSITY).forGetter(Entry::particleDensity),
                Codec.FLOAT.optionalFieldOf("particle_speed", DEFAULT_PARTICLE_SPEED).forGetter(Entry::particleSpeed),
                Codec.list(Codec.INT).optionalFieldOf("quality_weights", ones(QUALITY_COUNT)).forGetter(Entry::qualityWeights),
                Codec.list(Codec.INT).optionalFieldOf("star_weights", ones(STAR_COUNT)).forGetter(Entry::starWeights)
        ).apply(inst, Entry::new));
    }

    public static final Codec<DifficultyConfigData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Entry.CODEC).fieldOf("difficulties").forGetter(d -> d.entries)
    ).apply(inst, map -> {
        DifficultyConfigData data = new DifficultyConfigData();
        data.entries.putAll(map);
        return data;
    }));

    public static final SavedDataType<DifficultyConfigData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "difficulty_config"),
            DifficultyConfigData::new, CODEC);

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DifficultyConfigData() {
        for (String d : DIFFICULTIES) {
            this.entries.put(d, defaultEntry(d));
        }
    }

    /** A fully-populated default entry (equal card weights, purple particles, balanced charge). */
    public static Entry defaultEntry(String difficulty) {
        return new Entry(difficulty, DEFAULT_BASE, DEFAULT_CELL, DEFAULT_CRYSTAL,
                DEFAULT_PARTICLE_COLOR, DEFAULT_PARTICLE_SCALE, DEFAULT_PARTICLE_CHANCE,
                DEFAULT_PARTICLE_DENSITY, DEFAULT_PARTICLE_SPEED,
                ones(QUALITY_COUNT), ones(STAR_COUNT));
    }

    private static List<Integer> ones(int n) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(1);
        }
        return list;
    }

    /** The config for a difficulty, or a sensible default if it is somehow missing. */
    public Entry entryFor(String difficulty) {
        Entry e = this.entries.get(difficulty);
        return e != null ? e : defaultEntry(difficulty);
    }

    /** Replace a difficulty's config (from the editor) and mark dirty for persistence. */
    public void setEntry(String difficulty, Entry entry) {
        this.entries.put(difficulty, entry);
        setDirty();
    }

    /** Fetch (or create) the shared difficulty config from the always-loaded overworld. */
    public static DifficultyConfigData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
