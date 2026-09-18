package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.season.SeasonFinale;
import com.srqingchen.eden.season.Seasons;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.server.level.ServerBossEvent;

import javax.annotation.Nullable;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The season-finale flow (批 D): advance vote passes → the arena gate opens PERMANENTLY
 * (multi-retry by design - a beaten crew goes back to the polluted world, grows, and returns) →
 * challengers gather in the void colosseum → a countdown → the season's apex boss → beat it and
 * the season's purification completes, the ending ceremony plays and the campaign reaches 0%.
 * <p>The boss's stat block is Eden's own (crew-scaled at spawn so the fight scales with however
 * many challengers walk in); the entity_modifier profile layer adds its combat effects on top
 * (see {@link FinaleProfile}). Eden's phase script drives the drama beats at 66% / 33%.
 * <p>Session-scoped state only: the durable bits (gate open, season completed) live in
 * {@link CampaignData}, so a reboot mid-fight just means the next entry restarts the challenge.
 */
public final class FinaleSystem {
    private FinaleSystem() {}

    /** Entity tag marking the season-finale boss (any mob carrying the season's spec). */
    public static final String TAG_BOSS = "eden_finale_boss";

    /** Baseline boss stats before crew scaling (the warden apex of S0). */
    private static final float BOSS_BASE_HEALTH = 800.0f;
    private static final float BOSS_ARMOR = 16.0f;
    private static final float BOSS_KB_RESIST = 1.0f;
    /** +25% health per challenger above the first, uncapped - bring friends, face the deep. */
    private static final float HEALTH_PER_CHALLENGER = 0.25f;

    /** Countdown from gate entry to the boss waking, in ticks. */
    private static final int COUNTDOWN_TICKS = 15 * 20;
    /** Boss despawns once the arena has been empty this long (re-entry restarts the fight). */
    private static final int EMPTY_DESPAWN_TICKS = 60 * 20;

    // ---------- live fight state (session only) ----------

    private static UUID bossId;
    private static ServerBossEvent bossBar;
    private static int countdown = -1;
    private static int emptyTicks;
    private static int phase = 0;
    /** Players who died inside the arena this fight (routed back to the ark on respawn). */
    private static final Set<UUID> diedInArena = new HashSet<>();

    private static final Identifier PHASE_SPEED = Identifier.fromNamespaceAndPath("eden", "finale_phase_speed");
    private static final Identifier PHASE_ATTACK = Identifier.fromNamespaceAndPath("eden", "finale_phase_attack");

    // ---------- gate / entry ----------

    /** The advance vote reached majority: open the gate forever and gather the crew. */
    public static void onVotePassed(MinecraftServer server) {
        CampaignData data = CampaignData.get(server);
        if (data.seasonCompleted()) {
            return;
        }
        data.setFinaleUnlocked(true);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, "eden.finale.gate_open");
        }
        gather(server);
    }

    /** Everyone online is pulled into the arena (they voted for it). */
    private static void gather(MinecraftServer server) {
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            enter(p);
        }
    }

    /** Enter (or re-enter) the finale challenge: chronicle button, re-entry after a wipe, ... */
    public static void enter(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        if (server == null) {
            return;
        }
        CampaignData data = CampaignData.get(server);
        if (!data.finaleUnlocked() || data.seasonCompleted()) {
            return;
        }
        SeasonFinale spec = spec(server);
        if (spec == null) {
            return;
        }
        ServerLevel arena = server.getLevel(EdenDimensions.FINALE_ARENA);
        if (arena == null) {
            EdenMessages.send(sp, Type.DANGER, "eden.finale.no_arena");
            return;
        }
        BlockPos spawn = FinaleArenaBuilder.ensureArena(arena, spec);
        sp.teleportTo(arena, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), 180.0f, 0.0f, true);
        if (bossId == null && countdown < 0) {
            countdown = COUNTDOWN_TICKS;
            emptyTicks = 0;
            for (ServerPlayer p : arena.players()) {
                EdenMessages.send(p, Type.WARNING, "eden.finale.countdown");
            }
        }
    }

    private static SeasonFinale spec(MinecraftServer server) {
        var season = Seasons.current(server);
        return season == null ? null : season.finale();
    }

    // ---------- ticking: countdown, phases, cleanup ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel arena = server.getLevel(EdenDimensions.FINALE_ARENA);
        if (arena == null) {
            return;
        }
        Mob boss = boss(arena);
        if (countdown >= 0) {
            int crew = CrewScaler.crewCount(arena);
            if (crew == 0) {
                countdown = -1;   // everyone left before the wake; re-entry restarts it
                return;
            }
            if (--countdown % 20 == 0 && countdown > 0) {
                arena.playSound(null, 0.0, 65.0, 0.0, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0f, 0.6f);
            }
            if (countdown <= 0) {
                countdown = -1;
                spawnBoss(server, arena);
            }
            return;
        }
        if (boss == null) {
            return;
        }
        // Boss bar follows the boss; all arena players see it.
        if (bossBar != null) {
            bossBar.setProgress(boss.getHealth() / Math.max(1.0f, boss.getMaxHealth()));
            for (var p : arena.players()) {
                bossBar.addPlayer(p);
            }
        }
        tickPhases(server, arena, boss);
        // Empty-arena cleanup: the deep goes back to sleep, the gate stays open.
        if (arena.players().isEmpty()) {
            if (++emptyTicks >= EMPTY_DESPAWN_TICKS) {
                discard(boss);
            }
        } else {
            emptyTicks = 0;
        }
    }

    private static void tickPhases(MinecraftServer server, ServerLevel arena, Mob boss) {
        float frac = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
        int want = frac > 0.66f ? 1 : frac > 0.33f ? 2 : 3;
        if (want == phase) {
            return;
        }
        phase = want;
        arena.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 0.7f);
        if (phase == 2) {
            buff(boss, 0.20f, 0.15f);
            summonAdds(arena, boss.blockPosition(), EntityType.CAVE_SPIDER, Math.min(6, 2 + CrewScaler.crewCount(arena)));
            broadcast(server, "eden.finale.phase2");
        } else if (phase == 3) {
            buff(boss, 0.40f, 0.30f);
            summonAdds(arena, boss.blockPosition(), EntityType.HUSK, Math.min(6, 2 + CrewScaler.crewCount(arena)));
            summonAdds(arena, boss.blockPosition(), EntityType.CAVE_SPIDER, 2);
            broadcast(server, "eden.finale.phase3");
        }
    }

    /** Eden-side phase buffs on top of whatever the entity_modifier profile layer does. */
    private static void buff(Mob boss, float speedMult, float attackMult) {
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(PHASE_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(PHASE_SPEED,
                    speedMult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance attack = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && !attack.hasModifier(PHASE_ATTACK)) {
            attack.addTransientModifier(new AttributeModifier(PHASE_ATTACK,
                    attackMult, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void summonAdds(ServerLevel arena, BlockPos at, EntityType<? extends Monster> type, int count) {
        for (int i = 0; i < count; i++) {
            Monster add = type.create(arena, EntitySpawnReason.TRIGGERED);
            if (add == null) {
                continue;
            }
            double ang = arena.getRandom().nextDouble() * Math.PI * 2;
            double dist = 8.0 + arena.getRandom().nextDouble() * 6.0;
            add.snapTo(at.getX() + 0.5 + Math.cos(ang) * dist, at.getY() + 1.0,
                    at.getZ() + 0.5 + Math.sin(ang) * dist, 0.0f, 0.0f);
            add.setPersistenceRequired();
            arena.addFreshEntity(add);
        }
    }

    // ---------- boss lifecycle ----------

    private static void spawnBoss(MinecraftServer server, ServerLevel arena) {
        SeasonFinale spec = spec(server);
        if (spec == null) {
            return;
        }
        // Combat-effect layer first: entities joining after applyProfile pick the profile up.
        FinaleProfile.ensureDefault(spec.bossProfile());
        com.entitymodifier.data.ConfigProfileManager.applyProfile(spec.bossProfile());

        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(spec.bossEntity());
        Mob boss = type instanceof EntityType<?> et && et.create(arena, EntitySpawnReason.TRIGGERED) instanceof Mob m
                ? m : EntityType.WARDEN.create(arena, EntitySpawnReason.TRIGGERED);
        if (boss == null) {
            return;
        }
        int crew = Math.max(1, CrewScaler.crewCount(arena));
        boss.snapTo(FinaleArenaBuilder.DAIS.getX() + 0.5, FinaleArenaBuilder.DAIS.getY() + 2.0,
                FinaleArenaBuilder.DAIS.getZ() + 0.5, 0.0f, 0.0f);
        boss.addTag(TAG_BOSS);
        boss.setPersistenceRequired();
        boss.setCustomName(Component.translatable(spec.bossNameKey()));
        boss.setCustomNameVisible(true);
        AttributeInstance maxHealth = boss.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(BOSS_BASE_HEALTH * (1.0f + HEALTH_PER_CHALLENGER * (crew - 1)));
        }
        AttributeInstance armor = boss.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(BOSS_ARMOR);
        }
        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(BOSS_KB_RESIST);
        }
        boss.setHealth(boss.getMaxHealth());
        arena.addFreshEntity(boss);
        bossId = boss.getUUID();
        phase = 1;
        bossBar = new ServerBossEvent(boss.getUUID(), Component.translatable(spec.bossNameKey()),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        bossBar.setProgress(1.0f);
        broadcast(server, "eden.finale.boss_wake");
        arena.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 4.0f, 0.5f);
    }

    @Nullable
    private static Mob boss(ServerLevel arena) {
        if (bossId == null) {
            return null;
        }
        var entity = arena.getEntity(bossId);
        if (entity instanceof Mob m && m.isAlive()) {
            return m;
        }
        resetFight();
        return null;
    }

    private static void resetFight() {
        bossId = null;
        phase = 0;
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
    }

    private static void discard(Mob boss) {
        boss.discard();
        resetFight();
    }

    // ---------- victory / defeat ----------

    /** The boss died: complete the season's purification, then hand over to the ceremony (D-4). */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !mob.entityTags().contains(TAG_BOSS)) {
            trackArenaDeath(event);
            return;
        }
        MinecraftServer server = mob.level().getServer();
        if (server == null) {
            return;
        }
        ServerPlayer hero = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        resetFight();
        CampaignData data = CampaignData.get(server);
        if (data.seasonCompleted()) {
            return;
        }
        data.setSeasonCompleted(true);
        data.markEndingSeen(FinaleCeremony.pickEnding(server).id());
        // The boss's fall completes the season's purification condition: drain the gauge to 0
        // through the normal campaign path so every stage reward, the victory broadcast and the
        // paradise unlock all fire exactly as if the crew purified it block by block.
        if (data.pollution() > 0.0f) {
            CampaignSystem.reducePollution(server, data.pollution(), hero,
                    "eden.chronicle.entry.finale", 1);
        }
        FinaleCeremony.play(server, hero);
    }

    /** Players who fall in the arena respawn back on the ark (the fight stays open). */
    private static void trackArenaDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && sp.level().dimension().equals(EdenDimensions.FINALE_ARENA)) {
            diedInArena.add(sp.getUUID());
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (diedInArena.remove(sp.getUUID()) && sp.level().getServer() != null) {
            ServerLevel ark = sp.level().getServer().getLevel(EdenDimensions.ARK);
            if (ark != null) {
                sp.teleportTo(ark, 0.5, 66.0, 0.5, Set.of(), 0.0f, 0.0f, true);
                EdenMessages.send(sp, Type.WARNING, "eden.finale.died");
            }
        }
    }

    /** Gate open & season not finished: the chronicle button routes here (进入终局挑战). */
    public static boolean gateOpen(MinecraftServer server) {
        CampaignData data = CampaignData.get(server);
        return data.finaleUnlocked() && !data.seasonCompleted();
    }

    private static void broadcast(MinecraftServer server, String key) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, key);
        }
    }
}
