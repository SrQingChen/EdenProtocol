package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.season.SeasonFinale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the season-finale colosseum (批 D) inside the {@code eden:finale_arena} void dimension —
 * code-generated exactly like the ark's orbital station. Deep-dark theme to face S0's warden
 * apex: a polished-deepslate disc with a blackstone mosaïc ring, a raised sculk boss dais at the
 * centre, a soul-lantern lit rim wall with four gated stairways, and an obsidian foundation
 * hanging into the void. Idempotent: the dais marker block is the "already built" check, so
 * re-entering (multi-retry by design) never duplicates or stacks anything.
 */
public final class FinaleArenaBuilder {
    private FinaleArenaBuilder() {}

    /** Floor surface Y of the arena. */
    public static final int FLOOR_Y = 64;
    /** Boss dais centre = the arena's world origin. */
    public static final BlockPos DAIS = new BlockPos(0, FLOOR_Y, 0);

    /** Generate the colosseum if it is not already there. Returns the spectator spawn on the rim. */
    public static BlockPos ensureArena(ServerLevel arena, SeasonFinale spec) {
        if (!arena.dimension().equals(EdenDimensions.FINALE_ARENA)) {
            return DAIS;
        }
        if (arena.getBlockState(DAIS.above()).is(Blocks.SCULK)) {
            return rimSpawn(arena, spec);
        }
        int radius = Math.max(24, Math.min(64, spec.arenaRadius()));
        buildDisc(arena, radius);
        buildRim(arena, radius);
        buildGates(arena, radius);
        buildDais(arena);
        EdenProtocol.LOGGER.info("[Eden] generated finale colosseum (radius {}, floor y={})", radius, FLOOR_Y);
        return rimSpawn(arena, spec);
    }

    /** Where challengers land: on the floor disc by the southern gate, facing the dais. */
    private static BlockPos rimSpawn(ServerLevel arena, SeasonFinale spec) {
        int radius = Math.max(24, Math.min(64, spec.arenaRadius()));
        return new BlockPos(0, FLOOR_Y + 1, radius - 3);
    }

    private static void buildDisc(ServerLevel arena, int radius) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState mosaic = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        int r2 = radius * radius;
        int ring2 = (radius - 4) * (radius - 4);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 > r2) {
                    continue;
                }
                // Mosaic ring near the rim, plain deepslate floor inside.
                BlockState state = d2 >= ring2 ? mosaic : floor;
                arena.setBlock(new BlockPos(x, FLOOR_Y, z), state, Block.UPDATE_CLIENTS);
                // Foundation layer under the floor so the disc reads as solid from below.
                arena.setBlock(new BlockPos(x, FLOOR_Y - 1, z), Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void buildRim(ServerLevel arena, int radius) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState top = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState lamp = Blocks.SOUL_LANTERN.defaultBlockState();
        int r2 = radius * radius;
        int inner2 = (radius - 1) * (radius - 1);
        // Raised walkway ring (2 wide, 1 up) with the wall on its outer edge.
        int walk2 = (radius - 2) * (radius - 2);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 > r2 || d2 < walk2) {
                    continue;
                }
                boolean outer = d2 >= inner2;
                arena.setBlock(new BlockPos(x, FLOOR_Y + 1, z), outer ? wall : top, Block.UPDATE_CLIENTS);
                // Soul lanterns stud the walkway every 5 cells — the only light in the deep dark.
                if (!outer && ((x + z) & 7) == 0 && (x & 3) == 0) {
                    arena.setBlock(new BlockPos(x, FLOOR_Y + 2, z), lamp, Block.UPDATE_CLIENTS);
                }
                if (outer && d2 <= r2) {
                    arena.setBlock(new BlockPos(x, FLOOR_Y + 2, z), wall, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Four stair gaps punched through the rim wall (N/E/S/W): the challengers' way in. */
    private static void buildGates(ServerLevel arena, int radius) {
        for (int d = radius - 3; d <= radius; d++) {
            gate(arena, 0, -d);
            gate(arena, d, 0);
            gate(arena, 0, d);
            gate(arena, -d, 0);
        }
    }

    private static void gate(ServerLevel arena, int x, int z) {
        arena.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(x, FLOOR_Y + 2, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(x, FLOOR_Y + 3, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The boss dais: a raised sculk circle with an echoing shrieker at each cardinal point. */
    private static void buildDais(ServerLevel arena) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x * x + z * z <= 16) {
                    arena.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        for (int[] card : new int[][]{{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
            // Decorative only: a shrieker that could summon would flood the fight with wardens.
            arena.setBlock(new BlockPos(card[0], FLOOR_Y + 1, card[1]),
                    Blocks.SCULK_SHRIEKER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SculkShriekerBlock.CAN_SUMMON, false),
                    Block.UPDATE_CLIENTS);
        }
        // Marker: the dais centre keeps its sculk block (checked by ensureArena's idempotency test).
    }
}
