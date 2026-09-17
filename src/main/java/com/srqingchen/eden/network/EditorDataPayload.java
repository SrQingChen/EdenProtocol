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
 * Server -&gt; client: everything the difficulty editor needs - the available entity_modifier profile names
 * plus the current per-difficulty config. All lists are parallel to {@code DifficultyConfigData.DIFFICULTIES}:
 * {@code charge} (base,cell,crystal x5), {@code particle} (scale,chance,speed x5), {@code density} (x5),
 * {@code quality} (5 tiers x5) and {@code star} (5 levels x5). Particle colour is not edited here (the server
 * preserves it on save). The trailing {@link LootPayloadBlock} carries the loot-injection config for the
 * editor's loot tab.
 */
public record EditorDataPayload(List<String> profiles, List<String> selected, List<Float> charge,
                                List<Float> particle, List<Integer> density, List<Integer> quality, List<Integer> star,
                                LootPayloadBlock loot)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditorDataPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "editor_data"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Float>> FLOAT_LIST =
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorDataPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_LIST, EditorDataPayload::profiles,
            STRING_LIST, EditorDataPayload::selected,
            FLOAT_LIST, EditorDataPayload::charge,
            FLOAT_LIST, EditorDataPayload::particle,
            INT_LIST, EditorDataPayload::density,
            INT_LIST, EditorDataPayload::quality,
            INT_LIST, EditorDataPayload::star,
            LootPayloadBlock.STREAM_CODEC, EditorDataPayload::loot,
            EditorDataPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
