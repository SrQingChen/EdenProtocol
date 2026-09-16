package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> client: open the shop screen, carrying the player's current supply-point balance plus
 * today's market state (per-key buy-price fluctuation and the shortage-good item id), so the screen
 * can render live prices with trend markers (§19.3).
 */
public record OpenShopPayload(int balance, Map<String, Float> fluctuation, String shortageId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenShopPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "open_shop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenShopPayload::balance,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT),
            OpenShopPayload::fluctuation,
            ByteBufCodecs.STRING_UTF8, OpenShopPayload::shortageId,
            OpenShopPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
