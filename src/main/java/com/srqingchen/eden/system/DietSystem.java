package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.talent.TalentSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * Custom food-based health regeneration (server-side, per tick).
 * <p>When health &lt; max and (foodLevel is full OR saturation &gt; 0), consume food to heal 1:1 at
 * {@code iconsPerSec = 2 * (2 - healthRatio) * difficultyCoeff} (1 icon = 2 food points). Lower
 * health regens faster; harder difficulties regen slower. Consumes saturation first, then foodLevel.
 * <p>This is authoritative and far faster than vanilla natural regen; disabling the vanilla
 * {@code natural_health_regeneration} gamerule is handled in a later polish step.
 */
public class DietSystem {

    public static void tick(ServerPlayer sp) {
        float maxHp = sp.getMaxHealth();
        float hp = sp.getHealth();
        if (hp <= 0f || hp >= maxHp) return;

        FoodData food = sp.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        if (!(foodLevel >= 20 || saturation > 0f)) return;

        RaidState state = sp.getData(EdenAttachments.RAID_STATE);
        if (state.downed) return; // downed players cannot eat their way back up; they must be revived
        float coeff = state.inRaid ? DifficultyTable.dietCoeff(state.difficulty) : 1.0f;

        float healthRatio = hp / maxHp;
        float iconsPerSec = 2f * (2f - healthRatio) * coeff;
        if (TalentSystem.hasMech(sp, "med_healbonus")) iconsPerSec *= 1.25f;   // Medic: +25% healing
        float pointsPerTick = iconsPerSec * 2f / 20f;

        float available = saturation + foodLevel;
        float heal = Math.min(Math.min(pointsPerTick, maxHp - hp), available);
        if (heal <= 0f) return;

        // Consume saturation (float) first, then foodLevel (int) via a fractional accumulator.
        float remaining = heal;
        if (saturation > 0f) {
            float take = Math.min(saturation, remaining);
            food.setSaturation(saturation - take);
            remaining -= take;
        }
        if (remaining > 0f) {
            state.foodDrainAccum += remaining;
            int whole = (int) state.foodDrainAccum;
            if (whole > 0) {
                food.setFoodLevel(Math.max(0, foodLevel - whole));
                state.foodDrainAccum -= whole;
            }
        }
        sp.heal(heal);
    }
}
