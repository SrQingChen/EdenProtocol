package com.srqingchen.eden.system;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.block.TaintedSludgeBlock;
import com.srqingchen.eden.data.RaidWorldData;
import com.srqingchen.eden.dimension.EdenDimensions;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 浊潮生态 (tidal ecology, 功能清单 §10 v2): the taint as a LIVING ecosystem that fights back.
 * <ul>
 *   <li><b>Ecology lairs</b> - every fresh expedition seeds three lairs out in the taint: the 孢子母树
 *   spore mound, the 浊鳞巨物 sludge pond and the 深渊掘凿者 burrow. Closing in wakes a buffed, named
 *   boss; the kill pays a unique boss material (rift-altar ascend fuel), a card-pack chance, and
 *   -1% shared campaign pollution with a server-wide broadcast.</li>
 *   <li><b>污染积液</b> - sludge sources pooled around lairs and cores creep outward via
 *   {@link TaintedSludgeBlock} random ticks.</li>
 *   <li><b>浊雨 (acid rain)</b> - on HIGH-RISK difficulties only (purge/abyss/endgame) each run rolls one
 *   rain event: a 60s scanner/chat warning, then ~2 minutes of outdoor rain that stacks pollution and
 *   erosion on anyone the sky can see, and sprinkles fresh sludge. Indoors or underground is safe - the
 *   event is a routing decision, not a tax.</li>
 * </ul>
 */
public final class EcologySystem {
    private EcologySystem() {}

    /** Lair wake radius (players closer than this wake the boss, once per expedition). */
    public static final double WAKE_RADIUS = 16.0;
    /** Campaign pollution % removed per ecology boss slain. */
    public static final float POLLUTION_PER_BOSS = 1.0f;
    /** Difficulty ids that can roll the acid-rain event (high-risk only, per design confirmation). */
    private static final List<String> HIGH_RISK = List.of("purge", "abyss", "endgame");

    /** Scoreboard tags marking each ecology boss (rich, material-carrying drops). */
    public static final String TAG_SPORE = "eden_eco_spore";
    public static final String TAG_LEVIATHAN = "eden_eco_leviathan";
    public static final String TAG_EXCAVATOR = "eden_eco_excavator";

    /** Rain state machine, persisted per expedition: 0 idle, 1 scheduled, 2 warned, 3 active, 4 done. */
    private static final int RAIN_IDLE = 0, RAIN_SCHEDULED = 1, RAIN_WARNED = 2, RAIN_ACTIVE = 3, RAIN_DONE = 4;
    private static final int RAIN_MIN_DELAY = 20 * 60 * 4;    // earliest: 4 minutes into the run
    private static final int RAIN_MAX_DELAY = 20 * 60 * 9;
    private static final int RAIN_WARNING_TICKS = 20 * 60;    // 60s heads-up
    private static final int RAIN_DURATION_TICKS = 20 * 120;  // ~2 minutes of rain

    // ---------- seeding (called from RaidWorldFeatures.seedIfFresh) ----------

    public static void placeFeatures(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        placeSporeMound(raid, data, rand);
        placeLeviathanPond(raid, data, rand);
        placeExcavatorBurrow(raid, data, rand);
        // Pools of creeping sludge around every pollution core tie the ecology to the campaign lever.
        for (BlockPos core : data.cores()) {
            sprinkleSludge(raid, rand, core, 6, 3);
        }
    }

    private static boolean pickSpot(ServerLevel raid, RandomSource rand, int index, BlockPos[] out) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = (Math.PI * 2.0 * index) / 3.0 + rand.nextDouble() * 1.4;
            int dist = 110 + rand.nextInt(190);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            int y = raid.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos base = new BlockPos(x, y, z);
            if (raid.getBlockState(base).canBeReplaced() && !raid.getBlockState(base.below()).isAir()) {
                out[0] = base;
                return true;
            }
        }
        return false;
    }

    /** 孢子母树 lair: a mycelium mound with a mushroom-stem "tree" and a sludge ring. */
    private static void placeSporeMound(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        BlockPos[] spot = new BlockPos[1];
        if (!pickSpot(raid, rand, 0, spot)) {
            return;
        }
        BlockPos base = spot[0];
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 0, 3))) {
            if (p.distSqr(base) <= 9 && raid.getBlockState(p).canBeReplaced()) {
                raid.setBlock(p.immutable(), Blocks.MYCELIUM.defaultBlockState(), 3);
            }
        }
        for (int h = 1; h <= 3; h++) {
            raid.setBlock(base.above(h), Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
        raid.setBlock(base.above(4), Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), 3);
        sprinkleSludge(raid, rand, base, 8, 4);
        data.addLair(base, RaidWorldData.LAIR_SPORE);
    }

    /** 浊鳞巨物 lair: a prismarine-rimmed sludge pond. */
    private static void placeLeviathanPond(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        BlockPos[] spot = new BlockPos[1];
        if (!pickSpot(raid, rand, 1, spot)) {
            return;
        }
        BlockPos base = spot[0];
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 0, 3))) {
            int d = Math.abs(p.getX() - base.getX()) + Math.abs(p.getZ() - base.getZ());
            if (d == 5 && raid.getBlockState(p).canBeReplaced()) {
                raid.setBlock(p.immutable(), Blocks.PRISMARINE.defaultBlockState(), 3);
            } else if (d < 5 && raid.getBlockState(p).canBeReplaced()) {
                raid.setBlock(p.immutable(), TaintedSludgeBlock.source(), 3);
            }
        }
        data.addLair(base, RaidWorldData.LAIR_LEVIATHAN);
    }

    /** 深渊掘凿者 lair: a hidden underground pocket walled with tainted stone and studded with ore. */
    private static void placeExcavatorBurrow(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        BlockPos[] spot = new BlockPos[1];
        if (!pickSpot(raid, rand, 2, spot)) {
            return;
        }
        BlockPos floor = spot[0].below(12);
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-2, 0, -2), floor.offset(2, 4, 2))) {
            boolean shell = Math.abs(p.getX() - floor.getX()) == 2 || Math.abs(p.getY() - floor.getY()) == 4
                    || Math.abs(p.getZ() - floor.getZ()) == 2;
            raid.setBlock(p.immutable(), shell ? EdenBlocks.TAINTED_STONE.get().defaultBlockState()
                    : Blocks.CAVE_AIR.defaultBlockState(), 3);
        }
        // The hoard: a small rich vein the excavator guards.
        List<net.minecraft.world.level.block.Block> ores = List.of(
                Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.LAPIS_ORE);
        for (int i = 0; i < 7; i++) {
            BlockPos wall = floor.offset(rand.nextInt(5) - 2, rand.nextInt(5), rand.nextInt(5) - 2);
            if (raid.getBlockState(wall).is(EdenBlocks.TAINTED_STONE.get())) {
                raid.setBlock(wall, ores.get(rand.nextInt(ores.size())).defaultBlockState(), 3);
            }
        }
        raid.setBlock(floor, TaintedSludgeBlock.source(), 3);
        data.addLair(floor.above(), RaidWorldData.LAIR_EXCAVATOR);
    }

    /** Scatter a sludge puddle (sources + weaker edges) around a site. */
    private static void sprinkleSludge(ServerLevel raid, RandomSource rand, BlockPos centre, int count, int radius) {
        for (int i = 0; i < count; i++) {
            BlockPos p = centre.offset(rand.nextInt(radius * 2 + 1) - radius, 0, rand.nextInt(radius * 2 + 1) - radius);
            if (raid.getBlockState(p).canBeReplaced() && raid.getBlockState(p.below()).isSolidRender()) {
                raid.setBlock(p.immutable(), rand.nextInt(3) == 0 ? TaintedSludgeBlock.edge(1) : TaintedSludgeBlock.source(), 3);
            }
        }
    }

    // ---------- awakening + rain (server tick) ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel raid = server.getLevel(EdenDimensions.RAID_OVERWORLD);
        if (raid == null || raid.players().isEmpty()) {
            return;
        }
        RaidWorldData data = RaidWorldData.get(raid);
        tickLairWake(raid, data);
        tickRain(raid, data);
    }

    private static void tickLairWake(ServerLevel raid, RaidWorldData data) {
        for (ServerPlayer sp : raid.players()) {
            for (RaidWorldData.Lair lair : new ArrayList<>(data.lairs())) {
                if (lair.awake()) {
                    continue;
                }
                if (sp.blockPosition().distSqr(lair.pos()) <= WAKE_RADIUS * WAKE_RADIUS) {
                    wakeLair(raid, data, lair, sp);
                }
            }
        }
    }

    private static void wakeLair(ServerLevel raid, RaidWorldData data, RaidWorldData.Lair lair, ServerPlayer trigger) {
        data.markLairAwake(lair.pos());
        RandomSource rand = raid.getRandom();
        BlockPos at = lair.pos();
        switch (lair.kind()) {
            case RaidWorldData.LAIR_SPORE -> {
                Witch boss = EntityType.WITCH.create(raid, EntitySpawnReason.TRIGGERED);
                if (boss != null) {
                    buffAndSpawn(raid, boss, at, trigger, TAG_SPORE, "eden.mob.spore_matriarch",
                            100f, 4f, 6f, 0.06f);
                }
                // Spore adds: two poisoned bogged keep the melee honest.
                for (int i = 0; i < 2; i++) {
                    var add = EntityType.BOGGED.create(raid, EntitySpawnReason.TRIGGERED);
                    if (add != null) {
                        add.snapTo(at.getX() + rand.nextInt(5) - 2, at.getY() + 1, at.getZ() + rand.nextInt(5) - 2,
                                rand.nextFloat() * 360f, 0f);
                        add.setPersistenceRequired();
                        add.setTarget(trigger);
                        raid.addFreshEntity(add);
                    }
                }
            }
            case RaidWorldData.LAIR_LEVIATHAN -> {
                ElderGuardian boss = EntityType.ELDER_GUARDIAN.create(raid, EntitySpawnReason.TRIGGERED);
                if (boss != null) {
                    buffAndSpawn(raid, boss, at, trigger, TAG_LEVIATHAN, "eden.mob.tainted_leviathan",
                            80f, 8f, 2f, 0f);
                }
            }
            case RaidWorldData.LAIR_EXCAVATOR -> {
                Husk boss = EntityType.HUSK.create(raid, EntitySpawnReason.TRIGGERED);
                if (boss != null) {
                    buffAndSpawn(raid, boss, at, trigger, TAG_EXCAVATOR, "eden.mob.deep_excavator",
                            90f, 6f, 8f, 0.12f);
                    boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                            new ItemStack(Items.DIAMOND_PICKAXE));
                }
                for (int i = 0; i < 2; i++) {
                    Silverfish add = EntityType.SILVERFISH.create(raid, EntitySpawnReason.TRIGGERED);
                    if (add != null) {
                        add.snapTo(at.getX() + rand.nextInt(3) - 1, at.getY(), at.getZ() + rand.nextInt(3) - 1,
                                rand.nextFloat() * 360f, 0f);
                        add.setPersistenceRequired();
                        add.setTarget(trigger);
                        raid.addFreshEntity(add);
                    }
                }
            }
            default -> {
                return;
            }
        }
        raid.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                3, 0.5, 0.5, 0.5, 0.0);
        raid.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.7f);
        for (ServerPlayer sp : raid.players()) {
            EdenMessages.send(sp, Type.DANGER, "eden.eco.boss_wake");
        }
    }

    private static void buffAndSpawn(ServerLevel raid, Mob boss, BlockPos at, ServerPlayer trigger,
                                     String tag, String nameKey, float hp, float attack, float armor, float speed) {
        boss.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, raid.getRandom().nextFloat() * 360f, 0f);
        buff(boss, Attributes.MAX_HEALTH, hp, AttributeModifier.Operation.ADD_VALUE);
        boss.setHealth(boss.getMaxHealth());
        buff(boss, Attributes.ATTACK_DAMAGE, attack, AttributeModifier.Operation.ADD_VALUE);
        buff(boss, Attributes.ARMOR, armor, AttributeModifier.Operation.ADD_VALUE);
        buff(boss, Attributes.ARMOR_TOUGHNESS, armor * 0.5f, AttributeModifier.Operation.ADD_VALUE);
        if (speed > 0f) {
            buff(boss, Attributes.MOVEMENT_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        boss.setCustomName(Component.translatable(nameKey));
        boss.setPersistenceRequired();
        boss.addTag(tag);
        boss.setTarget(trigger);
        raid.addFreshEntity(boss);
    }

    private static void buff(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                             float amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                    Identifier("eden", "eco_boss_buff"),
                    amount, op));
        }
    }

    // ---------- acid rain (high-risk difficulties only) ----------

    private static void tickRain(ServerLevel raid, RaidWorldData data) {
        long now = raid.getGameTime();
        switch (data.rainState()) {
            case RAIN_IDLE -> {
                boolean highRisk = false;
                for (ServerPlayer sp : raid.players()) {
                    RaidState rs = sp.getData(com.srqingchen.eden.registry.EdenAttachments.RAID_STATE);
                    if (rs.inRaid && HIGH_RISK.contains(rs.difficulty)) {
                        highRisk = true;
                        break;
                    }
                }
                // S1 矿洞季 event tendency (batch B): the acid rain backs off while the cave season
                // runs (0.75 -> 0.45 roll chance) so the collapse event owns the weather slot.
                float chance = com.srqingchen.eden.data.CampaignData.get(raid.getServer()).seasonIndex() == 1
                        ? 0.45f : 0.75f;
                if (highRisk && raid.getRandom().nextFloat() < chance) {
                    data.setRain(now + RAIN_MIN_DELAY + raid.getRandom().nextInt(RAIN_MAX_DELAY - RAIN_MIN_DELAY));
                    data.setRainState(RAIN_SCHEDULED);
                } else if (highRisk) {
                    data.setRainState(RAIN_DONE);   // rolled, skipped this run
                }
            }
            case RAIN_SCHEDULED -> {
                if (now >= data.rainAt() - RAIN_WARNING_TICKS) {
                    data.setRainState(RAIN_WARNED);
                    for (ServerPlayer sp : raid.players()) {
                        EdenMessages.send(sp, Type.WARNING, "eden.eco.rain_warning");
                    }
                    raid.playSound(null, raid.players().get(0).blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 2.0f, 0.6f);
                }
            }
            case RAIN_WARNED -> {
                if (now >= data.rainAt()) {
                    data.setRainState(RAIN_ACTIVE);
                    data.setRainEnd(now + RAIN_DURATION_TICKS);
                    for (ServerPlayer sp : raid.players()) {
                        EdenMessages.send(sp, Type.DANGER, "eden.eco.rain_start");
                    }
                }
            }
            case RAIN_ACTIVE -> {
                if (now % 40 == 0) {
                    tickRainEffects(raid, raid.getRandom());
                }
                if (now >= data.rainEnd()) {
                    data.setRainState(RAIN_DONE);
                    for (ServerPlayer sp : raid.players()) {
                        EdenMessages.send(sp, Type.SUCCESS, "eden.eco.rain_end");
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Outdoor players (sky visible) take pollution + erosion; fresh sludge sprinkles near them. */
    private static void tickRainEffects(ServerLevel raid, RandomSource rand) {
        for (ServerPlayer sp : raid.players()) {
            if (!raid.canSeeSky(sp.blockPosition())) {
                // 浊雨守序 (批C): honouring the storm protocol quietly leans the meter toward 肃.
                if (raid.getServer() != null && sp.getData(com.srqingchen.eden.registry.EdenAttachments.RAID_STATE).inRaid) {
                    com.srqingchen.eden.data.CampaignData.get(raid.getServer()).addProtocolPurity(0.02f);
                }
                continue;   // indoors / underground is safe - routing, not taxing
            }
            sp.addEffect(new MobEffectInstance(com.srqingchen.eden.registry.EdenEffects.POLLUTION, 100, 0, true, true));
            RaidState rs = sp.getData(com.srqingchen.eden.registry.EdenAttachments.RAID_STATE);
            if (rs.inRaid) {
                rs.erosion = Math.min(com.srqingchen.eden.EdenConfig.EROSION_MAX.get(), rs.erosion + 0.4f);
                EdenNetwork.syncTo(sp);
            }
            raid.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, sp.getX(), sp.getY() + 2.2, sp.getZ(),
                    6, 0.8, 0.4, 0.8, 0.02);
        }
        // A little fresh sludge outdoors near a random raider.
        if (!raid.players().isEmpty() && rand.nextInt(3) == 0) {
            ServerPlayer any = raid.players().get(rand.nextInt(raid.players().size()));
            BlockPos p = any.blockPosition().offset(rand.nextInt(17) - 8, 0, rand.nextInt(17) - 8);
            if (raid.canSeeSky(p) && raid.getBlockState(p).canBeReplaced()
                    && raid.getBlockState(p.below()).isSolidRender()) {
                raid.setBlock(p.immutable(), TaintedSludgeBlock.edge(rand.nextInt(2)), 3);
            }
        }
    }

    // ---------- rewards ----------

    /** Boss slain: unique material + salvage + a card-pack chance drop (handled in onLivingDrops). */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel raid)) {
            return;
        }
        String tag = ecologyTag(mob);
        if (tag == null || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        MinecraftServer server = raid.getServer();
        CampaignSystem.reducePollution(server, POLLUTION_PER_BOSS, killer,
                "eden.chronicle.entry.eco_boss", 1);
        com.srqingchen.eden.data.CampaignData.get(server).addProtocolPurity(2.0f);   // 批C
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        String tag = ecologyTag(mob);
        if (tag == null || !(mob.level() instanceof ServerLevel raid)) {
            return;
        }
        var drops = event.getDrops();
        ItemStack material = new ItemStack(materialFor(tag));
        drops.add(new net.minecraft.world.entity.item.ItemEntity(raid, mob.getX(), mob.getY() + 0.5, mob.getZ(), material));
        if (raid.getRandom().nextFloat() < 0.4f) {
            drops.add(new net.minecraft.world.entity.item.ItemEntity(raid, mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    new ItemStack(EdenItems.CARD_PACK.get())));
        }
        drops.add(new net.minecraft.world.entity.item.ItemEntity(raid, mob.getX(), mob.getY() + 0.5, mob.getZ(),
                new ItemStack(EdenItems.TAINT_CRYSTAL.get(), 2 + raid.getRandom().nextInt(3))));
    }

    @javax.annotation.Nullable
    private static String ecologyTag(LivingEntity entity) {
        for (String t : List.of(TAG_SPORE, TAG_LEVIATHAN, TAG_EXCAVATOR)) {
            if (entity.entityTags().contains(t)) {
                return t;
            }
        }
        return null;
    }

    private static net.minecraft.world.item.Item materialFor(String tag) {
        if (tag.equals(TAG_SPORE)) {
            return EdenItems.SPORE_SAC.get();
        }
        if (tag.equals(TAG_LEVIATHAN)) {
            return EdenItems.TAINTED_SCALE.get();
        }
        return EdenItems.EXCAVATOR_CLAW.get();
    }

    /** Forge helper: is the rain currently hammering this raid world? (HUD flavour, scanner line.) */
    public static boolean rainActive(ServerLevel raid) {
        return RaidWorldData.get(raid).rainState() == RAIN_ACTIVE;
    }

    private static net.minecraft.resources.Identifier Identifier(String ns, String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(ns, path);
    }
}
