package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.registry.EdenBlockEntities;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The return pod block. Empty-hand use registers the player aboard (starting the charge); using an Eden Cell or
 * Taint Crystal feeds fuel that raises the charge EFFICIENCY (the rate the single charge pool fills at - the same
 * pool the extraction defenses drain). Server-side ticking (via {@link ReturnPodBlockEntity#serverTick}) advances
 * the charge and launches once the pool is full. Renders as a normal model (an explicit {@link RenderShape#MODEL}
 * override keeps it visible even though it is an entity block).
 */
public class ReturnPodBlock extends BaseEntityBlock {
    public static final MapCodec<ReturnPodBlock> CODEC = simpleCodec(ReturnPodBlock::new);

    public ReturnPodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReturnPodBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, EdenBlockEntities.RETURN_POD.get(), ReturnPodBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Full-cell collision shared by the base and its invisible extension cells (the hull fills its 1x1 footprint). */
    public static final VoxelShape CELL_SHAPE = box(0, 0, 0, 16, 16, 16);

    /**
     * Vertical cells the pod occupies: the base plus {@code STRUCTURE_HEIGHT - 1} invisible
     * {@link ReturnPodExtensionBlock} cells above it. The OBJ model is ~4.25 blocks tall; collision covers
     * the bottom 4 blocks (the tapered nose's last quarter-block stays visual-only). MC resolves collision
     * and the crosshair raytrace per cell, so a multi-cell structure is the only way the whole silhouette is
     * solid and targetable - a single block's shape cannot reach into the cells above it.
     */
    public static final int STRUCTURE_HEIGHT = 4;

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CELL_SHAPE;
    }

    /** Breaking the base tears down the extension cells above (the base cell itself is removed by the caller's flow). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        clearStructure(level, pos, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Scan straight down (up to {@link #STRUCTURE_HEIGHT}-1 cells) for the base an extension cell belongs to. */
    @Nullable
    public static BlockPos findBase(Level level, BlockPos from) {
        for (int i = 1; i < STRUCTURE_HEIGHT; i++) {
            BlockPos p = from.below(i);
            if (level.getBlockState(p).getBlock() == EdenBlocks.RETURN_POD.get()) {
                return p;
            }
        }
        return null;
    }

    /**
     * Remove the whole pod (base + extension cells), skipping {@code except} - that cell is being broken by
     * the caller's own flow and must not be double-removed. Uses {@code removeBlock} (no drops): the pod has
     * no loot table, so nothing would drop either way.
     */
    public static void clearStructure(Level level, BlockPos base, @Nullable BlockPos except) {
        for (int i = STRUCTURE_HEIGHT - 1; i >= 0; i--) {
            BlockPos p = base.above(i);
            if (!p.equals(except)) {
                level.removeBlock(p, false);
            }
        }
    }

    /** True if the base cell and the {@link #STRUCTURE_HEIGHT}-1 cells above it are all replaceable (air-like). */
    public static boolean canPlaceAt(Level level, BlockPos base) {
        for (int i = 0; i < STRUCTURE_HEIGHT; i++) {
            if (!level.getBlockState(base.above(i)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** Place the base pod plus its invisible extension cells above (all cells at once, none left out). */
    public static void placeStructure(Level level, BlockPos base) {
        level.setBlock(base, EdenBlocks.RETURN_POD.get().defaultBlockState(), UPDATE_NEIGHBORS | UPDATE_CLIENTS);
        for (int i = 1; i < STRUCTURE_HEIGHT; i++) {
            level.setBlock(base.above(i), EdenBlocks.RETURN_POD_EXTENSION.get().defaultBlockState(), UPDATE_NEIGHBORS | UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReturnPodBlockEntity pod)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            pod.register(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReturnPodBlockEntity pod)) {
            return InteractionResult.PASS;
        }
        // 26.1.2 removed the is(ItemLike) overload, so compare getItem() directly.
        boolean isCell = stack.getItem() == EdenItems.EDEN_CELL.get();
        boolean isCrystal = stack.getItem() == EdenItems.TAINT_CRYSTAL.get();
        if (!isCell && !isCrystal) {
            // 26.1.2: only TRY_WITH_EMPTY_HAND lets the empty-hand useWithoutItem (register) run after the
            // item interaction passes. A plain PASS makes ServerPlayerGameMode skip useWithoutItem entirely,
            // so an empty-hand right-click would never register (the pod would feel un-clickable).
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            stack.shrink(1);
            if (player instanceof ServerPlayer sp) {
                pod.addFuel(sp, isCell);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
