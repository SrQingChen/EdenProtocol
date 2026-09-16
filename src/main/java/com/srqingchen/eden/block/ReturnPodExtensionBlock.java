package com.srqingchen.eden.block;

import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The invisible filler occupying each cell above a {@link ReturnPodBlock}, so the pod's ~4-block-tall OBJ
 * silhouette gets real per-cell collision and targeting. MC resolves collision and the crosshair raytrace
 * cell-by-cell, so a single block's shape can never reach into the cells above it - these companion blocks
 * fill them. They render as {@link RenderShape#INVISIBLE} (the pod base's OBJ model already draws the whole
 * silhouette), carry no item, and are only ever placed by the locator alongside the base. Breaking ANY cell
 * (base or filler) tears down the entire pod; a filler finds its base by scanning straight down.
 */
public class ReturnPodExtensionBlock extends Block {
    public ReturnPodExtensionBlock(Properties properties) {
        super(properties);
    }

    /** Full-cell collision: the pod hull fills its 1x1 footprint at these heights. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ReturnPodBlock.CELL_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Pick-block (middle-click) yields the return pod item; this technical block has no item of its own. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(EdenItems.RETURN_POD_ITEM.get());
    }

    /** Breaking a filler cell tears down the whole pod (base + all fillers); this cell is left to the caller's flow. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos base = ReturnPodBlock.findBase(level, pos);
        if (base != null) {
            ReturnPodBlock.clearStructure(level, base, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Right-clicking a filler cell forwards to the pod base's BlockEntity, so the crew can register / feed
     * fuel by clicking anywhere on the 4-block-tall silhouette (the base BE lives in the bottom cell).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ReturnPodBlockEntity pod = findPod(level, pos);
        if (pod == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            pod.register(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ReturnPodBlockEntity pod = findPod(level, pos);
        if (pod == null) {
            return InteractionResult.PASS;
        }
        boolean isCell = stack.getItem() == EdenItems.EDEN_CELL.get();
        boolean isCrystal = stack.getItem() == EdenItems.TAINT_CRYSTAL.get();
        if (!isCell && !isCrystal) {
            // Let the empty-hand register path run afterwards (mirrors the pod base behaviour).
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

    /** Resolve the pod base's BlockEntity from any filler cell (scan down for the base, then its BE). */
    @Nullable
    private static ReturnPodBlockEntity findPod(Level level, BlockPos pos) {
        BlockPos base = ReturnPodBlock.findBase(level, pos);
        if (base == null) {
            return null;
        }
        return level.getBlockEntity(base) instanceof ReturnPodBlockEntity pod ? pod : null;
    }
}
