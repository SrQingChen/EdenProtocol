package com.srqingchen.eden.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side mirror of the local player's talent state, updated by {@link TalentSyncPayload}. Plain data holder (no
 * client-only Minecraft types) so it is safe to touch from common code; the talent screen reads it to draw the map.
 * Nodes are multi-rank: each tree maps node id -&gt; rank (0/absent = locked).
 */
public class ClientTalentData {
    public static int totalTP = 0;
    public static int supplyPoints = 0;
    public static String currentClass = "";
    /** Universal-tree node id -&gt; rank. */
    public static final Map<String, Integer> universal = new HashMap<>();
    /** Class id -&gt; (node id -&gt; rank). */
    public static final Map<String, Map<String, Integer>> classTrees = new HashMap<>();

    public static void update(TalentSyncPayload p) {
        totalTP = p.totalTP();
        supplyPoints = p.supplyPoints();
        currentClass = p.currentClass() == null ? "" : p.currentClass();
        universal.clear();
        for (String enc : p.universal()) {                 // "<id>:<rank>"
            int i = enc.lastIndexOf(':');
            if (i <= 0) continue;
            try {
                universal.put(enc.substring(0, i), Integer.parseInt(enc.substring(i + 1)));
            } catch (NumberFormatException ignored) {}
        }
        classTrees.clear();
        for (String enc : p.classNodes()) {                // "<class>|<id>|<rank>"
            int a = enc.indexOf('|'), b = enc.lastIndexOf('|');
            if (a <= 0 || b <= a) continue;
            try {
                String cls = enc.substring(0, a), id = enc.substring(a + 1, b);
                int r = Integer.parseInt(enc.substring(b + 1));
                classTrees.computeIfAbsent(cls, k -> new HashMap<>()).put(id, r);
            } catch (NumberFormatException ignored) {}
        }
    }

    /** Current rank of a node in a tree ({@code ""} = universal); 0 if locked. */
    public static int rankOf(String tree, String node) {
        Map<String, Integer> m = tree.isEmpty() ? universal : classTrees.get(tree);
        return m == null ? 0 : m.getOrDefault(node, 0);
    }

    public static boolean isUnlocked(String tree, String node) {
        return rankOf(tree, node) > 0;
    }

    public static void clear() {
        totalTP = 0;
        supplyPoints = 0;
        currentClass = "";
        universal.clear();
        classTrees.clear();
    }
}
