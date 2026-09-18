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
 * <p>Season block (S1 批A 推进按钮): season index, contracts done count / total, the >=3/5 advance
 * gate and the live server-wide advance vote (yes-count and the majority threshold).
 */
public record ChroniclePayload(float pollution, int stage, boolean paradiseUnlocked,
                               int supplyPoints, int totalRaids, int successfulExtracts,
                               int coresDestroyed, int dragonsSlain,
                               List<String> highlightKeys, List<String> highlightPlayers, List<Integer> highlightValues,
                               int seasonIndex, String seasonTitleKey, int contractsDone, int contractsTotal,
                               boolean advanceReady, boolean voteActive, int voteYes, int voteNeed,
                               boolean finaleUnlocked, boolean seasonCompleted, List<String> endingsSeen,
                               float meterPurity, float meterSymbiosis, float meterArchive,
                               int pagesPurity, int pagesSymbiosis, int pagesArchive)
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
                    int seasonIndex = buf.readVarInt();
                    String seasonTitleKey = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int contractsDone = buf.readVarInt();
                    int contractsTotal = buf.readVarInt();
                    boolean advanceReady = buf.readBoolean();
                    boolean voteActive = buf.readBoolean();
                    int voteYes = buf.readVarInt();
                    int voteNeed = buf.readVarInt();
                    boolean finaleUnlocked = buf.readBoolean();
                    boolean seasonCompleted = buf.readBoolean();
                    List<String> endings = readStrings(buf);
                    float mPurity = buf.readFloat();
                    float mSymbiosis = buf.readFloat();
                    float mArchive = buf.readFloat();
                    int pPurity = buf.readVarInt();
                    int pSymbiosis = buf.readVarInt();
                    int pArchive = buf.readVarInt();
                    return new ChroniclePayload(pollution, stage, paradise, supply, raids, extracts,
                            cores, dragons, keys, players, values,
                            seasonIndex, seasonTitleKey, contractsDone, contractsTotal, advanceReady, voteActive, voteYes, voteNeed,
                            finaleUnlocked, seasonCompleted, endings,
                            mPurity, mSymbiosis, mArchive, pPurity, pSymbiosis, pArchive);
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
                    buf.writeVarInt(p.seasonIndex);
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.seasonTitleKey);
                    buf.writeVarInt(p.contractsDone);
                    buf.writeVarInt(p.contractsTotal);
                    buf.writeBoolean(p.advanceReady);
                    buf.writeBoolean(p.voteActive);
                    buf.writeVarInt(p.voteYes);
                    buf.writeVarInt(p.voteNeed);
                    buf.writeBoolean(p.finaleUnlocked);
                    buf.writeBoolean(p.seasonCompleted);
                    writeStrings(buf, p.endingsSeen);
                    buf.writeFloat(p.meterPurity);
                    buf.writeFloat(p.meterSymbiosis);
                    buf.writeFloat(p.meterArchive);
                    buf.writeVarInt(p.pagesPurity);
                    buf.writeVarInt(p.pagesSymbiosis);
                    buf.writeVarInt(p.pagesArchive);
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
