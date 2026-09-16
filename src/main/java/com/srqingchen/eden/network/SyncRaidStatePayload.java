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
 * Server -> client sync of the local player's raid state plus the per-difficulty ambient-particle tuning.
 * Consumed by the erosion HUD, the difficulty-prefix tooltip handler and the pollution particles (all read
 * {@link ClientRaidData}).
 * <p>Also carries the info-game state (§11): the affixes revealed so far (progressive reveal - the rest stay
 * hidden until a scanner sweep / time / the insight card peels them), and - when the pathfinder card is
 * equipped - the bearing target (nearest standing extraction point) for the HUD pointer.
 * <p>The field count exceeds {@code StreamCodec.composite}'s 16-argument ceiling, so the codec is written
 * by hand: one ordered read/write pair, no nesting.
 */
public record SyncRaidStatePayload(boolean inRaid, String difficulty, float erosion, int erosionLevel, int erosionMax, int timeLeftSeconds,
                                   int particleColor, float particleScale, float particleChance, int particleDensity, float particleSpeed,
                                   List<String> revealedAffixes, int hiddenAffixCount, boolean hasExtract, int extractX, int extractY, int extractZ)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncRaidStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "sync_raid_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRaidStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncRaidStatePayload decode(RegistryFriendlyByteBuf buf) {
                    return read(buf);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SyncRaidStatePayload p) {
                    write(buf, p);
                }
            };

    private static SyncRaidStatePayload read(RegistryFriendlyByteBuf buf) {
        return new SyncRaidStatePayload(
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    private static void write(RegistryFriendlyByteBuf buf, SyncRaidStatePayload p) {
        buf.writeBoolean(p.inRaid);
        ByteBufCodecs.STRING_UTF8.encode(buf, p.difficulty);
        buf.writeFloat(p.erosion);
        buf.writeVarInt(p.erosionLevel);
        buf.writeVarInt(p.erosionMax);
        buf.writeVarInt(p.timeLeftSeconds);
        buf.writeInt(p.particleColor);
        buf.writeFloat(p.particleScale);
        buf.writeFloat(p.particleChance);
        buf.writeVarInt(p.particleDensity);
        buf.writeFloat(p.particleSpeed);
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
                .encode(buf, new ArrayList<>(p.revealedAffixes));
        buf.writeVarInt(p.hiddenAffixCount);
        buf.writeBoolean(p.hasExtract);
        buf.writeVarInt(p.extractX);
        buf.writeVarInt(p.extractY);
        buf.writeVarInt(p.extractZ);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
