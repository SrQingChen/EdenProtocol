package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -&gt; client: the loot-table LIST for the per-table editor. Enumerates every chest-type
 * loot table in the registry (CHEST param set or {@code chests/} path - vanilla + mods), each with
 * its tri-state (0 follow the global rules, 1 forced on, 2 forced off), a lang key hinting at the
 * structure that uses it ("" when unknown) and the table's current pool block - the global pools
 * when no explicit override exists, so the editor always opens with something sensible. Sent on
 * demand ({@link RequestLootTablesPayload}).
 */
public record LootTablesPayload(List<String> ids, List<Integer> states, List<String> hints,
                                List<LootPayloadBlock> blocks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LootTablesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "loot_tables"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, LootTablesPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_LIST, LootTablesPayload::ids,
            INT_LIST, LootTablesPayload::states,
            STRING_LIST, LootTablesPayload::hints,
            LootPayloadBlock.STREAM_CODEC.apply(ByteBufCodecs.list()), LootTablesPayload::blocks,
            LootTablesPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Rows as (id, state, hint, pools) tuples for the list screen. */
    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        int n = Math.min(Math.min(ids.size(), states.size()), Math.min(hints.size(), blocks.size()));
        for (int i = 0; i < n; i++) {
            out.add(new Row(ids.get(i), states.get(i), hints.get(i), blocks.get(i)));
        }
        return out;
    }

    public record Row(String id, int state, String hintKey, LootPayloadBlock pools) {}
}
