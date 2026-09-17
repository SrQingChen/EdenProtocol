package com.srqingchen.eden.system;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.data.LootInjectionData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.registry.EdenAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Chest-loot injection (§12 战利品注入), replacing the retired fixed-list GLM. Every loot table that
 * loads - vanilla, any mod, any datapack - passes through {@link #onLootTableLoad}; tables matching
 * the configured rules ({@link LootInjectionData#matches}: {@code chests/} prefix by default, all
 * namespaces, minus exclusions) gain one extra pool PER raid difficulty. Each pool is gated at ROLL
 * time by two cheap conditions, so one shared table object serves every dimension correctly:
 * <ul>
 *   <li>{@link RaidDimensionCondition} - the roll happens in a raid dimension;</li>
 *   <li>{@link RaidDifficultyCondition} - the opener (THIS_ENTITY) is mid-raid on that difficulty.</li>
 * </ul>
 * <p><b>Lifecycle</b>: tables load on datapack (re)load, possibly off the main thread, so the event
 * reads a main-thread-published SNAPSHOT, never SavedData directly. The first load of a boot happens
 * before the overworld exists (SavedData unreachable) and applies defaults; if the saved config then
 * differs, {@link #reapplyIfChanged} triggers one background datapack reload (same as {@code /reload})
 * right after server start, and every GUI save does the same - so edits go live in seconds without a
 * reboot. A per-session attempt cap stops any reload loop before it starts.
 */
public final class LootInjectionSystem {
    private LootInjectionSystem() {}

    /** Pools injected by this system are named {@code eden_inject_<difficulty>} (idempotency guard). */
    private static final String POOL_PREFIX = "eden_inject_";

    /** Config snapshot published on the main thread; read on datapack-load threads. */
    private static volatile LootInjectionData snapshot = new LootInjectionData();

    /** Signature of the config actually baked into the tables during the last load pass. */
    private static volatile String lastLoadedSignature = "";

    /** Tables injected during this session (diagnostics; logged once after boot). */
    private static final java.util.concurrent.atomic.AtomicInteger injectedTables = new java.util.concurrent.atomic.AtomicInteger();

    /** Reload attempts this session (guards against a config/defaults mismatch loop). */
    private static int reapplyAttempts = 0;

    // ---------- event wiring (game bus, see EdenProtocol ctor) ----------

    /** Inject matching tables as they load. Runs on a datapack-load thread: touch ONLY the snapshot. */
    public static void onLootTableLoad(LootTableLoadEvent event) {
        LootInjectionData cfg = snapshot;
        lastLoadedSignature = LootInjectionData.signature(cfg);
        if (!cfg.enabled || !cfg.matches(event.getName())) {
            return;
        }
        for (String difficulty : DifficultyConfigData.DIFFICULTIES) {
            LootPool pool = buildPool(difficulty, cfg.poolFor(difficulty));
            if (pool != null) {
                try {
                    event.getTable().addPool(pool);
                    injectedTables.incrementAndGet();
                } catch (RuntimeException e) {
                    // e.g. a same-named pool already present from a duplicated load path - never fatal.
                    EdenProtocol.LOGGER.debug("[Eden] loot injection skipped for {}: {}",
                            event.getName(), e.getMessage());
                }
            }
        }
    }

    /** After boot: if the saved config differs from the defaults baked in during first load, reload. */
    public static void onServerStarted(ServerStartedEvent event) {
        reapplyAttempts = 0;
        int count = injectedTables.get();
        if (count > 0) {
            EdenProtocol.LOGGER.info("[Eden] chest-loot injection active: {} pool additions across loaded tables", count);
        }
        reapplyIfChanged(event.getServer());
    }

    /** Publish a fresh snapshot; if it differs from what the tables carry, reload the datapacks. */
    public static void reapplyIfChanged(MinecraftServer server) {
        snapshot = LootInjectionData.snapshot(server);
        String current = LootInjectionData.signature(snapshot);
        if (current.equals(lastLoadedSignature)) {
            return;   // tables already carry exactly this config
        }
        if (reapplyAttempts >= 2) {
            EdenProtocol.LOGGER.warn("[Eden] loot-injection config changed but reapply cap reached; changes apply after the next /reload or restart");
            return;
        }
        reapplyAttempts++;
        EdenProtocol.LOGGER.info("[Eden] loot-injection config changed - reloading datapacks to apply (attempt {})", reapplyAttempts);
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(t -> {
            EdenProtocol.LOGGER.warn("[Eden] loot-injection reload failed", t);
            return null;
        });
    }

    /** Build one difficulty's injected pool, or null when it has no usable entries. */
    static LootPool buildPool(String difficulty, LootInjectionData.PoolConfig pool) {
        if (pool.items().isEmpty()) {
            return null;
        }
        int rollsMin = Math.max(0, pool.rollsMin());
        int rollsMax = Math.max(rollsMin, pool.rollsMax());
        LootPool.Builder builder = LootPool.lootPool()
                .name(POOL_PREFIX + difficulty)
                .setRolls(UniformGenerator.between(rollsMin, rollsMax))
                .when(RaidDimensionCondition.builder())
                .when(() -> new RaidDifficultyCondition(difficulty));
        boolean any = false;
        for (LootInjectionData.ItemEntry entry : pool.items()) {
            Item item = resolveItem(entry.item());
            if (item == null || entry.weight() < 1) {
                continue;
            }
            int min = Math.max(1, Math.min(entry.minCount(), entry.maxCount()));
            int max = Math.max(min, entry.maxCount());
            var itemBuilder = LootItem.lootTableItem(item)
                    .setWeight(entry.weight())
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
            if (entry.chance() < 1.0f) {
                itemBuilder.when(LootItemRandomChanceCondition.randomChance(clamp01(entry.chance())));
            }
            builder.add(itemBuilder);
            any = true;
        }
        return any ? builder.build() : null;
    }

    @Nullable
    private static Item resolveItem(String id) {
        Identifier parsed = id == null || id.isBlank() ? null : Identifier.tryParse(id.trim());
        if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) {
            return null;
        }
        return BuiltInRegistries.ITEM.getValue(parsed);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    // ---------- roll-time conditions ----------

    /** True when the roll happens inside one of the three raid dimensions. */
    public record RaidDimensionCondition() implements LootItemCondition {
        public static final MapCodec<RaidDimensionCondition> MAP_CODEC = MapCodec.unit(new RaidDimensionCondition());

        @Override
        public MapCodec<? extends LootItemCondition> codec() {
            return MAP_CODEC;
        }

        @Override
        public boolean test(LootContext context) {
            var dim = context.getLevel().dimension();
            return dim.equals(EdenDimensions.RAID_OVERWORLD)
                    || dim.equals(EdenDimensions.RAID_NETHER)
                    || dim.equals(EdenDimensions.RAID_END);
        }

        public static LootItemCondition.Builder builder() {
            return () -> new RaidDimensionCondition();
        }
    }

    /** True when the loot context's player is mid-raid on the given difficulty (chests roll on open). */
    public record RaidDifficultyCondition(String difficulty) implements LootItemCondition {
        public static final MapCodec<RaidDifficultyCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(
                inst -> inst.group(
                        com.mojang.serialization.Codec.STRING.fieldOf("difficulty").forGetter(RaidDifficultyCondition::difficulty)
                ).apply(inst, RaidDifficultyCondition::new));

        @Override
        public MapCodec<? extends LootItemCondition> codec() {
            return MAP_CODEC;
        }

        @Override
        public boolean test(LootContext context) {
            Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
            if (!(entity instanceof Player player)) {
                return false;   // hopper-extracted or structure-time rolls: never inject
            }
            var state = player.getData(EdenAttachments.RAID_STATE);
            return state.inRaid && state.difficulty.equals(this.difficulty);
        }
    }

    /** Registration of the two condition codecs ({@code eden:raid_dimension} / {@code eden:raid_difficulty}). */
    public static final class Conditions {
        public static final DeferredRegister<MapCodec<? extends LootItemCondition>> LOOT_CONDITIONS =
                DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, EdenProtocol.MODID);

        public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<RaidDimensionCondition>> RAID_DIMENSION =
                LOOT_CONDITIONS.register("raid_dimension", () -> RaidDimensionCondition.MAP_CODEC);
        public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<RaidDifficultyCondition>> RAID_DIFFICULTY =
                LOOT_CONDITIONS.register("raid_difficulty", () -> RaidDifficultyCondition.MAP_CODEC);
    }
}
