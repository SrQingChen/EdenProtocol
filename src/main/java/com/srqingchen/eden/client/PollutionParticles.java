package com.srqingchen.eden.client;

import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Ambient "pollution" particles inside the raid world (client-only, {@link ClientTickEvent.Post} on the
 * game bus). The tainted ground off-gasses: each tick we sample {@link ClientRaidData#particleDensity}
 * random columns around the local player and, where the motion-blocking surface falls inside the player's
 * height band (playerY-{@value #BAND_DOWN} .. playerY+{@value #BAND_UP}), the exposed top block vents a
 * coloured dust mote with probability {@link ClientRaidData#particleChance}.
 * <p>Route A: a vanilla {@link DustParticleOptions} (100% code-driven, no texture / no custom render class).
 * Its {@code scale} drives BOTH mote size and lifetime; colour, chance, density and drift speed all come
 * from the per-difficulty config synced to {@link ClientRaidData}. Gated on {@link ClientRaidData#inRaid},
 * and {@link ClientLevel#addParticle} already respects the user's particle graphics setting.
 */
public final class PollutionParticles {
    private PollutionParticles() {}

    /** Horizontal sampling radius in blocks - within view distance but bounded for performance. */
    private static final int RADIUS_XZ = 24;
    /** Height band around the player that may vent: playerY + BAND_UP .. playerY - BAND_DOWN. */
    private static final int BAND_UP = 5;
    private static final int BAND_DOWN = 20;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !ClientRaidData.inRaid) {
            return;
        }
        int density = ClientRaidData.particleDensity;
        if (density <= 0) {
            return;
        }
        float chance = ClientRaidData.particleChance;
        float speed = ClientRaidData.particleSpeed;
        DustParticleOptions dust = new DustParticleOptions(ClientRaidData.particleColor, ClientRaidData.particleScale);

        RandomSource rand = level.getRandom();
        double py = mc.player.getY();
        int baseX = Mth.floor(mc.player.getX());
        int baseZ = Mth.floor(mc.player.getZ());
        for (int i = 0; i < density; i++) {
            int x = baseX + rand.nextIntBetweenInclusive(-RADIUS_XZ, RADIUS_XZ);
            int z = baseZ + rand.nextIntBetweenInclusive(-RADIUS_XZ, RADIUS_XZ);
            if (!level.hasChunkAt(x, z)) {
                continue;
            }
            // getHeight returns the first air Y above the surface; the exposed solid block sits one below.
            int surfaceAirY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int surfaceY = surfaceAirY - 1;
            if (surfaceY < py - BAND_DOWN || surfaceY > py + BAND_UP) {
                continue;
            }
            if (rand.nextFloat() >= chance) {
                continue;
            }
            double sx = x + rand.nextDouble();
            double sy = surfaceAirY + 0.1D;
            double sz = z + rand.nextDouble();
            double vx = (rand.nextDouble() - 0.5D) * 0.06D * speed;
            double vy = (0.02D + rand.nextDouble() * 0.04D) * speed;
            double vz = (rand.nextDouble() - 0.5D) * 0.06D * speed;
            // Difficulty-tinted dust motes plus a share of the custom spore sprite (§16).
            if (rand.nextFloat() < 0.35f) {
                level.addParticle(com.srqingchen.eden.registry.EdenParticles.POLLUTION_SPORE.get(), sx, sy, sz, vx, vy, vz);
            } else {
                level.addParticle(dust, sx, sy, sz, vx, vy, vz);
            }
        }
    }
}
