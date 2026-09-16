package com.srqingchen.eden.block;

import com.mojang.serialization.Codec;
import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.dimension.DimensionManager;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CurioCards;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenBlockEntities;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.system.SettlementService;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The return pod: the extraction objective, deployed by a {@code locator} in the raid world.
 * <p>There is ONE resource - the <b>charge pool</b> ({@code chargeProgress}, 0 .. {@link #CHARGE_MAX}). It fills
 * a little every tick at the pod's <b>charge efficiency</b>, and the pod launches the moment it is full:
 * <ul>
 *   <li><b>Efficiency</b> (the per-tick rate, the number shown on the gauge) = {@code min(cap, (a + x*b + y*c) *
 *       registered/crew)} - a base {@code a}, plus a permanent {@code b} per Eden Cell and {@code c} per Taint
 *       Crystal fed (x / y of them), split across the crew and hard-capped at {@link #EFFICIENCY_CAP} (see
 *       {@link #recomputeSpeed}). Feeding fuel ONLY raises this rate; it never dumps charge into the pool.</li>
 *   <li><b>Pool</b>: seeded on deploy with a random 0..{@link #INIT_CHARGE_RATIO} fraction of the max. The
 *       purification shield and life-support field DRAIN it - so turtling is safe but sets your launch back.</li>
 * </ul>
 * A solo, no-fuel run fills the pool in ~10 min. Once full, every registered player still in this dimension is
 * sent home to the ark and their salvage settled into supply points.
 * <p>State persists across chunk reloads via {@link ValueOutput}/{@link ValueInput} (the 26.1.2
 * replacement for {@code CompoundTag}-based save/load).
 */
public class ReturnPodBlockEntity extends BlockEntity {
    /** Charge-pool capacity; the pod launches when {@code chargeProgress} reaches it (12000 = ~10 min solo, no fuel). */
    public static final float CHARGE_MAX = 12000.0f;
    /** Fallback base charge efficiency (a) if the difficulty config is unavailable; config chargeBase is authoritative. */
    public static final float BASE_SPEED = 1.0f;
    /** Hard ceiling on the per-tick charge efficiency, no matter how much fuel is fed (min launch ~2.5 min). */
    public static final float EFFICIENCY_CAP = 4.0f;
    /** A freshly-deployed pod seeds its charge pool with a random 0..this fraction of {@link #CHARGE_MAX}. */
    public static final float INIT_CHARGE_RATIO = 0.3f;
    /** Players within this radius of the pod see the charge gauge. */
    private static final double VIEW_RANGE = 48.0;
    /** Purification shield radius: players inside this many blocks have incoming damage intercepted. */
    public static final double SHIELD_RADIUS = 5.0;
    /**
     * Shield resist ratio {@code t}: intercepting damage costs {@code damage * t} CHARGE. When the pool cannot
     * cover the whole blow it is spent down to empty and the un-absorbed remainder lands (see the climax system).
     * A larger {@code t} makes the shield set the launch back faster.
     */
    public static final float SHIELD_RESIST_RATIO = 5.0f;
    /** Charge cost of one life-support save (a registered crew member's once-per-raid lethal negation). */
    public static final float LIFELINE_COST = 1000.0f;
    /** Ticks between taint-swarm waves while charging (200 = every 10s). */
    private static final int SWARM_INTERVAL = 200;
    /** Base mobs per swarm wave; scales up with crew size (base + 2 per crew member). */
    private static final int SWARM_BASE_COUNT = 3;
    /** Mob types the taint swarm draws from ("life twisted by pollution" - vanilla mobs, buffed by entity_modifier). */
    private static final List<EntityType<? extends Mob>> TAINT_MOB_POOL = List.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY,
            EntityType.SPIDER, EntityType.CAVE_SPIDER);

    private final Set<UUID> registered = new LinkedHashSet<>();
    private float chargeProgress = 0.0f;   // THE charge pool (0..CHARGE_MAX): fills by efficiency, drained by defenses
    // Charge-efficiency coefficients latched from the difficulty config on first registration:
    // rate = min(EFFICIENCY_CAP, (baseSpeed + fedCells*cellBonus + fedCrystals*crystalBonus) * registered/crew).
    private float baseSpeed = 0.0f;     // a: base charge efficiency
    private float cellBonus = 0.0f;     // b: efficiency gained per Eden Cell fed
    private float crystalBonus = 0.0f;  // c: efficiency gained per Taint Crystal fed
    private int fedCells = 0;           // x: Eden Cells fed so far (persisted; each permanently bumps the rate)
    private int fedCrystals = 0;        // y: Taint Crystals fed so far (persisted)
    private float chargeSpeed = 0.0f;   // the (capped) per-tick charge efficiency, recomputed each tick / on fuel
    private boolean chargeSeeded = false;   // whether the starting random charge has been rolled (persisted)
    private final Set<UUID> lifeLineUsed = new LinkedHashSet<>();  // who already spent their once-per-raid save
    private int swarmTimer = 0;   // ticks until the next taint-swarm wave (transient; resets on chunk reload)
    private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(),
            Component.translatable("eden.pod.charge_bar", 0, "0.00", EFFICIENCY_CAP),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    /**
     * Charging pods keyed by level, so the extraction-climax events (purification shield, life-support field,
     * taint swarm) can find the relevant pod without scanning loaded block entities. Weak keys drop a level's
     * entry once its dimension is torn down (each raid rebuilds a fresh ServerLevel).
     */
    private static final Map<Level, Set<ReturnPodBlockEntity>> ACTIVE_PODS = new WeakHashMap<>();

    /**
     * EVERY loaded pod (charging or not) keyed by level: the retreat card and the scanner ask "is there any
     * pod within r blocks of me", which includes pre-deployed evacuation points nobody has boarded yet.
     * Membership is maintained on load / setRemoved, so it self-heals after chunk reloads.
     */
    private static final Map<Level, Set<ReturnPodBlockEntity>> ALL_PODS = new WeakHashMap<>();

    public ReturnPodBlockEntity(BlockPos pos, BlockState state) {
        super(EdenBlockEntities.RETURN_POD.get(), pos, state);
        // A charge gauge, not a boss fight: no darkened screen, no boss music, no world fog.
        this.bossBar.setDarkenScreen(false);
        this.bossBar.setPlayBossMusic(false);
        this.bossBar.setCreateWorldFog(false);
        this.bossBar.setProgress(0.0f);
    }

    /**
     * Register a player aboard. The base charge rate is latched from the first registrant's difficulty
     * config; the effective speed then scales with how much of the crew has registered (see recomputeSpeed).
     */
    public void register(ServerPlayer player) {
        if (this.registered.add(player.getUUID())) {
            if (this.baseSpeed <= 0.0f) {
                latchChargeCoefficients(player);   // a/b/c from the first registrant's difficulty config
            }
            markActive();   // charging has begun: expose this pod to the shield / life-support / swarm systems
            setChanged();
            this.bossBar.addPlayer(player);
            recomputeSpeed();
            updateBar();
            broadcastRegistration(player);
        }
    }

    /**
     * Feed fuel: bumps the fed-count that permanently raises the charge efficiency (see {@link #recomputeSpeed}).
     * Fuel does NOT dump charge into the pool - it only makes the pool fill faster, up to {@link #EFFICIENCY_CAP}.
     */
    public void addFuel(ServerPlayer player, boolean isCell) {
        if (isCell) {
            this.fedCells++;
        } else {
            this.fedCrystals++;
        }
        recomputeSpeed();   // x*b / y*c term grew: the pool now fills faster for the rest of the run
        setChanged();
        updateBar();
        EdenMessages.overlay(player, Type.INFO, "eden.msg.pod_fuel",
                String.format(Locale.ROOT, "%.2f", this.chargeSpeed),
                String.format(Locale.ROOT, "%.1f", EFFICIENCY_CAP));
    }

    /** Latch the charge-efficiency coefficients a/b/c from the difficulty config of the first registrant. */
    private void latchChargeCoefficients(ServerPlayer player) {
        DifficultyConfigData.Entry cfg = configFor(player);
        this.baseSpeed = cfg != null ? cfg.chargeBase() : BASE_SPEED;
        this.cellBonus = cfg != null ? cfg.chargeCell() : 0.0f;
        this.crystalBonus = cfg != null ? cfg.chargeCrystal() : 0.0f;
    }

    /** Roll the pod's starting charge once: a random 0..INIT_CHARGE_RATIO fraction of the pool capacity. */
    private void seedChargeIfNeeded() {
        if (this.chargeSeeded || !(getLevel() instanceof ServerLevel sl)) {
            return;
        }
        this.chargeSeeded = true;
        this.chargeProgress = sl.getRandom().nextFloat() * INIT_CHARGE_RATIO * CHARGE_MAX;
        setChanged();
        updateBar();
    }

    private static DifficultyConfigData.Entry configFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        return DifficultyConfigData.get(server).entryFor(player.getData(EdenAttachments.RAID_STATE).difficulty);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReturnPodBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.seedChargeIfNeeded();   // roll the starting charge once, as soon as the pod exists and ticks
        be.updateViewers(serverLevel);
        if (be.registered.isEmpty()) {
            return;
        }
        // Self-heal the active registry: ACTIVE_PODS is runtime-only (never persisted), so after a chunk reload or a
        // relog the pod comes back with its crew but is MISSING from the map - and the purification shield / life-support
        // field look pods up THERE (findChargingPodNear / findPodFor), so they would silently stop intercepting damage
        // even though the pod keeps charging. markActive() is idempotent, so re-assert membership every tick.
        be.markActive();
        // Recompute every tick: the crew count changes as players join/leave the raid, so the registered
        // fraction (and thus the charge rate) must track it live.
        be.recomputeSpeed();
        if (be.chargeSpeed <= 0.0f) {
            return;
        }
        be.chargeProgress = Math.min(CHARGE_MAX, be.chargeProgress + be.chargeSpeed);
        be.updateBar();
        be.arcTick(serverLevel);           // §16: electric arcs crawl the hull while charging
        be.swarmTick(serverLevel);   // taint swarm: the charge signal draws pollution mobs in waves
        be.shieldAmbient(serverLevel);   // purification-shield zone feedback so the crew can see it is up
        if (serverLevel.getGameTime() % 20L == 0L) {
            be.setChanged();
        }
        // Launch fires the moment the pool is full. The defenses drain this SAME pool, so a heavy fight sets the
        // launch back - feeding fuel raises the efficiency to refill it faster and outpace the drain.
        if (be.chargeProgress >= CHARGE_MAX) {
            be.launch(serverLevel);
        }
    }

    private void updateBar() {
        float p = Math.min(1.0f, this.chargeProgress / CHARGE_MAX);
        this.bossBar.setProgress(p);
        this.bossBar.setName(Component.translatable("eden.pod.charge_bar",
                String.format(Locale.ROOT, "%.2f", p * 100.0f),
                String.format(Locale.ROOT, "%.2f", this.chargeSpeed),
                String.format(Locale.ROOT, "%.1f", EFFICIENCY_CAP)));
        this.bossBar.setColor(p < 0.34f ? BossEvent.BossBarColor.RED
                : (p < 0.67f ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN));
    }

    /**
     * Charge efficiency (the per-tick rate the gauge shows) = min(cap, (a + x*b + y*c) * registered/crew): a base
     * {@code a}, plus a permanent {@code b} per Eden Cell and {@code c} per Taint Crystal fed (x / y of them),
     * split across the crew (one registrant out of n runs at 1/n) and hard-capped at {@link #EFFICIENCY_CAP}. The
     * pool only grows by this rate over time; feeding fuel raises the rate, never the pool directly (addFuel).
     */
    private void recomputeSpeed() {
        int crew = raidCrewCount();
        float ratio = crew > 0 ? Math.min(1.0f, this.registered.size() / (float) crew) : 0.0f;
        float full = this.baseSpeed + this.fedCells * this.cellBonus + this.fedCrystals * this.crystalBonus;
        float speed = full * ratio;
        // Engineer charge-efficiency talent (eng_charge): +15% rate if any registered engineer unlocked it.
        if (crewHasChargeTalent()) speed *= 1.15f;
        // Beacon card (§8 撤离卡): any registered carrier overdrives the pod's induction coil, +25% rate.
        if (crewHasBeaconCard()) speed *= 1.25f;
        // eng_overload (过载部署): a registered engineer with the keystone raises the efficiency ceiling itself.
        float cap = crewHasOverload() ? EFFICIENCY_CAP + 1.0f : EFFICIENCY_CAP;
        this.chargeSpeed = Math.min(cap, speed);
    }

    /** True if any registered, online player unlocked the Engineer overload keystone (eng_overload). */
    private boolean crewHasOverload() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        MinecraftServer server = sl.getServer();
        if (server == null) return false;
        for (UUID id : this.registered) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && TalentSystem.hasMech(p, "eng_overload")) return true;
        }
        return false;
    }

    /** True if any registered, online player has unlocked the Engineer charge-efficiency talent (eng_charge). */
    private boolean crewHasChargeTalent() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        MinecraftServer server = sl.getServer();
        if (server == null) return false;
        for (UUID id : this.registered) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && TalentSystem.hasMech(p, "eng_charge")) return true;
        }
        return false;
    }

    /** True if any registered, online player wears the beacon card (card_beacon, §8). */
    private boolean crewHasBeaconCard() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        MinecraftServer server = sl.getServer();
        if (server == null) return false;
        for (UUID id : this.registered) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && CurioCards.isEquipped(p, (CardItem) EdenItems.CARD_BEACON.get())) return true;
        }
        return false;
    }

    /** How many crew members are currently in this raid dimension (spectators excluded, §17). */
    private int raidCrewCount() {
        if (getLevel() instanceof ServerLevel sl) {
            return com.srqingchen.eden.system.CrewScaler.crewCount(sl);
        }
        return Math.max(1, this.registered.size());
    }

    /** Announce a registration to the whole crew in chat: "<name> checked in - x/n registered". */
    private void broadcastRegistration(ServerPlayer player) {
        int crew = raidCrewCount();
        int done = this.registered.size();
        String name = player.getName().getString();
        if (getLevel() instanceof ServerLevel sl) {
            for (ServerPlayer sp : sl.players()) {
                EdenMessages.send(sp, Type.SUCCESS, "eden.msg.pod_registered", name, done, crew);
            }
        } else {
            EdenMessages.send(player, Type.SUCCESS, "eden.msg.pod_registered", name, done, crew);
        }
    }

    /** Show the charge gauge to the crew working around the pod, and hide it from those who wandered off. */
    private void updateViewers(ServerLevel level) {
        if (this.registered.isEmpty()) {   // the gauge shows once the crew boards (the seeded charge stays hidden till then)
            this.bossBar.removeAllPlayers();
            return;
        }
        AABB box = new AABB(this.getBlockPos()).inflate(VIEW_RANGE);
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, box);
        for (ServerPlayer sp : near) {
            if (!this.bossBar.getPlayers().contains(sp)) {
                this.bossBar.addPlayer(sp);
            }
        }
        for (ServerPlayer sp : new ArrayList<>(this.bossBar.getPlayers())) {
            if (!near.contains(sp)) {
                this.bossBar.removePlayer(sp);
            }
        }
    }

    /** Send every registered player still in this dimension home to the ark, then remove the pod. */
    private void launch(ServerLevel level) {
        launchEffects(level);
        MinecraftServer server = level.getServer();
        for (UUID id : this.registered) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null && sp.level().dimension().equals(level.dimension())) {
                SettlementService.settle(sp);
                DimensionManager.enterArk(sp);
                sp.getData(EdenAttachments.RAID_STATE).reset();
                EdenNetwork.syncTo(sp);
                EdenMessages.send(sp, Type.SUCCESS, "eden.msg.extracted");
            }
        }
        // §4 缺员发射: anyone still in this world who never registered is LEFT BEHIND - say it loudly and
        // point them at their remaining options (another evacuation point, or their own locator).
        for (ServerPlayer sp : level.players()) {
            if (!this.registered.contains(sp.getUUID())) {
                EdenMessages.send(sp, Type.DANGER, "eden.msg.pod_left_behind");
            }
        }
        unmarkActive();
        this.bossBar.removeAllPlayers();
        // Remove the whole 4-cell structure (base + the extension cells above), not just the base.
        ReturnPodBlock.clearStructure(level, this.getBlockPos(), null);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() != null && !getLevel().isClientSide()) {
            ALL_PODS.computeIfAbsent(getLevel(), k -> new LinkedHashSet<>()).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unmarkActive();
        Level lvl = getLevel();
        if (lvl != null) {
            Set<ReturnPodBlockEntity> set = ALL_PODS.get(lvl);
            if (set != null) {
                set.remove(this);
            }
        }
        this.bossBar.removeAllPlayers();
    }

    public float getChargeProgress() {
        return this.chargeProgress;
    }

    /** Inject a burst of charge straight into the pool (Engineer "emergency charge" skill); clamped to {@link #CHARGE_MAX}. */
    public void injectCharge(float amount) {
        this.chargeProgress = Math.min(CHARGE_MAX, this.chargeProgress + amount);
        setChanged();
        updateBar();
    }

    public float getChargeSpeed() {
        return this.chargeSpeed;
    }

    public Set<UUID> getRegistered() {
        return this.registered;
    }

    /**
     * True while the pod is crewed and still on-station (registered; launch removes it). The shield, life-support
     * field and taint swarm all key off this, so defenses stay up for the WHOLE extraction - including the tail
     * where charge is full but the crew is still fighting to survive until the pod launches.
     */
    public boolean isActive() {
        return !this.registered.isEmpty();
    }

    /**
     * Drain the charge pool (the purification shield calls this when it eats damage, the life-support field when
     * it stops a lethal blow). Clamped at 0 - draining sets the launch progress back, which is the whole tension.
     */
    public void consumeCharge(float amount) {
        if (amount <= 0.0f) {
            return;
        }
        this.chargeProgress = Math.max(0.0f, this.chargeProgress - amount);
        setChanged();
        updateBar();
    }

    /**
     * Life-support field: try to spend this player's once-per-raid lethal save. Returns true if the blow was
     * absorbed (the caller must then cancel the death and leave the player at 1 HP), false if unavailable.
     */
    public boolean tryLifeLine(UUID player) {
        if (this.lifeLineUsed.contains(player)) {
            return false;
        }
        if (this.chargeProgress < LIFELINE_COST) {
            return false;
        }
        this.lifeLineUsed.add(player);
        consumeCharge(LIFELINE_COST);
        return true;
    }

    /** Add this pod to the active registry so the extraction-climax events can find it. */
    private void markActive() {
        Level lvl = getLevel();
        if (lvl != null) {
            ACTIVE_PODS.computeIfAbsent(lvl, k -> new LinkedHashSet<>()).add(this);
        }
    }

    /** Remove this pod from the active registry (launched or broken). */
    private void unmarkActive() {
        Level lvl = getLevel();
        if (lvl != null) {
            Set<ReturnPodBlockEntity> set = ACTIVE_PODS.get(lvl);
            if (set != null) {
                set.remove(this);
            }
        }
    }

    /**
     * Nearest actively-charging pod within {@code radius} of {@code pos} (the purification shield uses this to
     * decide whether a player standing near a pod has their incoming damage intercepted).
     */
    @Nullable
    public static ReturnPodBlockEntity findChargingPodNear(Level level, Vec3 pos, double radius) {
        Set<ReturnPodBlockEntity> set = ACTIVE_PODS.get(level);
        if (set == null || set.isEmpty()) {
            return null;
        }
        double r2 = radius * radius;
        for (ReturnPodBlockEntity pod : set) {
            if (pod.isActive() && Vec3.atCenterOf(pod.getBlockPos()).distanceToSqr(pos) <= r2) {
                return pod;
            }
        }
        return null;
    }

    /**
     * Nearest loaded pod of ANY state (charging or idle evacuation point) within {@code radius}. The
     * retreat card's clean-field effect and the scanner's "charging pod" readout use this.
     */
    @Nullable
    public static ReturnPodBlockEntity findAnyPodNear(Level level, Vec3 pos, double radius) {
        Set<ReturnPodBlockEntity> set = ALL_PODS.get(level);
        if (set == null || set.isEmpty()) {
            return null;
        }
        ReturnPodBlockEntity best = null;
        double bestD2 = radius * radius;
        for (ReturnPodBlockEntity pod : set) {
            double d2 = Vec3.atCenterOf(pod.getBlockPos()).distanceToSqr(pos);
            if (d2 <= bestD2) {
                best = pod;
                bestD2 = d2;
            }
        }
        return best;
    }

    /**
     * The charging pod this player registered aboard, if any (the life-support field uses this; per design the
     * save works regardless of where in the world the player falls, so there is no radius check here).
     */
    @Nullable
    public static ReturnPodBlockEntity findPodFor(UUID player) {
        for (Set<ReturnPodBlockEntity> set : ACTIVE_PODS.values()) {
            for (ReturnPodBlockEntity pod : set) {
                if (pod.isActive() && pod.getRegistered().contains(player)) {
                    return pod;
                }
            }
        }
        return null;
    }

    /**
     * Charging feedback (§16 充能电弧/能量波动): electric sparks crawl the pod's hull and a soft
     * resonance chime ticks every 5 seconds while the pool is filling, so the crew can HEAR progress.
     */
    private void arcTick(ServerLevel level) {
        if (level.getGameTime() % 10L != 0L) {
            return;
        }
        RandomSource rand = level.getRandom();
        Vec3 c = Vec3.atCenterOf(this.getBlockPos());
        for (int i = 0; i < 3; i++) {
            double a = rand.nextDouble() * Math.PI * 2.0;
            double r = 0.8;
            level.sendParticles(com.srqingchen.eden.registry.EdenParticles.ENERGY_ARC.get(),
                    c.x + Math.cos(a) * r, c.y - 1.5 + rand.nextDouble() * 3.2, c.z + Math.sin(a) * r,
                    2, 0.05, 0.15, 0.05, 0.02);
        }
        if (level.getGameTime() % 100L == 0L) {
            level.playSound(null, this.getBlockPos(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f,
                    0.8f + (this.chargeProgress / CHARGE_MAX) * 0.8f);   // pitch rises with the pool
        }
    }

    /** Launch fanfare: beacon power-down into a fireworks burst as the pod tears home. */
    private void launchEffects(ServerLevel level) {
        Vec3 c = Vec3.atCenterOf(this.getBlockPos());
        level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.2f, 1.4f);
        level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1.0, c.z, 40, 0.6, 1.2, 0.6, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 30, 0.5, 1.0, 0.5, 0.2);
    }

    /**
     * Purification-shield zone feedback: a faint ring of light at {@link #SHIELD_RADIUS} so the crew can SEE the
     * protected zone (a shield that silently eats damage is easy to forget). Only while crewed and the pool has
     * charge; pulses every two seconds instead of every tick.
     */
    private void shieldAmbient(ServerLevel level) {
        if (!isActive() || this.chargeProgress <= 0.0f || level.getGameTime() % 40L != 0L) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(this.getBlockPos());
        RandomSource rand = level.getRandom();
        for (int i = 0; i < 18; i++) {
            double a = (Math.PI * 2.0 * i) / 18.0 + rand.nextDouble() * 0.25;
            double x = c.x + Math.cos(a) * SHIELD_RADIUS;
            double z = c.z + Math.sin(a) * SHIELD_RADIUS;
            double y = c.y - 0.5 + rand.nextDouble() * 2.5;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    /**
     * Taint swarm (the "extraction climax" pressure): while charging, the pod's energy signal draws pollution
     * mobs that spawn in waves around it, scaling with crew size. entity_modifier's raid profile buffs them.
     */
    private void swarmTick(ServerLevel level) {
        if (!isActive()) {
            return;
        }
        if (++this.swarmTimer < SWARM_INTERVAL) {
            return;
        }
        this.swarmTimer = 0;
        spawnSwarmWave(level);
    }

    private void spawnSwarmWave(ServerLevel level) {
        int crew = raidCrewCount();
        int count = SWARM_BASE_COUNT + crew * 2;
        BlockPos center = getBlockPos();
        RandomSource rand = level.getRandom();
        // The crew defending the pod are the swarm's targets (each mob picks one at random so they spread out).
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, new AABB(center).inflate(VIEW_RANGE));
        if (!near.isEmpty()) {
            // §16 浊潮 warning sting so a wave is heard coming, not just seen.
            level.playSound(null, center, net.minecraft.sounds.SoundEvents.BELL_RESONATE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.9f, 0.5f);
        }
        for (int i = 0; i < count; i++) {
            BlockPos spawn = findSwarmSpawn(level, center, rand);
            if (spawn == null) {
                continue;
            }
            EntityType<? extends Mob> type = TAINT_MOB_POOL.get(rand.nextInt(TAINT_MOB_POOL.size()));
            Mob mob = type.create(level, EntitySpawnReason.TRIGGERED);
            if (mob == null) {
                continue;
            }
            mob.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), EntitySpawnReason.TRIGGERED, null);
            if (!near.isEmpty()) {
                mob.setTarget(near.get(rand.nextInt(near.size())));
            }
            level.addFreshEntity(mob);
        }
    }

    /** Pick a surface spot 8-16 blocks out from the pod, so the swarm closes in instead of popping on top of it. */
    @Nullable
    private BlockPos findSwarmSpawn(ServerLevel level, BlockPos center, RandomSource rand) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double dist = 8.0 + rand.nextDouble() * 8.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("chargeProgress", this.chargeProgress);
        output.putFloat("baseSpeed", this.baseSpeed);
        output.putFloat("cellBonus", this.cellBonus);
        output.putFloat("crystalBonus", this.crystalBonus);
        output.putInt("fedCells", this.fedCells);
        output.putInt("fedCrystals", this.fedCrystals);
        output.putBoolean("chargeSeeded", this.chargeSeeded);
        ValueOutput.TypedOutputList<String> list = output.list("registered", Codec.STRING);
        for (UUID id : this.registered) {
            list.add(id.toString());
        }
        ValueOutput.TypedOutputList<String> lifeLine = output.list("lifeLineUsed", Codec.STRING);
        for (UUID id : this.lifeLineUsed) {
            lifeLine.add(id.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.chargeProgress = input.getFloatOr("chargeProgress", 0.0f);
        this.baseSpeed = input.getFloatOr("baseSpeed", 0.0f);
        this.cellBonus = input.getFloatOr("cellBonus", 0.0f);
        this.crystalBonus = input.getFloatOr("crystalBonus", 0.0f);
        this.fedCells = input.getIntOr("fedCells", 0);
        this.fedCrystals = input.getIntOr("fedCrystals", 0);
        this.chargeSeeded = input.getBooleanOr("chargeSeeded", false);
        this.registered.clear();
        for (String s : input.listOrEmpty("registered", Codec.STRING)) {
            try {
                this.registered.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUID entries defensively.
            }
        }
        this.lifeLineUsed.clear();
        for (String s : input.listOrEmpty("lifeLineUsed", Codec.STRING)) {
            try {
                this.lifeLineUsed.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUID entries defensively.
            }
        }
        recomputeSpeed();
        updateBar();
    }
}
