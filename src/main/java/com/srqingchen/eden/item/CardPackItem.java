package com.srqingchen.eden.item;

import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A card pack: right-click to open it, consuming the pack and granting one random card from the
 * weighted {@link CardPool}. Packs are a rare drop (high-tier chests and a low chance from polluted
 * mobs), giving the roguelite "what will I get" beat without needing a rift-altar GUI.
 */
public class CardPackItem extends Item {
    public CardPackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        DifficultyConfigData.Entry cfg = null;
        MinecraftServer server = level.getServer();
        if (server != null && player instanceof ServerPlayer sp) {
            cfg = DifficultyConfigData.get(server).entryFor(sp.getData(EdenAttachments.RAID_STATE).difficulty);
        }
        ItemStack card = CardPool.roll(level.getRandom(), cfg);
        if (card.isEmpty()) {
            return InteractionResult.PASS;
        }
        Component cardName = card.getHoverName();
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
        held.shrink(1);
        EdenMessages.send(player, Type.SPECIAL, "eden.msg.card_opened", cardName);
        return InteractionResult.CONSUME;
    }
}
