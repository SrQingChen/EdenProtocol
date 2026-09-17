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
 * Server -> client: open the launch pad screen. Carries the campaign snapshot (stage, pollution,
 * paradise flag) plus the difficulty / destination rows with their unlock states and the player's
 * current class, so the client screen renders one authoritative picture with no server round-trips.
 * <p>Season block (S1 批A 委托行): the S1 contract ids with their done flags, the weekly-focus index
 * (payout x1.5) and the contract the player currently carries ("" = none).
 */
public record OpenLaunchPadPayload(int stage, float pollution, boolean paradiseUnlocked,
                                   List<Boolean> difficultyUnlocked, List<Boolean> dimensionUnlocked,
                                   String currentClass,
                                   List<String> contractIds, List<Boolean> contractDone,
                                   int weeklyFocus, String currentContract)
        implements CustomPacketPayload {

    /** Difficulty order mirrors the screen rows: scout / salvage / purge / abyss / endgame. */
    public static final List<String> DIFFICULTIES = List.of("scout", "salvage", "purge", "abyss", "endgame");
    /** Destination order mirrors the screen rows: overworld / nether / end. */
    public static final List<String> DIMENSIONS = List.of("overworld", "nether", "end");

    public static final CustomPacketPayload.Type<OpenLaunchPadPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "open_launch_pad"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLaunchPadPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenLaunchPadPayload decode(RegistryFriendlyByteBuf buf) {
                    int stage = buf.readVarInt();
                    float pollution = buf.readFloat();
                    boolean paradise = buf.readBoolean();
                    List<Boolean> diffs = readBools(buf);
                    List<Boolean> dims = readBools(buf);
                    String cls = ByteBufCodecs.STRING_UTF8.decode(buf);
                    List<String> contractIds = readStrings(buf);
                    List<Boolean> contractDone = readBools(buf);
                    int weeklyFocus = buf.readVarInt();
                    String currentContract = ByteBufCodecs.STRING_UTF8.decode(buf);
                    return new OpenLaunchPadPayload(stage, pollution, paradise, diffs, dims, cls,
                            contractIds, contractDone, weeklyFocus, currentContract);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, OpenLaunchPadPayload p) {
                    buf.writeVarInt(p.stage);
                    buf.writeFloat(p.pollution);
                    buf.writeBoolean(p.paradiseUnlocked);
                    writeBools(buf, p.difficultyUnlocked);
                    writeBools(buf, p.dimensionUnlocked);
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.currentClass);
                    writeStrings(buf, p.contractIds);
                    writeBools(buf, p.contractDone);
                    buf.writeVarInt(p.weeklyFocus);
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.currentContract);
                }
            };

    private static List<Boolean> readBools(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Boolean> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readBoolean());
        }
        return out;
    }

    private static void writeBools(RegistryFriendlyByteBuf buf, List<Boolean> list) {
        buf.writeVarInt(list.size());
        for (boolean b : list) {
            buf.writeBoolean(b);
        }
    }

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
