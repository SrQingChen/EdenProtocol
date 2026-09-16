package com.srqingchen.eden.system;

import com.srqingchen.eden.dimension.EdenDimensions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Crew scaling (§17 按人数缩放): monsters that join a raid dimension are buffed by how many players
 * are currently in that world - +8% max health and +4% attack per crew member above one, capped at
 * +40% / +20%. Difficulty scaling stays with entity_modifier profiles; this is the live multiplayer
 * coefficient on top, so a four-player crew faces meaningfully beefier taint without the scout solo
 * being punished. Runs on EntityJoinLevel so natural spawns, swarm waves and结构 spawns all scale.
 */
public final class CrewScaler {
    private CrewScaler() {}

    private static final float HEALTH_PER_CREW = 0.08f;
    private static final float ATTACK_PER_CREW = 0.04f;
    private static final float HEALTH_CAP = 0.40f;
    private static final float ATTACK_CAP = 0.20f;

    private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("eden", "crew_scale_health");
    private static final Identifier ATTACK_ID = Identifier.fromNamespaceAndPath("eden", "crew_scale_attack");

    /** Players who count as the raid CREW: everyone in the level EXCEPT spectators (§17 观战不缩放). */
    public static int crewCount(ServerLevel level) {
        int n = 0;
        for (var p : level.players()) {
            if (!p.isSpectator()) {
                n++;
            }
        }
        return n;
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!isRaidLevel(level)) {
            return;
        }
        int crew = crewCount(level);
        if (crew <= 1) {
            return;   // solo runs keep the entity_modifier profile as-is
        }
        float health = Math.min(HEALTH_CAP, (crew - 1) * HEALTH_PER_CREW);
        float attack = Math.min(ATTACK_CAP, (crew - 1) * ATTACK_PER_CREW);
        apply(mob, Attributes.MAX_HEALTH, HEALTH_ID, health);
        if (health > 0f) {
            mob.setHealth(mob.getMaxHealth());   // the extra hearts are filled, not hollow
        }
        apply(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID, attack);
    }

    private static boolean isRaidLevel(ServerLevel level) {
        return level.dimension().equals(EdenDimensions.RAID_OVERWORLD)
                || level.dimension().equals(EdenDimensions.RAID_NETHER)
                || level.dimension().equals(EdenDimensions.RAID_END);
    }

    private static void apply(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                              Identifier id, float amount) {
        if (amount <= 0f) {
            return;
        }
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(id, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
