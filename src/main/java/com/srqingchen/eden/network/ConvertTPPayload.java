package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -&gt; server: convert supply points into talent points ({@code amount} = talent points to buy). */
public record ConvertTPPayload(int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConvertTPPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "convert_tp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConvertTPPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ConvertTPPayload::amount, ConvertTPPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
