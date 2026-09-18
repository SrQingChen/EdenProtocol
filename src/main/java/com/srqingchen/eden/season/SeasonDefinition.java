package com.srqingchen.eden.season;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;

/**
 * One season's content package (S0 矿洞季 is the base mod's own; future seasons ship as independent
 * add-on mods that depend on Eden and register a definition here — that is the mod-series
 * architecture the user settled on: base game = S0, every later season = its own downloadable mod).
 * <p>A definition owns the season's contracts, its live modifier layer (spawn pressure, events,
 * affix mechanics), its fragment drops and — as of 批 D — its finale: an arena challenge plus a
 * multi-ending ceremony script. Everything except identity and contracts is an optional hook, so a
 * minimal season only needs {@link #id}, {@link #titleKey} and {@link #contracts}.
 * <p>Hooks are dispatched by {@link SeasonHooks} ONLY while this definition is the active season
 * (see {@link Seasons#current}); an implementation never needs to check "am I active" itself.
 */
public interface SeasonDefinition {

    /** Stable registry id, e.g. {@code eden:s0_cave}. Persisted in CampaignData as the season key. */
    Identifier id();

    /** Lang key of the season's display name (e.g. "S0 · 矿洞季"). */
    String titleKey();

    /** The season's five expedition contracts (profession-agnostic by design). */
    List<SeasonSystem.Contract> contracts();

    /** How many contracts unlock the season's finale gate; 3-of-5 by default. */
    default int advanceThreshold() {
        return Math.max(1, contracts().size() - 2);
    }

    // ---------- live modifier layer (raid-world gameplay, dispatched while active) ----------

    /** Per-server-tick behaviour: spawn pressure, collapse events, affix ambience, ... */
    default void serverTick(MinecraftServer server) {}

    /** Entity-spawn interception (cave stock swaps, crew scaling hooks, ...). */
    default void entityJoinLevel(EntityJoinLevelEvent event) {}

    /** Block-break interception (ore resonance, collapse triggers, fragment seams, ...). */
    default void blockBreak(BreakBlockEvent event) {}

    // ---------- fragments (《三位起草人》 page drops; roll-time gated, see FragmentSystem) ----------

    /** Does this season scatter fragment pages? Injection is data-driven and roll-time gated. */
    default boolean fragmentsActive() {
        return false;
    }

    // ---------- finale (批 D: arena challenge + ceremony; null = no finale authored yet) ----------

    default SeasonFinale finale() {
        return null;
    }

    /** True once this season's finale has been beaten (multi-playthrough reset clears it). */
    default boolean hasFinale() {
        return finale() != null;
    }
}
