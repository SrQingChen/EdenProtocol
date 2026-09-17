package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: carry (or clear, empty id) the season contract chosen on the launch pad's
 * contract row. At most one contract per launch; the server resolves the id against the current
 * season's list, so a stale id from a re-opened screen silently clears the slot.
 */
public record SelectContractPayload(String contractId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectContractPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "select_contract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectContractPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectContractPayload::contractId,
                    SelectContractPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
