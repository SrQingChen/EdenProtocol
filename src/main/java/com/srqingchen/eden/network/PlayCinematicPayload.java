package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: play a fullscreen cinematic. {@code video} points at a packed .ecine asset
 * ({@code eden:cinematics/<name>}); {@code sound} is the matching fixed sound slot (may be null when
 * the video is silent). The client silently no-ops when the asset is absent, so cinematics are
 * strictly file-optional content.
 */
public record PlayCinematicPayload(Identifier video, Identifier sound) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayCinematicPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "play_cinematic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayCinematicPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, PlayCinematicPayload::video,
            Identifier.STREAM_CODEC, PlayCinematicPayload::sound,
            PlayCinematicPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
