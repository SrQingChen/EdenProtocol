package com.srqingchen.eden.talent;

import javax.annotation.Nullable;

/**
 * The five expedition classes. A player has at most ONE active class at a time (see
 * {@link com.srqingchen.eden.attachment.TalentData#currentClass}); switching class only changes WHICH class tree's
 * allocations are active - every tree's spent talent points are stored independently and survive a switch, so
 * switching back is free (the respec model in 功能清单 §19.4).
 */
public enum ClassId {
    ENGINEER("engineer"),
    PROSPECTOR("prospector"),
    MEDIC("medic"),
    VANGUARD("vanguard"),
    SCAVENGER("scavenger");

    /** Stable persistence id - stored in {@code TalentData.currentClass} and as {@code classTrees} keys. */
    public final String id;

    ClassId(String id) {
        this.id = id;
    }

    /** Translation key for the class display name. */
    public String nameKey() {
        return "eden.class." + this.id;
    }

    /** Resolve a persisted id back to a class, or {@code null} for empty/unknown (no class chosen). */
    @Nullable
    public static ClassId byId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (ClassId c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }
}
