package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: open the per-table loot editor - the server enumerates the chest loot
 * tables (vanilla + mods) and answers with a {@link LootTablesPayload}.
 */
public record RequestLootTablesPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestLootTablesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "request_loot_tables"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLootTablesPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestLootTablesPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
