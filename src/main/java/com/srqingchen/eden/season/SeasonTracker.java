package com.srqingchen.eden.season;

import com.srqingchen.eden.system.RaidWorldFeatures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Season contract tracking hooks (S1 批 A): MINE counts deepslate-era ore broken below y=0, SURVEY
 * samples cave movement (below y=0, sky-occluded, roughly 1 block per meter walked), LODGE counts
 * night ticks inside a mineshaft or trial chamber, PURGE counts ecology-boss / lair-guard kills.
 * Everything only accumulates while the player carries the matching contract in a raid world.
 */
public final class SeasonTracker {
    private SeasonTracker() {}

    private static final int SURVEY_SAMPLE_EVERY = 10;   // ticks between movement samples
    /** The vanilla ore tags - shared with CaveSeasonSystem (矿脉共鸣). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>[] ORE_TAGS =
            new net.minecraft.tags.TagKey[]{net.minecraft.tags.BlockTags.COAL_ORES,
                    net.minecraft.tags.BlockTags.IRON_ORES, net.minecraft.tags.BlockTags.COPPER_ORES,
                    net.minecraft.tags.BlockTags.GOLD_ORES, net.minecraft.tags.BlockTags.REDSTONE_ORES,
                    net.minecraft.tags.BlockTags.LAPIS_ORES, net.minecraft.tags.BlockTags.DIAMOND_ORES,
                    net.minecraft.tags.BlockTags.EMERALD_ORES};

    public static void onBreakSpeed() {
    }   // (reserved: batch B mining-flavour)

    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        var r = SeasonSystem.run(sp);
        var state = event.getState();
        // 三值温度计 feeding (批C 共生值): count tainted-block mining regardless of carried contract.
        if (SeasonSystem.inRaid(sp) && isTainted(state)) {
            r.taintedMined++;
        }
        if (r.contract == null || r.contract.goal() != com.srqingchen.eden.season.SeasonSystem.Goal.MINE
                || !SeasonSystem.inRaid(sp) || sp.getBlockY() >= 0) {
            return;
        }
        for (net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag : ORE_TAGS) {
            if (state.is(tag)) {
                r.minedOres++;
                break;
            }
        }
    }

    private static boolean isTainted(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(com.srqingchen.eden.registry.EdenBlocks.TAINTED_STONE.get())
                || state.is(com.srqingchen.eden.registry.EdenBlocks.TAINTED_SOIL.get())
                || state.is(com.srqingchen.eden.registry.EdenBlocks.TAINTED_GRASS.get())
                || state.is(com.srqingchen.eden.registry.EdenBlocks.TAINTED_COBBLESTONE.get());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % SURVEY_SAMPLE_EVERY != 0) {
            return;
        }
        var r = SeasonSystem.run(sp);
        if (r.contract == null || !SeasonSystem.inRaid(sp)) {
            return;
        }
        ServerLevel level = (ServerLevel) sp.level();
        boolean belowGround = sp.getBlockY() < 0 && !level.canSeeSky(sp.blockPosition());
        boolean moving = sp.getDeltaMovement().horizontalDistanceSqr() > 0.003;
        // SURVEY: ~1 point per sampled meter of cave movement (2 samples/second at walk speed).
        if (r.contract.goal() == com.srqingchen.eden.season.SeasonSystem.Goal.SURVEY && belowGround && moving) {
            r.surveyBlocks += 1;
        }
        // LODGE: night ticks (13000..23000) inside a mineshaft or trial chamber.
        if (r.contract.goal() == com.srqingchen.eden.season.SeasonSystem.Goal.LODGE) {
            long dayTime = level.getOverworldClockTime() % 24000L;
            if (dayTime >= 13000L && dayTime <= 23000L && insideCaveStructure(level, sp.blockPosition())) {
                r.lodgeTicks++;   // reaches 2400 (one full night) naturally
            }
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        var r = SeasonSystem.run(killer);
        if (r.contract == null
                || r.contract.goal() != com.srqingchen.eden.season.SeasonSystem.Goal.PURGE
                || !SeasonSystem.inRaid(killer)) {
            return;
        }
        var tags = event.getEntity().entityTags();
        if (tags.contains("eden_eco_spore") || tags.contains("eden_eco_leviathan")
                || tags.contains("eden_eco_excavator") || tags.contains("eden_core_guard")
                || tags.contains("eden_core_warden")) {
            r.purgeKills++;
        }
    }

    private static boolean insideCaveStructure(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (String key : new String[]{"mineshaft", "trial_chamber"}) {
            var opt = structures.get(Identifier.fromNamespaceAndPath("minecraft", key));
            if (opt.isPresent()
                    && level.structureManager().getStructureWithPieceAt(pos, opt.get().value()).isValid()) {
                return true;
            }
        }
        return false;
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SeasonSystem.onLogout(event.getEntity().getUUID());
    }

    /** Unused-import sink for RaidWorldFeatures (kept for future batch B usage). */
    static {
        assert RaidWorldFeatures.class != null;
    }
}
