package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenDamageTypes;
import com.srqingchen.eden.registry.EdenEffects;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Card actives for CURSE cards (功能清单 §19.1 / §19.2), fired by the card-skill key (default Tab,
 * short tap vs. hold - see {@code EdenKeyMappings}).
 *
 * <p><b>Short press</b> = a one-shot burst with a per-card cooldown:
 * <ul>
 *   <li><b>嗜血 bloodthirst</b> - pay 50% of max health, the next melee hit adds TRUE VOID damage
 *       (bypasses everything) equal to the paid health + 50% of that hit's damage.</li>
 *   <li><b>贪婪 greed</b> - instantly deposit 15-40 supply points into the shared pool; +20 erosion.</li>
 *   <li><b>玻璃大炮 glass</b> - the next melee hit deals x3; recoil of 20% max health immediately.</li>
 *   <li><b>污染共鸣 resonance</b> - poison + wither every monster in range 8; +10 erosion.</li>
 * </ul>
 *
 * <p><b>Long press</b> = the SACRIFICIAL BURN: the equipped card is DESTROYED and a powerful state with a
 * RAMPING cost begins. All four burn states share the same frame:
 * <ul>
 *   <li>bloodthirst - the passive bonus stays; lose X% max health / sec, X = elapsed seconds (0%, 1%, 2%,
 *       ...) capped at 30%/s. Health reaching 1 -> EMBER.</li>
 *   <li>greed「饕餮」90s - settlement value +200%, magnet ground items; erosion cost ramps 0.5..3/s.
 *       Erosion full -> EMBER.</li>
 *   <li>glass「孤注一掷」60s - attack +150%; damage taken ramps x1.5..x3. A lethal blow -> EMBER.</li>
 *   <li>resonance「共鸣潮」90s - hits apply Poison II; self-poison amplifier ramps every 15s.
 *       Health reaching 2 -> EMBER.</li>
 * </ul>
 *
 * <p><b>EMBER (余烬)</b>: the burn's zero-condition. Health is locked to 1 (damage besides world collapse
 * is voided, death is cancelled), the burn bonus KEPT; after 6 seconds it resolves - with an upright
 * teammate in the dimension the player drops into the DOWNED state (revivable), otherwise true death.
 *
 * <p>All state is transient (a relog clears it safely: cooldowns reset, burns end, modifiers are transient
 * and therefore already gone).
 */
public final class CurseCardSystem {
    private CurseCardSystem() {}

    // ---------- per-card tuning ----------

    /** Short-press cooldown per card id, in ticks. */
    private static final Map<String, Integer> SHORT_CD = Map.of(
            "bloodthirst", 60 * 20,
            "greed", 90 * 20,
            "glass", 45 * 20,
            "resonance", 60 * 20);

    /** Burn duration in seconds; -1 = no cap (runs until its zero-condition). */
    private static final Map<String, Integer> BURN_DURATION = Map.of(
            "bloodthirst", -1,
            "greed", 90,
            "glass", 60,
            "resonance", 90);

    /** Burn ambience colour per card (0xRRGGBB dust). */
    private static final Map<String, Integer> BURN_COLOR = Map.of(
            "bloodthirst", 0xB01818,
            "greed", 0x3FBF5A,
            "glass", 0xE8E8FF,
            "resonance", 0x8A3FC8);

    private static final Identifier BURN_MODIFIER_ID = Identifier.fromNamespaceAndPath("eden", "curse_burn");
    private static final float EMBER_SECONDS = 6.0f;
    private static final double GREED_MAGNET_RANGE = 8.0;
    private static final double RESONANCE_BURST_RANGE = 8.0;

    // ---------- state ----------

    private static final Map<UUID, CardState> STATES = new HashMap<>();

    private static final class CardState {
        /** Short-press cooldown expiry (game time) per card id. */
        final Map<String, Long> shortCdUntil = new HashMap<>();
        /** bloodthirst short press: true-void bonus armed for the next melee hit. */
        float pendingBloodBonus = 0f;
        /** glass short press: next melee hit x3. */
        boolean pendingGlassOverload = false;
        /** Active burn (long press) card id, or null. */
        @Nullable String burnId;
        /** Seconds elapsed in the burn (fractional; whole-second boundaries apply the cost). */
        float burnElapsedSec = 0f;
        int burnLastWholeSec = -1;
        /** Ember start game time, or -1 when not in ember. */
        long emberStartTick = -1L;
    }

    private static CardState stateOf(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new CardState());
    }

    /** Whether the player is inside a burn state (used by tooltips / HUD later). */
    public static boolean isBurning(ServerPlayer sp) {
        CardState st = STATES.get(sp.getUUID());
        return st != null && st.burnId != null;
    }

    /** The active burn card id ("greed" while 饕餮 runs), or null - read by the settlement bonus. */
    public static String activeBurnId(ServerPlayer sp) {
        CardState st = STATES.get(sp.getUUID());
        return st == null ? null : st.burnId;
    }

    /** Whether the player is in the ember lock (health pinned to 1). */
    public static boolean isEmber(ServerPlayer sp) {
        CardState st = STATES.get(sp.getUUID());
        return st != null && st.emberStartTick >= 0L;
    }

    // ---------- entry point (card-skill key) ----------

    /**
     * The card-skill key fired. Short press triggers the one-shot of EVERY equipped active card whose
     * cooldown is ready; long press starts the sacrificial burn of the FIRST eligible equipped card.
     */
    public static void onUse(ServerPlayer sp, boolean longPress) {
        CardState st = STATES.get(sp.getUUID());
        if (st == null) {
            st = stateOf(sp);
        }
        if (st.burnId != null || st.emberStartTick >= 0L) {
            EdenMessages.overlay(sp, Type.WARNING, "eden.card.msg.burning");
            return;
        }
        List<CardItem> actives = new ArrayList<>();
        for (ItemStack stack : CurioCards.equippedCards(sp)) {
            CardItem card = (CardItem) stack.getItem();
            if (card.hasActive()) {
                actives.add(card);
            }
        }
        if (actives.isEmpty()) {
            EdenMessages.overlay(sp, Type.INFO, "eden.card.msg.no_active");
            return;
        }
        long now = sp.level().getGameTime();
        if (longPress) {
            for (CardItem card : actives) {
                String id = card.activeId();
                if (now >= st.shortCdUntil.getOrDefault(id, 0L)) {
                    startBurn(sp, st, id);
                    return;
                }
            }
            EdenMessages.overlay(sp, Type.WARNING, "eden.card.msg.burn_cooldown");
        } else {
            int fired = 0;
            for (CardItem card : actives) {
                String id = card.activeId();
                if (now >= st.shortCdUntil.getOrDefault(id, 0L) && triggerShort(sp, st, id)) {
                    st.shortCdUntil.put(id, now + SHORT_CD.getOrDefault(id, 60 * 20));
                    fired++;
                }
            }
            if (fired == 0) {
                EdenMessages.overlay(sp, Type.WARNING, "eden.card.msg.short_cooldown");
            }
        }
    }

    // ---------- short press ----------

    private static boolean triggerShort(ServerPlayer sp, CardState st, String id) {
        return switch (id) {
            case "bloodthirst" -> shortBloodPact(sp, st);
            case "greed" -> shortWindfall(sp, st);
            case "glass" -> shortOverload(sp, st);
            case "resonance" -> shortTaintBurst(sp, st);
            default -> false;
        };
    }

    /** 嗜血·血契: pay 50% max health (leaving >=1), arm true-void damage for the next melee hit. */
    private static boolean shortBloodPact(ServerPlayer sp, CardState st) {
        float max = sp.getMaxHealth();
        float health = sp.getHealth();
        float paid = Math.min(health - 1.0f, max * 0.5f);
        if (paid <= 0.5f) {
            EdenMessages.overlay(sp, Type.WARNING, "eden.card.msg.no_health");
            return false;   // too wounded to pay: not fired, no cooldown burned
        }
        sp.setHealth(health - paid);
        st.pendingBloodBonus = paid;
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9f, 0.7f);
        ServerLevel level = (ServerLevel) sp.level();
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, sp.getX(), sp.getY() + 1.1, sp.getZ(),
                18, 0.5, 0.6, 0.5, 0.15);
        EdenMessages.overlay(sp, Type.SPECIAL, "eden.card.msg.blood_armed", (int) paid);
        return true;
    }

    /** 贪婪·横财: 15-40 supply points straight into the shared pool; +20 erosion. */
    private static boolean shortWindfall(ServerPlayer sp, CardState st) {
        MinecraftServer server = sp.level().getServer();
        if (server == null) {
            return false;
        }
        int gain = 15 + sp.getRandom().nextInt(26);
        CampaignData.get(server).addSupplyPoints(gain);
        addErosion(sp, 20f);
        ServerLevel level = (ServerLevel) sp.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                14, 0.5, 0.6, 0.5, 0.0);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.5f);
        EdenMessages.overlay(sp, Type.SPECIAL, "eden.card.msg.windfall", gain);
        return true;
    }

    /** 玻璃·过载: next melee hit x3; immediate 20% max-health recoil (cannot kill). */
    private static boolean shortOverload(ServerPlayer sp, CardState st) {
        st.pendingGlassOverload = true;
        float recoil = Math.min(sp.getHealth() - 1.0f, sp.getMaxHealth() * 0.2f);
        if (recoil > 0f) {
            sp.setHealth(sp.getHealth() - recoil);
        }
        ServerLevel level = (ServerLevel) sp.level();
        level.sendParticles(ParticleTypes.CRIT, sp.getX(), sp.getY() + 1.1, sp.getZ(), 16, 0.5, 0.6, 0.5, 0.2);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        EdenMessages.overlay(sp, Type.SPECIAL, "eden.card.msg.overload_armed");
        return true;
    }

    /** 共鸣·浊爆: poison II + wither I (5s) to every monster in range 8; +10 erosion. */
    private static boolean shortTaintBurst(ServerPlayer sp, CardState st) {
        ServerLevel level = (ServerLevel) sp.level();
        List<Monster> targets = level.getEntitiesOfClass(Monster.class,
                new AABB(sp.blockPosition()).inflate(RESONANCE_BURST_RANGE));
        for (Monster m : targets) {
            m.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            m.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
            level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight() * 0.7, m.getZ(),
                    8, 0.3, 0.4, 0.3, 0.1);
        }
        addErosion(sp, 10f);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0f, 0.7f);
        EdenMessages.overlay(sp, Type.SPECIAL, "eden.card.msg.taint_burst", targets.size());
        return true;
    }

    // ---------- long press: the sacrificial burn ----------

    private static void startBurn(ServerPlayer sp, CardState st, String id) {
        if (!CurioCards.burnEquipped(sp, id)) {
            return;   // card vanished between key press and here: nothing to burn
        }
        st.burnId = id;
        st.burnElapsedSec = 0f;
        st.burnLastWholeSec = -1;
        // The burn keeps (or exceeds) the card's own passive while it runs.
        if ("bloodthirst".equals(id)) {
            applyBurnModifier(sp, 6.0, AttributeModifier.Operation.ADD_VALUE);
        } else if ("glass".equals(id)) {
            applyBurnModifier(sp, 1.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.6f);
        EdenMessages.overlay(sp, Type.DANGER, "eden.card.msg.burn_start." + id);
    }

    private static void applyBurnModifier(ServerPlayer sp, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(BURN_MODIFIER_ID);
            inst.addTransientModifier(new AttributeModifier(BURN_MODIFIER_ID, amount, op));
        }
    }

    /** Whole-second cost of each burn state; returns true when the zero-condition fired (ember). */
    private static boolean applySecondCost(ServerPlayer sp, CardState st, int second) {
        ServerLevel level = (ServerLevel) sp.level();
        switch (st.burnId) {
            case "bloodthirst" -> {
                float pct = Math.min(30, second) / 100f;
                float health = sp.getHealth();
                sp.setHealth(Math.max(1.0f, health - sp.getMaxHealth() * pct));
                if (health - sp.getHealth() > 0f) {
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                            6, 0.3, 0.5, 0.3, 0.1);
                }
                if (sp.getHealth() <= 1.0f) {
                    return true;
                }
            }
            case "greed" -> {
                float rate = Math.min(3.0f, 0.5f + 0.5f * second);
                addErosion(sp, rate);
                if (sp.getData(EdenAttachments.RAID_STATE).erosion >= EdenConfig.EROSION_MAX.get() - 0.01f) {
                    return true;
                }
            }
            case "glass" -> {
                // glass's cost is the ramping damage-taken multiplier (applied in onIncomingDamage)
            }
            case "resonance" -> {
                if (sp.getHealth() <= 2.0f) {
                    return true;
                }
            }
            default -> {}
        }
        return false;
    }

    /** Normal end of a timed burn (the bonus and the cost both stop). */
    private static void endBurn(ServerPlayer sp, CardState st) {
        String id = st.burnId;
        st.burnId = null;
        st.burnElapsedSec = 0f;
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(BURN_MODIFIER_ID);
        }
        if (id != null) {
            EdenMessages.overlay(sp, Type.INFO, "eden.card.msg.burn_end." + id);
        }
    }

    // ---------- ember ----------

    private static void enterEmber(ServerPlayer sp, CardState st) {
        st.emberStartTick = sp.level().getGameTime();
        sp.setHealth(1.0f);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        EdenMessages.overlay(sp, Type.DANGER, "eden.card.msg.ember");
    }

    /** Ember resolution after {@link #EMBER_SECONDS}: downed with a teammate present, else true death. */
    private static void finishEmber(ServerPlayer sp, CardState st) {
        String burnId = st.burnId;
        st.emberStartTick = -1L;
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        if (inst != null) {
            inst.removeModifier(BURN_MODIFIER_ID);
        }
        STATES.remove(sp.getUUID());
        if (hasUprightTeammate(sp)) {
            RaidState rs = sp.getData(EdenAttachments.RAID_STATE);
            rs.downed = true;
            sp.setHealth(1.0f);
            sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1000000, 2), null);
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1000000, 1), null);
            EdenNetwork.syncTo(sp);
            EdenMessages.send(sp, Type.DANGER, "eden.msg.downed");
            if (burnId != null) {
                EdenMessages.send(sp, Type.WARNING, "eden.card.msg.ember_downed");
            }
        } else {
            ServerLevel level = (ServerLevel) sp.level();
            sp.invulnerableTime = 0;
            sp.hurtServer(level, level.damageSources().source(EdenDamageTypes.VOID_TRUE),
                    sp.getMaxHealth() * 4f + 100f);
        }
    }

    /** True if another upright (alive, not downed) player shares this dimension - mirrors ReviveSystem. */
    private static boolean hasUprightTeammate(ServerPlayer player) {
        for (Player p : player.level().players()) {
            if (p == player || p.isDeadOrDying()) {
                continue;
            }
            RaidState s = p.getData(EdenAttachments.RAID_STATE);
            if (s.inRaid && !s.downed) {
                return true;
            }
        }
        return false;
    }

    // ---------- per-tick ----------

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        CardState st = STATES.get(sp.getUUID());
        // Greed passive: lure polluted mobs (only meaningful inside a raid).
        boolean hasGreed = CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_GREED.get());
        CurioCards.greedLureTick(sp, hasGreed);
        if (st == null) {
            return;
        }
        long now = sp.level().getGameTime();
        ServerLevel level = (ServerLevel) sp.level();

        if (st.burnId != null && st.emberStartTick < 0L) {
            st.burnElapsedSec += 1f / 20f;
            int whole = (int) st.burnElapsedSec;
            if (whole > st.burnLastWholeSec) {
                st.burnLastWholeSec = whole;
                if (applySecondCost(sp, st, whole)) {
                    enterEmber(sp, st);
                    return;
                }
            }
            burnTickEffects(sp, st, level);
            int duration = BURN_DURATION.getOrDefault(st.burnId, -1);
            if (duration > 0 && st.burnElapsedSec >= duration) {
                endBurn(sp, st);
            }
            return;
        }

        if (st.emberStartTick >= 0L) {
            sp.setHealth(1.0f);   // locked: cannot gain or lose health
            if (sp.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.FLAME, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                        6, 0.3, 0.6, 0.3, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                        3, 0.2, 0.4, 0.2, 0.02);
            }
            if (now - st.emberStartTick >= EMBER_SECONDS * 20f) {
                finishEmber(sp, st);
            }
        }
    }

    /** Continuous burn ambience + greed's item magnet + resonance's self-poison refresh. */
    private static void burnTickEffects(ServerPlayer sp, CardState st, ServerLevel level) {
        String id = st.burnId;
        if (sp.tickCount % 5 == 0) {
            int color = BURN_COLOR.getOrDefault(id, 0xFFFFFF);
            double r = 0.9;
            for (int i = 0; i < 3; i++) {
                double a = sp.tickCount * 0.35 + i * (Math.PI * 2 / 3);
                level.sendParticles(new DustParticleOptions(color, 1.2f),
                        sp.getX() + Math.cos(a) * r, sp.getY() + 0.4 + (sp.tickCount % 20) * 0.05,
                        sp.getZ() + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        if ("greed".equals(id) && sp.tickCount % 40 == 0) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(sp.blockPosition()).inflate(GREED_MAGNET_RANGE));
            for (ItemEntity item : items) {
                if (item.isAlive()) {
                    item.setPickUpDelay(0);
                    item.playerTouch(sp);
                }
            }
        }
        if ("resonance".equals(id)) {
            int amplifier = Math.min(3, (int) (st.burnElapsedSec / 15f));
            sp.addEffect(new MobEffectInstance(MobEffects.POISON, 40, amplifier, true, false));
        }
    }

    // ---------- damage / death hooks ----------

    /** Damage adjustments: ember invulnerability, glass vulnerability, armed shorts, resonance taint. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        // Self-directed effects only when the VICTIM is one of our players.
        if (victim instanceof ServerPlayer target) {
            CardState ts = STATES.get(target.getUUID());
            // Ember: everything is voided except world collapse.
            if (ts != null && ts.emberStartTick >= 0L) {
                if (!isCollapse(event.getSource())) {
                    event.setCanceled(true);
                }
                return;
            }
            // Glass burn: ramping damage-taken multiplier (true void damage itself is exempt).
            if (ts != null && "glass".equals(ts.burnId) && !isVoidTrue(event.getSource())) {
                float mult = Math.min(3.0f, 1.5f + 0.05f * ts.burnElapsedSec);
                event.setAmount(event.getAmount() * mult);
            }
        }
        // Attack-side effects when the ATTACKER is one of our players (victim may be any living entity).
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            CardState as = STATES.get(attacker.getUUID());
            boolean melee = event.getSource().getDirectEntity() == attacker;
            if (as != null && melee) {
                float amount = event.getAmount();
                if (as.pendingGlassOverload) {
                    event.setAmount(amount * 3f);
                    amount *= 3f;
                    as.pendingGlassOverload = false;
                }
                if (as.pendingBloodBonus > 0f) {
                    queueVoidDamage(victim, as.pendingBloodBonus + amount * 0.5f);
                    as.pendingBloodBonus = 0f;
                }
            }
            if (as != null && "resonance".equals(as.burnId)) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            } else if (CurioCards.isEquipped(attacker, (CardItem) EdenItems.CARD_RESONANCE.get())) {
                // resonance passive: hits taint the target with eden:pollution
                victim.addEffect(new MobEffectInstance(EdenEffects.POLLUTION, 80, 0));
            }
        }
    }

    /**
     * Death interception (HIGH, ahead of ReviveSystem): ember fully cancels death; a glass-burn lethal
     * blow converts to ember instead. World collapse is never intercepted.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        CardState st = STATES.get(sp.getUUID());
        if (st == null || isCollapse(event.getSource())) {
            return;
        }
        if (st.emberStartTick >= 0L) {
            event.setCanceled(true);
            sp.setHealth(1.0f);
            return;
        }
        if ("glass".equals(st.burnId)) {
            event.setCanceled(true);
            enterEmber(sp, st);
        }
    }

    /** True when a burn/ember player receives a genuinely non-preventable death (world collapse). */
    private static boolean isCollapse(DamageSource source) {
        return source.is(EdenDamageTypes.COLLAPSE);
    }

    private static boolean isVoidTrue(DamageSource source) {
        return source.is(EdenDamageTypes.VOID_TRUE);
    }

    // ---------- true void damage (deferred so it lands after the triggering hit) ----------

    private record PendingVoidHit(LivingEntity target, float amount) {}

    private static final List<PendingVoidHit> VOID_QUEUE = new ArrayList<>();

    private static void queueVoidDamage(LivingEntity target, float amount) {
        VOID_QUEUE.add(new PendingVoidHit(target, amount));
    }

    /** Apply queued true-void hits: pure health loss through a damage type that bypasses everything. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (VOID_QUEUE.isEmpty()) {
            return;
        }
        List<PendingVoidHit> batch = new ArrayList<>(VOID_QUEUE);
        VOID_QUEUE.clear();
        for (PendingVoidHit hit : batch) {
            LivingEntity target = hit.target();
            if (!target.isAlive()) {
                continue;
            }
            ServerLevel level = (ServerLevel) target.level();
            target.invulnerableTime = 0;
            target.hurtServer(level, level.damageSources().source(EdenDamageTypes.VOID_TRUE), hit.amount());
            if (target instanceof ServerPlayer) {
                level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0, target.getZ(),
                        12, 0.3, 0.6, 0.3, 0.4);
            }
        }
    }

    // ---------- lifecycle cleanup ----------

    /** Full clear: dimension change (extraction) ends burns; logout / real death drop the state. */
    public static void clear(ServerPlayer sp) {
        CardState st = STATES.remove(sp.getUUID());
        if (st != null) {
            AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_DAMAGE);
            if (inst != null) {
                inst.removeModifier(BURN_MODIFIER_ID);
            }
        }
    }

    public static void onChangedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            clear(sp);
        }
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            STATES.remove(sp.getUUID());
        }
    }

    /**
     * LOWEST-priority death observer: if the death survived the whole chain uncanceled, the state is
     * gone for good - drop it (burn modifiers are transient and already cleared by the death itself).
     */
    public static void onDeathFinal(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer sp) {
            STATES.remove(sp.getUUID());
        }
    }

    // ---------- small helpers ----------

    private static void addErosion(ServerPlayer sp, float amount) {
        RaidState rs = sp.getData(EdenAttachments.RAID_STATE);
        if (rs.inRaid) {
            rs.erosion = Math.min(EdenConfig.EROSION_MAX.get(), rs.erosion + amount);
            EdenNetwork.syncTo(sp);
        }
    }
}
