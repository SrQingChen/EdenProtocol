package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the chronicle wall snapshot (§13/§14) - campaign gauge, stage, tallies, supply
 * pool and the highlight reel. Highlight entries travel as parallel string/int lists (key, hero,
 * value) so the client renders them with {@code Component.translatable(key, player, value)}.
 */
public record ChroniclePayload(float pollution, int stage, boolean paradiseUnlocked,
                               int supplyPoints, int totalRaids, int successfulExtracts,
                               int coresDestroyed, int dragonsSlain,
                               List<String> highlightKeys, List<String> highlightPlayers, List<Integer> highlightValues)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChroniclePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "chronicle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChroniclePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ChroniclePayload decode(RegistryFriendlyByteBuf buf) {
                    float pollution = buf.readFloat();
                    int stage = buf.readVarInt();
                    boolean paradise = buf.readBoolean();
                    int supply = buf.readVarInt();
                    int raids = buf.readVarInt();
                    int extracts = buf.readVarInt();
                    int cores = buf.readVarInt();
                    int dragons = buf.readVarInt();
                    List<String> keys = readStrings(buf);
                    List<String> players = readStrings(buf);
                    List<Integer> values = new ArrayList<>();
                    int n = buf.readVarInt();
                    for (int i = 0; i < n; i++) {
                        values.add(buf.readVarInt());
                    }
                    return new ChroniclePayload(pollution, stage, paradise, supply, raids, extracts,
                            cores, dragons, keys, players, values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ChroniclePayload p) {
                    buf.writeFloat(p.pollution);
                    buf.writeVarInt(p.stage);
                    buf.writeBoolean(p.paradiseUnlocked);
                    buf.writeVarInt(p.supplyPoints);
                    buf.writeVarInt(p.totalRaids);
                    buf.writeVarInt(p.successfulExtracts);
                    buf.writeVarInt(p.coresDestroyed);
                    buf.writeVarInt(p.dragonsSlain);
                    writeStrings(buf, p.highlightKeys);
                    writeStrings(buf, p.highlightPlayers);
                    buf.writeVarInt(p.highlightValues.size());
                    for (int v : p.highlightValues) {
                        buf.writeVarInt(v);
                    }
                }
            };

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        return out;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) {
            ByteBufCodecs.STRING_UTF8.encode(buf, s);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
