package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelData;

import java.util.Optional;

/**
 * Builds the ark's spawn platform. The ark is a void dimension, so on first entry we generate a
 * small 3 (X) x 5 (Z) iron-block platform centred on the origin and move the world spawn onto it, so a
 * respawn or fresh join lands on the platform instead of falling into the void.
 * <p><b>User-supplied ark (§13 方舟 .nbt 美化)</b>: drop an exported structure file at
 * {@code src/main/resources/data/eden/structure/ark.nbt} (structure block export, origin corner at the
 * world spawn side) and it is placed at (0, 64, 0) INSTEAD of the placeholder platform - the placement
 * is idempotent (same template, same spot), so re-entering the ark just re-asserts it.
 * <p>The placeholder build stays as the fallback when no template ships.
 */
public final class ArkHubBuilder {
    private ArkHubBuilder() {}

    /** Top surface Y of the spawn platform / the structure origin. */
    private static final int PLATFORM_Y = 64;
    /** Platform half-extents: X in [-1, 1] (3 wide), Z in [-2, 2] (5 long). */
    private static final int HALF_X = 1;
    private static final int HALF_Z = 2;
    /** The user's exported ark structure, resolved from the data pack if present. */
    private static final Identifier ARK_TEMPLATE = Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "ark");

    /**
     * Generate the spawn platform if it is not already there, and point the world spawn at it.
     */
    public static void ensurePlatform(ServerLevel ark) {
        BlockPos centre = new BlockPos(0, PLATFORM_Y, 0);
        if (tryPlaceUserTemplate(ark, centre)) {
            return;
        }
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

    /**
     * Place {@code eden:ark} (the user's exported structure) at the origin if the data pack ships it.
     * Returns true when the template exists (the placeholder is then skipped). Re-placement is cheap
     * and idempotent; the spawn anchor is re-asserted on top of the structure's centre each time.
     */
    private static boolean tryPlaceUserTemplate(ServerLevel ark, BlockPos centre) {
        Optional<StructureTemplate> template = ark.getStructureManager().get(ARK_TEMPLATE);
        if (template.isEmpty() || template.get().getSize().getX() < 3) {
            return false;   // no user template (or degenerate) - placeholder path
        }
        StructureTemplate t = template.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        // Anchor the structure so its CENTRE sits on the origin column (export origin = corner).
        BlockPos origin = centre.offset(-t.getSize().getX() / 2, 0, -t.getSize().getZ() / 2);
        t.placeInWorld(ark, origin, origin, settings, ark.getRandom(), 2);
        ark.setRespawnData(new LevelData.RespawnData(
                GlobalPos.of(EdenDimensions.ARK, findStandingSpot(ark, centre.above())), 0.0f, 0.0f));
        return true;
    }

    /** First free spot at or above the centre where a player can stand inside the placed structure. */
    private static BlockPos findStandingSpot(ServerLevel ark, BlockPos from) {
        BlockPos p = from;
        for (int i = 0; i < 24; i++) {
            if (ark.getBlockState(p).isAir() && ark.getBlockState(p.above()).isAir()
                    && !ark.getBlockState(p.below()).isAir()) {
                return p;
            }
            p = p.above();
        }
        return from;
    }
}
