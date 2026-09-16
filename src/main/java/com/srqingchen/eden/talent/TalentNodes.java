package com.srqingchen.eden.talent;

import com.srqingchen.eden.talent.TalentNode.Tier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The static talent-node registry (mirrors {@code ShopCatalog}'s code-driven catalog): every universal-branch and
 * class-tree node with its tier, cost, grid position, prerequisites and effect. This is the single source of truth the
 * map UI (P3) draws and the attribute recompute (P2) reads. Display names/descriptions are lang keys derived from the
 * id ({@link TalentNode#nameKey()} / {@link TalentNode#descKey()}).
 * <p><b>Single-unlock nodes in chains</b>: every node unlocks ONCE (maxRank 1); the "many points" feel comes from MANY
 * small nodes chained left -&gt; right (a small node leading to the next), not from re-clicking one node. Each branch is
 * a progression of small nodes capped by a medium/large keystone.
 * <p>Effect encoding: attribute nodes carry a vanilla attribute registry name in {@code effectId} (e.g.
 * {@code "minecraft:max_health"}) plus {@code effectValue} / {@code multiplicative}; passive mechanics use
 * {@code "mech.<key>"} and active skills {@code "skill.<key>"}, both resolved by the class-skill system later.
 * <p>Layout: per-tree grid coords. The universal tree is 6 branches (rows), each a left-&gt;right chain (x = depth);
 * each class tree is 2 lines (main row 0 / sub row 1) of 4 columns converging on a keystone + active.
 */
public final class TalentNodes {
    private TalentNodes() {}

    /** The universal tree's id (empty string - {@link TalentData} treats "" as the always-active universal tree). */
    public static final String UNIVERSAL = "";

    private static final Map<String, TalentNode> NODES = new LinkedHashMap<>();

    static {
        universal();
        engineer();
        prospector();
        medic();
        vanguard();
        scavenger();
    }

    // ---------- lookups ----------

    @Nullable
    public static TalentNode get(String id) {
        return NODES.get(id);
    }

    public static Collection<TalentNode> all() {
        return Collections.unmodifiableCollection(NODES.values());
    }

    /** All nodes of one tree ({@code ""} = universal, else a class id), in registration order. */
    public static List<TalentNode> ofTree(String tree) {
        List<TalentNode> out = new ArrayList<>();
        for (TalentNode n : NODES.values()) {
            if (n.tree().equals(tree)) {
                out.add(n);
            }
        }
        return out;
    }

    // ---------- builder helpers (every node is single-unlock: maxRank = 1) ----------

    private static TalentNode attr(String id, String tree, Tier tier, int cost, int x, int y,
                                   String attribute, float value, boolean mult, String... prereqs) {
        return put(new TalentNode(id, tree, tier, cost, 1, x, y, List.of(prereqs), attribute, value, mult));
    }

    private static TalentNode mech(String id, String tree, Tier tier, int cost, int x, int y,
                                   String key, String... prereqs) {
        return put(new TalentNode(id, tree, tier, cost, 1, x, y, List.of(prereqs), "mech." + key, 0f, false));
    }

    private static TalentNode skill(String id, String tree, Tier tier, int cost, int x, int y,
                                    String key, String... prereqs) {
        return put(new TalentNode(id, tree, tier, cost, 1, x, y, List.of(prereqs), "skill." + key, 0f, false));
    }

    private static TalentNode put(TalentNode n) {
        NODES.put(n.id(), n);
        return n;
    }

    // ---------- universal tree: 6 branches (rows), each a left -> right chain ----------

    private static void universal() {
        String t = UNIVERSAL;
        // 生存 survival (row 0): health chain -> armor -> regen -> once-per-raid totem
        attr("uni_surv_hp", t, Tier.SMALL, 1, 0, 0, "minecraft:max_health", 2f, false);
        attr("uni_surv_vigor", t, Tier.SMALL, 1, 1, 0, "minecraft:max_health", 2f, false, "uni_surv_hp");
        attr("uni_surv_armor", t, Tier.SMALL, 1, 2, 0, "minecraft:armor", 1f, false, "uni_surv_vigor");
        mech("uni_surv_regen", t, Tier.MEDIUM, 2, 3, 0, "out_of_combat_regen", "uni_surv_armor");
        mech("uni_surv_totem", t, Tier.LARGE, 3, 4, 0, "once_per_raid_totem", "uni_surv_regen");
        // 机动 mobility (row 1): speed chain -> jump -> fall reduction
        attr("uni_mob_speed", t, Tier.SMALL, 1, 0, 1, "minecraft:movement_speed", 0.05f, true);
        attr("uni_mob_agility", t, Tier.SMALL, 1, 1, 1, "minecraft:movement_speed", 0.05f, true, "uni_mob_speed");
        mech("uni_mob_jump", t, Tier.SMALL, 1, 2, 1, "jump_boost", "uni_mob_agility");
        mech("uni_mob_fall", t, Tier.MEDIUM, 2, 3, 1, "fall_reduction", "uni_mob_jump");
        // 采集 gathering (row 2): supply conversion -> luck -> salvage speed
        mech("uni_gath_supply", t, Tier.SMALL, 1, 0, 2, "supply_conversion_5");
        attr("uni_gath_haste", t, Tier.SMALL, 1, 1, 2, "minecraft:luck", 2f, false, "uni_gath_supply");
        mech("uni_gath_salvage", t, Tier.MEDIUM, 2, 2, 2, "salvage_speed", "uni_gath_haste");
        // 抗性 resistance (row 3): erosion resist -> armor -> pollution resist
        mech("uni_res_erosion", t, Tier.SMALL, 1, 0, 3, "erosion_resist");
        attr("uni_res_hardy", t, Tier.SMALL, 1, 1, 3, "minecraft:armor", 1f, false, "uni_res_erosion");
        mech("uni_res_pollution", t, Tier.MEDIUM, 2, 2, 3, "pollution_resist", "uni_res_hardy");
        // 协作 cooperation (row 4): revive bonus -> field medic -> shared shield
        mech("uni_coop_revive", t, Tier.SMALL, 1, 0, 4, "revive_ally_bonus");
        mech("uni_coop_medic", t, Tier.SMALL, 1, 1, 4, "revive_speed", "uni_coop_revive");
        mech("uni_coop_shield", t, Tier.MEDIUM, 2, 2, 4, "shared_shield", "uni_coop_medic");
        // 适配 adaptive (row 5): main-attr scaling -> focus -> adaptive core
        mech("uni_adapt_main", t, Tier.SMALL, 1, 0, 5, "adaptive_main_attr");
        mech("uni_adapt_focus", t, Tier.SMALL, 1, 1, 5, "adaptive_focus", "uni_adapt_main");
        mech("uni_adapt_core", t, Tier.LARGE, 3, 2, 5, "adaptive_core", "uni_adapt_focus");
    }

    // ---------- class trees: main line (row 0) + sub line (row 1), converging on keystone + active (col 3) ----------

    /** 工程 Engineer - main health / sub armor; charge + deploy passives; overload keystone; emergency-charge active. */
    private static void engineer() {
        String t = "engineer";
        attr("eng_hp", t, Tier.SMALL, 1, 0, 0, "minecraft:max_health", 2f, false);
        attr("eng_vigor", t, Tier.SMALL, 1, 1, 0, "minecraft:max_health", 2f, false, "eng_hp");
        mech("eng_charge", t, Tier.MEDIUM, 2, 2, 0, "charge_efficiency_15", "eng_vigor");
        mech("eng_overload", t, Tier.LARGE, 3, 3, 0, "overload_deploy", "eng_charge", "eng_deploy");
        attr("eng_armor", t, Tier.SMALL, 1, 0, 1, "minecraft:armor", 1f, false);
        attr("eng_plating", t, Tier.SMALL, 1, 1, 1, "minecraft:armor", 1f, false, "eng_armor");
        mech("eng_deploy", t, Tier.MEDIUM, 2, 2, 1, "fast_deploy", "eng_plating");
        skill("eng_active", t, Tier.LARGE, 3, 3, 1, "engineer_emergency_charge", "eng_overload");
    }

    /** 勘探 Prospector - main speed / sub luck; scan + jump passives; ore-vision keystone; ore-link active. */
    private static void prospector() {
        String t = "prospector";
        attr("pro_speed", t, Tier.SMALL, 1, 0, 0, "minecraft:movement_speed", 0.05f, true);
        attr("pro_agility", t, Tier.SMALL, 1, 1, 0, "minecraft:movement_speed", 0.05f, true, "pro_speed");
        mech("pro_scan", t, Tier.MEDIUM, 2, 2, 0, "scan_enhance", "pro_agility");
        mech("pro_vision", t, Tier.LARGE, 3, 3, 0, "ore_vision", "pro_scan", "pro_jump");
        attr("pro_luck", t, Tier.SMALL, 1, 0, 1, "minecraft:luck", 2f, false);
        attr("pro_fortune", t, Tier.SMALL, 1, 1, 1, "minecraft:luck", 2f, false, "pro_luck");
        mech("pro_jump", t, Tier.MEDIUM, 2, 2, 1, "jump_boost", "pro_fortune");
        skill("pro_active", t, Tier.LARGE, 3, 3, 1, "prospector_ore_link", "pro_vision");
    }

    /** 医疗 Medic - main health / sub speed; healing + erosion passives; field-medic keystone; triage active. */
    private static void medic() {
        String t = "medic";
        attr("med_hp", t, Tier.SMALL, 1, 0, 0, "minecraft:max_health", 2f, false);
        attr("med_vigor", t, Tier.SMALL, 1, 1, 0, "minecraft:max_health", 2f, false, "med_hp");
        mech("med_healbonus", t, Tier.MEDIUM, 2, 2, 0, "healing_bonus_25", "med_vigor");
        mech("med_field", t, Tier.LARGE, 3, 3, 0, "field_medic", "med_healbonus", "med_erosion");
        attr("med_speed", t, Tier.SMALL, 1, 0, 1, "minecraft:movement_speed", 0.05f, true);
        attr("med_alacrity", t, Tier.SMALL, 1, 1, 1, "minecraft:movement_speed", 0.05f, true, "med_speed");
        mech("med_erosion", t, Tier.MEDIUM, 2, 2, 1, "erosion_resist", "med_alacrity");
        skill("med_active", t, Tier.LARGE, 3, 3, 1, "medic_triage", "med_field");
    }

    /** 战斗 Vanguard - main attack / sub armor; regen + slayer passives; undying keystone; taunt active. */
    private static void vanguard() {
        String t = "vanguard";
        attr("van_attack", t, Tier.SMALL, 1, 0, 0, "minecraft:attack_damage", 1f, false);
        attr("van_might", t, Tier.SMALL, 1, 1, 0, "minecraft:attack_damage", 1f, false, "van_attack");
        mech("van_regen", t, Tier.MEDIUM, 2, 2, 0, "out_of_combat_regen", "van_might");
        mech("van_totem", t, Tier.LARGE, 3, 3, 0, "undying_totem", "van_regen", "van_slayer");
        attr("van_armor", t, Tier.SMALL, 1, 0, 1, "minecraft:armor", 1f, false);
        attr("van_bulwark", t, Tier.SMALL, 1, 1, 1, "minecraft:armor", 1f, false, "van_armor");
        mech("van_slayer", t, Tier.MEDIUM, 2, 2, 1, "kill_attack_stack", "van_bulwark");
        skill("van_active", t, Tier.LARGE, 3, 3, 1, "vanguard_taunt", "van_totem");
    }

    /** 拾荒 Scavenger - main speed / sub health; supply + loot passives; waste-to-wealth keystone; discovery active. */
    private static void scavenger() {
        String t = "scavenger";
        attr("scv_speed", t, Tier.SMALL, 1, 0, 0, "minecraft:movement_speed", 0.05f, true);
        attr("scv_gust", t, Tier.SMALL, 1, 1, 0, "minecraft:movement_speed", 0.05f, true, "scv_speed");
        mech("scv_supply", t, Tier.MEDIUM, 2, 2, 0, "supply_conversion_15", "scv_gust");
        mech("scv_convert", t, Tier.LARGE, 3, 3, 0, "waste_to_wealth", "scv_supply", "scv_loot");
        attr("scv_hp", t, Tier.SMALL, 1, 0, 1, "minecraft:max_health", 2f, false);
        attr("scv_vigor", t, Tier.SMALL, 1, 1, 1, "minecraft:max_health", 2f, false, "scv_hp");
        mech("scv_loot", t, Tier.MEDIUM, 2, 2, 1, "fast_looting", "scv_vigor");
        skill("scv_active", t, Tier.LARGE, 3, 3, 1, "scavenger_discover", "scv_convert");
    }
}
