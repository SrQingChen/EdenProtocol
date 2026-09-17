package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.block.RiftAltarMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom container menus. The rift altar (v2 card forge) is a proper vanilla-style
 * {@link MenuType}/{@link net.minecraft.world.MenuProvider} menu (per user feedback: the old
 * code-drawn staging screen could not perform its function; vanilla slot interactions - click,
 * drag, shift-click, quick-craft - are what players expect and they come free this way).
 */
public class EdenMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EdenProtocol.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RiftAltarMenu>> RIFT_ALTAR =
            MENUS.register("rift_altar", () -> new MenuType<>(RiftAltarMenu::new, FeatureFlags.VANILLA_SET));
}
