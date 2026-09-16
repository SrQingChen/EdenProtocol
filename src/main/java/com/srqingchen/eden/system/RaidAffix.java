package com.srqingchen.eden.system;

/**
 * Per-raid random modifiers - the "the world is unknown" information-game pillar (design doc §7/§11).
 * <p>Each raid rolls 1-3 distinct affixes on entry ({@link RaidService}), stores their ids in
 * {@code RaidState.affixes}, and they then: drive server-side tick effects ({@link AffixSystem}), thicken the
 * client fog / play ambience, and are revealed to the crew so they can read the affixes and adapt their
 * route and strategy. Ids are persisted as plain strings so bad/old data degrades gracefully via {@link #byId}.
 */
public enum RaidAffix {
    /** Spore storm: periodic blindness pulses on the crew. */
    SPORE_STORM("spore_storm"),
    /** Crystallization: razor crystals erupt periodically, nicking the crew for small true-ish damage. */
    CRYSTALLIZATION("crystallization"),
    /** Whisper: misleading ambient sounds (client-side psychological pressure, no mechanical damage). */
    WHISPER("whisper"),
    /** Proliferation: pollution mobs grow stronger the longer the raid runs. */
    PROLIFERATION("proliferation"),
    /** Corrosion: equipped gear loses durability faster. */
    CORROSION("corrosion"),
    /** Dense fog: visibility drops further (the client fog thickens). */
    DENSE_FOG("dense_fog");

    public final String id;

    RaidAffix(String id) {
        this.id = id;
    }

    /** Localized name key, e.g. {@code eden.affix.spore_storm.name}. */
    public String langName() {
        return "eden.affix." + this.id + ".name";
    }

    /** Localized description key, e.g. {@code eden.affix.spore_storm.desc}. */
    public String langDesc() {
        return "eden.affix." + this.id + ".desc";
    }

    /** Resolve an affix by its persisted id, or {@code null} if unknown (defensive against stale data). */
    public static RaidAffix byId(String id) {
        for (RaidAffix a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
