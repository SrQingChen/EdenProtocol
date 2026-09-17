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
 * Client -&gt; server: save ONE table's explicit override from the per-table editor. Mode: 0 clear
 * (back to the global rules), 1 forced on, 2 forced off. When mode is 1 the pools travel flattened
 * exactly like the global block ({@code n * MAX_ITEMS} parallel lists + min,max roll pairs); an
 * empty pools list means "forced on, but use the global pools". Server sanitises, persists and
 * re-applies via the background datapack reload.
 */
public record SaveTableLootPayload(String tableId, int mode,
                                   List<Integer> rolls, List<String> items, List<Integer> weights,
                                   List<Integer> min, List<Integer> max, List<Float> chance)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveTableLootPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "save_table_loot"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Float>> FLOAT_LIST =
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTableLootPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SaveTableLootPayload::tableId,
            ByteBufCodecs.VAR_INT, SaveTableLootPayload::mode,
            INT_LIST, SaveTableLootPayload::rolls,
            STRING_LIST, SaveTableLootPayload::items,
            INT_LIST, SaveTableLootPayload::weights,
            INT_LIST, SaveTableLootPayload::min,
            INT_LIST, SaveTableLootPayload::max,
            FLOAT_LIST, SaveTableLootPayload::chance,
            SaveTableLootPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
