package com.srqingchen.eden.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.system.RaidService;
import com.srqingchen.eden.system.SettlementService;
import com.srqingchen.eden.system.ShopCatalog;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.function.Predicate;

/**
 * {@code /eden} management commands. For MVP these drive the per-player {@link RaidState} directly
 * (so erosion/diet systems are testable); the dimension teleport is wired in the dimension milestone.
 */
public class EdenCommands {

    /** Admin gate for management subcommands; {@code status} and {@code buy} stay open to all players. */
    private static final Predicate<CommandSourceStack> EDEN_ADMIN =
            src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("eden")
                .then(Commands.literal("start").requires(EDEN_ADMIN)
                        .executes(ctx -> startRaid(ctx, "scout"))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(ctx -> startRaid(ctx, StringArgumentType.getString(ctx, "difficulty")))))
                .then(Commands.literal("stop").requires(EDEN_ADMIN).executes(EdenCommands::stopRaid))
                .then(Commands.literal("status").executes(EdenCommands::status))
                .then(Commands.literal("set_difficulty").requires(EDEN_ADMIN)
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(ctx -> setDifficulty(ctx, StringArgumentType.getString(ctx, "difficulty")))))
                // Dimension travel (the real MVP loop): enter the polluted world, or return to the ark.
                .then(Commands.literal("enter").requires(EDEN_ADMIN)
                        .executes(ctx -> enterRaid(ctx, "scout"))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(ctx -> enterRaid(ctx, StringArgumentType.getString(ctx, "difficulty")))))
                .then(Commands.literal("ark").requires(EDEN_ADMIN).executes(EdenCommands::returnToArk))
                // Ark requisition: open to all players (the shop action); spends shared supply points.
                .then(Commands.literal("buy")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> buy(ctx, StringArgumentType.getString(ctx, "item"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> buy(ctx, StringArgumentType.getString(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                // One-shot ark outfitting for admins: places launch pad + terminal + storage chest.
                .then(Commands.literal("setup_ark").requires(EDEN_ADMIN).executes(EdenCommands::setupArk)));
    }

    private static int startRaid(CommandContext<CommandSourceStack> ctx, String difficulty) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.WARNING, "eden.msg.need_player"));
            return 0;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        state.inRaid = true;
        state.difficulty = difficulty;
        state.raidStartTick = player.level().getGameTime();
        state.erosion = 0f;
        state.erosionLevel = 0;
        EdenNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.SUCCESS, "eden.msg.raid_started", difficulty), true);
        return 1;
    }

    private static int stopRaid(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        player.getData(EdenAttachments.RAID_STATE).reset();
        EdenNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.INFO, "eden.msg.raid_stopped"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        Component line = EdenMessages.styled(Type.INFO, "eden.msg.status", state.inRaid, state.difficulty,
                String.format("%.1f", state.erosion), EdenConfig.EROSION_MAX.get(), state.erosionLevel);
        ctx.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    private static int enterRaid(CommandContext<CommandSourceStack> ctx, String difficulty) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.WARNING, "eden.msg.need_player"));
            return 0;
        }
        if (!RaidService.startRaid(player, difficulty)) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.DANGER, "eden.msg.raid_dim_unavailable"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.SUCCESS, "eden.msg.entered_raid", difficulty), true);
        return 1;
    }

    private static int returnToArk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.WARNING, "eden.msg.need_player"));
            return 0;
        }
        if (!DimensionManager.enterArk(player)) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.DANGER, "eden.msg.ark_dim_unavailable"));
            return 0;
        }
        SettlementService.settle(player);
        player.getData(EdenAttachments.RAID_STATE).reset();
        EdenNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.SUCCESS, "eden.msg.returned_ark"), true);
        return 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> ctx, String difficulty) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        state.difficulty = difficulty;
        EdenNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.INFO, "eden.msg.difficulty_set", difficulty), true);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> ctx, String key, int count) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.WARNING, "eden.msg.need_player"));
            return 0;
        }
        return ShopCatalog.buy(player, key, count) > 0 ? 1 : 0;
    }

    private static int setupArk(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel ark = server.getLevel(EdenDimensions.ARK);
        if (ark == null) {
            ctx.getSource().sendFailure(EdenMessages.styled(Type.DANGER, "eden.msg.ark_dim_unavailable"));
            return 0;
        }
        // Flat ark: resolve the standing surface Y in front of spawn, then lay out the facility strip.
        int y = ark.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 3);
        BlockPos pad = new BlockPos(0, y, 3);
        int flags = Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS;
        ark.setBlock(pad, EdenBlocks.LAUNCH_PAD.get().defaultBlockState(), flags);
        ark.setBlock(pad.east(2), EdenBlocks.SHOP.get().defaultBlockState(), flags);
        ark.setBlock(pad.west(2), Blocks.CHEST.defaultBlockState(), flags);
        // Personal lockers + the chronicle wall (campaign panel) flank the pad.
        ark.setBlock(pad.south(2), EdenBlocks.LOCKER.get().defaultBlockState(), flags);
        ark.setBlock(pad.south(2).east(2), EdenBlocks.LOCKER.get().defaultBlockState(), flags);
        ark.setBlock(pad.south(2).west(2), EdenBlocks.CHRONICLE_WALL.get().defaultBlockState(), flags);
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null && !player.level().dimension().equals(EdenDimensions.ARK)) {
            DimensionManager.enterArk(player);
        }
        ctx.getSource().sendSuccess(() -> EdenMessages.styled(Type.SUCCESS, "eden.msg.ark_outfitted"), true);
        return 1;
    }
}
