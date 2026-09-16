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
 * small nodes chained outward, not from re-clicking one node.
 * <p><b>Effect encoding</b>: attribute nodes carry a vanilla attribute registry name in {@code effectId} (e.g.
 * {@code "minecraft:max_health"}) plus {@code effectValue} / {@code multiplicative}; passive mechanics use
 * {@code "mech.<key>"} and active skills {@code "skill.<key>"}, both resolved by the class-skill system later.
 * <p><b>Layout (2026-09-16 radial redesign)</b>: every tree is a MATRIX EXPANDING FROM ITS CENTRE - grid coords may be
 * negative and radiate around an origin node at (0,0). The universal tree is a six-spoke hexagon (survival N,
 * mobility NE, gathering SE, resistance S, cooperation SW, adaptation NW) with cross-link bridges on the inner ring;
 * each class tree is a compass cross (N/E/S/W arms) converging on the keystone + active at the far end. Per-node values
 * are deliberately SMALL (health +1, speed +3%...): with ~90 nodes the totals stay meaningful while every single click
 * is cheap.
 * <p><b>Save compatibility</b>: node ids are the persistence key (TalentData ranks) AND the mech keys systems query
 * ({@code TalentSystem.hasMech}), so existing ids are never renamed or removed - the redesign only moves coordinates,
 * lowers values and inserts new nodes.
 */
public final class TalentNodes {
    private TalentNodes() {}

    /** The universal tree's id (empty string - {@code TalentData} treats "" as the always-active universal tree). */
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

    // ---------- universal tree: origin + six spokes radiating outward + inner-ring bridges ----------
    //
    //        survival(N)      mobility(NE)
    //   adaptation(NW)  origin    gathering(SE)
    //     cooperation(SW)  resistance(S)
    //
    // Each spoke: small -> small -> small -> medium -> capstone (totem/core are relic-gated LARGE).
    // Bridges on ring 2 knit adjacent spokes together so the map reads as a matrix, not six lines.

    private static void universal() {
        String t = UNIVERSAL;
        // origin - the heart of the matrix, every ring-1 node grows out of it
        attr("uni_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:max_health", 1f, false);

        // survival (N): health chain -> regen -> once-per-raid totem
        attr("uni_surv_hp", t, Tier.SMALL, 1, 0, -1, "minecraft:max_health", 1f, false, "uni_origin");
        attr("uni_surv_vital", t, Tier.SMALL, 1, 0, -2, "minecraft:max_health", 1f, false, "uni_surv_hp");
        attr("uni_surv_vigor", t, Tier.SMALL, 1, 0, -3, "minecraft:max_health", 1f, false, "uni_surv_vital");
        mech("uni_surv_regen", t, Tier.MEDIUM, 2, 0, -4, "out_of_combat_regen", "uni_surv_vigor");
        mech("uni_surv_totem", t, Tier.LARGE, 3, 0, -5, "once_per_raid_totem", "uni_surv_regen");

        // mobility (NE): speed chain -> jump -> fall reduction -> gale capstone
        attr("uni_mob_speed", t, Tier.SMALL, 1, 1, -1, "minecraft:movement_speed", 0.03f, true, "uni_origin");
        attr("uni_mob_stride", t, Tier.SMALL, 1, 2, -2, "minecraft:movement_speed", 0.03f, true, "uni_mob_speed");
        mech("uni_mob_jump", t, Tier.SMALL, 1, 3, -3, "jump_boost", "uni_mob_stride");
        mech("uni_mob_fall", t, Tier.MEDIUM, 2, 4, -4, "fall_reduction", "uni_mob_jump");
        attr("uni_mob_gale", t, Tier.MEDIUM, 2, 5, -5, "minecraft:movement_speed", 0.04f, true, "uni_mob_fall");

        // gathering (SE): supply conversion -> luck chain -> salvage speed -> fortune capstone
        mech("uni_gath_supply", t, Tier.SMALL, 1, 1, 1, "supply_conversion_5", "uni_origin");
        attr("uni_gath_knowledge", t, Tier.SMALL, 1, 2, 2, "minecraft:luck", 1f, false, "uni_gath_supply");
        attr("uni_gath_haste", t, Tier.SMALL, 1, 3, 3, "minecraft:luck", 1f, false, "uni_gath_knowledge");
        mech("uni_gath_salvage", t, Tier.MEDIUM, 2, 4, 4, "salvage_speed", "uni_gath_haste");
        attr("uni_gath_fortune", t, Tier.MEDIUM, 2, 5, 5, "minecraft:luck", 2f, false, "uni_gath_salvage");

        // resistance (S): erosion resist -> armor chain -> pollution resist -> aegis capstone
        mech("uni_res_erosion", t, Tier.SMALL, 1, 0, 1, "erosion_resist", "uni_origin");
        attr("uni_res_scale", t, Tier.SMALL, 1, 0, 2, "minecraft:armor", 1f, false, "uni_res_erosion");
        attr("uni_res_hardy", t, Tier.SMALL, 1, 0, 3, "minecraft:armor", 1f, false, "uni_res_scale");
        mech("uni_res_pollution", t, Tier.MEDIUM, 2, 0, 4, "pollution_resist", "uni_res_hardy");
        attr("uni_res_aegis", t, Tier.MEDIUM, 2, 0, 5, "minecraft:armor", 2f, false, "uni_res_pollution");

        // cooperation (SW): revive chain -> shared shield -> unity capstone
        mech("uni_coop_revive", t, Tier.SMALL, 1, -1, 1, "revive_ally_bonus", "uni_origin");
        attr("uni_coop_kinship", t, Tier.SMALL, 1, -2, 2, "minecraft:max_health", 1f, false, "uni_coop_revive");
        mech("uni_coop_medic", t, Tier.SMALL, 1, -3, 3, "revive_speed", "uni_coop_kinship");
        mech("uni_coop_shield", t, Tier.MEDIUM, 2, -4, 4, "shared_shield", "uni_coop_medic");
        attr("uni_coop_unity", t, Tier.MEDIUM, 2, -5, 5, "minecraft:armor", 1f, false, "uni_coop_shield");

        // adaptation (NW): main-attr scaling -> focus -> adaptive core (relic-gated)
        mech("uni_adapt_main", t, Tier.SMALL, 1, -1, -1, "adaptive_main_attr", "uni_origin");
        attr("uni_adapt_track", t, Tier.SMALL, 1, -2, -2, "minecraft:movement_speed", 0.02f, true, "uni_adapt_main");
        mech("uni_adapt_focus", t, Tier.SMALL, 1, -3, -3, "adaptive_focus", "uni_adapt_track");
        mech("uni_adapt_core", t, Tier.LARGE, 3, -4, -4, "adaptive_core", "uni_adapt_focus");
        attr("uni_adapt_apex", t, Tier.MEDIUM, 2, -5, -5, "minecraft:attack_damage", 1f, false, "uni_adapt_core");

        // ring-2 bridges: knit adjacent spokes (each hangs off ONE neighbour, so both sides can reach it)
        attr("uni_lnk_hp_a", t, Tier.SMALL, 1, 1, -2, "minecraft:max_health", 1f, false, "uni_surv_hp");
        attr("uni_lnk_luck_a", t, Tier.SMALL, 1, 2, 0, "minecraft:luck", 1f, false, "uni_mob_speed");
        attr("uni_lnk_armor_a", t, Tier.SMALL, 1, 1, 2, "minecraft:armor", 1f, false, "uni_gath_supply");
        attr("uni_lnk_armor_b", t, Tier.SMALL, 1, -1, 2, "minecraft:armor", 1f, false, "uni_res_erosion");
        attr("uni_lnk_hp_b", t, Tier.SMALL, 1, -2, 0, "minecraft:max_health", 1f, false, "uni_coop_revive");
        attr("uni_lnk_luck_b", t, Tier.SMALL, 1, -1, -2, "minecraft:luck", 1f, false, "uni_adapt_main");
    }

    // ---------- class trees: origin core + compass arms, keystone west, active far south ----------

    /** 工程 Engineer - origin + health N / charge E / deploy S / overload W; emergency-charge active. */
    private static void engineer() {
        String t = "engineer";
        attr("eng_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:max_health", 1f, false);
        attr("eng_hp", t, Tier.SMALL, 1, 0, -1, "minecraft:max_health", 1f, false, "eng_origin");
        attr("eng_vigor", t, Tier.SMALL, 1, 0, -2, "minecraft:max_health", 1f, false, "eng_hp");
        attr("eng_battery", t, Tier.SMALL, 1, 2, 0, "minecraft:luck", 1f, false, "eng_origin");
        mech("eng_charge", t, Tier.MEDIUM, 2, 4, 0, "charge_efficiency_15", "eng_battery");
        attr("eng_armor", t, Tier.SMALL, 1, 0, 1, "minecraft:armor", 1f, false, "eng_origin");
        attr("eng_plating", t, Tier.SMALL, 1, 0, 2, "minecraft:armor", 1f, false, "eng_armor");
        mech("eng_deploy", t, Tier.MEDIUM, 2, 0, 3, "fast_deploy", "eng_plating");
        attr("eng_tools", t, Tier.SMALL, 1, -2, 0, "minecraft:movement_speed", 0.02f, true, "eng_origin");
        mech("eng_overload", t, Tier.LARGE, 3, -4, 0, "overload_deploy", "eng_tools", "eng_charge", "eng_deploy");
        skill("eng_active", t, Tier.LARGE, 3, 0, 4, "engineer_emergency_charge", "eng_overload");
    }

    /** 勘探 Prospector - origin + speed N / scan E / luck&jump S / ore-vision W; ore-link active. */
    private static void prospector() {
        String t = "prospector";
        attr("pro_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:movement_speed", 0.02f, true);
        attr("pro_speed", t, Tier.SMALL, 1, 0, -1, "minecraft:movement_speed", 0.03f, true, "pro_origin");
        attr("pro_agility", t, Tier.SMALL, 1, 0, -2, "minecraft:movement_speed", 0.03f, true, "pro_speed");
        attr("pro_sense", t, Tier.SMALL, 1, 2, 0, "minecraft:luck", 1f, false, "pro_origin");
        mech("pro_scan", t, Tier.MEDIUM, 2, 4, 0, "scan_enhance", "pro_sense");
        attr("pro_luck", t, Tier.SMALL, 1, 0, 1, "minecraft:luck", 1f, false, "pro_origin");
        attr("pro_fortune", t, Tier.SMALL, 1, 0, 2, "minecraft:luck", 1f, false, "pro_luck");
        mech("pro_jump", t, Tier.MEDIUM, 2, 0, 3, "jump_boost", "pro_fortune");
        attr("pro_fit", t, Tier.SMALL, 1, -2, 0, "minecraft:armor", 1f, false, "pro_origin");
        mech("pro_vision", t, Tier.LARGE, 3, -4, 0, "ore_vision", "pro_fit", "pro_scan", "pro_jump");
        skill("pro_active", t, Tier.LARGE, 3, 0, 4, "prospector_ore_link", "pro_vision");
    }

    /** 医疗 Medic - origin + health N / healing E / speed&erosion S / field-medic W; triage active. */
    private static void medic() {
        String t = "medic";
        attr("med_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:max_health", 1f, false);
        attr("med_hp", t, Tier.SMALL, 1, 0, -1, "minecraft:max_health", 1f, false, "med_origin");
        attr("med_vigor", t, Tier.SMALL, 1, 0, -2, "minecraft:max_health", 1f, false, "med_hp");
        attr("med_calm", t, Tier.SMALL, 1, 2, 0, "minecraft:armor", 1f, false, "med_origin");
        mech("med_healbonus", t, Tier.MEDIUM, 2, 4, 0, "healing_bonus_25", "med_calm");
        attr("med_speed", t, Tier.SMALL, 1, 0, 1, "minecraft:movement_speed", 0.03f, true, "med_origin");
        attr("med_alacrity", t, Tier.SMALL, 1, 0, 2, "minecraft:movement_speed", 0.03f, true, "med_speed");
        mech("med_erosion", t, Tier.MEDIUM, 2, 0, 3, "erosion_resist", "med_alacrity");
        attr("med_smart", t, Tier.SMALL, 1, -2, 0, "minecraft:luck", 1f, false, "med_origin");
        mech("med_field", t, Tier.LARGE, 3, -4, 0, "field_medic", "med_smart", "med_healbonus", "med_erosion");
        skill("med_active", t, Tier.LARGE, 3, 0, 4, "medic_triage", "med_field");
    }

    /** 战斗 Vanguard - origin + attack N / slayer E / armor&regen S / undying W; taunt active. */
    private static void vanguard() {
        String t = "vanguard";
        attr("van_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:attack_damage", 1f, false);
        attr("van_attack", t, Tier.SMALL, 1, 0, -1, "minecraft:attack_damage", 1f, false, "van_origin");
        attr("van_might", t, Tier.SMALL, 1, 0, -2, "minecraft:attack_damage", 1f, false, "van_attack");
        attr("van_edge", t, Tier.SMALL, 1, 2, 0, "minecraft:attack_damage", 1f, false, "van_origin");
        mech("van_slayer", t, Tier.MEDIUM, 2, 4, 0, "kill_attack_stack", "van_edge");
        attr("van_armor", t, Tier.SMALL, 1, 0, 1, "minecraft:armor", 1f, false, "van_origin");
        attr("van_bulwark", t, Tier.SMALL, 1, 0, 2, "minecraft:armor", 1f, false, "van_armor");
        mech("van_regen", t, Tier.MEDIUM, 2, 0, 3, "out_of_combat_regen", "van_bulwark");
        attr("van_guard", t, Tier.SMALL, 1, -2, 0, "minecraft:max_health", 1f, false, "van_origin");
        mech("van_totem", t, Tier.LARGE, 3, -4, 0, "undying_totem", "van_guard", "van_regen", "van_slayer");
        skill("van_active", t, Tier.LARGE, 3, 0, 4, "vanguard_taunt", "van_totem");
    }

    /** 拾荒 Scavenger - origin + speed N / supply E / health&loot S / waste-to-wealth W; discovery active. */
    private static void scavenger() {
        String t = "scavenger";
        attr("scv_origin", t, Tier.MEDIUM, 1, 0, 0, "minecraft:movement_speed", 0.02f, true);
        attr("scv_speed", t, Tier.SMALL, 1, 0, -1, "minecraft:movement_speed", 0.03f, true, "scv_origin");
        attr("scv_gust", t, Tier.SMALL, 1, 0, -2, "minecraft:movement_speed", 0.03f, true, "scv_speed");
        attr("scv_eye", t, Tier.SMALL, 1, 2, 0, "minecraft:luck", 1f, false, "scv_origin");
        mech("scv_supply", t, Tier.MEDIUM, 2, 4, 0, "supply_conversion_15", "scv_eye");
        attr("scv_hp", t, Tier.SMALL, 1, 0, 1, "minecraft:max_health", 1f, false, "scv_origin");
        attr("scv_vigor", t, Tier.SMALL, 1, 0, 2, "minecraft:max_health", 1f, false, "scv_hp");
        mech("scv_loot", t, Tier.MEDIUM, 2, 0, 3, "fast_looting", "scv_vigor");
        attr("scv_pack", t, Tier.SMALL, 1, -2, 0, "minecraft:armor", 1f, false, "scv_origin");
        mech("scv_convert", t, Tier.LARGE, 3, -4, 0, "waste_to_wealth", "scv_pack", "scv_supply", "scv_loot");
        skill("scv_active", t, Tier.LARGE, 3, 0, 4, "scavenger_discover", "scv_convert");
    }
}
