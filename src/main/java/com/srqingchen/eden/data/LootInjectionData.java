package com.srqingchen.eden.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-wide, persistent configuration for the chest-loot injection system (§12 战利品注入),
 * edited in-game through the difficulty editor's loot tab. The injection itself happens in
 * {@code LootInjectionSystem.onLootTableLoad}: every chest loot table that loads (vanilla, mod or
 * datapack - the event fires for all of them) gains one extra pool per raid difficulty, gated at
 * roll time by "opener is in a raid of that difficulty" conditions.
 * <p>Matching rules: a table matches when its path starts with any of {@link #prefixes}, its
 * namespace is allowed ({@link #allNamespaces} or {@code minecraft:}) and its full id is not in
 * {@link #exclusions}. Tables under our own {@code eden:} namespace never match - those are the
 * mod's own supply sources and must not double up.
 * <p>The SavedData lives on the always-loaded overworld. During the FIRST datapack load of a boot
 * the overworld does not exist yet, so {@link #snapshot(MinecraftServer)} falls back to a shared
 * DEFAULTS instance; if those defaults differ from what was applied, the system re-triggers a
 * datapack reload right after server start (and after every GUI save) so the saved config wins.
 */
public class LootInjectionData extends SavedData {

    /** Editable pool slots per difficulty (GUI rows). */
    public static final int MAX_ITEMS = 6;

    /** One injectable item: id + weight + count range + per-entry roll chance. */
    public record ItemEntry(String item, int weight, int minCount, int maxCount, float chance) {
        public static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("item").forGetter(ItemEntry::item),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(ItemEntry::weight),
                Codec.INT.optionalFieldOf("min_count", 1).forGetter(ItemEntry::minCount),
                Codec.INT.optionalFieldOf("max_count", 1).forGetter(ItemEntry::maxCount),
                Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(ItemEntry::chance)
        ).apply(inst, ItemEntry::new));
    }

    /** One difficulty's injected pool: rolls range + the item list. */
    public record PoolConfig(int rollsMin, int rollsMax, List<ItemEntry> items) {
        public static final Codec<PoolConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.optionalFieldOf("rolls_min", 1).forGetter(PoolConfig::rollsMin),
                Codec.INT.optionalFieldOf("rolls_max", 2).forGetter(PoolConfig::rollsMax),
                Codec.list(ItemEntry.CODEC).optionalFieldOf("items", List.of()).forGetter(PoolConfig::items)
        ).apply(inst, PoolConfig::new));
    }

    /**
     * One table's EXPLICIT override (0.3.4 逐表编辑): a forced on/off switch that bypasses the
     * global prefix rules, plus optional per-difficulty pools replacing the global ones. An empty
     * pools map falls back to the global per-difficulty pools for the tables that are only
     * force-enabled/disabled.
     */
    public record TableOverride(boolean enabled, Map<String, PoolConfig> pools) {
        public static final Codec<TableOverride> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(TableOverride::enabled),
                Codec.unboundedMap(Codec.STRING, PoolConfig.CODEC).optionalFieldOf("pools", Map.of())
                        .forGetter(TableOverride::pools)
        ).apply(inst, TableOverride::new));
    }

    public static final Codec<LootInjectionData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(d -> d.enabled),
            Codec.list(Codec.STRING).optionalFieldOf("prefixes", List.of("chests/")).forGetter(d -> d.prefixes),
            Codec.list(Codec.STRING).optionalFieldOf("exclusions", List.of()).forGetter(d -> d.exclusions),
            Codec.BOOL.optionalFieldOf("all_namespaces", true).forGetter(d -> d.allNamespaces),
            Codec.unboundedMap(Codec.STRING, PoolConfig.CODEC).optionalFieldOf("pools", Map.of()).forGetter(d -> d.pools),
            Codec.unboundedMap(Codec.STRING, TableOverride.CODEC).optionalFieldOf("tables", Map.of()).forGetter(d -> d.tables)
    ).apply(inst, (enabled, prefixes, exclusions, allNs, pools, tables) -> {
        LootInjectionData data = new LootInjectionData();
        data.enabled = enabled;
        data.prefixes = new ArrayList<>(prefixes);
        data.exclusions = new ArrayList<>(exclusions);
        data.allNamespaces = allNs;
        data.pools.putAll(pools);
        data.tables.putAll(tables);
        return data;
    }));

    public static final SavedDataType<LootInjectionData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "loot_injection"),
            LootInjectionData::new, CODEC);

    /** Shared defaults used whenever the SavedData is not reachable (first load of a boot). */
    private static final LootInjectionData DEFAULTS = new LootInjectionData();

    public boolean enabled = true;
    public List<String> prefixes = new ArrayList<>(List.of("chests/"));
    public List<String> exclusions = new ArrayList<>();
    public boolean allNamespaces = true;
    public final Map<String, PoolConfig> pools = new LinkedHashMap<>();
    /** Per-table explicit overrides (id -> forced state + optional custom pools). */
    public final Map<String, TableOverride> tables = new LinkedHashMap<>();

    public LootInjectionData() {
        for (String d : DifficultyConfigData.DIFFICULTIES) {
            this.pools.put(d, defaultPool(d));
        }
    }

    /** A difficulty's pool, or the default for that tier if it is somehow missing. */
    public PoolConfig poolFor(String difficulty) {
        PoolConfig p = this.pools.get(difficulty);
        return p != null ? p : defaultPool(difficulty);
    }

    /**
     * Default pools per tier: the same six salvage goods with escalating rolls, weights and counts
     * (scout is lean, endgame is rich) - tuned from the retired GLM's polluted_salvage table.
     */
    public static PoolConfig defaultPool(String difficulty) {
        int tier = Math.max(0, DifficultyConfigData.DIFFICULTIES.indexOf(difficulty));
        return switch (tier) {
            case 0 -> new PoolConfig(1, 2, List.of(
                    item("eden:essence", 26, 1, 2, 1.0f), item("eden:taint_crystal", 18, 1, 2, 1.0f),
                    item("eden:tainted_ore", 12, 1, 1, 1.0f), item("eden:salvage_tech", 6, 1, 1, 0.8f),
                    item("eden:eden_cell", 3, 1, 1, 0.7f), item("eden:card_pack", 2, 1, 1, 0.4f)));
            case 1 -> new PoolConfig(2, 3, List.of(
                    item("eden:essence", 24, 1, 3, 1.0f), item("eden:taint_crystal", 20, 1, 2, 1.0f),
                    item("eden:tainted_ore", 14, 1, 2, 1.0f), item("eden:salvage_tech", 8, 1, 1, 1.0f),
                    item("eden:eden_cell", 4, 1, 1, 0.8f), item("eden:card_pack", 3, 1, 1, 0.5f)));
            case 2 -> new PoolConfig(2, 3, List.of(
                    item("eden:essence", 22, 2, 4, 1.0f), item("eden:taint_crystal", 22, 1, 3, 1.0f),
                    item("eden:tainted_ore", 14, 1, 2, 1.0f), item("eden:salvage_tech", 10, 1, 2, 1.0f),
                    item("eden:eden_cell", 5, 1, 1, 0.9f), item("eden:card_pack", 4, 1, 1, 0.6f)));
            case 3 -> new PoolConfig(3, 4, List.of(
                    item("eden:essence", 20, 2, 5, 1.0f), item("eden:taint_crystal", 24, 1, 3, 1.0f),
                    item("eden:tainted_ore", 16, 1, 3, 1.0f), item("eden:salvage_tech", 12, 1, 2, 1.0f),
                    item("eden:eden_cell", 6, 1, 2, 1.0f), item("eden:card_pack", 5, 1, 1, 0.7f)));
            default -> new PoolConfig(3, 4, List.of(
                    item("eden:essence", 18, 2, 6, 1.0f), item("eden:taint_crystal", 26, 2, 4, 1.0f),
                    item("eden:tainted_ore", 18, 1, 3, 1.0f), item("eden:salvage_tech", 14, 1, 2, 1.0f),
                    item("eden:eden_cell", 8, 1, 2, 1.0f), item("eden:card_pack", 6, 1, 1, 0.8f)));
        };
    }

    private static ItemEntry item(String item, int weight, int min, int max, float chance) {
        return new ItemEntry(item, weight, min, max, chance);
    }

    /** Does a loot table id match the configured rules? (eden: never matches - our own tables.) */
    public boolean matches(Identifier id) {
        if (!this.enabled || EdenProtocol.MODID.equals(id.getNamespace())) {
            return false;
        }
        if (!this.allNamespaces && !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        if (this.exclusions.contains(id.toString())) {
            return false;
        }
        String path = id.getPath();
        for (String prefix : this.prefixes) {
            if (!prefix.isEmpty() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The config snapshot to inject with: the SavedData when the overworld exists, else the shared
     * defaults. The snapshot is taken on the server thread and read on datapack-load threads, so
     * callers get whichever instance was resolved at snapshot time - never SavedData mid-read.
     */
    public static LootInjectionData snapshot(MinecraftServer server) {
        LootInjectionData data = getOrNull(server);
        return data != null ? data : DEFAULTS;
    }

    /** Tri-state of one table for the list UI: 0 follow global rules, 1 forced on, 2 forced off. */
    public int tableState(String tableId) {
        TableOverride o = this.tables.get(tableId);
        return o == null ? 0 : (o.enabled() ? 1 : 2);
    }

    /** The pools a table with an explicit override injects (its own if set, else the global ones). */
    public PoolConfig overridePoolFor(String tableId, String difficulty) {
        TableOverride o = this.tables.get(tableId);
        if (o != null && o.pools().containsKey(difficulty)) {
            return o.pools().get(difficulty);
        }
        return poolFor(difficulty);
    }

    /** Content signature used to decide whether tables need re-injecting after a config change. */
    public static String signature(LootInjectionData d) {
        StringBuilder sb = new StringBuilder(d.enabled ? "1" : "0").append('|')
                .append(d.allNamespaces ? "1" : "0").append('|')
                .append(String.join(",", d.prefixes)).append('|')
                .append(String.join(",", d.exclusions));
        for (String diff : DifficultyConfigData.DIFFICULTIES) {
            PoolConfig p = d.poolFor(diff);
            sb.append('|').append(diff).append(':').append(p.rollsMin()).append('-').append(p.rollsMax());
            for (ItemEntry e : p.items()) {
                sb.append(';').append(e.item()).append(',').append(e.weight()).append(',')
                        .append(e.minCount()).append(',').append(e.maxCount()).append(',').append(e.chance());
            }
        }
        for (Map.Entry<String, TableOverride> e : d.tables.entrySet()) {
            TableOverride o = e.getValue();
            sb.append('|').append(e.getKey()).append(':').append(o.enabled() ? '1' : '0');
            for (String diff : DifficultyConfigData.DIFFICULTIES) {
                PoolConfig p = o.pools().get(diff);
                if (p == null) {
                    continue;
                }
                sb.append(';').append(diff).append(',').append(p.rollsMin()).append('-').append(p.rollsMax());
                for (ItemEntry it : p.items()) {
                    sb.append(',').append(it.item()).append('/').append(it.weight()).append('/')
                            .append(it.minCount()).append('/').append(it.maxCount()).append('/').append(it.chance());
                }
            }
        }
        return sb.toString();
    }

    /** Fetch (or create) the persistent config from the always-loaded overworld, or null pre-world. */
    @Nullable
    public static LootInjectionData getOrNull(MinecraftServer server) {
        if (server.overworld() == null) {
            return null;   // first datapack load of a boot: levels not created yet
        }
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Fetch (or create) the persistent config; must only be called once levels exist. */
    public static LootInjectionData get(MinecraftServer server) {
        LootInjectionData data = getOrNull(server);
        return data != null ? data : DEFAULTS;
    }
}
