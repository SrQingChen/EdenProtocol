package com.srqingchen.eden.system;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The §19.5 raid gamble items:
 * <ul>
 *   <li><b>猎杀者信标·一级</b> - consumed on raid entry; a short while later a far-away SUPER-champed
 *       hunter (a wither skeleton with stacked transient attributes, entity tag {@code eden_hunter_t1})
 *       spawns and stalks the crew. Killing it drops a rich bounty (taint crystals, essence, relic shards,
 *       a chance at a card pack). More tiers later.</li>
 *   <li><b>贪婪之尾</b> - consumed on raid entry; the whole crew pays -5% move speed for the raid, and
 *       the carrier's flags go up: on a SUCCESSFUL extraction their settlement pays x2; on failure their
 *       keep-ratio is halved.</li>
 *   <li><b>终古遗物</b> - earned by in-raid challenge feats (Wither at <=5% health, Elder Guardian at
 *       <=5% health, Warden taken down solo), announced server-wide, kept through success AND failure,
 *       and consumed when unlocking LARGE talent nodes.</li>
 * </ul>
 */
public final class RaidGambitSystem {
    private RaidGambitSystem() {}

    /** Scoreboard tag marking a spawned hunter (survives save/load with the entity). */
    public static final String HUNTER_TAG = "eden_hunter_t1";
    /** Delay before the hunter materializes (lets the fresh world finish loading around the crew). */
    private static final int HUNTER_DELAY_TICKS = 100;
    /** Hunter spawn distance from the summoning player, in blocks. */
    private static final int HUNTER_MIN_DIST = 40;
    private static final int HUNTER_MAX_DIST = 64;

    /** Pending hunter spawns: player UUID -> ticks remaining. */
    private static final Map<UUID, Integer> PENDING_HUNTERS = new HashMap<>();

    // ---------- raid entry (called from RaidService after the teleport) ----------

    /** Consume any carried gamble items on entering the raid world. */
    public static void consumeOnRaidStart(ServerPlayer sp) {
        if (consumeOne(sp, EdenItems.HUNTER_BEACON_T1.get())) {
            PENDING_HUNTERS.put(sp.getUUID(), HUNTER_DELAY_TICKS);
            EdenMessages.send(sp, Type.WARNING, "eden.msg.hunter_armed");
        }
        if (consumeOne(sp, EdenItems.GREED_TAIL.get())) {
            RaidState state = sp.getData(EdenAttachments.RAID_STATE);
            state.greedTail = true;
            applyGreedTailSlow(sp);
            // the tail drags the whole crew down a little
            for (Player p : sp.level().players()) {
                if (p instanceof ServerPlayer mate && mate != sp
                        && mate.getData(EdenAttachments.RAID_STATE).inRaid) {
                    applyGreedTailSlow(mate);
                }
            }
            EdenMessages.send(sp, Type.SPECIAL, "eden.msg.greed_tail_on");
        }
    }

    /** Later crewmates joining a raid whose tail is already active inherit the -5% slow. */
    public static void inheritGreedTail(ServerPlayer joining, ServerLevel raid) {
        for (Player p : raid.players()) {
            if (p instanceof ServerPlayer mate && mate != joining
                    && mate.getData(EdenAttachments.RAID_STATE).greedTail) {
                applyGreedTailSlow(joining);
                EdenMessages.send(joining, Type.WARNING, "eden.msg.greed_tail_inherit");
                return;
            }
        }
    }

    private static void applyGreedTailSlow(ServerPlayer sp) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            Identifier id = Identifier.fromNamespaceAndPath("eden", "greed_tail");
            speed.removeModifier(id);
            speed.addTransientModifier(new AttributeModifier(id, -0.05,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static boolean consumeOne(ServerPlayer sp, Item item) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack stack = sp.getInventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ---------- hunter spawn + bounty ----------

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_HUNTERS.isEmpty()) {
            return;
        }
        List<UUID> ready = new ArrayList<>();
        PENDING_HUNTERS.replaceAll((k, v) -> v - 1);
        PENDING_HUNTERS.forEach((uuid, ticks) -> {
            if (ticks <= 0) {
                ready.add(uuid);
            }
        });
        for (UUID uuid : ready) {
            PENDING_HUNTERS.remove(uuid);
            ServerPlayer sp = event.getServer().getPlayerList().getPlayer(uuid);
            if (sp != null && sp.getData(EdenAttachments.RAID_STATE).inRaid) {
                spawnHunter(sp);
            }
        }
    }

    /** Spawn the tier-1 hunter: a far-away wither skeleton with stacked transient attributes. */
    private static void spawnHunter(ServerPlayer sp) {
        ServerLevel level = (ServerLevel) sp.level();
        double angle = sp.getRandom().nextDouble() * Math.PI * 2;
        int dist = HUNTER_MIN_DIST + sp.getRandom().nextInt(HUNTER_MAX_DIST - HUNTER_MIN_DIST + 1);
        int x = sp.blockPosition().getX() + (int) (Math.cos(angle) * dist);
        int z = sp.blockPosition().getZ() + (int) (Math.sin(angle) * dist);
        BlockPos ground = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z);

        WitherSkeleton hunter = EntityType.WITHER_SKELETON.create(level, EntitySpawnReason.TRIGGERED);
        if (hunter == null) {
            return;
        }
        hunter.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
                sp.getRandom().nextFloat() * 360f, 0f);
        buff(hunter, Attributes.MAX_HEALTH, 60f, AttributeModifier.Operation.ADD_VALUE);
        hunter.setHealth(hunter.getMaxHealth());
        buff(hunter, Attributes.ATTACK_DAMAGE, 14f, AttributeModifier.Operation.ADD_VALUE);
        buff(hunter, Attributes.ARMOR, 10f, AttributeModifier.Operation.ADD_VALUE);
        buff(hunter, Attributes.ARMOR_TOUGHNESS, 8f, AttributeModifier.Operation.ADD_VALUE);
        buff(hunter, Attributes.MOVEMENT_SPEED, 0.25f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        buff(hunter, Attributes.KNOCKBACK_RESISTANCE, 1.0f, AttributeModifier.Operation.ADD_VALUE);
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        hunter.setCustomName(Component.translatable("eden.mob.hunter_t1"));
        hunter.setPersistenceRequired();
        hunter.addTag(HUNTER_TAG);
        hunter.setTarget(sp);
        level.addFreshEntity(hunter);

        EdenMessages.send(sp, Type.DANGER, "eden.msg.hunter_spawned", dist);
        MinecraftServer server = level.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() == level && p != sp) {
                    EdenMessages.send(p, Type.WARNING, "eden.msg.hunter_spawned_team", sp.getName());
                }
            }
        }
    }

    private static void buff(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                             double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                    Identifier.fromNamespaceAndPath("eden", "hunter_t1"), amount, op));
        }
    }

    /** Hunter bounty on top of its vanilla drops. */
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.entityTags().contains(HUNTER_TAG)) {
            return;
        }
        ServerLevel level = (ServerLevel) entity.level();
        var random = entity.getRandom();
        addDrop(event, level, entity, EdenItems.TAINT_CRYSTAL.get(), 5 + random.nextInt(5));
        addDrop(event, level, entity, EdenItems.ESSENCE.get(), 8 + random.nextInt(7));
        addDrop(event, level, entity, EdenItems.RELIC_SHARD.get(), 2 + random.nextInt(3));
        if (random.nextFloat() < 0.4f) {
            addDrop(event, level, entity, EdenItems.CARD_PACK.get(), 1);
        }
    }

    private static void addDrop(LivingDropsEvent event, ServerLevel level, LivingEntity at,
                                Item item, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        ItemEntity drop = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
        event.getDrops().add(drop);
    }

    // ---------- ancient relic challenge feats ----------

    /** Challenge-feat kills that award an ancient relic (kept through success AND failure). */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        RaidState state = killer.getData(EdenAttachments.RAID_STATE);
        if (!state.inRaid) {
            return;
        }
        LivingEntity target = event.getEntity();
        Item reward = null;
        String featKey = null;
        boolean lowHp = killer.getHealth() <= killer.getMaxHealth() * 0.05f;
        if (target.getType() == EntityType.WITHER && lowHp) {
            reward = EdenItems.RELIC_WRAITH.get();
            featKey = "eden.msg.relic_feat.wraith";
        } else if (target.getType() == EntityType.ELDER_GUARDIAN && lowHp) {
            reward = EdenItems.RELIC_DEEP.get();
            featKey = "eden.msg.relic_feat.deep";
        } else if (target.getType() == EntityType.WARDEN && isSolo(killer)) {
            reward = EdenItems.RELIC_STALKER.get();
            featKey = "eden.msg.relic_feat.stalker";
        }
        if (reward == null) {
            return;
        }
        ItemStack stack = new ItemStack(reward);
        if (!killer.getInventory().add(stack)) {
            killer.drop(stack, false);
        }
        killer.level().playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS,
                0.8f, 1.0f);
        MinecraftServer server = killer.level().getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                EdenMessages.send(p, Type.SPECIAL, "eden.msg.relic_earned",
                        killer.getName(), Component.translatable(featKey), stack.getHoverName());
            }
        }
    }

    /** True when no other player stands within 16 blocks - the "solo" condition. */
    private static boolean isSolo(ServerPlayer killer) {
        for (Player p : killer.level().players()) {
            if (p != killer && p.isAlive() && p.distanceToSqr(killer) <= 16.0 * 16.0) {
                return false;
            }
        }
        return true;
    }

    // ---------- relic consumption for LARGE talent nodes ----------

    /** True when the player carries any ancient relic. */
    public static boolean hasRelic(ServerPlayer sp) {
        return findRelicSlot(sp) >= 0;
    }

    /** Consume ONE ancient relic; returns false when none is carried. */
    public static boolean consumeRelic(ServerPlayer sp) {
        int slot = findRelicSlot(sp);
        if (slot < 0) {
            return false;
        }
        sp.getInventory().getItem(slot).shrink(1);
        return true;
    }

    private static int findRelicSlot(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack stack = sp.getInventory().getItem(i);
            if (stack.is(EdenItems.RELIC_WRAITH.get()) || stack.is(EdenItems.RELIC_DEEP.get())
                    || stack.is(EdenItems.RELIC_STALKER.get())) {
                return i;
            }
        }
        return -1;
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PENDING_HUNTERS.remove(sp.getUUID());
        }
    }
}
