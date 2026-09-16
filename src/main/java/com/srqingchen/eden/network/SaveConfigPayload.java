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
 * Client -&gt; server: save the edited per-difficulty config. Mirrors {@link EditorDataPayload} minus the
 * profile list; all lists are parallel to {@code DifficultyConfigData.DIFFICULTIES}. The server is
 * authoritative - it clamps every value and preserves the (non-edited) particle colour.
 */
public record SaveConfigPayload(List<String> selected, List<Float> charge, List<Float> particle,
                                List<Integer> density, List<Integer> quality, List<Integer> star)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "save_difficulty_config"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Float>> FLOAT_LIST =
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveConfigPayload> STREAM_CODEC = StreamCodec.composite(
            STRING_LIST, SaveConfigPayload::selected,
            FLOAT_LIST, SaveConfigPayload::charge,
            FLOAT_LIST, SaveConfigPayload::particle,
            INT_LIST, SaveConfigPayload::density,
            INT_LIST, SaveConfigPayload::quality,
            INT_LIST, SaveConfigPayload::star,
            SaveConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
