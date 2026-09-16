package com.srqingchen.eden.talent;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.attachment.TalentData;
import com.srqingchen.eden.data.CampaignData;
import com.srqingchen.eden.network.TalentSyncPayload;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.system.RaidGambitSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Server-authoritative class / talent logic (P2). Applies the ACTIVE unlocked attribute nodes as transient modifiers
 * (scaled by rank), and runs the three meta operations: convert supply points -&gt; talent points, choose/switch class,
 * and unlock (rank up) a node.
 *
 * <p>Only "universal + the current class" trees are active (see {@link TalentData}); a class switch just changes which
 * class tree counts, keeping every tree's ranks stored. Transient modifiers do NOT persist and are dropped on respawn /
 * dimension change / relog, so {@link #recompute} is re-run from those player events (registered in {@code EdenProtocol}).
 */
public final class TalentSystem {
    private TalentSystem() {}

    /** Supply points spent per talent point - the one-way sink that balances the shared pool. */
    public static final int SUPPLY_PER_TP = 100;

    /** Attribute registry name (a node's {@code effectId}) -&gt; Holder, for the attribute nodes. */
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = Map.of(
            "minecraft:max_health", Attributes.MAX_HEALTH,
            "minecraft:armor", Attributes.ARMOR,
            "minecraft:movement_speed", Attributes.MOVEMENT_SPEED,
            "minecraft:attack_damage", Attributes.ATTACK_DAMAGE,
            "minecraft:luck", Attributes.LUCK,
            "minecraft:jump_strength", Attributes.JUMP_STRENGTH);

    private static Identifier modifierId(String nodeId) {
        return Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "talent/" + nodeId);
    }

    // ---------- talent-point accounting (cost is per rank) ----------

    private static int treeCost(Map<String, Integer> ranks) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            TalentNode n = TalentNodes.get(e.getKey());
            if (n != null && e.getValue() != null) sum += n.cost() * Math.max(0, e.getValue());
        }
        return sum;
    }

    /** TP committed to the always-active universal tree. */
    public static int universalCost(TalentData d) {
        return treeCost(d.universal);
    }

    /** TP committed to one class tree (only the current class counts against the budget). */
    public static int classCost(TalentData d, String cls) {
        Map<String, Integer> m = d.classTrees.get(cls);
        return m == null ? 0 : treeCost(m);
    }

    /** Spendable TP right now = total - universal - current class (never negative). */
    public static int available(TalentData d) {
        int spent = universalCost(d) + (d.currentClass.isEmpty() ? 0 : classCost(d, d.currentClass));
        return Math.max(0, d.totalTP - spent);
    }

    // ---------- operations ----------

    /** Validate + rank up one node by 1 (universal or current-class tree, prereqs lit, affordable, under maxRank). */
    public static boolean unlockNode(ServerPlayer p, String nodeId) {
        TalentData d = p.getData(EdenAttachments.TALENT_DATA);
        TalentNode n = TalentNodes.get(nodeId);
        if (n == null) return false;
        String tree = n.tree();
        if (!tree.isEmpty() && !tree.equals(d.currentClass)) return false;   // only universal or current class
        Map<String, Integer> ranks = d.ranksOf(tree);
        int cur = ranks.getOrDefault(nodeId, 0);
        if (cur >= n.maxRank()) return false;                                // already maxed
        for (String pre : n.prereqs()) {
            if (ranks.getOrDefault(pre, 0) <= 0) {
                EdenMessages.overlay(p, Type.WARNING, "eden.talent.msg.prereq");
                return false;
            }
        }
        if (n.cost() > available(d)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.talent.msg.no_tp");
            return false;
        }
        // LARGE keystones also consume one ancient relic (§19.5) - the deliberate end-of-tree gate.
        if (n.tier() == TalentNode.Tier.LARGE && !RaidGambitSystem.consumeRelic(p)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.talent.msg.need_relic");
            return false;
        }
        ranks.put(nodeId, cur + 1);
        recompute(p);
        syncTo(p);
        if (n.maxRank() > 1) {
            EdenMessages.overlay(p, Type.SUCCESS, "eden.talent.msg.rank_up",
                    Component.translatable(n.nameKey()), cur + 1, n.maxRank());
        } else {
            EdenMessages.overlay(p, Type.SUCCESS, "eden.talent.msg.unlocked", Component.translatable(n.nameKey()));
        }
        return true;
    }

    /** Choose / switch the active class tree. Keeps every tree's ranks; only re-runs which is active. */
    public static void chooseClass(ServerPlayer p, ClassId c) {
        TalentData d = p.getData(EdenAttachments.TALENT_DATA);
        d.setCurrentClass(c);
        recompute(p);
        syncTo(p);
        if (c != null) {
            EdenMessages.overlay(p, Type.SUCCESS, "eden.talent.msg.class_set", Component.translatable(c.nameKey()));
        }
    }

    /** Spend supply points from the shared campaign pool to buy talent points (one-way). */
    public static boolean convert(ServerPlayer p, int tpAmount) {
        if (tpAmount <= 0) return false;
        MinecraftServer server = p.level().getServer();
        if (server == null) return false;
        int cost = tpAmount * SUPPLY_PER_TP;
        CampaignData campaign = CampaignData.get(server);
        if (!campaign.spendSupplyPoints(cost)) {
            EdenMessages.overlay(p, Type.WARNING, "eden.talent.msg.no_supply", cost, campaign.getSupplyPoints());
            return false;
        }
        p.getData(EdenAttachments.TALENT_DATA).addTP(tpAmount);
        syncTo(p);
        EdenMessages.overlay(p, Type.SUCCESS, "eden.talent.msg.converted", tpAmount, cost);
        return true;
    }

    // ---------- attribute recompute ----------

    /** Re-derive every attribute modifier from scratch: clear all talent modifiers, re-add active ones scaled by rank. */
    public static void recompute(ServerPlayer p) {
        TalentData d = p.getData(EdenAttachments.TALENT_DATA);
        for (TalentNode n : TalentNodes.all()) {
            if (!n.isAttribute()) continue;
            Holder<Attribute> attr = ATTRIBUTES.get(n.effectId());
            if (attr == null) continue;
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            Identifier mid = modifierId(n.id());
            if (inst.hasModifier(mid)) inst.removeModifier(mid);
            int rank = activeRank(d, n);
            if (rank > 0) {
                AttributeModifier.Operation op = n.multiplicative()
                        ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                        : AttributeModifier.Operation.ADD_VALUE;
                inst.addTransientModifier(new AttributeModifier(mid, n.effectValue() * rank, op));
            }
        }
        applyAdaptive(p, d);
    }

    private static final Identifier ADAPTIVE_ID = Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "talent/adaptive_main");

    /**
     * 适配系 (uni_adapt_main + uni_adapt_core): grants a main attribute keyed to the CURRENT class -
     * engineer/medic +2 max health, prospector/scavenger +5% speed, vanguard +1 attack. The adaptive core
     * (uni_adapt_core) doubles it. One modifier, removed + re-added so class switches re-target it.
     */
    private static void applyAdaptive(ServerPlayer p, TalentData d) {
        for (Holder<Attribute> a : List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE)) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && inst.hasModifier(ADAPTIVE_ID)) {
                inst.removeModifier(ADAPTIVE_ID);
            }
        }
        if (!hasMech(p, "uni_adapt_main")) {
            return;
        }
        double mult = hasMech(p, "uni_adapt_core") ? 2.0 : 1.0;
        Holder<Attribute> attr;
        double amount;
        AttributeModifier.Operation op;
        switch (d.currentClass) {
            case "engineer", "medic" -> {
                attr = Attributes.MAX_HEALTH;
                amount = 2.0 * mult;
                op = AttributeModifier.Operation.ADD_VALUE;
            }
            case "prospector", "scavenger" -> {
                attr = Attributes.MOVEMENT_SPEED;
                amount = 0.05 * mult;
                op = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            }
            case "vanguard" -> {
                attr = Attributes.ATTACK_DAMAGE;
                amount = 1.0 * mult;
                op = AttributeModifier.Operation.ADD_VALUE;
            }
            default -> {
                return;   // no class chosen yet
            }
        }
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(ADAPTIVE_ID, amount, op));
        }
    }

    /** The node's rank IF its tree is active (universal always; a class tree only when it is the current class). */
    private static int activeRank(TalentData d, TalentNode n) {
        String tree = n.tree();
        if (tree.isEmpty()) return d.rankOf("", n.id());
        if (!tree.equals(d.currentClass)) return 0;
        return d.rankOf(tree, n.id());
    }

    /**
     * Whether a MECHANIC (non-attribute) node is unlocked in an ACTIVE tree (universal always; a class tree only when
     * it is the current class). Systems call this to gate talent-driven behaviour (erosion resistance, out-of-combat
     * regen, healing bonus, charge speed, supply conversion, ...). Attribute nodes are applied via {@link #recompute}.
     */
    public static boolean hasMech(ServerPlayer p, String nodeId) {
        TalentData d = p.getData(EdenAttachments.TALENT_DATA);
        TalentNode n = TalentNodes.get(nodeId);
        if (n == null) return false;
        String tree = n.tree();
        if (tree.isEmpty()) return d.rankOf("", nodeId) > 0;
        if (!tree.equals(d.currentClass)) return false;
        return d.rankOf(tree, nodeId) > 0;
    }

    // ---------- client sync ----------

    /** Push the full talent snapshot (+ shared supply balance) to the player's client. */
    public static void syncTo(ServerPlayer p) {
        TalentData d = p.getData(EdenAttachments.TALENT_DATA);
        List<String> uni = new ArrayList<>();
        d.universal.forEach((id, r) -> { if (r != null && r > 0) uni.add(id + ":" + r); });
        List<String> classNodes = new ArrayList<>();
        d.classTrees.forEach((cls, ranks) ->
                ranks.forEach((id, r) -> { if (r != null && r > 0) classNodes.add(cls + "|" + id + "|" + r); }));
        int supply = 0;
        MinecraftServer server = p.level().getServer();
        if (server != null) supply = CampaignData.get(server).getSupplyPoints();
        PacketDistributor.sendToPlayer(p,
                new TalentSyncPayload(d.totalTP, supply, d.currentClass, uni, classNodes));
    }

    // ---------- player events (re-apply transient modifiers, which do not survive these) ----------

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { recompute(p); syncTo(p); }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) recompute(p);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) recompute(p);
    }
}
