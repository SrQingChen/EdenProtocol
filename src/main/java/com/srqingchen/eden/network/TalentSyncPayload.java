package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server -&gt; client: the full talent snapshot for the local player, driving {@link ClientTalentData} and the talent
 * screen. {@code classNodes} is a flat list of {@code "<classId>|<nodeId>"} so the client can rebuild every class tree
 * without a map codec. {@code supplyPoints} is the shared campaign balance (for the convert button).
 */
public record TalentSyncPayload(int totalTP, int supplyPoints, String currentClass,
                                List<String> universal, List<String> classNodes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TalentSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "talent_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, TalentSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalentSyncPayload::totalTP,
            ByteBufCodecs.VAR_INT, TalentSyncPayload::supplyPoints,
            ByteBufCodecs.STRING_UTF8, TalentSyncPayload::currentClass,
            STRING_LIST, TalentSyncPayload::universal,
            STRING_LIST, TalentSyncPayload::classNodes,
            TalentSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
