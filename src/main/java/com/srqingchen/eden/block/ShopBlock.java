package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.system.ShopCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ark requisition terminal: right-click opens the code-drawn shop screen (a searchable, list-style
 * GUI paid for with supply points). Purchases are sent to the server, which validates the balance,
 * deducts points and hands over the items.
 */
public class ShopBlock extends Block {
    public static final MapCodec<ShopBlock> CODEC = simpleCodec(ShopBlock::new);

    public ShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            ShopCatalog.openShop(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
