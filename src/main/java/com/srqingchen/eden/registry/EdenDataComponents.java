package com.srqingchen.eden.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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

    /**
     * 《三位起草人》fragment identity (S1 批C): kind 0=肃(purity) 1=融(symbiosis) 2=铭(archive),
     * page 1-6 (archive:6 is the charred final page). One component keeps the item single-registered.
     */
    public record Fragment(int kind, int page) {
        public static final Codec<Fragment> CODEC = RecordCodecBuilder.create(
                inst -> inst.group(
                        Codec.INT.fieldOf("kind").forGetter(Fragment::kind),
                        Codec.INT.fieldOf("page").forGetter(Fragment::page)
                ).apply(inst, Fragment::new));

        public static final StreamCodec<ByteBuf, Fragment> STREAM = new StreamCodec<>() {
            @Override
            public Fragment decode(ByteBuf buf) {
                return new Fragment(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Fragment value) {
                ByteBufCodecs.VAR_INT.encode(buf, value.kind());
                ByteBufCodecs.VAR_INT.encode(buf, value.page());
            }
        };
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Fragment>> FRAGMENT =
            DATA_COMPONENTS.register("fragment", () -> DataComponentType.<Fragment>builder()
                    .persistent(Fragment.CODEC)
                    .networkSynchronized(Fragment.STREAM)
                    .build());
}
