package com.srqingchen.eden.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The loot-injection config block shared by {@link EditorDataPayload} (server -&gt; client, opening the
 * editor) and {@link SaveLootConfigPayload} (client -&gt; server, saving it). Extracted because
 * {@link StreamCodec#composite} caps at 16 fields and the payloads would exceed it inline.
 * <p>Global rules first (enabled / namespace scope / comma-joined prefixes and exclusions), then the
 * per-difficulty pools flattened into {@code n * LootInjectionData.MAX_ITEMS}-long lists: {@code items}
 * holds item ids ("" = empty slot) with weight / min / max / chance columns in parallel lists, plus
 * {@code rolls} as min,max pairs per difficulty.
 */
public record LootPayloadBlock(boolean enabled, boolean allNamespaces, String prefixes, String exclusions,
                               List<Integer> rolls, List<String> items, List<Integer> weights,
                               List<Integer> min, List<Integer> max, List<Float> chance) {

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Float>> FLOAT_LIST =
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, LootPayloadBlock> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LootPayloadBlock::enabled,
            ByteBufCodecs.BOOL, LootPayloadBlock::allNamespaces,
            ByteBufCodecs.STRING_UTF8, LootPayloadBlock::prefixes,
            ByteBufCodecs.STRING_UTF8, LootPayloadBlock::exclusions,
            INT_LIST, LootPayloadBlock::rolls,
            STRING_LIST, LootPayloadBlock::items,
            INT_LIST, LootPayloadBlock::weights,
            INT_LIST, LootPayloadBlock::min,
            INT_LIST, LootPayloadBlock::max,
            FLOAT_LIST, LootPayloadBlock::chance,
            LootPayloadBlock::new);
}
