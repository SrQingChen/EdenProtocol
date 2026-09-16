package com.srqingchen.eden.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side mirror of the local player's raid state, updated by {@link SyncRaidStatePayload}.
 * Plain data holder (no client-only Minecraft types) so it is safe to touch from common code. Carries both
 * the erosion/timer HUD data and the per-difficulty ambient-particle tuning read by {@code PollutionParticles},
 * plus the info-game state (§11): revealed affix ids (HUD strip / fog / whisper ambience) and the
 * pathfinder card's extraction bearing target.
 */
public class ClientRaidData {
    public static boolean inRaid = false;
    public static String difficulty = "scout";
    public static float erosion = 0f;
    public static int erosionLevel = 0;
    public static int erosionMax = 100;
    public static int timeLeftSeconds = 0;

    // Ambient pollution-particle tuning for the current difficulty (route A vanilla dust).
    public static int particleColor = 0x9B5CFF;
    public static float particleScale = 1.0f;
    public static float particleChance = 0.015625f;
    public static int particleDensity = 32;
    public static float particleSpeed = 1.0f;

    // Info-game state (§11).
    public static List<String> revealedAffixes = new ArrayList<>();
    public static int hiddenAffixCount = 0;
    public static boolean hasExtract = false;
    public static int extractX = 0;
    public static int extractY = 0;
    public static int extractZ = 0;

    public static void update(SyncRaidStatePayload p) {
        inRaid = p.inRaid();
        difficulty = p.difficulty();
        erosion = p.erosion();
        erosionLevel = p.erosionLevel();
        erosionMax = p.erosionMax();
        timeLeftSeconds = p.timeLeftSeconds();
        particleColor = p.particleColor();
        particleScale = p.particleScale();
        particleChance = p.particleChance();
        particleDensity = p.particleDensity();
        particleSpeed = p.particleSpeed();
        revealedAffixes = new ArrayList<>(p.revealedAffixes());
        hiddenAffixCount = p.hiddenAffixCount();
        hasExtract = p.hasExtract();
        extractX = p.extractX();
        extractY = p.extractY();
        extractZ = p.extractZ();
    }

    /** True when the REVEALED affix set contains the given affix id (client ambience gating). */
    public static boolean hasAffix(String id) {
        return revealedAffixes.contains(id);
    }

    public static void clear() {
        inRaid = false;
        difficulty = "scout";
        erosion = 0f;
        erosionLevel = 0;
        erosionMax = 100;
        timeLeftSeconds = 0;
        particleColor = 0x9B5CFF;
        particleScale = 1.0f;
        particleChance = 0.015625f;
        particleDensity = 32;
        particleSpeed = 1.0f;
        revealedAffixes = new ArrayList<>();
        hiddenAffixCount = 0;
        hasExtract = false;
        extractX = extractY = extractZ = 0;
    }
}
