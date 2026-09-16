package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenConfig;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.block.ReturnPodBlockEntity;
import com.srqingchen.eden.data.RaidWorldData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenDamageTypes;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The erosion system (replaces a battle-royale shrink circle). Server-side, per tick, for players
 * flagged {@code inRaid}:
 * <ul>
 *   <li>30-minute hard limit -&gt; collapse (lethal).</li>
 *   <li>Erosion value accumulates slowly for 20 min, then ramps; killing mobs adds a little.</li>
 *   <li>Miasma pulses (0-20 min): every 60s then 30s, a chance to lose 10% max health.</li>
 *   <li>Erosion level (value / cap * difficulty max): each level drains 0.5%/sec of max health.</li>
 * </ul>
 * Per-tick drain uses {@code setHealth} to avoid the damage-cooldown and constant hurt flash; the
 * killing blow is routed through {@code hurtServer} so the death is attributed to {@code eden:erosion}.
 */
public class ErosionSystem {

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Custom food-based regen runs for everyone (in and out of raids).
        DietSystem.tick(sp);

        RaidState state = sp.getData(EdenAttachments.RAID_STATE);
        // Out-of-combat regen talent works everywhere (not just in a raid), so it runs before the inRaid gate.
        outOfCombatRegen(sp, state);
        if (!state.inRaid) return;

        ServerLevel level = (ServerLevel) sp.level();
        long elapsed = level.getGameTime() - state.raidStartTick;
        float elapsedMin = elapsed / 1200f;

        // Hard time limit -> collapse (guaranteed lethal).
        if (elapsedMin >= EdenConfig.RAID_DURATION_MINUTES.get()) {
            DamageSource collapse = level.damageSources().source(EdenDamageTypes.COLLAPSE);
            sp.invulnerableTime = 0;
            sp.hurtServer(level, collapse, sp.getHealth() * 4f + 100f);
            return;
        }

        // Clean fields (§10 oasis / §8 retreat card): inside one, the gauge PAUSES and drains 0.5/s.
        if (cleanFieldTick(sp, level, state)) {
            if (elapsed % 20L == 0L) EdenNetwork.syncTo(sp);
            return;
        }

        float cap = EdenConfig.EROSION_MAX.get();
        // Accumulate erosion value (slow before 20 min, faster after); erosion-resistance talents trim the
        // rate, and equipped cards scale it (curse cards up, the purity card down - see CurioCards).
        float perSec = elapsedMin < 20f
                ? DifficultyTable.erosionPerSecEarly(state.difficulty)
                : DifficultyTable.erosionPerSecLate(state.difficulty);
        float resist = erosionResistance(sp);
        float cardMult = CurioCards.erosionGrowthMultiplier(sp);
        state.erosion = Math.min(cap, state.erosion + perSec * (1f - resist) * cardMult / 20f);

        // Miasma pulses (0-20 min): periodic chance to lose 10% of max health.
        if (elapsedMin < 20f) {
            long interval = elapsedMin < 10f ? 1200L : 600L; // 60s / 30s
            if (elapsed % interval == 0L && sp.getRandom().nextFloat() < EdenConfig.MIASMA_CHANCE.get()) {
                DamageSource miasma = level.damageSources().source(EdenDamageTypes.MIASMA);
                sp.invulnerableTime = 0;
                sp.hurtServer(level, miasma, sp.getMaxHealth() * 0.10f);
            }
        }

        // Erosion level from value; each level drains 0.5%/sec of max health (per tick).
        int maxLevel = DifficultyTable.maxErosionLevel(state.difficulty);
        state.erosionLevel = cap <= 0f ? 0 : Math.min(maxLevel, (int) (state.erosion / cap * maxLevel));
        if (state.erosionLevel > 0) {
            float dmgPerTick = state.erosionLevel * 0.005f * sp.getMaxHealth() / 20f;
            float health = sp.getHealth();
            if (health - dmgPerTick <= 0f) {
                DamageSource erosion = level.damageSources().source(EdenDamageTypes.EROSION);
                sp.invulnerableTime = 0;
                sp.hurtServer(level, erosion, health + 1f);
            } else {
                sp.setHealth(health - dmgPerTick);
            }
        }

        // Sync to client ~1/sec for the HUD and difficulty-prefix tooltip.
        if (elapsed % 20L == 0L) EdenNetwork.syncTo(sp);
    }

    /** Killing a mob during a raid adds a little erosion, discouraging unnecessary combat. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            RaidState state = killer.getData(EdenAttachments.RAID_STATE);
            if (state.inRaid) {
                float cap = EdenConfig.EROSION_MAX.get();
                state.erosion = Math.min(cap, state.erosion + DifficultyTable.erosionPerKill(state.difficulty));
            }
        }
    }

    // ---------- clean fields (oasis / retreat card) ----------

    /** Erosion drained per second inside a clean field. */
    private static final float CLEAN_FIELD_DECAY = 0.5f;
    /** The retreat card's clean-field radius around any placed pod. */
    private static final double RETREAT_RADIUS = 8.0;

    /**
     * Clean-field tick (oasis §10 / retreat card §8): when the player stands inside one, erosion pauses
     * and drains {@link #CLEAN_FIELD_DECAY}/s. Returns true when a field applies (the caller then skips
     * the normal accumulation for this tick). Also fires the one-time oasis welcome.
     */
    private static boolean cleanFieldTick(ServerPlayer sp, ServerLevel level, RaidState state) {
        boolean inField = false;
        if (level.dimension().equals(EdenDimensions.RAID_OVERWORLD)) {
            RaidWorldData data = RaidWorldData.get(level);
            boolean inOasis = OasisSystem.isInOasis(sp, data);
            if (inOasis) {
                inField = true;
                if (!state.oasisVisited) {
                    state.oasisVisited = true;
                    OasisSystem.announceFirstEntry(sp, false);
                    EdenMessages.send(sp, Type.SPECIAL, "eden.msg.oasis_entered");
                }
            }
        }
        if (!inField && CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_RETREAT.get())
                && ReturnPodBlockEntity.findAnyPodNear(level, sp.position(), RETREAT_RADIUS) != null) {
            inField = true;
        }
        if (!inField) {
            return false;
        }
        if (state.erosion > 0f && sp.tickCount % 20 == 0) {
            state.erosion = Math.max(0f, state.erosion - CLEAN_FIELD_DECAY);
            state.erosionLevel = recalcLevel(state);
        }
        return true;
    }

    /** Recompute the derived erosion level for the current gauge value (shared by the drain paths). */
    private static int recalcLevel(RaidState state) {
        float cap = EdenConfig.EROSION_MAX.get();
        int maxLevel = DifficultyTable.maxErosionLevel(state.difficulty);
        return cap <= 0f ? 0 : Math.min(maxLevel, (int) (state.erosion / cap * maxLevel));
    }

    // ---------- talent-driven mechanics ----------

    /** Erosion-accumulation reduction from unlocked resistance talents (uni_res_erosion / med_erosion, each -15%; uni_adapt_focus -10%, doubled by uni_adapt_core). */
    private static float erosionResistance(ServerPlayer sp) {
        float r = 0f;
        if (TalentSystem.hasMech(sp, "uni_res_erosion")) r += 0.15f;
        if (TalentSystem.hasMech(sp, "med_erosion")) r += 0.15f;
        if (TalentSystem.hasMech(sp, "uni_adapt_focus")) {
            r += TalentSystem.hasMech(sp, "uni_adapt_core") ? 0.20f : 0.10f;
        }
        return Math.min(0.8f, r);
    }

    private static final int REGEN_DELAY = 100;        // 5s clear of damage before regen kicks in
    private static final int REGEN_INTERVAL = 20;      // heal once per second
    private static final float REGEN_AMOUNT = 0.5f;    // HP per second per node (0.5 HP = 1/4 heart)

    /** Out-of-combat regen (uni_surv_regen / van_regen): once 5s clear of damage, heal a little each second. */
    private static void outOfCombatRegen(ServerPlayer sp, RaidState state) {
        if (state.downed) return;
        float amount = 0f;
        if (TalentSystem.hasMech(sp, "uni_surv_regen")) amount += REGEN_AMOUNT;
        if (TalentSystem.hasMech(sp, "van_regen")) amount += REGEN_AMOUNT;
        if (amount <= 0f || sp.getHealth() >= sp.getMaxHealth()) return;
        long now = sp.level().getGameTime();
        if (now - state.lastCombatTick < REGEN_DELAY) return;   // still in combat
        if (now % REGEN_INTERVAL != 0L) return;                 // heal once per second
        sp.heal(amount);
    }

    /** Track the last damage taken, so the out-of-combat regen talent knows when combat ended. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            RaidState state = sp.getData(EdenAttachments.RAID_STATE);
            state.lastCombatTick = sp.level().getGameTime();
            // Perfect-extraction tracking (§12): any real damage this raid voids the flawless bonus.
            if (state.inRaid && event.getAmount() > 0f) {
                state.tookDamage = true;
            }
        }
    }
}
