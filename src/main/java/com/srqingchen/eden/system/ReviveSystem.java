package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenDamageTypes;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Basic cooperative revive (MVP).
 * <ul>
 *   <li>Lethal damage in a raid with at least one upright teammate in the same dimension is
 *       absorbed: the death is cancelled, the player is left at 1 HP and marked {@code downed}
 *       (Slowness + Weakness).</li>
 *   <li>While downed they bleed ({@link #BLEED_DAMAGE} every {@link #BLEED_INTERVAL} ticks); if not
 *       revived they die for real (a second lethal event is not cancelled while already downed).</li>
 *   <li>A teammate right-clicks the downed player to revive: downed cleared, a little health
 *       restored, and BOTH players gain erosion (the taint costs something).</li>
 *   <li>A real death respawns the player in the ark (home) with raid state cleared.</li>
 * </ul>
 * A richer downed state (dedicated GUI, crawl, revive-time channel, insurance) is a v1 refinement.
 */
public class ReviveSystem {
    private static final int BLEED_INTERVAL = 40;      // ticks between bleed damage while downed
    private static final float BLEED_DAMAGE = 1.0f;
    private static final float REVIVE_EROSION_COST = 15f; // erosion added to both players on revive
    private static final float REVIVED_HEALTH = 4.0f;     // 2 hearts after being revived

    /** Absorb a lethal blow in a raid when a teammate can still revive you. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return; // a higher-priority handler (the pod's life-support field) already saved them
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid || state.downed) {
            return; // not in a raid, or already downed -> let the death happen
        }
        if (!hasUprightTeammate(player)) {
            return; // solo -> real death
        }
        event.setCanceled(true);
        state.downed = true;
        player.setHealth(1.0f);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1000000, 2), null);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1000000, 1), null);
        EdenMessages.send(player, Type.DANGER, "eden.msg.downed");
        EdenNetwork.syncTo(player);
    }

    /** Bleed while downed; drives the real death if nobody revives in time. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        if (!state.downed) {
            return;
        }
        if (player.tickCount % BLEED_INTERVAL == 0) {
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, level.damageSources().source(EdenDamageTypes.EROSION), BLEED_DAMAGE);
        }
    }

    /** A teammate right-clicks a downed player to revive them. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer downed)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer reviver)) {
            return;
        }
        if (reviver.level().isClientSide()) {
            return;
        }
        RaidState downedState = downed.getData(EdenAttachments.RAID_STATE);
        if (!downedState.downed) {
            return;
        }
        // Cooperation talents: revive_ally_bonus revives at +2 hearts; revive_speed halves BOTH erosion costs.
        boolean allyBonus = TalentSystem.hasMech(reviver, "uni_coop_revive");
        boolean quickHands = TalentSystem.hasMech(reviver, "uni_coop_medic");
        // Medic linkage (§6 医疗职业联动): a medic with the field-medic keystone revives from a short
        // distance's worth of extra blood (+4 HP), halves the erosion cost AGAIN and leaves a regen field.
        boolean fieldMedic = TalentSystem.hasMech(reviver, "med_field");
        downedState.downed = false;
        downed.setHealth(REVIVED_HEALTH + (allyBonus ? 4.0f : 0.0f) + (fieldMedic ? 4.0f : 0.0f));
        downed.removeEffect(MobEffects.SLOWNESS);
        downed.removeEffect(MobEffects.WEAKNESS);
        if (fieldMedic) {
            downed.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), null);
        }

        float cost = REVIVE_EROSION_COST;
        if (quickHands) cost *= 0.5f;
        if (fieldMedic) cost *= 0.5f;
        downedState.erosion += cost;
        RaidState reviverState = reviver.getData(EdenAttachments.RAID_STATE);
        reviverState.erosion += cost;

        EdenNetwork.syncTo(downed);
        EdenNetwork.syncTo(reviver);
        EdenMessages.send(downed, Type.SUCCESS, "eden.msg.revived_by", reviver.getName());
        EdenMessages.send(reviver, Type.SUCCESS, "eden.msg.you_revived", downed.getName());
        event.setCanceled(true);
    }

    /** Real death: respawn at the ark (home base) with raid state cleared. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getData(EdenAttachments.RAID_STATE).reset();
        DimensionManager.enterArk(player);
        FailureRetention.onRespawn(player);
        EdenNetwork.syncTo(player);
    }

    /** True if another upright (in-raid, not downed, alive) player shares this dimension. */
    private static boolean hasUprightTeammate(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        for (Player p : level.players()) {
            if (!(p instanceof ServerPlayer sp) || sp == player || sp.isDeadOrDying()) {
                continue;
            }
            RaidState s = sp.getData(EdenAttachments.RAID_STATE);
            if (s.inRaid && !s.downed) {
                return true;
            }
        }
        return false;
    }
}
