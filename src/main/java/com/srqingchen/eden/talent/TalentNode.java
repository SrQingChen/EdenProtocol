package com.srqingchen.eden.talent;

import java.util.List;

/**
 * One node in a talent tree - the atomic unit the map UI (P3) draws and the attribute / class-skill systems (P2/P3)
 * consume. Immutable; every node lives in the {@link TalentNodes} static registry.
 *
 * @param id             unique id - also the lang-key stem ({@link #nameKey()}) and the P2 attribute-modifier id suffix
 * @param tree           which tree: {@link TalentNodes#UNIVERSAL} ({@code ""}) for universal, else a {@link ClassId#id}
 * @param tier           node rank (SMALL/MEDIUM/LARGE) - drives the on-map box size and the keystone glow
 * @param cost           talent points PER RANK (a node is unlocked rank-by-rank up to {@code maxRank})
 * @param maxRank        how many times the node can be unlocked (attribute nodes are multi-rank; mech/skill = 1)
 * @param x              grid X for the map layout (the UI multiplies by the node spacing)
 * @param y              grid Y for the map layout
 * @param prereqs        node ids that must ALL be unlocked first (the "前置全点亮才能点下一个" rule)
 * @param effectId       what it grants: a vanilla attribute registry name ("minecraft:max_health") for attribute nodes,
 *                       or "mech.&lt;key&gt;" / "skill.&lt;key&gt;" for passive-mechanic / active-skill nodes
 * @param effectValue    attribute amount PER RANK (total = effectValue x rank; unused for mech/skill nodes)
 * @param multiplicative true = ADD_MULTIPLIED_BASE (value is a fraction, e.g. 0.05 = +5%), false = ADD_VALUE
 */
public record TalentNode(String id, String tree, Tier tier, int cost, int maxRank, int x, int y,
                         List<String> prereqs, String effectId, float effectValue, boolean multiplicative) {

    /** Node rank - also its on-map pixel size (bigger = more important, per 功能清单 §19.4). */
    public enum Tier {
        SMALL(16), MEDIUM(24), LARGE(32);

        public final int px;

        Tier(int px) {
            this.px = px;
        }
    }

    /** Lang key for the node's display name. */
    public String nameKey() {
        return "eden.talent." + this.id + ".name";
    }

    /** Lang key for the node's description (shown in the tooltip / details panel). */
    public String descKey() {
        return "eden.talent." + this.id + ".desc";
    }

    /** True for the always-active universal tree ({@code tree == ""}). */
    public boolean isUniversal() {
        return this.tree.isEmpty();
    }

    /** True if this node grants a plain attribute modifier (vs a mech/skill resolved by the class-skill system). */
    public boolean isAttribute() {
        return this.effectId != null && !this.effectId.startsWith("mech.") && !this.effectId.startsWith("skill.");
    }

    /** True if the node can be unlocked more than once (multi-rank); mech/skill nodes are rank-1. */
    public boolean isMultiRank() {
        return this.maxRank > 1;
    }
}
