package com.srqingchen.eden.registry;

import com.mojang.serialization.Codec;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom item data components. The card star level (1-5) is stored per-stack here; {@code CardItem} reads
 * it to scale its attribute modifiers and to render ★N in the quality-coloured card name. Registered on
 * the mod bus from {@code EdenProtocol}.
 */
public class EdenDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EdenProtocol.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CARD_STAR =
            DATA_COMPONENTS.register("card_star", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());
}
