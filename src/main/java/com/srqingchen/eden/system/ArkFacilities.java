package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.registry.EdenBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One-shot placement of ark facilities that arrive during play (campaign victory gate, milestone
 * terminals). Idempotent: each placement checks its anchor block first, so calling it again is free.
 * The placeholder ark is a void platform; facilities land on fixed offsets from the spawn platform so a
 * later user-supplied {@code .nbt} ark can keep the same anchors.
 */
public final class ArkFacilities {
    private ArkFacilities() {}

    /** Victory: a paradise gate appears on the spawn platform's north edge. */
    public static void placeParadiseGate(MinecraftServer server) {
        ServerLevel ark = server.getLevel(EdenDimensions.ARK);
        if (ark == null) {
            return;
        }
        int y = ark.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, -3);
        BlockPos pos = new BlockPos(0, y, -3);
        if (ark.getBlockState(pos).is(EdenBlocks.PARADISE_GATE.get())) {
            return;   // already placed
        }
        // Small dais so the gate reads as a landmark even on the bare platform.
        for (int x = -1; x <= 1; x++) {
            BlockPos p = pos.offset(x, -1, 0);
            if (ark.getBlockState(p).isAir()) {
                ark.setBlock(p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            }
        }
        ark.setBlock(pos, EdenBlocks.PARADISE_GATE.get().defaultBlockState(), 3);
        EdenProtocol.LOGGER.info("[Eden] paradise gate placed in the ark at {}", pos.toShortString());
    }
}
