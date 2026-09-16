package com.srqingchen.eden.client.gui.search;

import me.towdium.jecharacters.utils.Match;
import net.neoforged.fml.ModList;

/**
 * Bridge to the "Just Enough Characters" (jecharacters) pinyin search mod.
 *
 * <p>This class directly references jecharacters classes and therefore MUST only be class-loaded after
 * checking {@code ModList.get().isLoaded("jecharacters")}. Callers should use {@link #matches(String, String)}
 * which performs the guard itself and falls back to a plain case-insensitive substring search when the mod
 * is absent. Mirrors the proven implementation in the author's {@code entity_modifier} mod.
 */
public final class JechCompat {

    private static Boolean cachedLoaded;

    private JechCompat() {}

    /** Whether the jecharacters mod is present (cached). */
    public static boolean isLoaded() {
        if (cachedLoaded == null) {
            try {
                cachedLoaded = ModList.get().isLoaded("jecharacters");
            } catch (Throwable t) {
                cachedLoaded = false;
            }
        }
        return cachedLoaded;
    }

    /**
     * Check whether the searchable text matches the query. With jecharacters installed this supports pinyin
     * (full / initial) matching via {@code Match.contains}; otherwise it falls back to case-insensitive
     * substring search.
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return true;
        // Plain substring match always applies (also covers registry names / english keys).
        if (text.toLowerCase().contains(query.toLowerCase())) return true;
        if (isLoaded()) {
            try {
                return Match.contains(text, query);
            } catch (Throwable t) {
                // Never let the search mod break our UI.
                cachedLoaded = false;
                return false;
            }
        }
        return false;
    }
}
