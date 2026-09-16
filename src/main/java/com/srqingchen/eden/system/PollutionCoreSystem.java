package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.data.RaidWorldData;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Pollution cores + guardians (功能清单 §10). Each fresh expedition seeds 2-3 cores out in the taint.
 * A core wakes when a player closes within {@link #WAKE_RADIUS} blocks: the 母巢守卫 boss (a buffed,
 * named ravager) plus two wither-skeleton wardens materialise. Breaking the core (hardness 8, real
 * effort) pays research salvage, lowers the SHARED campaign pollution (§14) and broadcasts server-wide -
 * cores are the campaign's main purification lever.
 */
public final class PollutionCoreSystem {
    private PollutionCoreSystem() {}

    /** Coles per fresh expedition. */
    private static final int CORE_MIN = 2;
    private static final int CORE_MAX = 3;
    private static final int CORE_MIN_DIST = 90;
    private static final int CORE_MAX_DIST = 320;
    /** A player inside this radius wakes the core's guardians (once per expedition). */
    public static final double WAKE_RADIUS = 20.0;
    /** Campaign pollution % removed per core destroyed - the campaign's main lever. */
    public static final float POLLUTION_PER_CORE = 2.0f;

    /** Scoreboard tag marking a core guardian (rich drops on kill). */
    public static final String GUARD_TAG = "eden_core_guard";

    // ---------- seeding (called from RaidWorldFeatures.seedIfFresh) ----------

    /** Place 2-3 cores, each as a small tainted bulwark ring around the core block. */
    public static void placeCores(ServerLevel raid, RaidWorldData data, RandomSource rand) {
        int count = CORE_MIN + rand.nextInt(CORE_MAX - CORE_MIN + 1);
        for (int i = 0; i < count; i++) {
            placeOneCore(raid, data, rand, i);
        }
    }

    private static void placeOneCore(ServerLevel raid, RaidWorldData data, RandomSource rand, int index) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = (Math.PI * 2.0 * index) / Math.max(1, CORE_MAX) + rand.nextDouble() * 1.2;
            int dist = CORE_MIN_DIST + rand.nextInt(CORE_MAX_DIST - CORE_MIN_DIST + 1);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            int y = raid.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos core = new BlockPos(x, y, z);
            if (!raid.getBlockState(core).canBeReplaced() || raid.getBlockState(core.below()).isAir()) {
                continue;
            }
            // A bulwark ring of tainted cobble makes the core read as a structure, not a stray block.
            for (BlockPos p : BlockPos.betweenClosed(core.offset(-2, 0, -2), core.offset(2, 1, 2))) {
                boolean edge = Math.abs(p.getX() - core.getX()) == 2 || Math.abs(p.getZ() - core.getZ()) == 2;
                if (p.equals(core)) {
                    continue;
                }
                if (edge && rand.nextFloat() < 0.45f && raid.getBlockState(p).canBeReplaced()) {
                    raid.setBlock(p.immutable(), EdenBlocks.TAINTED_COBBLESTONE.get().defaultBlockState(), 3);
                }
            }
            raid.setBlock(core, EdenBlocks.POLLUTION_CORE.get().defaultBlockState(), 3);
            data.addCore(core);
            return;
        }
    }

    // ---------- awakening (server tick) ----------

    /** Watch for players approaching a sleeping core and wake it exactly once. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel raid = server.getLevel(com.srqingchen.eden.dimension.EdenDimensions.RAID_OVERWORLD);
        if (raid == null || raid.players().isEmpty()) {
            return;
        }
        RaidWorldData data = RaidWorldData.get(raid);
        if (data.cores().isEmpty()) {
            return;
        }
        for (ServerPlayer sp : raid.players()) {
            for (BlockPos core : new ArrayList<>(data.cores())) {
                if (data.isCoreAwake(core) || !raid.getBlockState(core).is(EdenBlocks.POLLUTION_CORE.get())) {
                    continue;
                }
                if (sp.blockPosition().distSqr(core) <= WAKE_RADIUS * WAKE_RADIUS) {
                    wakeCore(raid, data, core, sp);
                }
            }
        }
    }

    /** Spawn the core boss + wardens and mark the core awake. */
    private static void wakeCore(ServerLevel raid, RaidWorldData data, BlockPos core, ServerPlayer trigger) {
        data.markCoreAwake(core);
        RandomSource rand = raid.getRandom();
        // Boss: 母巢守卫 - a ravager with stacked transient buffs and a name plate.
        Ravager boss = EntityType.RAVAGER.create(raid, EntitySpawnReason.TRIGGERED);
        if (boss != null) {
            BlockPos spawn = core.offset(rand.nextInt(5) - 2, 1, rand.nextInt(5) - 2);
            boss.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, rand.nextFloat() * 360f, 0f);
            buff(boss, Attributes.MAX_HEALTH, 40f, AttributeModifier.Operation.ADD_VALUE);
            boss.setHealth(boss.getMaxHealth());
            buff(boss, Attributes.ARMOR, 8f, AttributeModifier.Operation.ADD_VALUE);
            buff(boss, Attributes.ARMOR_TOUGHNESS, 6f, AttributeModifier.Operation.ADD_VALUE);
            buff(boss, Attributes.ATTACK_DAMAGE, 5f, AttributeModifier.Operation.ADD_VALUE);
            buff(boss, Attributes.MOVEMENT_SPEED, 0.10f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            boss.setCustomName(Component.translatable("eden.mob.core_guard"));
            boss.setPersistenceRequired();
            boss.addTag(GUARD_TAG);
            boss.setTarget(trigger);
            raid.addFreshEntity(boss);
        }
        // Two wither-skeleton wardens flank the boss.
        for (int i = 0; i < 2; i++) {
            WitherSkeleton warden = EntityType.WITHER_SKELETON.create(raid, EntitySpawnReason.TRIGGERED);
            if (warden == null) {
                continue;
            }
            BlockPos spawn = core.offset(rand.nextInt(7) - 3, 1, rand.nextInt(7) - 3);
            warden.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, rand.nextFloat() * 360f, 0f);
            buff(warden, Attributes.MAX_HEALTH, 20f, AttributeModifier.Operation.ADD_VALUE);
            warden.setHealth(warden.getMaxHealth());
            buff(warden, Attributes.ATTACK_DAMAGE, 6f, AttributeModifier.Operation.ADD_VALUE);
            buff(warden, Attributes.ARMOR, 6f, AttributeModifier.Operation.ADD_VALUE);
            warden.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            warden.setCustomName(Component.translatable("eden.mob.core_warden"));
            warden.setPersistenceRequired();
            warden.addTag(GUARD_TAG);
            warden.setTarget(trigger);
            raid.addFreshEntity(warden);
        }
        raid.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5,
                3, 0.5, 0.5, 0.5, 0.0);
        raid.playSound(null, core, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 0.6f);
        MinecraftServer server = raid.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() == raid) {
                    EdenMessages.send(p, Type.DANGER, "eden.msg.core_awake");
                }
            }
        }
    }

    private static void buff(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                             double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                    Identifier.fromNamespaceAndPath("eden", "core_guard"), amount, op));
        }
    }

    // ---------- destruction ----------

    /** Core broken: pay the reward, lower the campaign pollution, broadcast the deed. */
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getLevel() instanceof ServerLevel raid
                && event.getState().is(EdenBlocks.POLLUTION_CORE.get())
                && event.getPlayer() instanceof ServerPlayer sp) {
            rewardCore(raid, sp, event.getPos());
        }
    }

    private static void rewardCore(ServerLevel raid, ServerPlayer sp, BlockPos pos) {
        RandomSource rand = raid.getRandom();
        // Research salvage pops out of the shattered core.
        drop(raid, pos, EdenItems.SAMPLE_MINERAL.get(), 2 + rand.nextInt(3));
        drop(raid, pos, EdenItems.SAMPLE_FLORA.get(), 1 + rand.nextInt(2));
        drop(raid, pos, EdenItems.RELIC_SHARD.get(), 1 + rand.nextInt(2));
        drop(raid, pos, EdenItems.ESSENCE.get(), 4 + rand.nextInt(5));
        if (rand.nextFloat() < 0.4f) {
            drop(raid, pos, EdenItems.CARD_PACK.get(), 1);
        }
        // The campaign-level deed: shared pollution down, chronicle + server-wide broadcast.
        MinecraftServer server = raid.getServer();
        if (server != null) {
            CampaignData campaign = CampaignData.get(server);
            campaign.addCoreDestroyed();
            CampaignSystem.reducePollution(server, POLLUTION_PER_CORE, sp,
                    "eden.chronicle.entry.core", campaign.coresDestroyed());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                EdenMessages.send(p, Type.SPECIAL, "eden.msg.core_destroyed", sp.getName(), POLLUTION_PER_CORE);
            }
        }
        raid.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                60, 1.0, 1.0, 1.0, 0.15);
        raid.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    private static void drop(ServerLevel level, BlockPos at, Item item, int count) {
        if (count <= 0) {
            return;
        }
        level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                new ItemStack(item, count)));
    }

    /** Core-guardian bounty on top of vanilla drops. */
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.entityTags().contains(GUARD_TAG)) {
            return;
        }
        ServerLevel level = (ServerLevel) entity.level();
        var random = entity.getRandom();
        boolean boss = entity instanceof Ravager;
        addDrop(event, level, entity, EdenItems.TAINT_CRYSTAL.get(), 3 + random.nextInt(4));
        addDrop(event, level, entity, EdenItems.ESSENCE.get(), 4 + random.nextInt(4));
        addDrop(event, level, entity, EdenItems.SAMPLE_FAUNA.get(), 1 + random.nextInt(2));
        if (boss && random.nextFloat() < 0.5f) {
            addDrop(event, level, entity, EdenItems.CARD_PACK.get(), 1);
        }
    }

    private static void addDrop(LivingDropsEvent event, ServerLevel level, LivingEntity at,
                                Item item, int count) {
        if (count <= 0) {
            return;
        }
        event.getDrops().add(new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(),
                new ItemStack(item, count)));
    }
}
