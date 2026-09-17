package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

/**
 * 裂隙祭坛 (rift altar, 功能清单 §8 v2): the ark's card-forge. Right-click opens the
 * vanilla-style {@link RiftAltarMenu} through the standard {@link MenuProvider} flow (per user
 * feedback the old code-drawn screen's clicks never landed; a real container menu gets click, drag,
 * shift-click and quick-craft for free). Fuse three same-quality cards into one of the next tier,
 * or feed a boss material + supply points to ascend a card's star level - all consumed and rolled
 * server-side inside the menu.
 */
public class RiftAltarBlock extends Block {
    public static final MapCodec<RiftAltarBlock> CODEC = simpleCodec(RiftAltarBlock::new);

    public RiftAltarBlock(Properties properties) {
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
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new RiftAltarMenu(id, inv),
                    Component.translatable("eden.forge.title")));
        }
        return InteractionResult.SUCCESS;
    }
}
