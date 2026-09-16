package com.srqingchen.eden.system;

import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * TRIGGER cards (功能清单 §8 "触发卡") - event-driven effects instead of flat attributes:
 * <ul>
 *   <li><b>汲血之卡</b> - killing a mob heals 1.5 hearts.</li>
 *   <li><b>余烬之卡</b> - melee hits have a 25% chance to ignite the target for 4s.</li>
 *   <li><b>愈合之卡</b> - after 5s out of combat, regenerate 0.5 hearts every 2s.</li>
 *   <li><b>圣盾之卡</b> - when absorption is empty, re-arm 4 absorption hearts every 10s.</li>
 * </ul>
 */
public final class TriggerCards {
    private TriggerCards() {}

    private static final float LEECH_HEAL = 3.0f;
    private static final float EMBER_CHANCE = 0.25f;
    private static final float EMBER_SECONDS = 4.0f;
    private static final int MEND_DELAY = 100;
    private static final int MEND_INTERVAL = 40;
    private static final float MEND_AMOUNT = 1.0f;
    private static final int AEGIS_INTERVAL = 200;
    private static final int AEGIS_SHIELD = 8;      // 4 absorption hearts

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        boolean mending = CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_MENDING.get());
        boolean aegis = CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_AEGIS.get());
        boolean insight = CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_INSIGHT.get());
        if (mending && sp.tickCount % MEND_INTERVAL == 0
                && sp.getHealth() < sp.getMaxHealth()
                && sp.level().getGameTime() - sp.getData(EdenAttachments.RAID_STATE).lastCombatTick >= MEND_DELAY) {
            sp.heal(MEND_AMOUNT);
        }
        if (aegis && sp.tickCount % AEGIS_INTERVAL == 0 && sp.getAbsorptionAmount() <= 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, AEGIS_INTERVAL + 60, (AEGIS_SHIELD - 2) / 4));
        }
        // Insight card (§11 情报卡): wearing it decrypts every hidden affix at once (checked 1/s, cheap).
        if (insight && sp.tickCount % 20 == 0) {
            AffixSystem.revealAllAffixes(sp);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && CurioCards.isEquipped(killer, (CardItem) EdenItems.CARD_LEECH.get())) {
            killer.heal(LEECH_HEAL);
            if (killer.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, killer.getX(), killer.getY() + 1.4, killer.getZ(),
                        4, 0.3, 0.4, 0.3, 0.0);
            }
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker
                && CurioCards.isEquipped(attacker, (CardItem) EdenItems.CARD_EMBER.get())
                && attacker.getRandom().nextFloat() < EMBER_CHANCE) {
            LivingEntity target = event.getEntity();
            target.igniteForSeconds(EMBER_SECONDS);
        }
    }
}
