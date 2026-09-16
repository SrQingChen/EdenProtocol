package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: request to buy {@code count} of the catalog entry {@code key}. The server is
 * authoritative - it validates the balance, deducts points, hands over the items and replies with a
 * {@link ShopBalancePayload}.
 */
public record BuyPayload(String key, int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "buy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BuyPayload::key,
            ByteBufCodecs.VAR_INT, BuyPayload::count,
            BuyPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
