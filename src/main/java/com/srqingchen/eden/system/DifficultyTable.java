package com.srqingchen.eden.system;

/**
 * Per-difficulty tuning for the erosion + diet systems. MVP only uses "scout"; the other rows are
 * forward-declared for v1. Values are design defaults; can be surfaced to config later.
 */
public final class DifficultyTable {
    private DifficultyTable() {}

    /** Max erosion level reachable at full erosion gauge (scout 2 ... endgame 10). */
    public static int maxErosionLevel(String d) {
        return switch (d) {
            case "salvage" -> 4;
            case "purge" -> 6;
            case "abyss" -> 8;
            case "endgame" -> 10;
            default -> 2; // scout
        };
    }

    /** Diet regen coefficient: harder raids regen slower (1 / 1 / 0.8 / 0.5 / 0.2). */
    public static float dietCoeff(String d) {
        return switch (d) {
            case "purge" -> 0.8f;
            case "abyss" -> 0.5f;
            case "endgame" -> 0.2f;
            default -> 1.0f; // scout, salvage, and safe/ark (not in raid)
        };
    }

    /** Erosion value gained per second during the first 20 minutes (very slow). */
    public static float erosionPerSecEarly(String d) {
        return 0.033f * erosionScale(d); // ~40 over 20 min at scout
    }

    /** Erosion value gained per second after 20 minutes (ramps up). */
    public static float erosionPerSecLate(String d) {
        return 0.10f * erosionScale(d); // ~60 over the last 10 min -> gauge full near 30 min
    }

    /** Extra erosion added each time the player kills a mob ("no unnecessary combat"). */
    public static float erosionPerKill(String d) {
        return 0.5f * erosionScale(d);
    }

    private static float erosionScale(String d) {
        return switch (d) {
            case "salvage" -> 1.25f;
            case "purge" -> 1.5f;
            case "abyss" -> 1.8f;
            case "endgame" -> 2.2f;
            default -> 1.0f; // scout
        };
    }
}
