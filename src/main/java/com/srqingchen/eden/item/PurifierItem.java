package com.srqingchen.eden.item;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 净化剂 (purifier) - the erosion-relief consumable (功能清单 §5 "侵蚀缓解手段"): right-click during a raid
 * to purge {@link #EROSION_RELIEF} erosion. Consumed on use; worthless (but not consumed) outside a raid
 * or with nothing to purge.
 */
public class PurifierItem extends Item {
    /** Erosion removed per use. */
    public static final float EROSION_RELIEF = 30.0f;

    public PurifierItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        RaidState state = sp.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid || state.erosion <= 0.01f) {
            EdenMessages.overlay(sp, Type.INFO, "eden.msg.purifier_no_need");
            return InteractionResult.PASS;
        }
        state.erosion = Math.max(0f, state.erosion - EROSION_RELIEF);
        EdenNetwork.syncTo(sp);
        held.shrink(1);
        serverLevel.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.1, sp.getZ(),
                24, 0.4, 0.7, 0.4, 0.08);
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.4f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.msg.purifier_used", (int) EROSION_RELIEF,
                (int) Math.max(0f, state.erosion));
        return InteractionResult.CONSUME;
    }
}
