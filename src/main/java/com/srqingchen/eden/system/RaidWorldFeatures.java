package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.block.ReturnPodBlock;
import com.srqingchen.eden.data.RaidWorldData;
import com.srqingchen.eden.dimension.EdenDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the per-expedition world features (功能清单 §3/§10): 2-4 pre-deployed public evacuation pods at
 * random bearings, one hope oasis and 2-3 pollution cores. Called from {@link RaidService} right after a
 * FRESH world rebuild - the {@link RaidWorldData#seeded} flag (wiped with the dimension folder) is what
 * tells "brand-new expedition" apart from "crew already inside, reuse the world".
 * <p>Evacuation pods are REAL return pods (the same 4-cell structure the locator deploys), so register /
 * fuel / charge / launch all work unchanged; the crew just has to FIND one (explore, or buy intel: the
 * scanner and the pathfinder card point at them).
 */
public final class RaidWorldFeatures {
    private RaidWorldFeatures() {}

    /** Evacuation points per fresh expedition: 2-4 (design: "每局 2–4 个"). */
    private static final int EVAC_MIN = 2;
    private static final int EVAC_MAX = 4;
    /** Bearing ring for evacuation pods: far enough to force a trek, close enough to reach on foot. */
    private static final int EVAC_MIN_DIST = 140;
    private static final int EVAC_MAX_DIST = 320;

    /**
     * Seed a fresh raid world once. No-op when the data says this expedition was already seeded (a
     * crewmate opened the world earlier) so joining teammates never duplicate the layout.
     */
    public static void seedIfFresh(ServerLevel raid) {
        RaidWorldData data = RaidWorldData.get(raid);
        if (data.seeded()) {
            return;
        }
        RandomSource rand = raid.getRandom();
        int evacCount = EVAC_MIN + rand.nextInt(EVAC_MAX - EVAC_MIN + 1);
        List<Integer> usedAngles = new ArrayList<>();
        for (int i = 0; i < evacCount; i++) {
            placeEvacuationPod(raid, rand, usedAngles);
        }
        OasisSystem.placeOasis(raid, data, rand);
        PollutionCoreSystem.placeCores(raid, data, rand);
        EcologySystem.placeFeatures(raid, data, rand);
        data.markSeeded();
        EdenProtocol.LOGGER.info("[Eden] seeded fresh raid world: {} evacuation points, oasis={}, cores={}",
                evacCount, data.hasOasis(), data.cores().size());
    }

    /** Place one public return pod on the surface at a random, well-spread bearing; record it. */
    private static void placeEvacuationPod(ServerLevel raid, RandomSource rand, List<Integer> usedAngles) {
        for (int attempt = 0; attempt < 12; attempt++) {
            // Spread pods across the compass: quantise the angle into 6 sectors and never reuse one.
            int sector = rand.nextInt(6);
            if (usedAngles.contains(sector)) {
                continue;
            }
            double angle = (Math.PI * 2.0 * sector) / 6.0 + rand.nextDouble() * (Math.PI / 3.0) * 0.6;
            int dist = EVAC_MIN_DIST + rand.nextInt(EVAC_MAX_DIST - EVAC_MIN_DIST + 1);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            int y = raid.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            if (!ReturnPodBlock.canPlaceAt(raid, base)) {
                continue;   // surface obstruction (tree / cliff overhang) - try another bearing
            }
            ReturnPodBlock.placeStructure(raid, base);
            RaidWorldData.get(raid).addExtractionPoint(base);
            usedAngles.add(sector);
            return;
        }
    }

    /**
     * Nearest still-standing extraction point (pre-deployed public pod) to {@code from}. Launched or
     * destroyed pods are pruned lazily - the block state is authoritative, the list is just an index.
     */
    @Nullable
    public static BlockPos nearestExtractionPoint(ServerLevel raid, Vec3 from) {
        RaidWorldData data = RaidWorldData.get(raid);
        if (data.extractionPoints().isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : new ArrayList<>(data.extractionPoints())) {
            if (!raid.getBlockState(pos).is(
                    com.srqingchen.eden.registry.EdenBlocks.RETURN_POD.get())) {
                data.extractionPoints().remove(pos);   // launched / destroyed since
                data.setDirty();
                continue;
            }
            double d = pos.distToCenterSqr(from.x, from.y, from.z);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    /** True if the given level is one of the mod's raid dimensions (feature/erosion gating). */
    public static boolean isRaidLevel(ServerLevel level) {
        return level.dimension().equals(EdenDimensions.RAID_OVERWORLD)
                || level.dimension().equals(EdenDimensions.RAID_NETHER)
                || level.dimension().equals(EdenDimensions.RAID_END);
    }
}
