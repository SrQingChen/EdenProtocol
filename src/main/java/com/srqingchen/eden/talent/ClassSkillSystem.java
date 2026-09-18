package com.srqingchen.eden.talent;

import com.srqingchen.eden.attachment.RaidState;
import com.srqingchen.eden.attachment.TalentData;
import com.srqingchen.eden.block.ReturnPodBlockEntity;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side class ACTIVE skills, fired by the shared skill key (default R) -&gt; UseClassSkillPayload -&gt;
 * {@link #useSkill}. A skill is usable only when the player's ACTIVE class has lit its large "active" talent node
 * (pro_active / scv_active / ...); each class skill has its own cooldown. Passive "mech" nodes are applied inside the
 * systems they affect (charge speed, out-of-combat regen, ...), not here.
 *
 * <p>Implemented: Prospector <b>Ore Link</b> - scans a radius around the player and draws a straight coloured
 * dust-particle beam from each nearby ore / container to the player's eyes, colour keyed by material (redstone red,
 * diamond pale-blue, chest brown, ... per the design). The other four actives are stubbed (report "coming soon") and
 * land next; each returns {@code false} so it does NOT consume the cooldown while unimplemented.
 */
public final class ClassSkillSystem {
    private ClassSkillSystem() {}

    /** Active-skill cooldown in ticks, per class. */
    private static final Map<ClassId, Integer> COOLDOWN = Map.of(
            ClassId.PROSPECTOR, 20 * 20,           // Ore Link: 20s
            ClassId.SCAVENGER, 5 * 60 * 20,        // Discover: 5 min (per design)
            ClassId.ENGINEER, 60 * 20,
            ClassId.MEDIC, 30 * 20,
            ClassId.VANGUARD, 30 * 20);

    /** The large "active" talent node each class must light to unlock its skill. */
    private static final Map<ClassId, String> ACTIVE_NODE = Map.of(
            ClassId.ENGINEER, "eng_active",
            ClassId.PROSPECTOR, "pro_active",
            ClassId.MEDIC, "med_active",
            ClassId.VANGUARD, "van_active",
            ClassId.SCAVENGER, "scv_active");

    /** Player UUID -&gt; the world game-time at which their skill becomes usable again. */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    // ---------- dispatch ----------

    public static void useSkill(ServerPlayer sp) {
        TalentData d = sp.getData(EdenAttachments.TALENT_DATA);
        ClassId cls = d.activeClass();
        if (cls == null) {
            EdenMessages.overlay(sp, Type.INFO, "eden.msg.no_class_skill");
            return;
        }
        String node = ACTIVE_NODE.get(cls);
        if (node == null || d.rankOf(cls.id, node) <= 0) {
            EdenMessages.overlay(sp, Type.WARNING, "eden.skill.locked", Component.translatable(cls.nameKey()));
            return;
        }
        long now = sp.level().getGameTime();
        long until = COOLDOWN_UNTIL.getOrDefault(sp.getUUID(), 0L);
        if (now < until) {
            EdenMessages.overlay(sp, Type.WARNING, "eden.skill.cooldown", (until - now + 19) / 20);
            return;
        }
        boolean fired = switch (cls) {
            case PROSPECTOR -> oreLink(sp);
            case SCAVENGER -> discover(sp);
            case ENGINEER -> emergencyCharge(sp);
            case MEDIC -> triage(sp);
            case VANGUARD -> taunt(sp);
        };
        if (fired) {
            COOLDOWN_UNTIL.put(sp.getUUID(), now + COOLDOWN.getOrDefault(cls, 20 * 20));
        }
    }

    /** Drop a logged-out player's cooldown entry (wire to a PlayerLoggedOutEvent to avoid unbounded map growth). */
    public static void onLogout(UUID id) {
        COOLDOWN_UNTIL.remove(id);
    }

    // ---------- Prospector: Ore Link ----------

    private static final int ORE_RADIUS = 12;
    private static final int MAX_LINKS = 16;

    /** Valuable block -&gt; dust colour (0xRRGGBB), keyed by material. */
    private static final Map<Block, Integer> ORE_COLORS = Map.ofEntries(
            Map.entry(Blocks.DIAMOND_ORE, 0x5DECF4), Map.entry(Blocks.DEEPSLATE_DIAMOND_ORE, 0x5DECF4),
            Map.entry(Blocks.REDSTONE_ORE, 0xFF3B3B), Map.entry(Blocks.DEEPSLATE_REDSTONE_ORE, 0xFF3B3B),
            Map.entry(Blocks.EMERALD_ORE, 0x17DD62), Map.entry(Blocks.DEEPSLATE_EMERALD_ORE, 0x17DD62),
            Map.entry(Blocks.GOLD_ORE, 0xFFD700), Map.entry(Blocks.DEEPSLATE_GOLD_ORE, 0xFFD700),
            Map.entry(Blocks.NETHER_GOLD_ORE, 0xFFD700),
            Map.entry(Blocks.IRON_ORE, 0xD8C4A0), Map.entry(Blocks.DEEPSLATE_IRON_ORE, 0xD8C4A0),
            Map.entry(Blocks.COPPER_ORE, 0xFF8844), Map.entry(Blocks.DEEPSLATE_COPPER_ORE, 0xFF8844),
            Map.entry(Blocks.COAL_ORE, 0x4A4A4A), Map.entry(Blocks.DEEPSLATE_COAL_ORE, 0x4A4A4A),
            Map.entry(Blocks.LAPIS_ORE, 0x2E5EAA), Map.entry(Blocks.DEEPSLATE_LAPIS_ORE, 0x2E5EAA),
            Map.entry(Blocks.NETHER_QUARTZ_ORE, 0xF0EAE0),
            Map.entry(Blocks.ANCIENT_DEBRIS, 0x9B4A7B),
            Map.entry(Blocks.CHEST, 0x8B5A2B), Map.entry(Blocks.TRAPPED_CHEST, 0x8B5A2B),
            Map.entry(Blocks.ENDER_CHEST, 0x2E1A47), Map.entry(Blocks.BARREL, 0x8B5A2B),
            Map.entry(Blocks.GLOWSTONE, 0xFFDD88));

    private record Hit(BlockPos pos, int color, double dist) {}

    /** True if the block is one of the Ore Link's valuable blocks (the passive ore_vision glint reuses this). */
    public static boolean isOreLike(ServerLevel level, BlockPos pos) {
        return ORE_COLORS.containsKey(level.getBlockState(pos).getBlock());
    }

    /** Scan for nearby ores/containers and beam a coloured particle line from each to the player's eyes. */
    private static boolean oreLink(ServerPlayer sp) {
        // pro_scan talent widens the scan.
        int radius = ORE_RADIUS + (TalentSystem.hasMech(sp, "pro_scan") ? 4 : 0);
        ServerLevel level = sp.level();
        BlockPos c = sp.blockPosition();
        Vec3 eye = sp.getEyePosition();
        List<Hit> hits = new ArrayList<>();
        BlockPos min = c.offset(-radius, -radius, -radius);
        BlockPos max = c.offset(radius, radius, radius);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            Integer col = ORE_COLORS.get(level.getBlockState(p).getBlock());
            if (col != null) {
                hits.add(new Hit(p.immutable(), col, eye.distanceToSqr(Vec3.atCenterOf(p))));
            }
        }
        if (hits.isEmpty()) {
            EdenMessages.overlay(sp, Type.INFO, "eden.skill.ore_link.none");
            return false;                                   // nothing found: do not burn the cooldown
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        int n = Math.min(MAX_LINKS, hits.size());
        for (int i = 0; i < n; i++) {
            Hit h = hits.get(i);
            beam(level, Vec3.atCenterOf(h.pos), eye, h.color());
        }
        level.playSound(null, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.3f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.skill.ore_link.found", n);
        return true;
    }

    /** A straight coloured dust-particle beam from {@code from} to {@code to}. */
    private static void beam(ServerLevel level, Vec3 from, Vec3 to, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.1f);
        int steps = (int) Math.max(2, Math.min(28, from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            level.sendParticles(dust,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ---------- Scavenger: Discover ----------

    /**
     * Pull the ENTIRE contents of one random chest loot table (vanilla + every mod, since we enumerate the LootTable
     * registry). "Chest" = param set == CHEST, or an id under {@code chests/} (covers mods that skip the param set).
     * Items go to the inventory, spilling at the player's feet when full. Rolls with the player's own Luck. CD 5 min.
     */
    private static boolean discover(ServerPlayer sp) {
        ServerLevel level = sp.level();
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        // Chest tables live in the RELOADABLE registry layer - enumerate through the reloadable-registries
        // holder (server.registryAccess() does not carry loot tables and used to throw here).
        List<Holder.Reference<LootTable>> chests = new ArrayList<>();
        server.reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE)
                .listElements().forEach(ref -> {
                    LootTable lt = ref.value();
                    if (lt == null || lt == LootTable.EMPTY) return;
                    Identifier id = ref.key().identifier();
                    if (lt.getParamSet() == LootContextParamSets.CHEST || id.getPath().startsWith("chests/")) {
                        chests.add(ref);
                    }
                });
        if (chests.isEmpty()) {
            EdenMessages.overlay(sp, Type.INFO, "eden.skill.discover.none");
            return false;                                   // no chest tables: don't burn the cooldown
        }
        Holder.Reference<LootTable> chosen = chests.get(sp.getRandom().nextInt(chests.size()));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, sp.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, sp)
                .withLuck(sp.getLuck())
                .create(LootContextParamSets.CHEST);
        List<ItemStack> items = chosen.value().getRandomItems(params);
        String name = chosen.key().identifier().getPath();
        if (items.isEmpty()) {
            EdenMessages.overlay(sp, Type.INFO, "eden.skill.discover.empty", name);
            return true;
        }
        for (ItemStack s : items) {
            if (!sp.addItem(s)) sp.drop(s, false);           // inventory full: spill at feet
        }
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9f, 0.7f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.skill.discover.found", name, items.size());
        return true;
    }

    // ---------- Engineer: Emergency Charge ----------

    /** Charge injected into the pod pool per use (~12.5% of the 12000 pool). */
    private static final float EMERGENCY_CHARGE = 1500.0f;
    private static final double POD_FIND_RANGE = 24.0;

    /** Dump a burst of charge into a nearby active return pod (any charging pod near you, or the one you registered with). */
    private static boolean emergencyCharge(ServerPlayer sp) {
        ServerLevel level = sp.level();
        ReturnPodBlockEntity pod = ReturnPodBlockEntity.findChargingPodNear(level, sp.position(), POD_FIND_RANGE);
        if (pod == null) pod = ReturnPodBlockEntity.findPodFor(sp.getUUID());
        if (pod == null || !pod.isActive()) {
            EdenMessages.overlay(sp, Type.INFO, "eden.skill.charge.none");
            return false;                                   // no pod: don't burn the cooldown
        }
        pod.injectCharge(EMERGENCY_CHARGE);
        Vec3 c = Vec3.atCenterOf(pod.getBlockPos());
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.9, 0.9, 0.9, 0.2);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.2f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.skill.charge.done", (int) EMERGENCY_CHARGE);
        return true;
    }

    // ---------- Medic: Triage ----------

    private static final float TRIAGE_HEAL = 6.0f;          // 3 hearts
    private static final float TRIAGE_PURIFY = 25.0f;       // erosion removed
    private static final double TRIAGE_RANGE = 12.0;

    /** Heal + purge erosion for yourself and every teammate in range. */
    private static boolean triage(ServerPlayer sp) {
        ServerLevel level = sp.level();
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(sp.blockPosition()).inflate(TRIAGE_RANGE));
        int healed = 0;
        for (ServerPlayer p : near) {
            if (!p.isAlive()) continue;
            p.heal(TRIAGE_HEAL);
            RaidState rs = p.getData(EdenAttachments.RAID_STATE);
            if (rs.erosion > 0.0f) rs.erosion = Math.max(0.0f, rs.erosion - TRIAGE_PURIFY);
            EdenNetwork.syncTo(p);                          // refresh the erosion HUD
            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.4, 0.6, 0.4, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.4, 0.9, 0.4, 0.0);
            healed++;
        }
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.3f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.skill.triage.done", healed);
        return true;
    }

    // ---------- Vanguard: Taunt ----------

    private static final double TAUNT_RANGE = 16.0;

    /** Force every mob in range to target you, then gain an absorption shield + brief regen to survive it. */
    private static boolean taunt(ServerPlayer sp) {
        ServerLevel level = sp.level();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(sp.blockPosition()).inflate(TAUNT_RANGE));
        int n = 0;
        for (Mob m : mobs) {
            if (m.isAlive()) { m.setTarget(sp); n++; }
        }
        sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 3));    // 8 hearts of shield, 10s
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));  // 5s regen
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1.0, sp.getZ(), 20, 0.6, 0.9, 0.6, 0.1);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.5f);
        EdenMessages.overlay(sp, Type.SUCCESS, "eden.skill.taunt.done", n);
        return true;
    }
}
