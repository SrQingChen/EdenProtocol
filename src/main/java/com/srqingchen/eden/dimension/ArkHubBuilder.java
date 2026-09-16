package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;

/**
 * Builds the ark's placeholder spawn platform. The ark is a void dimension, so on first entry we generate
 * a small 3 (X) x 5 (Z) iron-block platform centred on the origin and move the world spawn onto it, so a
 * respawn or fresh join lands on the platform instead of falling into the void.
 * <p>Idempotent: once the centre block is iron the build is skipped, so it is safe to call on every ark
 * entry. This is a self-contained placeholder meant to be replaced later by an exported {@code .nbt}
 * structure (the user will supply the real ark building).
 */
public final class ArkHubBuilder {
    private ArkHubBuilder() {}

    /** Top surface Y of the spawn platform. */
    private static final int PLATFORM_Y = 64;
    /** Platform half-extents: X in [-1, 1] (3 wide), Z in [-2, 2] (5 long). */
    private static final int HALF_X = 1;
    private static final int HALF_Z = 2;

    /**
     * Generate the spawn platform if it is not already there, and point the world spawn at it.
     */
    public static void ensurePlatform(ServerLevel ark) {
        BlockPos centre = new BlockPos(0, PLATFORM_Y, 0);
        if (ark.getBlockState(centre).is(Blocks.IRON_BLOCK)) {
            return;
        }
        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int z = -HALF_Z; z <= HALF_Z; z++) {
                ark.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.IRON_BLOCK.defaultBlockState(), 3);
            }
        }
        // World spawn sits on top of the platform centre so respawns / first join land here, not in the void.
        ark.setRespawnData(new LevelData.RespawnData(GlobalPos.of(EdenDimensions.ARK, centre.above()), 0.0f, 0.0f));
        EdenProtocol.LOGGER.info("[Eden] generated ark spawn platform ({}x{} iron at y={})",
                HALF_X * 2 + 1, HALF_Z * 2 + 1, PLATFORM_Y);
    }
}
