package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S1 矿洞季修饰层 (batch B) - a pure modifier package on top of the raid worlds, active while
 * {@code CampaignData.seasonIndex() == 1} and never touching world generation:
 * <ul>
 *   <li><b>刷怪倾向</b> - underground hostiles spawn more often (bonus dark-spot spawns near
 *   y&lt;0 players) and sometimes arrive as cave stock (spider→cave spider, zombie→husk); the
 *   surface stays comparatively safe, steering the crew downward where the season lives.</li>
 *   <li><b>局部塌方</b> - a periodic collapse event around an underground player: 10s rumble
 *   warning, then 30s of falling rock. Anyone with solid cover overhead is immune - the event is
 *   a routing decision (get under something), not a tax.</li>
 *   <li><b>词缀效果</b> - the two mechanical cave affixes live here: 幽暗菌毯 (spore motes +
 *   wider aggro radius underground) and 矿脉共鸣 (ore glints near the crew; mining ore angers
 *   everything in earshot). 深处低语 is client ambience (AffixAmbience).</li>
 * </ul>
 * All state is transient per server session (collapse cooldowns, swap tags) - nothing persists.
 */
public final class CaveSeasonSystem {
    private CaveSeasonSystem() {}

    /** The three expedition dimensions the modifier package applies to. */
    private static final ResourceKey<Level>[] RAID_DIMS = new ResourceKey[]{
            EdenDimensions.RAID_OVERWORLD, EdenDimensions.RAID_NETHER, EdenDimensions.RAID_END};

    // Bonus underground spawn tuning.
    private static final int SPAWN_EVERY_TICKS = 40;      // one attempt window per 2s
    private static final float SPAWN_CHANCE = 0.30f;      // ~+30% spawn pressure underground
    private static final int SPAWN_MIN_DIST = 10;
    private static final int SPAWN_MAX_DIST = 24;
    private static final int SPAWN_LOCAL_CAP = 10;        // skip when the neighbourhood is already full

    // Cave replacement probabilities (natural y<0 spawns only).
    private static final float SPIDER_TO_CAVE = 0.30f;
    private static final float ZOMBIE_TO_HUSK = 0.25f;
    /** Tag marking mobs we swapped ourselves (breaks the join-event recursion). */
    private static final String TAG_SWAPPED = "eden_cave_swapped";

    // Collapse tuning: after someone has been underground for a while, roll a collapse every so often.
    private static final int COLLAPSE_FIRST_DELAY = 20 * 180;      // earliest: 3 min into the run
    private static final int COLLAPSE_ROLL_INTERVAL = 20 * 60;     // roll once a minute after that
    private static final float COLLAPSE_ROLL_CHANCE = 0.25f;
    private static final int COLLAPSE_WARN_TICKS = 20 * 10;        // 10s rumble warning
    private static final int COLLAPSE_DURATION = 20 * 30;          // 30s of falling rock
    private static final double COLLAPSE_RADIUS = 18.0;
    private static final float COLLAPSE_DAMAGE = 2.0f;
    private static final int COVER_DEPTH = 4;                      // solid within 4 above = sheltered

    // 幽暗菌毯 aggro tuning: underground monsters track the crew from further out.
    private static final float GLOOM_FOLLOW_BONUS = 0.4f;
    private static final Identifier GLOOM_FOLLOW_ID = Identifier.fromNamespaceAndPath("eden", "gloom_follow");

    /** Per-level collapse state machine: 0 idle, 1 warned, 2 active. */
    private static final class Collapse {
        int state;
        long at;           // warn-end / active-end tick
        BlockPos centre = BlockPos.ZERO;
    }

    private static final Map<ResourceKey<Level>, Collapse> collapses = new HashMap<>();

    /** Is the cave-season modifier package live right now? */
    public static boolean active(MinecraftServer server) {
        return CampaignData.get(server).seasonIndex() == 1;
    }

    // ---------- wiring (see EdenProtocol ctor) ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!active(server)) {
            return;
        }
        for (ResourceKey<Level> key : RAID_DIMS) {
            ServerLevel raid = server.getLevel(key);
            if (raid == null || raid.players().isEmpty()) {
                collapses.remove(key);
                continue;
            }
            tickCollapse(server, raid);
            tickBonusSpawns(raid);
            tickAffixWorldEffects(raid);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !isRaidLevel(level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || !active(server) || mob.getBlockY() >= 0 || mob.entityTags().contains(TAG_SWAPPED)) {
            return;
        }
        // 幽暗菌毯: every underground monster tracks the crew from further out (applied at join so
        // it covers natural spawns, waves and structure mobs in one place).
        if (hasAnyAffix(level, RaidAffix.GLOOM_MYCELIUM)) {
            AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (follow != null && !follow.hasModifier(GLOOM_FOLLOW_ID)) {
                follow.addTransientModifier(new AttributeModifier(GLOOM_FOLLOW_ID,
                        GLOOM_FOLLOW_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        // Cave stock replacement: swap the vanilla surface mob for its cave cousin before it joins.
        RandomSource rand = level.getRandom();
        EntityType<?> replace = null;
        float roll = rand.nextFloat();
        if (mob.getType() == EntityType.SPIDER && roll < SPIDER_TO_CAVE) {
            replace = EntityType.CAVE_SPIDER;
        } else if (mob.getType() == EntityType.ZOMBIE && roll < ZOMBIE_TO_HUSK) {
            replace = EntityType.HUSK;
        }
        if (replace == null) {
            return;
        }
        event.setCanceled(true);
        Mob swap = (Mob) replace.create(level, EntitySpawnReason.NATURAL);
        if (swap == null) {
            return;
        }
        swap.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        swap.addTag(TAG_SWAPPED);
        level.addFreshEntity(swap);
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)
                || !(sp.level() instanceof ServerLevel level) || !isRaidLevel(level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        BlockPos pos = event.getPos();
        if (server == null || !active(server) || sp.getBlockY() >= 0) {
            return;
        }
        BlockState state = event.getState();
        if (!isOre(state)) {
            return;
        }
        // 矿脉共鸣: mining ore underground rings out and everything in earshot comes looking.
        if (!sp.getData(EdenAttachments.RAID_STATE).affixes.contains(RaidAffix.ORE_RESONANCE.id)) {
            return;
        }
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.6f, 0.5f);
        for (Monster m : level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(20.0))) {
            m.setTarget(sp);
        }
    }

    // ---------- collapse (局部塌方) ----------

    private static void tickCollapse(MinecraftServer server, ServerLevel raid) {
        Collapse c = collapses.computeIfAbsent(raid.dimension(), k -> new Collapse());
        long now = raid.getGameTime();
        RandomSource rand = raid.getRandom();
        switch (c.state) {
            case 0 -> {
                if (now < COLLAPSE_FIRST_DELAY) {
                    return;
                }
                ServerPlayer underground = randomUndergroundPlayer(raid, rand);
                if (underground == null || now % COLLAPSE_ROLL_INTERVAL != 0
                        || rand.nextFloat() >= COLLAPSE_ROLL_CHANCE) {
                    return;
                }
                c.state = 1;
                c.centre = underground.blockPosition();
                c.at = now + COLLAPSE_WARN_TICKS;
                for (ServerPlayer sp : raid.players()) {
                    EdenMessages.send(sp, Type.WARNING, "eden.cave.collapse_warning");
                }
                raid.playSound(null, c.centre, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.AMBIENT, 1.2f, 0.5f);
            }
            case 1 -> {
                if (now < c.at) {
                    if (now % 10 == 0) {
                        raid.playSound(null, c.centre, SoundEvents.STONE_STEP, SoundSource.AMBIENT, 1.4f, 0.4f);
                    }
                    return;
                }
                c.state = 2;
                c.at = now + COLLAPSE_DURATION;
                for (ServerPlayer sp : raid.players()) {
                    EdenMessages.send(sp, Type.DANGER, "eden.cave.collapse_start");
                }
            }
            case 2 -> {
                if (now % 10 == 0) {
                    collapseTick(raid, c, rand);
                }
                if (now >= c.at) {
                    c.state = 0;
                    for (ServerPlayer sp : raid.players()) {
                        EdenMessages.send(sp, Type.SUCCESS, "eden.cave.collapse_end");
                    }
                }
            }
            default -> c.state = 0;
        }
    }

    /** One second of active collapse: falling rock around the centre; uncovered players get hurt. */
    private static void collapseTick(ServerLevel raid, Collapse c, RandomSource rand) {
        for (int i = 0; i < 3; i++) {
            BlockPos at = c.centre.offset(rand.nextInt(17) - 8, 6 + rand.nextInt(4), rand.nextInt(17) - 8);
            if (raid.getBlockState(at).isAir() && raid.getBlockState(at.below()).isAir()) {
                FallingBlockEntity rock = FallingBlockEntity.fall(raid, at, Blocks.COBBLESTONE.defaultBlockState());
                rock.dropItem = false;   // no item dupe when something odd happens on landing
            }
        }
        raid.playSound(null, c.centre, SoundEvents.DEEPSLATE_STEP, SoundSource.AMBIENT, 1.6f, 0.5f);
        for (ServerPlayer sp : raid.players()) {
            if (sp.blockPosition().distSqr(c.centre) > COLLAPSE_RADIUS * COLLAPSE_RADIUS || isCovered(raid, sp.blockPosition())) {
                continue;   // out of the zone, or sensibly under cover
            }
            sp.hurtServer(raid, raid.damageSources().fallingBlock(null), COLLAPSE_DAMAGE);
        }
    }

    /** True when any solid block hangs within {@link #COVER_DEPTH} blocks above the position. */
    private static boolean isCovered(ServerLevel raid, BlockPos pos) {
        for (int i = 1; i <= COVER_DEPTH; i++) {
            if (raid.getBlockState(pos.above(i)).isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    @javax.annotation.Nullable
    private static ServerPlayer randomUndergroundPlayer(ServerLevel raid, RandomSource rand) {
        List<ServerPlayer> deep = new ArrayList<>();
        for (ServerPlayer sp : raid.players()) {
            if (sp.getBlockY() < 0 && sp.getData(EdenAttachments.RAID_STATE).inRaid) {
                deep.add(sp);
            }
        }
        return deep.isEmpty() ? null : deep.get(rand.nextInt(deep.size()));
    }

    // ---------- bonus underground spawns (刷怪倾向) ----------

    private static void tickBonusSpawns(ServerLevel raid) {
        if (raid.getGameTime() % SPAWN_EVERY_TICKS != 0 || raid.getRandom().nextFloat() >= SPAWN_CHANCE) {
            return;
        }
        ServerPlayer target = randomUndergroundPlayer(raid, raid.getRandom());
        if (target == null) {
            return;
        }
        RandomSource rand = raid.getRandom();
        int dist = SPAWN_MIN_DIST + rand.nextInt(SPAWN_MAX_DIST - SPAWN_MIN_DIST + 1);
        double angle = rand.nextDouble() * Math.PI * 2.0;
        int x = target.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = target.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = target.getBlockY() + rand.nextInt(9) - 4;
        BlockPos spot = new BlockPos(x, y, z);
        if (!raid.hasChunkAt(spot) || !raid.getBlockState(spot).isAir()
                || !raid.getBlockState(spot.below()).isSolidRender()
                || raid.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, spot) >= 8) {
            return;
        }
        if (raid.getEntitiesOfClass(Monster.class, new AABB(spot).inflate(20.0)).size() >= SPAWN_LOCAL_CAP) {
            return;
        }
        EntityType<? extends Mob> type = switch (rand.nextInt(3)) {
            case 0 -> EntityType.ZOMBIE;
            case 1 -> EntityType.SKELETON;
            default -> EntityType.SPIDER;
        };
        Mob mob = type.create(raid, EntitySpawnReason.NATURAL);
        if (mob == null) {
            return;
        }
        mob.snapTo(x + 0.5, y, z + 0.5, rand.nextFloat() * 360f, 0f);
        raid.addFreshEntity(mob);
    }

    // ---------- cave affix world effects ----------

    private static void tickAffixWorldEffects(ServerLevel raid) {
        long now = raid.getGameTime();
        for (ServerPlayer sp : raid.players()) {
            RaidState rs = sp.getData(EdenAttachments.RAID_STATE);
            if (!rs.inRaid || sp.getBlockY() >= 0) {
                continue;
            }
            // 幽暗菌毯: drifting spore motes in the dark around the crew.
            if (rs.affixes.contains(RaidAffix.GLOOM_MYCELIUM.id) && now % 30 == 0) {
                raid.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR,
                        sp.getX(), sp.getY() + 1.2, sp.getZ(), 5, 2.5, 0.8, 2.5, 0.01);
            }
            // 矿脉共鸣: nearby ore glints through the stone (sampled scan, cheap).
            if (rs.affixes.contains(RaidAffix.ORE_RESONANCE.id) && (now + sp.getId()) % 60 == 0) {
                glintOres(raid, sp);
            }
        }
    }

    /** Sample-scan a box around the player for ores and sparkle at up to three of them. */
    private static void glintOres(ServerLevel raid, ServerPlayer sp) {
        RandomSource rand = raid.getRandom();
        BlockPos base = sp.blockPosition();
        int found = 0;
        for (int i = 0; i < 90 && found < 3; i++) {
            BlockPos p = base.offset(rand.nextInt(17) - 8, rand.nextInt(13) - 6, rand.nextInt(17) - 8);
            if (isOre(raid.getBlockState(p))) {
                raid.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
                found++;
            }
        }
    }

    // ---------- helpers ----------

    /** Any vanilla ore tag (stone or deepslate variants both carry the tags). */
    private static boolean isOre(BlockState state) {
        for (net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag
                : com.srqingchen.eden.season.SeasonTracker.ORE_TAGS) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRaidLevel(ServerLevel level) {
        var key = level.dimension();
        return key.equals(EdenDimensions.RAID_OVERWORLD) || key.equals(EdenDimensions.RAID_NETHER)
                || key.equals(EdenDimensions.RAID_END);
    }

    /** Does any player in this level currently carry the given affix? */
    private static boolean hasAnyAffix(ServerLevel level, RaidAffix affix) {
        for (ServerPlayer sp : level.players()) {
            if (sp.getData(EdenAttachments.RAID_STATE).affixes.contains(affix.id)) {
                return true;
            }
        }
        return false;
    }
}
