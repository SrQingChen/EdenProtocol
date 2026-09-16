package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The paradise gate: the campaign-victory landmark in the ark. Right-click to cross into the purified
 * reward world {@code eden:paradise} (or back to the ark from inside it). Refuses with a hint while the
 * campaign pollution is above zero, so it doubles as a visible goal marker the moment it appears.
 */
public class ParadiseGateBlock extends Block {
    public static final MapCodec<ParadiseGateBlock> CODEC = simpleCodec(ParadiseGateBlock::new);

    public ParadiseGateBlock(Properties properties) {
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
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (level.dimension().equals(EdenDimensions.PARADISE)) {
            DimensionManager.enterArk(sp);
            EdenMessages.send(sp, Type.INFO, "eden.msg.paradise_return");
            return InteractionResult.SUCCESS;
        }
        if (!level.dimension().equals(EdenDimensions.ARK)) {
            return InteractionResult.PASS;
        }
        CampaignData data = CampaignData.get(sp.level().getServer());
        if (!data.paradiseUnlocked()) {
            EdenMessages.send(sp, Type.WARNING, "eden.msg.paradise_locked", data.pollution());
            return InteractionResult.SUCCESS;
        }
        if (DimensionManager.enterParadise(sp)) {
            EdenMessages.send(sp, Type.SPECIAL, "eden.msg.paradise_entered");
            return InteractionResult.SUCCESS;
        }
        EdenMessages.send(sp, Type.DANGER, "eden.msg.paradise_unavailable");
        return InteractionResult.SUCCESS;
    }
}
