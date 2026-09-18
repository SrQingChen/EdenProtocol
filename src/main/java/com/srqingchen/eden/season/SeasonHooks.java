package com.srqingchen.eden.season;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus dispatcher for the ACTIVE season's behaviour hooks (批 D). Instead of wiring every
 * season system into the main mod class, the bus listeners here resolve
 * {@link Seasons#current} once per event and forward — so a season add-on mod only registers a
 * {@link SeasonDefinition}, never its own event wiring. S0-specific engines
 * (CaveSeasonSystem / FragmentSystem) are called from S0CaveSeason's hook implementations.
 */
public final class SeasonHooks {
    private SeasonHooks() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        SeasonDefinition season = Seasons.current(server);
        if (season != null) {
            season.serverTick(server);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SeasonDefinition season = Seasons.current(level.getServer());
        if (season != null) {
            season.entityJoinLevel(event);
        }
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SeasonDefinition season = Seasons.current(level.getServer());
        if (season != null) {
            season.blockBreak(event);
        }
    }

    /** Fragment loot injection is data-level (fires for every table at load); keep the static hook. */
    public static void onServerStarted(ServerStartedEvent event) {
        // Reserved: per-season world bootstrap (arena pre-gen, profile generation) lands here.
    }
}
