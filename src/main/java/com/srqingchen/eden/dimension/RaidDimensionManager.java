package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Runtime (re)creation of the raid dimensions so every expedition is a brand-new, freshly seeded world.
 * <p>Verified against 26.1.2 sources: {@code MinecraftServer.forgeGetWorldMap()} returns the LIVE
 * level map, {@code ServerLevel}'s constructor is public, {@code LevelStem} carries a NeoForge
 * {@code seedOverride} that fully drives terrain generation, and clients learn a dimension from the
 * respawn packet (so no registry re-sync is needed when reusing the vanilla dimension types).
 * <p>The dimension KEYS stay fixed (so loot GLMs / dimension checks keep working); only the backing
 * {@link ServerLevel} is torn down and rebuilt with a new seed. Works for all three raid dimensions
 * (overworld / nether / end). Two private {@code MinecraftServer} fields ({@code executor},
 * {@code storageSource}) are read reflectively; if anything fails we degrade gracefully to the
 * existing dimension instead of crashing.
 */
public class RaidDimensionManager {
    private RaidDimensionManager() {}

    private static Field executorField;
    private static Field storageField;
    private static Field worldArrayMarkerField;

    /**
     * Ensure a raid world is a fresh, empty, newly-seeded dimension. If a raid is already in progress
     * (players inside) the current world is reused so a co-op team shares one expedition. Call on the
     * server thread before teleporting players in.
     *
     * @return true if a usable raid dimension exists afterwards, false if it could not be prepared
     */
    public static boolean prepareFreshRaidWorld(MinecraftServer server) {
        return prepare(server, EdenDimensions.RAID_OVERWORLD, "raid_overworld");
    }

    /** Same as {@link #prepareFreshRaidWorld} but for the polluted nether expedition dimension. */
    public static boolean prepareFreshNether(MinecraftServer server) {
        return prepare(server, EdenDimensions.RAID_NETHER, "raid_nether");
    }

    /** Same as {@link #prepareFreshRaidWorld} but for the polluted end expedition dimension. */
    public static boolean prepareFreshEnd(MinecraftServer server) {
        return prepare(server, EdenDimensions.RAID_END, "raid_end");
    }

    private static boolean prepare(MinecraftServer server, ResourceKey<net.minecraft.world.level.Level> dim, String folder) {
        ServerLevel existing = server.getLevel(dim);
        if (existing != null && !existing.players().isEmpty()) {
            return true; // raid already in progress -> reuse, do not reset under the crew
        }
        try {
            // 1) Tear down the old level (release chunk/region file handles first).
            if (existing != null) {
                server.forgeGetWorldMap().remove(dim);
                try {
                    existing.save(null, true, false);
                } catch (Throwable t) {
                    EdenProtocol.LOGGER.warn("[Eden] raid world save-before-reset failed: {}", t.toString());
                }
                existing.close();
                NeoForge.EVENT_BUS.post(new LevelEvent.Unload(existing));
            }
            // 2) Wipe the persisted region/poi/entity data so the new seed regenerates from scratch.
            deleteDimensionFiles(server, folder);
            // 3) Build a fresh LevelStem from the DATAPACK stem (eden:raid_overworld / raid_nether / raid_end),
            //    overriding only the seed. The stem's generator carries the polluted noise settings.
            long seed = ThreadLocalRandom.current().nextLong();
            Registry<LevelStem> stems = server.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
            ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dim.identifier());
            LevelStem base = stems.getValue(stemKey);
            if (base == null) {
                // Only if the datapack stem failed to load: fall back to the matching vanilla stem and warn
                // loudly (the surface would then NOT be polluted), instead of crashing the launch.
                base = fallbackStem(stems, folder);
                EdenProtocol.LOGGER.warn("[Eden] datapack LevelStem {} is missing; falling back to a vanilla generator - the raid surface will NOT be polluted",
                        dim.identifier());
            }
            if (base == null) {
                throw new IllegalStateException("no LevelStem found for " + dim.identifier());
            }
            LevelStem stem = new LevelStem(base.type(), base.generator(), OptionalLong.of(seed));
            // 4) Construct the ServerLevel (executor + storageSource are not publicly exposed).
            Executor executor = (Executor) readServerField(server, "executor");
            LevelStorageSource.LevelStorageAccess storage =
                    (LevelStorageSource.LevelStorageAccess) readServerField(server, "storageSource");
            ServerLevelData overworldData = server.getWorldData().overworldData();
            DerivedLevelData derived = new DerivedLevelData(server.getWorldData(), overworldData);
            ServerLevel fresh = new ServerLevel(server, executor, storage, derived,
                    dim, stem, false, 0L, List.of(), false);
            // 5) Register it live and announce, mirroring MinecraftServer.createLevels().
            server.forgeGetWorldMap().put(dim, fresh);
            // CRITICAL: NeoForge caches the ticked levels in a ServerLevel[] (MinecraftServer.getWorldArray)
            // that is only rebuilt when its private worldArrayMarker changes. Mutating the live level map
            // directly does NOT bump it, so without this the freshly built level is never ticked: block
            // updates never flush (every break rolls back), block entities freeze (the charge bar sticks),
            // and death drops / entity processing stall until the level is next reloaded.
            invalidateWorldArrayCache(server);
            NeoForge.EVENT_BUS.post(new LevelEvent.Load(fresh));
            fresh.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
            server.getPlayerList().addWorldborderListener(fresh);
            EdenProtocol.LOGGER.info("[Eden] Rebuilt raid world {} with fresh seed {}", dim.identifier(), seed);
            return true;
        } catch (Throwable t) {
            EdenProtocol.LOGGER.error("[Eden] Failed to rebuild the raid world {}; keeping whatever dimension exists",
                    dim.identifier(), t);
            return server.getLevel(dim) != null;
        }
    }

    /** Vanilla stem matching the raid dimension (nether raids fall back to the nether, etc.). */
    private static LevelStem fallbackStem(Registry<LevelStem> stems, String folder) {
        return switch (folder) {
            case "raid_nether" -> stems.getValue(LevelStem.NETHER);
            case "raid_end" -> stems.getValue(LevelStem.END);
            default -> stems.getValue(LevelStem.OVERWORLD);
        };
    }

    /** Recursively delete {@code <world>/dimensions/eden/<folder>} (region/poi/entities/data). */
    private static void deleteDimensionFiles(MinecraftServer server, String folder) {
        Path dimDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions").resolve(EdenProtocol.MODID).resolve(folder);
        if (!Files.exists(dimDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dimDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort; a locked file just means slightly stale terrain, not a crash.
                }
            });
        } catch (IOException e) {
            EdenProtocol.LOGGER.warn("[Eden] could not walk raid dimension dir for reset: {}", e.toString());
        }
    }

    /**
     * Bump NeoForge's private {@code worldArrayMarker} so {@code MinecraftServer.getWorldArray()} rebuilds
     * its cached {@code ServerLevel[]} from the live level map on the next tick. A level added by mutating
     * {@code forgeGetWorldMap()} directly is otherwise never ticked (block entities, block updates, drops
     * and entity processing all silently stall in it).
     */
    private static void invalidateWorldArrayCache(MinecraftServer server) {
        try {
            if (worldArrayMarkerField == null) {
                worldArrayMarkerField = MinecraftServer.class.getDeclaredField("worldArrayMarker");
                worldArrayMarkerField.setAccessible(true);
            }
            worldArrayMarkerField.setInt(server, worldArrayMarkerField.getInt(server) + 1);
        } catch (Throwable t) {
            EdenProtocol.LOGGER.error("[Eden] could not invalidate the world-array cache; the rebuilt raid world may not tick", t);
        }
    }

    private static Object readServerField(MinecraftServer server, String name) throws ReflectiveOperationException {
        Field f = name.equals("executor") ? executorField : storageField;
        if (f == null) {
            f = MinecraftServer.class.getDeclaredField(name);
            f.setAccessible(true);
            if (name.equals("executor")) {
                executorField = f;
            } else {
                storageField = f;
            }
        }
        return f.get(server);
    }
}
