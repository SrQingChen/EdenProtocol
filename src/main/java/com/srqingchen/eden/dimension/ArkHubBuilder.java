package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelData;

import java.util.Optional;

/**
 * Builds the ark's spawn structure. The ark is a void dimension, so on first entry we generate a
 * code-built ORBITAL STATION centred on the origin and move the world spawn onto it, so a respawn
 * or fresh join lands on the deck instead of falling into the void.
 * <p>The station is an octagonal plating deck (iron panels + polished-deepslate rim, cyan accent
 * ring with flush sea-lantern floor lights, end-rod beacons on the rim railing) over a deepslate
 * hull with a central reactor mast and corner struts hanging into the void. The deck top sits at
 * y=64, matching the old placeholder platform, so facility anchors (setup_ark's launch pad at
 * z=3, the paradise gate at z=-3) keep resolving from the heightmap straight onto the deck.
 * <p><b>User-supplied ark (§13 方舟 .nbt 美化)</b>: drop an exported structure file at
 * {@code src/main/resources/data/eden/structure/ark.nbt} (structure block export, origin corner at the
 * world spawn side) and it is placed at (0, 64, 0) INSTEAD of the code-built station - the placement
 * is idempotent (same template, same spot), so re-entering the ark just re-asserts it.
 */
public final class ArkHubBuilder {
    private ArkHubBuilder() {}

    /** Top surface Y of the station deck / the structure origin. */
    private static final int PLATFORM_Y = 64;
    /** Octagon "radius" (Chebyshev): deck spans x/z in [-9, 9] with the corners cut. */
    private static final int RADIUS = 9;
    /** Corner cut: rim cells with |x|+|z| above this are trimmed, turning the square into an octagon. */
    private static final int CUT_SUM = RADIUS + 5;
    /** The user's exported ark structure, resolved from the data pack if present. */
    private static final Identifier ARK_TEMPLATE = Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "ark");

    /**
     * Generate the station if it is not already there, and point the world spawn at it.
     */
    public static void ensurePlatform(ServerLevel ark) {
        BlockPos centre = new BlockPos(0, PLATFORM_Y, 0);
        if (tryPlaceUserTemplate(ark, centre)) {
            return;
        }
        if (ark.getBlockState(centre).is(Blocks.SEA_LANTERN)) {
            // Station already asserts the spawn pad - just re-anchor the world spawn.
            ark.setRespawnData(new LevelData.RespawnData(
                    GlobalPos.of(EdenDimensions.ARK, centre.above()), 0.0f, 0.0f));
            return;
        }
        buildStation(ark);
        ark.setRespawnData(new LevelData.RespawnData(GlobalPos.of(EdenDimensions.ARK, centre.above()), 0.0f, 0.0f));
        EdenProtocol.LOGGER.info("[Eden] generated ark orbital station (octagon r={}, deck y={})", RADIUS, PLATFORM_Y);
    }

    /**
     * Place {@code eden:ark} (the user's exported structure) at the origin if the data pack ships it.
     * Returns true when the template exists (the code-built station is then skipped). Re-placement is
     * cheap and idempotent; the spawn anchor is re-asserted on top of the structure's centre each time.
     */
    private static boolean tryPlaceUserTemplate(ServerLevel ark, BlockPos centre) {
        Optional<StructureTemplate> template = ark.getStructureManager().get(ARK_TEMPLATE);
        if (template.isEmpty() || template.get().getSize().getX() < 3) {
            return false;   // no user template (or degenerate) - code-built path
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

    // ---------- the code-built orbital station ----------

    private static void buildStation(ServerLevel ark) {
        // Deck (y=64) + hull underside (y=63) in one pass.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int cheb = Math.max(Math.abs(x), Math.abs(z));
                if (Math.abs(x) + Math.abs(z) > CUT_SUM) {
                    continue;                                            // corner cut -> octagon
                }
                // Hull underside: a dark shell so the deck is not a floating 1-block sheet.
                set(ark, x, PLATFORM_Y - 1, z, Blocks.POLISHED_DEEPSLATE);
                // Deck plating.
                set(ark, x, PLATFORM_Y, z, deckBlock(x, z, cheb));
                // Rim railing, with end-rod beacons on the 8 compass/diagonal points.
                if (cheb == RADIUS) {
                    if (isBeaconPoint(x, z)) {
                        set(ark, x, PLATFORM_Y + 1, z, Blocks.END_ROD);
                    } else if ((x + z) % 2 == 0) {
                        set(ark, x, PLATFORM_Y + 1, z, Blocks.IRON_BARS);
                    }
                }
            }
        }
        reactorMast(ark);
    }

    private static void set(ServerLevel ark, int x, int y, int z, net.minecraft.world.level.block.Block block) {
        ark.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
    }

    private static void set(ServerLevel ark, int x, int y, int z, BlockState state) {
        ark.setBlock(new BlockPos(x, y, z), state, 3);
    }

    /** Deck plating per cell: spawn pad, cyan accent ring with flush lights, rim, panelled field. */
    private static BlockState deckBlock(int x, int z, int cheb) {
        // 3x3 spawn pad: light concrete border + a glowing lantern centre (the respawn anchor).
        if (cheb <= 1) {
            return cheb == 0 ? Blocks.SEA_LANTERN.defaultBlockState()
                    : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        }
        if (cheb == 5) {
            // Flush floor lights on the accent ring at the 4 cardinal + 8 near-diagonal spots.
            int other = Math.abs(x) == 5 ? Math.abs(z) : Math.abs(x);
            if (other == 0 || other == 2) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            // Cyan accent band - the teal ring echoing the launch pad / shop art.
            return Blocks.CYAN_CONCRETE.defaultBlockState();
        }
        // Polished-deepslate rim on the outermost ring.
        if (cheb == RADIUS) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        // Iron plating field with a sparse concrete panel inlay.
        return (x * 7 + z * 5) % 11 == 0 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                : Blocks.IRON_BLOCK.defaultBlockState();
    }

    /**
     * The 8 beacon points on the rim: the four cardinals (±9, 0)/(0, ±9) and the four diagonal
     * rim cells (±9, ±5)/(±5, ±9) left by the corner cut.
     */
    private static boolean isBeaconPoint(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        return (ax == RADIUS && (az == 0 || az == 5)) || (az == RADIUS && (ax == 0 || ax == 5));
    }

    /**
     * Central mast hanging below the hull: a deepslate cage with a glowing core, four cross-arms
     * with lantern tips at mid-height, and corner landing struts - reads as a station anchored in
     * the void rather than a floating slab.
     */
    private static void reactorMast(ServerLevel ark) {
        int top = PLATFORM_Y - 2;
        for (int y = top; y >= PLATFORM_Y - 12; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean shell = Math.abs(x) == 1 || Math.abs(z) == 1;
                    set(ark, x, y, z, shell ? Blocks.POLISHED_DEEPSLATE
                            : (y % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK));
                }
            }
        }
        // Cross-arms with lantern tips, once at mid-mast.
        int ay = PLATFORM_Y - 7;
        for (int d = 2; d <= 5; d++) {
            for (int side = -1; side <= 1; side++) {
                set(ark, d, ay, side, Blocks.POLISHED_DEEPSLATE);
                set(ark, -d, ay, side, Blocks.POLISHED_DEEPSLATE);
                set(ark, side, ay, d, Blocks.POLISHED_DEEPSLATE);
                set(ark, side, ay, -d, Blocks.POLISHED_DEEPSLATE);
            }
            set(ark, d, ay + 1, 0, Blocks.SEA_LANTERN);
            set(ark, -d, ay + 1, 0, Blocks.SEA_LANTERN);
            set(ark, 0, ay + 1, d, Blocks.SEA_LANTERN);
            set(ark, 0, ay + 1, -d, Blocks.SEA_LANTERN);
        }
        // Corner landing struts under the octagon's diagonal rim.
        for (int[] c : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            for (int y = PLATFORM_Y - 2; y >= PLATFORM_Y - 7; y--) {
                set(ark, c[0], y, c[1], Blocks.POLISHED_DEEPSLATE);
            }
        }
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
