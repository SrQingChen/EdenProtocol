package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: a rift-altar forge request. {@code recipe} is "fuse" or "ascend"; the slots name
 * concrete main-inventory indexes the CLIENT clicked - the server re-validates every one of them
 * against the real stacks before consuming anything ({@link com.srqingchen.eden.system.RiftForge}).
 */
public record RiftForgePayload(String recipe, int slotA, int slotB, int slotC) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RiftForgePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "rift_forge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftForgePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RiftForgePayload::recipe,
            ByteBufCodecs.VAR_INT, RiftForgePayload::slotA,
            ByteBufCodecs.VAR_INT, RiftForgePayload::slotB,
            ByteBufCodecs.VAR_INT, RiftForgePayload::slotC,
            RiftForgePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
