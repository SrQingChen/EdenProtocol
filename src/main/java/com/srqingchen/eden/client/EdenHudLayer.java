package com.srqingchen.eden.client;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * In-raid HUD: a code-drawn (no textures) panel showing difficulty, the collapse countdown, and the
 * erosion gauge + level. Registered as a {@link GuiLayer} on the client mod bus. Reads the synced
 * {@link ClientRaidData}; renders nothing when the player is not in a raid.
 * <p>Also renders the info-game layer (§11): the revealed-affix strip (top-right; hidden affixes count as
 * "???×n") and, when the pathfinder card is equipped, the extraction bearing + distance pointer.
 * <p>Drawn with {@link GuiGraphicsExtractor} (26.1.2's replacement for GuiGraphics): {@code fill}
 * takes corner coords (x0,y0,x1,y1), {@code outline} takes (x,y,w,h). Fixed pixel layout (no GUI
 * scaling) - matches the rest of the mod's UI approach for MVP.
 */
public class EdenHudLayer implements GuiLayer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "raid_hud");

    private static final int BAR_W = 120;
    private static final int BAR_H = 10;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (!ClientRaidData.inRaid) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        // §16 GUI 缩放: the whole HUD renders in a virtual coordinate space of (w/s, h/s) under one
        // pose scale, so every element (bars, text, strip) grows or shrinks together.
        float scale = (float) com.srqingchen.eden.EdenClientConfig.HUD_SCALE.get().doubleValue();
        float marginX = com.srqingchen.eden.EdenClientConfig.HUD_MARGIN_X.get();
        float marginY = com.srqingchen.eden.EdenClientConfig.HUD_MARGIN_Y.get();
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        float vw = mc.getWindow().getGuiScaledWidth() / scale;
        float vh = mc.getWindow().getGuiScaledHeight() / scale;
        try {
            renderHud(g, mc, vw, vh, marginX, marginY);
        } finally {
            pose.popMatrix();
        }
    }

    private void renderHud(GuiGraphicsExtractor g, Minecraft mc, float width, float height,
                           float marginX, float marginY) {
        float x = marginX;
        float y = marginY;

        g.text(mc.font, Component.translatable("eden.hud.difficulty", ClientRaidData.difficulty), (int) x, (int) y, 0xFFAAAAAA);

        int t = Math.max(0, ClientRaidData.timeLeftSeconds);
        String time = String.format("%02d:%02d", t / 60, t % 60);
        int timeColor = t <= 300 ? 0xFFFF5555 : 0xFFFFFFFF;
        g.text(mc.font, Component.translatable("eden.hud.collapse", time), (int) x, (int) (y + 12), timeColor);

        int barX = (int) x;
        int barY = (int) (y + 26);
        float ratio = ClientRaidData.erosionMax > 0
                ? Math.min(1f, ClientRaidData.erosion / ClientRaidData.erosionMax)
                : 0f;
        g.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xAA000000);
        int fillW = (int) ((BAR_W - 2) * ratio);
        if (fillW > 0) {
            g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + BAR_H - 1, erosionColor(ClientRaidData.erosionLevel));
        }
        g.outline(barX, barY, BAR_W, BAR_H, 0xFF666666);
        g.text(mc.font, Component.translatable("eden.hud.erosion",
                        String.format("%.0f", ClientRaidData.erosion), ClientRaidData.erosionMax, ClientRaidData.erosionLevel),
                barX, barY + BAR_H + 3, 0xFFDD88FF);

        renderAffixStrip(g, mc, width, marginX, marginY);
        renderExtractPointer(g, mc, marginX, marginY, y);
    }

    /**
     * Affix strip (§11 HUD 词缀条), top-right: each REVEALED affix by name; a trailing "???×n" counter
     * marks how many modifiers are still encrypted. Dense fog / whisper only bite once revealed - the
     * strip is the crew's running intel picture.
     */
    private void renderAffixStrip(GuiGraphicsExtractor g, Minecraft mc, float width, float marginX, float marginY) {
        if (ClientRaidData.revealedAffixes.isEmpty()) {
            return;
        }
        int y = (int) marginY;
        g.text(mc.font, Component.translatable("eden.hud.affixes"), (int) (width - marginX - mc.font.width(
                Component.translatable("eden.hud.affixes"))), y, 0xFFAA66FF);
        y += 12;
        for (String id : ClientRaidData.revealedAffixes) {
            Component name = Component.translatable("eden.affix." + id + ".name");
            g.text(mc.font, name, (int) (width - marginX - mc.font.width(name)), y, 0xFFFF7F7F);
            y += 11;
        }
        // Hidden remainder: the raid still has tricks up its sleeve.
        int hidden = ClientRaidData.hiddenAffixCount;
        if (hidden > 0) {
            Component q = Component.translatable("eden.hud.affixes_hidden", hidden);
            g.text(mc.font, q, (int) (width - marginX - mc.font.width(q)), y, 0xFF777777);
        }
    }

    /**
     * Pathfinder card pointer (§8 撤离卡): bearing + live distance to the nearest standing
     * extraction point, pinned under the erosion gauge so it reads as part of the raid HUD.
     */
    private void renderExtractPointer(GuiGraphicsExtractor g, Minecraft mc, float marginX, float marginY, float hudY) {
        if (!ClientRaidData.hasExtract || mc.player == null) {
            return;
        }
        double dx = ClientRaidData.extractX + 0.5 - mc.player.getX();
        double dz = ClientRaidData.extractZ + 0.5 - mc.player.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        // Compass letter from the player's perspective (MC -Z = north).
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        int sector = (int) Math.floor(((angle + 270.0) % 360.0) / 45.0);
        String dirKey = switch (sector) {
            case 0 -> "eden.dir.north";
            case 1 -> "eden.dir.northeast";
            case 2 -> "eden.dir.east";
            case 3 -> "eden.dir.southeast";
            case 4 -> "eden.dir.south";
            case 5 -> "eden.dir.southwest";
            case 6 -> "eden.dir.west";
            default -> "eden.dir.northwest";
        };
        Component line = Component.translatable("eden.hud.extract_pointer",
                Component.translatable(dirKey), dist);
        g.text(mc.font, line, (int) marginX, (int) (hudY + 26 + 10 + 14), 0xFF7FDFFF);
    }

    /** Erosion gauge colour ramps with the erosion level (calm green -> deep taint purple). */
    private static int erosionColor(int level) {
        if (level <= 0) {
            return 0xFF3B5323;
        } else if (level <= 2) {
            return 0xFF7A5C9E;
        } else if (level <= 4) {
            return 0xFF9B30FF;
        } else if (level <= 6) {
            return 0xFFB026FF;
        }
        return 0xFF6A0DAD;
    }
}
