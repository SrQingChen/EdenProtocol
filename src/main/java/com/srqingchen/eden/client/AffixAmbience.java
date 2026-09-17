package com.srqingchen.eden.client;

import com.srqingchen.eden.network.ClientRaidData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Whisper affix ambience (§10/§16 client side): once the WHISPER affix has been revealed, the world
 * occasionally murmurs - a quiet, mis-positioned ambient sound (cave drone, distant vex, a portal
 * breath) somewhere AROUND the player but never quite where anything is. Pure psychological pressure:
 * no server state, no mechanical damage, just doubt. Only fires in-raid, every 25-55s.
 */
public final class AffixAmbience {
    private AffixAmbience() {}

    /** Sound palette: unsettling, not instantly identifiable as "danger". */
    private static final SoundEvent[] WHISPERS = {
            SoundEvents.AMBIENT_CAVE.value(),
            SoundEvents.VEX_AMBIENT,
            SoundEvents.PORTAL_AMBIENT,
            SoundEvents.SILVERFISH_AMBIENT,
            SoundEvents.ELDER_GUARDIAN_AMBIENT
    };

    /** 深处低语 (S1 cave affix): fake footsteps on cave stone - "something is walking, or isn't". */
    private static final SoundEvent[] DEEP_STEPS = {
            SoundEvents.STONE_STEP,
            SoundEvents.DEEPSLATE_STEP,
            SoundEvents.GRAVEL_STEP
    };

    private static long nextWhisperTick = -1L;
    private static long nextStepTick = -1L;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            return;
        }
        if (!ClientRaidData.inRaid) {
            nextWhisperTick = -1;
            nextStepTick = -1;
            return;
        }
        long now = level.getGameTime();
        RandomSource rand = level.getRandom();
        // 深处低语: underground-only fake footsteps - a few steps that approach, then nothing.
        if (ClientRaidData.hasAffix("deep_whisper") && player.getY() < 0) {
            if (nextStepTick < 0) {
                nextStepTick = now + 200 + rand.nextInt(400);
            } else if (now >= nextStepTick) {
                nextStepTick = now + 600 + rand.nextInt(800);   // a burst every 30-70s
                double angle = rand.nextDouble() * Math.PI * 2.0;
                double dist = 7.0 + rand.nextDouble() * 7.0;
                double x = player.getX() + Math.cos(angle) * dist;
                double z = player.getZ() + Math.sin(angle) * dist;
                // 3-5 quick steps closing in, then silence.
                int steps = 3 + rand.nextInt(3);
                for (int i = 0; i < steps; i++) {
                    double t = i / (double) steps;
                    level.playLocalSound(
                            x + (player.getX() - x) * t * 0.4, player.getY(), z + (player.getZ() - z) * t * 0.4,
                            DEEP_STEPS[rand.nextInt(DEEP_STEPS.length)], SoundSource.NEUTRAL,
                            0.5f, 0.75f + rand.nextFloat() * 0.2f, false);
                }
                if (rand.nextInt(4) == 0) {
                    level.playLocalSound(x, player.getY(), z, SoundEvents.AMBIENT_CAVE.value(),
                            SoundSource.AMBIENT, 0.4f, 0.5f, false);
                }
            }
        } else {
            nextStepTick = -1;
        }
        if (!ClientRaidData.hasAffix("whisper")) {
            nextWhisperTick = -1;
            return;
        }
        if (nextWhisperTick < 0) {
            nextWhisperTick = now + 400 + rand.nextInt(700);   // first murmur 20-55s in
        }
        if (now < nextWhisperTick) {
            return;
        }
        nextWhisperTick = now + 500 + rand.nextInt(600);       // then every 25-55s
        SoundEvent sound = WHISPERS[rand.nextInt(WHISPERS.length)];
        // Place the source 10-18 blocks out at a random bearing - close enough to notice, off enough to doubt.
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double dist = 10.0 + rand.nextDouble() * 8.0;
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        double y = player.getY() + Mth.clamp(rand.nextGaussian() * 2.0, -6.0, 8.0);
        level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, 0.35f,
                0.6f + rand.nextFloat() * 0.3f, false);
    }
}
