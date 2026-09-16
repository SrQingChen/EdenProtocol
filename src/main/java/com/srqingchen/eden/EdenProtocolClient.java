package com.srqingchen.eden;

import com.srqingchen.eden.client.EdenHudLayer;
import com.srqingchen.eden.client.EdenKeyMappings;
import com.srqingchen.eden.client.EdenOverlayLayer;
import com.srqingchen.eden.client.PollutionFog;
import com.srqingchen.eden.client.PollutionParticles;
import com.srqingchen.eden.client.TooltipPrefixHandler;
import com.srqingchen.eden.client.gui.DifficultyEditorScreen;
import com.srqingchen.eden.client.gui.ShopScreen;
import com.srqingchen.eden.network.EditorDataPayload;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.network.OpenShopPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point. Not loaded on dedicated servers, so client code here is safe.
 * <p>Wires the in-raid HUD layer (client MOD bus via {@link RegisterGuiLayersEvent}) and the
 * difficulty-prefix tooltip handler (client game bus via {@link ItemTooltipEvent}).
 */
@Mod(value = EdenProtocol.MODID, dist = Dist.CLIENT)
public class EdenProtocolClient {
    public EdenProtocolClient(IEventBus modEventBus, ModContainer container) {
        // Provides a config screen via Mods menu > Eden Protocol > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Install the client GUI hooks the common network layer calls into. EdenNetwork must stay free of
        // client-only class references (dedicated-server safety), so the screens open through this bridge.
        EdenNetwork.clientHooks = new EdenNetwork.ClientHooks() {
            @Override
            public void openShop(OpenShopPayload payload) {
                ShopScreen.open(payload.balance(), payload.fluctuation(), payload.shortageId());
            }

            @Override
            public void openEditor(EditorDataPayload payload) {
                Minecraft.getInstance().setScreen(new DifficultyEditorScreen(payload.profiles(),
                        payload.selected(), payload.charge(), payload.particle(), payload.density(),
                        payload.quality(), payload.star()));
            }

            @Override
            public void openLaunchPad(com.srqingchen.eden.network.OpenLaunchPadPayload payload) {
                Minecraft.getInstance().setScreen(new com.srqingchen.eden.client.gui.LaunchPadScreen(payload));
            }

            @Override
            public void openChronicle(com.srqingchen.eden.network.ChroniclePayload payload) {
                Minecraft.getInstance().setScreen(new com.srqingchen.eden.client.gui.ChronicleScreen(payload));
            }
        };

        // Client MOD bus: register the HUD layer.
        modEventBus.addListener(EdenProtocolClient::registerGuiLayers);

        // Client MOD bus: custom particle providers (§16 sprites).
        modEventBus.addListener(EdenProtocolClient::registerParticles);

        // Client MOD bus: register key mappings so they appear in vanilla Options > Controls (rebindable, persisted).
        modEventBus.addListener(EdenKeyMappings::register);

        // Client game bus: difficulty-prefix item tooltips.
        NeoForge.EVENT_BUS.addListener(TooltipPrefixHandler::onTooltip);

        // Client game bus: sparse ambient pollution particles while inside a raid.
        NeoForge.EVENT_BUS.addListener(PollutionParticles::onClientTick);

        // Client game bus: light pollution fog (pull the planes in + tint) while inside a raid.
        NeoForge.EVENT_BUS.addListener(PollutionFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(PollutionFog::onComputeFogColor);

        // Client game bus: whisper-affix ambience (mis-positioned murmurs once revealed).
        NeoForge.EVENT_BUS.addListener(com.srqingchen.eden.client.AffixAmbience::onClientTick);

        // Client game bus: poll the class-skill key and forward presses to the server.
        NeoForge.EVENT_BUS.addListener(EdenKeyMappings::onClientTick);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(EdenHudLayer.ID, new EdenHudLayer());
        // Screen overlays (erosion vignette / low-health pulse) draw ABOVE everything.
        event.registerAboveAll(EdenOverlayLayer.ID, new EdenOverlayLayer());
    }

    private static void registerParticles(
            net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.srqingchen.eden.registry.EdenParticles.POLLUTION_SPORE.get(),
                com.srqingchen.eden.client.EdenCustomParticle::spore);
        event.registerSpriteSet(com.srqingchen.eden.registry.EdenParticles.TAINT_MIST.get(),
                com.srqingchen.eden.client.EdenCustomParticle::mist);
        event.registerSpriteSet(com.srqingchen.eden.registry.EdenParticles.ENERGY_ARC.get(),
                com.srqingchen.eden.client.EdenCustomParticle::arc);
        event.registerSpriteSet(com.srqingchen.eden.registry.EdenParticles.HOPE_GLOW.get(),
                com.srqingchen.eden.client.EdenCustomParticle::hope);
    }
}
