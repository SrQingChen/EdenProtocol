package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -&gt; client: refresh the open shop screen's supply-point balance after a purchase.
 */
public record ShopBalancePayload(int balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShopBalancePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "shop_balance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopBalancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopBalancePayload::balance,
            ShopBalancePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
