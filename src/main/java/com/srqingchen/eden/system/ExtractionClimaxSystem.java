package com.srqingchen.eden.system;

import com.srqingchen.eden.block.ReturnPodBlockEntity;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The "extraction climax" defensive layer around a charging return pod (design doc §4). Server-authoritative;
 * registered via addListener method refs in {@code EdenProtocol}.
 * <ul>
 *   <li><b>Purification shield</b> ({@link LivingIncomingDamageEvent}): a player within {@code SHIELD_RADIUS}
 *       of an active pod has incoming damage bought off by the CHARGE pool at {@code damage * t} per hit; when
 *       the pool runs dry the shield fails and damage lands normally. This is the core tension: turtling inside
 *       is safe but drains the launch charge, while stepping out to clear the taint swarm preserves it but is
 *       dangerous.</li>
 *   <li><b>Life-support field</b> ({@link LivingDeathEvent}, HIGHEST priority, runs before ReviveSystem): each
 *       registered crew member's FIRST lethal blow of the raid is negated anywhere in the world (totem-like) at
 *       a heavy charge cost, leaving them at 1 HP with brief invulnerability. If it is unavailable (already
 *       used, or the pool is too low) the death falls through to the normal downed/revive flow.</li>
 * </ul>
 */
public final class ExtractionClimaxSystem {
    private ExtractionClimaxSystem() {}

    /**
     * Purification shield: a player near an active pod has incoming damage bought off by the CHARGE pool at a
     * {@code damage * t} cost ({@code t} = SHIELD_RESIST_RATIO). If the pool cannot cover the whole blow it is
     * spent to empty and only the un-absorbed remainder lands - with its ORIGINAL damage type/source intact (we
     * only shrink the amount, never touch the source), so armor-piercing / magic / etc. keep behaving correctly.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ReturnPodBlockEntity pod = ReturnPodBlockEntity.findChargingPodNear(
                player.level(), player.position(), ReturnPodBlockEntity.SHIELD_RADIUS);
        if (pod == null) {
            return;   // no active pod nearby -> no shield
        }
        float charge = pod.getChargeProgress();
        if (charge <= 0.0f) {
            return;   // pool empty -> shield offline, the blow lands normally
        }
        float t = ReturnPodBlockEntity.SHIELD_RESIST_RATIO;
        float damage = event.getAmount();
        float cost = Math.min(damage * t, charge);   // charge spent = min(damage*t, whatever is left)
        float remaining = damage - cost / t;         // the damage that charge could not buy off
        pod.consumeCharge(cost);
        if (cost > 0.0f) {
            shieldFeedback(player);   // make the absorb visible + audible - it was silently eating damage before
        }
        if (remaining <= 0.0f) {
            event.setCanceled(true);   // fully absorbed
        } else {
            event.setAmount(remaining); // partially absorbed; the rest lands with its original type
        }
    }

    /** Purification-light burst + block chime at the player, so an absorbed hit is seen and heard (and felt as charge cost). */
    private static void shieldFeedback(ServerPlayer player) {
        ServerLevel sl = player.level();
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 14, 0.45, 0.6, 0.45, 0.02);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.5, 0.6, 0.5, 0.15);
        sl.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    /** Life-support field: negate a registered crew member's first lethal blow of the raid, at a heavy charge cost. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;   // something higher-priority already handled it
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ReturnPodBlockEntity pod = ReturnPodBlockEntity.findPodFor(player.getUUID());
        if (pod == null || !pod.tryLifeLine(player.getUUID())) {
            return;   // not registered / already used / pool too low -> fall through to downed & revive
        }
        event.setCanceled(true);
        player.setHealth(1.0f);
        player.invulnerableTime = 20;   // brief grace so the same blow or a follow-up doesn't re-kill instantly
        // Totem-style burst + sound: the once-per-raid save must be unmistakable.
        ServerLevel sl = player.level();
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 60, 0.6, 0.9, 0.6, 0.4);
        sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 30, 0.5, 0.8, 0.5, 0.05);
        sl.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        EdenMessages.send(player, Type.WARNING, "eden.msg.lifeline_saved");
    }
}
