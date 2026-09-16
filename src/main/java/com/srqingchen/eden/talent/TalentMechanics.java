package com.srqingchen.eden.talent;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenEffects;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The remaining talent MECHANICS (功能清单 §19.4 "其余 mech 接入") that live outside the systems they
 * affect - each keyed off {@link TalentSystem#hasMech}:
 * <ul>
 *   <li><b>med_field 战地医疗</b> - every 4s heal 0.5 hearts and every 10s purge 2 erosion for nearby mates.</li>
 *   <li><b>uni_res_pollution 污染抗性</b> - eden:pollution effect duration is kept short.</li>
 *   <li><b>uni_mob_jump / pro_jump 弹跳</b> - a jump-strength transient modifier while lit.</li>
 *   <li><b>van_slayer 屠戮</b> - kills stack +1 attack (max 3) for 10s each.</li>
 *   <li><b>uni_gath_salvage / scv_loot 快手</b> - +20% block-break speed.</li>
 *   <li><b>uni_mob_fall 轻落</b> - fall damage x0.7.</li>
 *   <li><b>uni_surv_totem / van_totem 不死图腾</b> - once per raid, a lethal blow is negated.</li>
 * </ul>
 */
public final class TalentMechanics {
    private TalentMechanics() {}

    private static final Identifier JUMP_MOD_ID = Identifier.fromNamespaceAndPath("eden", "mech_jump");
    private static final Identifier SLAYER_MOD_ID = Identifier.fromNamespaceAndPath("eden", "mech_slayer");

    private static final double FIELD_MEND_RANGE = 8.0;
    private static final float FIELD_MEND_AMOUNT = 1.0f;
    private static final float FIELD_PURGE = 2.0f;

    /** Slayer stacks: player -> (stacks, expiry game time). */
    private static final Map<UUID, int[]> SLAYER = new HashMap<>();   // {stacks, expireTick}
    private static final int SLAYER_WINDOW = 200;                      // 10s per stack window
    private static final int SLAYER_MAX = 3;

    // ---------- ticks ----------

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        jumpModifier(sp);
        fieldMedic(sp);
        pollutionResistance(sp);
        slayerExpire(sp);
        oreVision(sp);
    }

    /** pro_vision (矿脉视觉): every 8s a faint END_ROD glint on the nearest ores - the active's quiet sibling. */
    private static void oreVision(ServerPlayer sp) {
        if (!TalentSystem.hasMech(sp, "pro_vision") || sp.tickCount % 160 != 20) {
            return;
        }
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos c = sp.blockPosition();
        int radius = 10;
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -radius, -radius), c.offset(radius, radius, radius))) {
            if (found >= 8) {
                break;
            }
            if (ClassSkillSystem.isOreLike(level, p)) {
                level.sendParticles(ParticleTypes.END_ROD,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                found++;
            }
        }
    }

    /** uni_mob_jump / pro_jump: a small jump-strength modifier while the node is lit. */
    private static void jumpModifier(ServerPlayer sp) {
        boolean jump = TalentSystem.hasMech(sp, "uni_mob_jump") || TalentSystem.hasMech(sp, "pro_jump");
        AttributeInstance inst = sp.getAttribute(Attributes.JUMP_STRENGTH);
        if (inst == null) {
            return;
        }
        boolean has = inst.hasModifier(JUMP_MOD_ID);
        if (jump && !has) {
            inst.addTransientModifier(new AttributeModifier(JUMP_MOD_ID, 0.15,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!jump && has) {
            inst.removeModifier(JUMP_MOD_ID);
        }
    }

    /** med_field: periodic heal + erosion purge for everyone within 8 blocks (yourself included). */
    private static void fieldMedic(ServerPlayer sp) {
        if (!TalentSystem.hasMech(sp, "med_field")) {
            return;
        }
        if (sp.tickCount % 80 == 0) {
            for (Player p : sp.level().getEntitiesOfClass(Player.class,
                    new AABB(sp.blockPosition()).inflate(FIELD_MEND_RANGE), Player::isAlive)) {
                if (p instanceof ServerPlayer mate && mate.getHealth() < mate.getMaxHealth()) {
                    mate.heal(FIELD_MEND_AMOUNT);
                }
            }
        }
        if (sp.tickCount % 200 == 0) {
            for (Player p : sp.level().getEntitiesOfClass(Player.class,
                    new AABB(sp.blockPosition()).inflate(FIELD_MEND_RANGE), Player::isAlive)) {
                if (p instanceof ServerPlayer mate) {
                    RaidState rs = mate.getData(EdenAttachments.RAID_STATE);
                    if (rs.inRaid && rs.erosion > 0f) {
                        rs.erosion = Math.max(0f, rs.erosion - FIELD_PURGE);
                        EdenNetwork.syncTo(mate);
                    }
                }
            }
        }
    }

    /** uni_res_pollution: clamp eden:pollution duration short while the node is lit. */
    private static void pollutionResistance(ServerPlayer sp) {
        if (!TalentSystem.hasMech(sp, "uni_res_pollution") || sp.tickCount % 40 != 0) {
            return;
        }
        MobEffectInstance pollution = sp.getEffect(EdenEffects.POLLUTION);
        if (pollution != null && pollution.getDuration() > 60) {
            sp.removeEffect(EdenEffects.POLLUTION);
            sp.addEffect(new MobEffectInstance(EdenEffects.POLLUTION, 40, pollution.getAmplifier(),
                    pollution.isAmbient(), pollution.isVisible()));
        }
    }

    /** van_slayer: drop expired stacks and mirror the count into the attack modifier. */
    private static void slayerExpire(ServerPlayer sp) {
        int[] st = SLAYER.get(sp.getUUID());
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (st == null || inst == null) {
            return;
        }
        if (sp.level().getGameTime() > st[1] && st[0] > 0) {
            st[0] = 0;
        }
        boolean has = inst.hasModifier(SLAYER_MOD_ID);
        if (st[0] > 0 && !has) {
            inst.addTransientModifier(new AttributeModifier(SLAYER_MOD_ID, st[0],
                    AttributeModifier.Operation.ADD_VALUE));
        } else if (st[0] <= 0 && has) {
            inst.removeModifier(SLAYER_MOD_ID);
        } else if (has && st[0] > 0) {
            inst.removeModifier(SLAYER_MOD_ID);
            inst.addTransientModifier(new AttributeModifier(SLAYER_MOD_ID, st[0],
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ---------- events ----------

    /** van_slayer: a kill adds (or refreshes) one stack, up to {@link #SLAYER_MAX}. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && TalentSystem.hasMech(killer, "van_slayer")) {
            int[] st = SLAYER.computeIfAbsent(killer.getUUID(), k -> new int[]{0, 0});
            st[0] = Math.min(SLAYER_MAX, st[0] + 1);
            st[1] = (int) (killer.level().getGameTime() + SLAYER_WINDOW);
        }
    }

    /** uni_gath_salvage / scv_loot: +20% mining speed. */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && (TalentSystem.hasMech(sp, "uni_gath_salvage") || TalentSystem.hasMech(sp, "scv_loot"))) {
            event.setNewSpeed(event.getNewSpeed() * 1.2f);
        }
    }

    /** uni_mob_fall: fall damage x0.7. */
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && TalentSystem.hasMech(sp, "uni_mob_fall")) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.7f);
        }
    }

    /**
     * uni_surv_totem / van_totem: once per raid, a lethal blow leaves the player at 3 hearts with brief
     * resistance instead. Runs HIGHEST so it resolves before the pod's life-support field would spend
     * charge on the same blow.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        // uni_coop_shield (共盾): a DIFFERENT player with the node standing within 8 blocks shields you
        // for -15% incoming damage (the holder pays their slot for the team, not for themselves).
        if (sp.level() instanceof ServerLevel level) {
            for (Player mate : level.getEntitiesOfClass(Player.class,
                    new AABB(sp.blockPosition()).inflate(8.0), Player::isAlive)) {
                if (mate != sp && mate instanceof ServerPlayer sMate
                        && TalentSystem.hasMech(sMate, "uni_coop_shield")) {
                    event.setAmount(event.getAmount() * 0.85f);
                    break;
                }
            }
        }
        RaidState rs = sp.getData(EdenAttachments.RAID_STATE);
        if (!rs.inRaid || rs.usedTotem || rs.downed) {
            return;
        }
        if (event.getAmount() < sp.getHealth()) {
            return;   // not lethal
        }
        if (!TalentSystem.hasMech(sp, "uni_surv_totem") && !TalentSystem.hasMech(sp, "van_totem")) {
            return;
        }
        event.setCanceled(true);
        rs.usedTotem = true;
        sp.setHealth(6.0f);
        sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 1));
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        if (sp.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                    40, 0.4, 0.9, 0.4, 0.3);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        EdenMessages.send(sp, Type.SPECIAL, "eden.msg.totem_talent");
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SLAYER.remove(sp.getUUID());
        }
    }
}
