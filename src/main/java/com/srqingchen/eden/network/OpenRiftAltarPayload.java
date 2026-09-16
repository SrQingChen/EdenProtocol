package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: open the rift-altar forge screen, carrying the campaign supply balance so the
 * ascend recipe can show its cost live. All crafting requests go back server-bound via
 * {@link RiftForgePayload} and are re-validated there.
 */
public record OpenRiftAltarPayload(int balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenRiftAltarPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "open_rift_altar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRiftAltarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenRiftAltarPayload::balance,
            OpenRiftAltarPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
