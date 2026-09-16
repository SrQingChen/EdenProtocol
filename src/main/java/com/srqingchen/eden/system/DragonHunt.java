package com.srqingchen.eden.system;

import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;

/**
 * The polluted End's dragon hunt (§2 raid_end 打龙). A fresh end expedition seeds ONE buffed, named
 * EnderDragon over the central island; it is the expedition's apex objective. Slaying it pays the
 * richest bounty in the game (crystals / essence / a guaranteed card pack), knocks a full 5% off the
 * shared campaign pollution and writes a chronicle highlight. Vanilla's DragonBattle manager only
 * exists for Level.END, so the dragon is spawned and tracked manually via a scoreboard tag.
 */
public final class DragonHunt {
    private DragonHunt() {}

    /** Scoreboard tag marking the expedition dragon. */
    public static final String DRAGON_TAG = "eden_polluted_dragon";
    /** Campaign pollution % removed for slaying the dragon - the single biggest lever. */
    public static final float POLLUTION_PER_DRAGON = 5.0f;

    /** Spawn the expedition dragon over the central island if no tagged dragon exists (fresh world). */
    public static void spawnDragonIfAbsent(ServerLevel end) {
        boolean present = !end.getEntities(EntityType.ENDER_DRAGON,
                e -> e.isAlive() && e.entityTags().contains(DRAGON_TAG)).isEmpty();
        if (present) {
            return;
        }
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(end, EntitySpawnReason.TRIGGERED);
        if (dragon == null) {
            return;
        }
        dragon.snapTo(0.5, 96.0, 0.5, 180.0f, 0.0f);
        buff(dragon, Attributes.MAX_HEALTH, 100f);
        dragon.setHealth(dragon.getMaxHealth());
        buff(dragon, Attributes.ARMOR, 6f);
        buff(dragon, Attributes.ARMOR_TOUGHNESS, 4f);
        dragon.setCustomName(net.minecraft.network.chat.Component.translatable("eden.mob.polluted_dragon"));
        dragon.setPersistenceRequired();
        dragon.addTag(DRAGON_TAG);
        end.addFreshEntity(dragon);
        end.playSound(null, BlockPos.ZERO, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.2f, 0.7f);
        com.srqingchen.eden.EdenProtocol.LOGGER.info("[Eden] polluted dragon spawned over the end island");
    }

    private static void buff(LivingEntity mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                             double amount) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                    Identifier.fromNamespaceAndPath("eden", "dragon"), amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Dragon slain: campaign pollution down, server-wide broadcast, chronicle highlight. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)
                || !dragon.entityTags().contains(DRAGON_TAG)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        MinecraftServer server = dragon.level().getServer();
        if (server == null) {
            return;
        }
        CampaignData campaign = CampaignData.get(server);
        campaign.addDragonSlain();
        CampaignSystem.reducePollution(server, POLLUTION_PER_DRAGON, killer,
                "eden.chronicle.entry.dragon", campaign.dragonsSlain());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EdenMessages.send(p, Type.SPECIAL, "eden.msg.dragon_slain", killer.getName(), POLLUTION_PER_DRAGON);
        }
    }

    /** Dragon bounty on top of vanilla drops. */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)
                || !dragon.entityTags().contains(DRAGON_TAG)) {
            return;
        }
        ServerLevel level = (ServerLevel) dragon.level();
        var random = dragon.getRandom();
        drop(level, dragon, EdenItems.TAINT_CRYSTAL.get(), 12 + random.nextInt(8));
        drop(level, dragon, EdenItems.ESSENCE.get(), 16 + random.nextInt(10));
        drop(level, dragon, EdenItems.RELIC_SHARD.get(), 4 + random.nextInt(4));
        drop(level, dragon, EdenItems.SAMPLE_MINERAL.get(), 3 + random.nextInt(3));
        drop(level, dragon, EdenItems.CARD_PACK.get(), 1 + random.nextInt(2));
    }

    private static void drop(ServerLevel level, LivingEntity at,
                             net.minecraft.world.item.Item item, int count) {
        if (count <= 0) {
            return;
        }
        level.addFreshEntity(new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(),
                new ItemStack(item, count)));
    }
}
