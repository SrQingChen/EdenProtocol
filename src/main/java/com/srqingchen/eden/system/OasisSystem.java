package com.srqingchen.eden.system;

import com.srqingchen.eden.data.RaidWorldData;
import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The hope oasis (希望绿洲, 功能清单 §10): a small pocket of clean land seeded into every fresh
 * expedition. Inside {@link #OASIS_RADIUS} the erosion gauge PAUSES and slowly drains (see
 * {@link ErosionSystem}), making the oasis a rally point when a run goes wrong. Its chest is the
 * reliable source of purity goods (净光之卡 / purifier / eden cell) - the "light" counterweight to the
 * taint economy. A particle pillar marks it from a distance (希望的萤光).
 */
public final class OasisSystem {
    private OasisSystem() {}

    /** Effective radius of the clean field. */
    public static final double OASIS_RADIUS = 12.0;
    /** Bearing ring for the oasis: a serious trek, but reachable within the 30-min window. */
    private static final int OASIS_MIN_DIST = 220;
    private static final int OASIS_MAX_DIST = 420;

    // ---------- seeding ----------

    /** Build one oasis: a clean grass disc, water pool, flowers, a light tree and the supply chest. */
    public static void placeOasis(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            int dist = OASIS_MIN_DIST + rand.nextInt(OASIS_MAX_DIST - OASIS_MIN_DIST + 1);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            int y = raid.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (y <= raid.getMinY() + 4) {
                continue;
            }
            BlockPos centre = new BlockPos(x, y, z);
            buildDisc(raid, centre, rand);
            data.setOasis(centre);
            return;
        }
    }

    private static void buildDisc(ServerLevel raid, BlockPos centre, RandomSource rand) {
        int r = (int) OASIS_RADIUS - 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                int y = raid.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + dx, centre.getZ() + dz) - 1;
                BlockPos top = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
                // Clean ground: grass over dirt, scouring the taint off the surface ring.
                raid.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                raid.setBlock(top.below(), Blocks.DIRT.defaultBlockState(), 3);
                if (rand.nextFloat() < 0.10f) {
                    BlockPos above = top.above();
                    if (raid.getBlockState(above).canBeReplaced()) {
                        raid.setBlock(above, rand.nextFloat() < 0.35f
                                ? Blocks.PEONY.defaultBlockState()
                                : Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Water pool at the heart.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 4) {
                    raid.setBlock(centre.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        // The supply chest: purity goods (the reliable 净光 card source).
        BlockPos chestPos = centre.offset(4, 1, 0);
        raid.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (raid.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(13, new ItemStack(EdenItems.CARD_PURITY.get()));
            chest.setItem(15, new ItemStack(EdenItems.PURIFIER.get(), 2));
            chest.setItem(11, new ItemStack(EdenItems.EDEN_CELL.get()));
        }
        // A small oak + glowstone sprig so the oasis reads alive at night.
        raid.setBlock(centre.offset(-4, 1, 1), Blocks.OAK_SAPLING.defaultBlockState(), 3);
        raid.setBlock(centre.above(4), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    // ---------- runtime queries + ambience ----------

    /** Server-tick entry (registered in EdenProtocol): runs the ambient hope-light while a crew is inside. */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        net.minecraft.server.level.ServerLevel raid =
                event.getServer().getLevel(com.srqingchen.eden.dimension.EdenDimensions.RAID_OVERWORLD);
        if (raid == null || raid.players().isEmpty()) {
            return;
        }
        ambientTick(raid, RaidWorldData.get(raid));
    }

    /** True when the player stands inside the oasis's clean field (horizontal distance). */
    public static boolean isInOasis(ServerPlayer sp, RaidWorldData data) {
        if (!data.hasOasis()) {
            return false;
        }
        double dx = sp.getX() - (data.oasisPos().getX() + 0.5);
        double dz = sp.getZ() - (data.oasisPos().getZ() + 0.5);
        return dx * dx + dz * dz <= OASIS_RADIUS * OASIS_RADIUS;
    }

    /** Ambient hope-light: a particle pillar + drifting motes while anyone is near the oasis. */
    public static void ambientTick(ServerLevel raid, RaidWorldData data) {
        if (!data.hasOasis() || raid.getGameTime() % 20L != 0L) {
            return;
        }
        BlockPos c = data.oasisPos();
        boolean anyoneNear = false;
        for (var p : raid.players()) {
            double pdx = p.getX() - c.getX();
            double pdz = p.getZ() - c.getZ();
            if (pdx * pdx + pdz * pdz <= 48.0 * 48.0) {
                anyoneNear = true;
                break;
            }
        }
        if (!anyoneNear) {
            return;
        }
        RandomSource rand = raid.getRandom();
        // The pillar: visible far away (希望的萤光).
        for (int i = 0; i < 8; i++) {
            double yy = c.getY() + 2 + rand.nextDouble() * 24;
            raid.sendParticles(ParticleTypes.END_ROD,
                    c.getX() + 0.5 + (rand.nextDouble() - 0.5) * 1.2, yy, c.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 1.2,
                    1, 0.0, 0.05, 0.0, 0.0);
        }
        // Drifting motes over the disc.
        for (int i = 0; i < 6; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double d = rand.nextDouble() * (OASIS_RADIUS - 2);
            raid.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    c.getX() + 0.5 + Math.cos(a) * d, c.getY() + 1.2, c.getZ() + 0.5 + Math.sin(a) * d,
                    1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /** One-time welcome chime the first time each expedition's oasis is entered. */
    public static void announceFirstEntry(ServerPlayer sp, boolean alreadyVisited) {
        if (alreadyVisited) {
            return;
        }
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
    }
}
