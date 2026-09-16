package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: the player pressed the active-skill key ({@code EdenKeyMappings.USE_CLASS_SKILL}). Carries no
 * data - the server resolves which skill to fire from the player's active class. Skill effects land in P3; for now the
 * server acknowledges so the whole key -&gt; packet -&gt; server path is verifiable in-game.
 */
public record UseClassSkillPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UseClassSkillPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "use_class_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseClassSkillPayload> STREAM_CODEC =
            StreamCodec.unit(new UseClassSkillPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
