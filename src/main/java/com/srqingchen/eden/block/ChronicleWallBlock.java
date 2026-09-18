package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.network.ChroniclePayload;
import com.srqingchen.eden.system.CampaignSystem;
import net.minecraft.core.BlockPos;
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
 * The chronicle wall (§13 编年史墙) - and, per the v1 decision, the Duststar campaign panel (§14): the
 * server-side half just snapshots {@link CampaignData} (pollution, stage, stats, highlights, next
 * objectives) into a {@link ChroniclePayload}; the client renders it as the expedition board. One
 * wall, zero external quest mods.
 */
public class ChronicleWallBlock extends Block {
    public static final MapCodec<ChronicleWallBlock> CODEC = simpleCodec(ChronicleWallBlock::new);

    public ChronicleWallBlock(Properties properties) {
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
        if (!(player instanceof ServerPlayer sp) || level.getServer() == null) {
            return InteractionResult.PASS;
        }
        CampaignData data = CampaignData.get(level.getServer());
        data.addProtocolArchive(0.5f);   // 三值温度计 (批C 存档值): consulting the record feeds it
        List<String> highlightKeys = new ArrayList<>();
        List<String> highlightPlayers = new ArrayList<>();
        List<Integer> highlightValues = new ArrayList<>();
        for (CampaignData.Highlight h : data.highlights()) {
            highlightKeys.add(h.key());
            highlightPlayers.add(h.player());
            highlightValues.add(h.value());
        }
        PacketDistributor.sendToPlayer(sp, new ChroniclePayload(
                data.pollution(), data.stage(), data.paradiseUnlocked(),
                data.getSupplyPoints(), data.totalRaids(), data.successfulExtracts(),
                data.coresDestroyed(), data.dragonsSlain(),
                highlightKeys, highlightPlayers, highlightValues,
                data.seasonIndex(),
                com.srqingchen.eden.season.Seasons.current(level.getServer()).titleKey(),
                data.contractsDone().size(),
                com.srqingchen.eden.season.SeasonSystem.currentContracts(level.getServer()).size(),
                com.srqingchen.eden.season.SeasonSystem.canAdvance(level.getServer()),
                com.srqingchen.eden.season.SeasonSystem.voteState(level.getServer())[0],
                com.srqingchen.eden.season.SeasonSystem.voteYesCount(),
                level.getServer().getPlayerList().getPlayerCount() / 2 + 1,
                data.finaleUnlocked(), data.seasonCompleted(), data.endingsSeen(),
                data.protocolPurity(), data.protocolSymbiosis(), data.protocolArchive(),
                data.pagesFound("purity"), data.pagesFound("symbiosis"), data.pagesFound("archive")));
        return InteractionResult.SUCCESS;
    }
}
