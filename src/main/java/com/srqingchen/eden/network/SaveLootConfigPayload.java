package com.srqingchen.eden.network;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: save the loot-injection config edited in the editor's loot tab. Carries the same
 * {@link LootPayloadBlock} the editor opened with. The server is authoritative - it sanitises the rules
 * (prefixes/exclusions), clamps every weight/count/chance, writes {@code LootInjectionData} and then
 * re-applies: when the content signature changed it reloads the datapacks in the background so the
 * new pools go live without a restart.
 */
public record SaveLootConfigPayload(LootPayloadBlock loot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveLootConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "save_loot_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveLootConfigPayload> STREAM_CODEC =
            StreamCodec.composite(LootPayloadBlock.STREAM_CODEC, SaveLootConfigPayload::loot, SaveLootConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
