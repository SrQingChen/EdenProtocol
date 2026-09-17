package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: cast a "advance the season" vote from the chronicle wall's advance button
 * (S1 批A). The first click opens a 30-second server-wide vote; the season advances the moment
 * more than half of the online players have clicked (see {@code SeasonSystem.castAdvanceVote}).
 */
public record AdvanceVotePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdvanceVotePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "advance_vote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvanceVotePayload> STREAM_CODEC =
            StreamCodec.unit(new AdvanceVotePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
