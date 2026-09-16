package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.network.OpenRiftAltarPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 裂隙祭坛 (rift altar, 功能清单 §8 v2): the ark's card-forge. Right-click pushes the player's
 * campaign supply balance and opens the code-drawn forge screen - fuse three same-quality cards into
 * one of the next tier, or feed a boss material + supply points to ascend a card's star level.
 * All crafting is validated server-side by {@link com.srqingchen.eden.system.RiftForge}.
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
            int balance = CampaignData.get(sp.level().getServer()).getSupplyPoints();
            PacketDistributor.sendToPlayer(sp, new OpenRiftAltarPayload(balance));
        }
        return InteractionResult.SUCCESS;
    }
}
