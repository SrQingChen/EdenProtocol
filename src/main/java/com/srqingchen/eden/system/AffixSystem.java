package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side tick effects for the raid's random affixes (design doc §7). Every affix with a mechanical effect
 * on the crew or the world's mobs is applied here on the player tick; purely client-side affixes (WHISPER
 * ambience, DENSE_FOG visibility) are handled in the client layer instead.
 * <p>Effects fire on staggered offsets of {@code player.tickCount} so several affixes don't all pulse on the
 * same tick. Registered via addListener in {@code EdenProtocol}.
 */
public final class AffixSystem {
    private AffixSystem() {}

    // Spore storm: a blindness pulse every 60s, lasting 5s.
    private static final int SPORE_INTERVAL = 1200;
    private static final int SPORE_BLIND_TICKS = 100;
    // Crystallization: 5 magic damage every 30s.
    private static final int CRYSTAL_INTERVAL = 600;
    private static final float CRYSTAL_DAMAGE = 5.0f;
    // Corrosion: every 30s, a 70% chance to add one stack of -70% armor & armor toughness for 60s (stackable).
    private static final int CORROSION_INTERVAL = 600;
    private static final float CORROSION_CHANCE = 0.7f;
    private static final float CORROSION_REDUCTION = 0.7f;
    private static final int CORROSION_DURATION = 1200;
    private static final int CORROSION_MAX_STACKS = 3;
    private static final Identifier CORROSION_ARMOR_ID = Identifier.fromNamespaceAndPath("eden", "affix_corrosion_armor");
    private static final Identifier CORROSION_TOUGH_ID = Identifier.fromNamespaceAndPath("eden", "affix_corrosion_toughness");
    // Proliferation: pollution mobs gain +20% max health (on top of entity_modifier's configured value).
    private static final int PROLIF_INTERVAL = 200;
    private static final double PROLIF_RANGE = 32.0;
    private static final float PROLIF_HEALTH_BONUS = 0.2f;
    private static final Identifier PROLIF_HEALTH_ID = Identifier.fromNamespaceAndPath("eden", "affix_proliferation_health");
    // §11 progressive reveal: one hidden affix surfaces on its own every 8 minutes.
    private static final int REVEAL_INTERVAL = 9600;

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid) {
            return;
        }
        // Time-based affix peel (§11): every 8 min the world betrays one more modifier on its own.
        if (player.tickCount % REVEAL_INTERVAL == REVEAL_INTERVAL / 2) {
            revealNextAffix(player, "eden.msg.affix_time_reveal");
        }
        if (state.affixes.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int tick = player.tickCount;
        for (String id : state.affixes) {
            RaidAffix affix = RaidAffix.byId(id);
            if (affix == null) {
                continue;
            }
            switch (affix) {
                case SPORE_STORM -> sporeStorm(player, tick);
                case CRYSTALLIZATION -> crystallization(player, level, tick);
                case CORROSION -> corrosion(player, level, state, tick);
                case PROLIFERATION -> proliferation(player, level, tick);
                case WHISPER, DENSE_FOG -> {
                    // Client-side ambience only (thicker fog / whisper sounds); nothing to apply on the server.
                }
            }
        }
    }

    // ---------- progressive reveal (§11) ----------

    /**
     * Reveal the next still-hidden affix to this player (scanner sweep / time peel). Announces the
     * freshly-read affix with the given message key. No-op when everything is already revealed.
     */
    public static void revealNextAffix(ServerPlayer player, String announceKey) {
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        for (String id : state.affixes) {
            if (!state.revealedAffixes.contains(id)) {
                state.revealedAffixes.add(id);
                EdenNetwork.syncTo(player);
                RaidAffix affix = RaidAffix.byId(id);
                EdenMessages.send(player, Type.WARNING, announceKey,
                        affix != null ? Component.translatable(affix.langName()) : Component.literal(id),
                        hiddenCount(state));
                return;
            }
        }
    }

    /** Insight card: lay every hidden affix on the table at once. */
    public static void revealAllAffixes(ServerPlayer player) {
        RaidState state = player.getData(EdenAttachments.RAID_STATE);
        boolean changed = false;
        for (String id : state.affixes) {
            if (!state.revealedAffixes.contains(id)) {
                state.revealedAffixes.add(id);
                changed = true;
            }
        }
        if (changed) {
            EdenNetwork.syncTo(player);
            EdenMessages.send(player, Type.SPECIAL, "eden.msg.affix_all_revealed");
        }
    }

    private static int hiddenCount(RaidState state) {
        int hidden = 0;
        for (String id : state.affixes) {
            if (!state.revealedAffixes.contains(id)) {
                hidden++;
            }
        }
        return hidden;
    }

    /** Spore storm: a blindness pulse every 60s so the crew can't rely on sight during the run. */
    private static void sporeStorm(ServerPlayer player, int tick) {
        if (tick % SPORE_INTERVAL == 100) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SPORE_BLIND_TICKS, 0), null);
        }
    }

    /** Crystallization: razor crystals erupt every 30s for a chunk of magic damage. */
    private static void crystallization(ServerPlayer player, ServerLevel level, int tick) {
        if (tick % CRYSTAL_INTERVAL == 50) {
            player.hurtServer(level, level.damageSources().magic(), CRYSTAL_DAMAGE);
        }
    }

    /**
     * Corrosion: every 30s a 70% chance to add one stack that cuts armor AND armor toughness by 70% for 60s.
     * Stacks compound (two stacks already floor armor at 0) and the timer refreshes on each new stack; when it
     * lapses all stacks drop at once. Stacks live in RaidState (transient - a relog sheds the debuff, matching
     * how attribute modifiers themselves are not persisted).
     */
    private static void corrosion(ServerPlayer player, ServerLevel level, RaidState state, int tick) {
        long now = level.getGameTime();
        if (state.corrosionStacks > 0 && now >= state.corrosionExpireTick) {
            state.corrosionStacks = 0;
            applyCorrosion(player, 0);
        }
        if (tick % CORROSION_INTERVAL == 200 && player.getRandom().nextFloat() < CORROSION_CHANCE) {
            state.corrosionStacks = Math.min(CORROSION_MAX_STACKS, state.corrosionStacks + 1);
            state.corrosionExpireTick = now + CORROSION_DURATION;
            applyCorrosion(player, state.corrosionStacks);
        }
    }

    /** (Re)set the corrosion armor/toughness modifiers to reflect the current stack count. */
    private static void applyCorrosion(ServerPlayer player, int stacks) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(CORROSION_ARMOR_ID);
            if (stacks > 0) {
                armor.addTransientModifier(new AttributeModifier(CORROSION_ARMOR_ID,
                        -CORROSION_REDUCTION * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        AttributeInstance tough = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (tough != null) {
            tough.removeModifier(CORROSION_TOUGH_ID);
            if (stacks > 0) {
                tough.addTransientModifier(new AttributeModifier(CORROSION_TOUGH_ID,
                        -CORROSION_REDUCTION * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** Proliferation: nearby pollution mobs gain +20% max health once (on top of entity_modifier's tuning). */
    private static void proliferation(ServerPlayer player, ServerLevel level, int tick) {
        if (tick % PROLIF_INTERVAL != 150) {
            return;
        }
        AABB box = new AABB(player.blockPosition()).inflate(PROLIF_RANGE);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null && !health.hasModifier(PROLIF_HEALTH_ID)) {
                float before = mob.getMaxHealth();
                health.addTransientModifier(new AttributeModifier(PROLIF_HEALTH_ID,
                        PROLIF_HEALTH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                mob.heal(mob.getMaxHealth() - before);   // top up the extra hearts so the buff is felt at once
            }
        }
    }
}
