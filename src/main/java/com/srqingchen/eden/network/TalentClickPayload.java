package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -&gt; server: request to unlock one talent node. The server re-validates tree / prereqs / cost. */
public record TalentClickPayload(String nodeId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TalentClickPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "talent_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TalentClickPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, TalentClickPayload::nodeId, TalentClickPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
