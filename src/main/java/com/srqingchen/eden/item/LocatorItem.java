package com.srqingchen.eden.item;

import com.srqingchen.eden.block.ReturnPodBlock;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The locator: found as salvage during a raid. Right-clicking a surface in the polluted raid world
 * deploys a {@code return pod} there (consuming the locator). It refuses to deploy outside the raid
 * world, so pods can only be set up on an active expedition.
 */
public class LocatorItem extends Item {
    public LocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        boolean inRaidDim = level.dimension().equals(EdenDimensions.RAID_OVERWORLD)
                || level.dimension().equals(EdenDimensions.RAID_NETHER)
                || level.dimension().equals(EdenDimensions.RAID_END);
        if (!inRaidDim) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        // The pod is a 4-cell structure (base + 3 invisible extension cells above). Deploy only when the base
        // cell and all 3 above it are free, so a pod is never left half-built.
        if (!ReturnPodBlock.canPlaceAt(level, pos)) {
            return InteractionResult.FAIL;
        }
        ReturnPodBlock.placeStructure(level, pos);
        Player player = context.getPlayer();
        if (player != null) {
            if (player instanceof ServerPlayer sp) {
                EdenMessages.send(sp, Type.INFO, "eden.msg.pod_deployed");
                // eng_deploy (快速部署): the deploying engineer primes the pod with +15% starting charge.
                if (com.srqingchen.eden.talent.TalentSystem.hasMech(sp, "eng_deploy")
                        && level.getBlockEntity(pos) instanceof com.srqingchen.eden.block.ReturnPodBlockEntity pod) {
                    pod.injectCharge(com.srqingchen.eden.block.ReturnPodBlockEntity.CHARGE_MAX * 0.15f);
                    EdenMessages.send(sp, Type.SPECIAL, "eden.msg.pod_deploy_prime");
                }
            }
            if (!player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
