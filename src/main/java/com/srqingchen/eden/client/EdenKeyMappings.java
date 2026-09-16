package com.srqingchen.eden.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.srqingchen.eden.client.gui.TalentScreen;
import com.srqingchen.eden.network.UseCardSkillPayload;
import com.srqingchen.eden.network.UseClassSkillPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Client key bindings. Registered through {@link RegisterKeyMappingsEvent}, so they appear in the vanilla
 * Options &gt; Controls &gt; Key Binds screen (under the GAMEPLAY category), are freely rebindable and persist to
 * {@code options.txt} exactly like vanilla keys. {@link KeyConflictContext#IN_GAME} keeps them from firing while a GUI
 * is open. Presses are polled each client tick and forwarded to the server, where the active-class skill resolves.
 */
@OnlyIn(Dist.CLIENT)
public final class EdenKeyMappings {
    private EdenKeyMappings() {}

    /**
     * Own key-bind category ("净土协议 / Eden Protocol") so both keys appear in a dedicated, easy-to-find section of
     * vanilla Options &gt; Controls instead of being buried among the many GAMEPLAY keys. Built with
     * {@code new Category(id)} - NOT the {@code @Deprecated Category.register}, which throws on a duplicate - and handed
     * to NeoForge via {@link RegisterKeyMappingsEvent#registerCategory} (which sorts + stores it). Its label resolves
     * from lang key {@code key.category.eden.keybind} (Category.label() = translatable(id.toLanguageKey("key.category"))).
     */
    public static final KeyMapping.Category EDEN_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("eden", "keybind"));

    /** Active class skill (default R) - the single shared "skill" key for class actives. */
    public static final KeyMapping USE_CLASS_SKILL = new KeyMapping(
            "eden.key.class_skill", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, EDEN_CATEGORY);

    /** Open the talent / class tree screen (default J). */
    public static final KeyMapping OPEN_TALENTS = new KeyMapping(
            "eden.key.open_talents", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, EDEN_CATEGORY);

    /**
     * Card active (default Tab, per 功能清单 §19.1). SHORT tap = the equipped curse card's one-shot;
     * HOLD (>= {@link #CARD_LONG_PRESS_TICKS} ticks) = the sacrificial burn. Tab also opens the vanilla
     * player list in multiplayer - players who mind can rebind either key in Options > Controls.
     */
    public static final KeyMapping CARD_SKILL = new KeyMapping(
            "eden.key.card_skill", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB, EDEN_CATEGORY);

    /** Held this many ticks or more = long press (sacrificial burn). */
    private static final int CARD_LONG_PRESS_TICKS = 8;

    /** Client MOD bus: register our own category first, then hand the key mappings to vanilla (Options > Controls). */
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(EDEN_CATEGORY);
        event.register(USE_CLASS_SKILL);
        event.register(OPEN_TALENTS);
        event.register(CARD_SKILL);
    }

    // Card-key hold tracking: isDown() edge detection (press -> release) with a tick counter.
    private static boolean cardKeyWasDown;
    private static int cardKeyDownTicks;

    /** Client game bus (per tick): forward skill-key presses, open the talent screen, or fire card actives. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            cardKeyWasDown = false;
            cardKeyDownTicks = 0;
            return;
        }
        while (USE_CLASS_SKILL.consumeClick()) {
            ClientPacketDistributor.sendToServer(new UseClassSkillPayload());
        }
        while (OPEN_TALENTS.consumeClick()) {
            TalentScreen.open();
        }
        boolean down = CARD_SKILL.isDown();
        if (down) {
            cardKeyDownTicks++;
        } else if (cardKeyWasDown) {
            // released this tick: short tap vs. hold decides which active fires
            if (cardKeyDownTicks >= CARD_LONG_PRESS_TICKS) {
                ClientPacketDistributor.sendToServer(new UseCardSkillPayload(true));
            } else if (cardKeyDownTicks > 0) {
                ClientPacketDistributor.sendToServer(new UseCardSkillPayload(false));
            }
            cardKeyDownTicks = 0;
        }
        cardKeyWasDown = down;
    }
}
