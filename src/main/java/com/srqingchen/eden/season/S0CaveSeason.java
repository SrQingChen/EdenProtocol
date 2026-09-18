package com.srqingchen.eden.season;

import com.srqingchen.eden.system.CaveSeasonSystem;
import com.srqingchen.eden.system.FragmentSystem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;

/**
 * S0 矿洞季 — the base mod's own season (the first chapter of the series). The behaviour all
 * shipped in 批 A/B/C lives on: the five cave contracts, the cave modifier layer (underground
 * spawn pressure + stock swaps, collapses, cave affixes) and the《三位起草人》fragment drops.
 * Engine classes stay where they are ({@link CaveSeasonSystem} / {@link FragmentSystem}); this
 * definition is the seam that lets the same systems be swapped out per season.
 */
public final class S0CaveSeason implements SeasonDefinition {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("eden", "s0_cave");

    /** entity_modifier profile of the S0 finale boss (generated on demand when missing). */
    public static final String FINALE_PROFILE = "eden_finale_s0";

    public S0CaveSeason() {}

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String titleKey() {
        return "eden.season.s0.title";
    }

    @Override
    public List<SeasonSystem.Contract> contracts() {
        return SeasonSystem.CAVE_CONTRACTS;
    }

    // ---------- modifier layer: thin delegation into the S0 engines ----------

    @Override
    public void serverTick(MinecraftServer server) {
        CaveSeasonSystem.tick(server);
    }

    @Override
    public void entityJoinLevel(EntityJoinLevelEvent event) {
        CaveSeasonSystem.joinLevel(event);
    }

    @Override
    public void blockBreak(BreakBlockEvent event) {
        CaveSeasonSystem.blockBroken(event);
        FragmentSystem.blockBroken(event);
    }

    @Override
    public boolean fragmentsActive() {
        return true;
    }

    @Override
    public SeasonFinale finale() {
        // Boss = 深渊之形 (a massively empowered Warden — the deep-dark apex fits the cave season;
        // stats & phases live in the entity_modifier profile, arena hazards in FinaleBossSystem).
        return new SeasonFinale(
                Identifier.withDefaultNamespace("warden"),
                FINALE_PROFILE,
                "eden.finale.s0.boss",
                44,
                "season_s0_finale");
    }
}
