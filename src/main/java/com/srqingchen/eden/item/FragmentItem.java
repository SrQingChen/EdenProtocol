package com.srqingchen.eden.item;

import com.srqingchen.eden.registry.EdenDataComponents;
import com.srqingchen.eden.system.FragmentSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 《三位起草人》残片 (S1 批C): one item, identity carried by the {@code eden:fragment} component
 * (kind 0=肃 1=融 2=铭, page 1-6). Right-click DECODES it: the page text is read out, the page is
 * banked server-wide (first copy only), and the scrap burns away. The corner of every page carries
 * the same unreadable triangle rune - the tooltip says so, and explains nothing else.
 */
public class FragmentItem extends Item {

    public FragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        EdenDataComponents.Fragment fragment = stack.get(EdenDataComponents.FRAGMENT.get());
        if (fragment == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            FragmentSystem.read(sp, fragment.kind(), fragment.page());
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                 net.minecraft.world.item.component.TooltipDisplay display,
                                 java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        EdenDataComponents.Fragment fragment = stack.get(EdenDataComponents.FRAGMENT.get());
        if (fragment != null) {
            tooltip.accept(Component.translatable("eden.fragment.kind." + FragmentSystem.KINDS[fragment.kind()]));
            tooltip.accept(Component.translatable("eden.fragment.page", fragment.page()));
        }
        tooltip.accept(Component.translatable("eden.fragment.rune").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        EdenDataComponents.Fragment fragment = stack.get(EdenDataComponents.FRAGMENT.get());
        if (fragment != null && fragment.kind() == FragmentSystem.KIND_ARCHIVE && fragment.page() == 6) {
            return Component.translatable("eden.fragment.charred");   // 铭的终章:焦黑残页
        }
        return super.getName(stack);
    }
}
