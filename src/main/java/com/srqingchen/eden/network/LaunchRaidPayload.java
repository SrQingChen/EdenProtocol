package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: launch an expedition from the pad screen. The server re-validates every unlock
 * (campaign stage gates difficulty tiers and destinations) before handing off to
 * {@link com.srqingchen.eden.system.RaidService#startRaid}; a stale screen can never bypass the gate.
 */
public record LaunchRaidPayload(String difficulty, String dimension) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LaunchRaidPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "launch_raid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchRaidPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LaunchRaidPayload::difficulty,
            ByteBufCodecs.STRING_UTF8, LaunchRaidPayload::dimension,
            LaunchRaidPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
