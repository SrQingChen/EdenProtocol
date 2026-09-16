package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: the player pressed the card-active key (default Tab). {@code longPress} distinguishes the
 * short tap (one-shot burst, per-card cooldown) from the hold (sacrificial burn: the card is destroyed and a
 * ramping-cost state begins; see {@code CurseCardSystem}).
 */
public record UseCardSkillPayload(boolean longPress) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseCardSkillPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "use_card_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseCardSkillPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UseCardSkillPayload::longPress,
            UseCardSkillPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
