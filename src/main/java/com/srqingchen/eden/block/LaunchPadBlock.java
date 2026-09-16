package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.attachment.TalentData;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.network.OpenLaunchPadPayload;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.system.CampaignSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The ark launch pad: right-click to open the expedition planner (difficulty / destination / class
 * selection, §3/§9/§15). The block itself only gathers the campaign snapshot and pushes it to the
 * client; the actual launch comes back as a {@link com.srqingchen.eden.network.LaunchRaidPayload},
 * which the network layer re-validates against the same unlock rules.
 */
public class LaunchPadBlock extends Block {
    public static final MapCodec<LaunchPadBlock> CODEC = simpleCodec(LaunchPadBlock::new);

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.dimension().equals(EdenDimensions.ARK)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResult.PASS;
        }
        MinecraftServer server = sl.getServer();
        if (server == null) {
            return InteractionResult.PASS;
        }
        CampaignData campaign = CampaignData.get(server);
        int stage = campaign.stage();
        List<Boolean> diffs = new ArrayList<>();
        for (String d : OpenLaunchPadPayload.DIFFICULTIES) {
            diffs.add(CampaignSystem.isDifficultyUnlocked(campaign, d));
        }
        List<Boolean> dims = new ArrayList<>();
        dims.add(true);   // overworld expeditions are always open
        dims.add(stage >= 3 || stage == 0);   // polluted nether
        dims.add(stage >= 4 || stage == 0);   // polluted end
        String currentClass = sp.getData(EdenAttachments.TALENT_DATA).currentClass;
        PacketDistributor.sendToPlayer(sp, new OpenLaunchPadPayload(
                stage, campaign.pollution(), campaign.paradiseUnlocked(), diffs, dims, currentClass));
        return InteractionResult.SUCCESS;
    }
}
