package com.srqingchen.eden.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Companion-AI styled messages. Everything the mod says to a player is voiced by the "Eden Protocol"
 * intelligence: a fixed bold light-cyan {@code [净土协议]} agent tag, then the body tinted by semantic
 * type (success / danger / warning / info / special). Centralising this keeps every chat line, action-bar
 * line and command reply consistent, and replaces scattered raw {@code sendSystemMessage} calls.
 */
public final class EdenMessages {
    private EdenMessages() {}

    /** Semantic message type -> body colour. */
    public enum Type {
        SUCCESS(ChatFormatting.GREEN),
        DANGER(ChatFormatting.RED),
        WARNING(ChatFormatting.GOLD),
        INFO(ChatFormatting.GRAY),
        SPECIAL(ChatFormatting.LIGHT_PURPLE);

        final ChatFormatting color;
        Type(ChatFormatting color) { this.color = color; }
    }

    /** The fixed agent prefix: bold light-cyan "[净土协议] ". */
    private static MutableComponent prefix() {
        return Component.literal("[净土协议] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    /** Build a styled message component: agent prefix + type-coloured translatable body. */
    public static MutableComponent styled(Type type, String key, Object... args) {
        return prefix().append(Component.translatable(key, args).withStyle(type.color));
    }

    /** Send a styled chat message. */
    public static void send(Player player, Type type, String key, Object... args) {
        player.sendSystemMessage(styled(type, key, args));
    }

    /** Send a styled action-bar (overlay) message. */
    public static void overlay(ServerPlayer player, Type type, String key, Object... args) {
        player.sendOverlayMessage(styled(type, key, args));
    }
}
