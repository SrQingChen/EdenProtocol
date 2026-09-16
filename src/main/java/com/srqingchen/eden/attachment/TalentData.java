package com.srqingchen.eden.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.talent.ClassId;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-player meta-progression for the class / talent system, held by the {@code eden:talent_data} attachment and
 * serialized with the player (survives raids, death and relog - like {@link RaidState}).
 * <p><b>Two-currency model</b>: supply points are converted ONE-WAY into talent points ({@link #totalTP}); nodes are
 * then unlocked by spending talent points. The supply -&gt; TP conversion is the real resource sink.
 * <p><b>Multi-rank nodes</b>: each tree stores node id -&gt; rank (0/absent = locked). Attribute nodes go up to their
 * {@code maxRank}, each rank costing TP and adding a small bonus; mech/skill nodes are rank-1.
 * <p><b>Respec model</b> (功能清单 §19.4): each tree's allocation is stored independently - {@link #universal} plus one
 * rank-map per class in {@link #classTrees}. Only "universal + the CURRENT class" are ACTIVE at any moment. Switching
 * class ({@link #setCurrentClass}) deactivates the old tree's bonuses but KEEPS its ranks, so switching back is free.
 * Every codec field is optional-with-default so the persisted format can grow without breaking old saves.
 */
public class TalentData {
    public static final MapCodec<TalentData> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.optionalFieldOf("totalTP", 0).forGetter(d -> d.totalTP),
            Codec.STRING.optionalFieldOf("currentClass", "").forGetter(d -> d.currentClass),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("universal", Map.of())
                    .forGetter(d -> new HashMap<>(d.universal)),
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .optionalFieldOf("classTrees", Map.of())
                    .forGetter(d -> {
                        Map<String, Map<String, Integer>> m = new HashMap<>();
                        d.classTrees.forEach((k, v) -> m.put(k, new HashMap<>(v)));
                        return m;
                    })
    ).apply(inst, (totalTP, currentClass, universal, classTrees) -> {
        TalentData d = new TalentData();
        d.totalTP = totalTP;
        d.currentClass = currentClass;
        d.universal = new HashMap<>(universal);
        classTrees.forEach((k, v) -> d.classTrees.put(k, new HashMap<>(v)));
        return d;
    }));

    /** Talent points ever purchased (supply -&gt; TP is one-way). Persistent and class-agnostic. */
    public int totalTP = 0;
    /** Active class id ("" = none chosen yet). Only this class's tree + the universal tree apply. */
    public String currentClass = "";
    /** Universal-tree node id -&gt; rank (always active, whatever the class). */
    public Map<String, Integer> universal = new HashMap<>();
    /** Class id -&gt; (node id -&gt; rank); only {@link #currentClass}'s map is active. */
    public Map<String, Map<String, Integer>> classTrees = new HashMap<>();

    /** The active class, or {@code null} if none is chosen. */
    @Nullable
    public ClassId activeClass() {
        return ClassId.byId(this.currentClass);
    }

    public boolean hasClass() {
        return activeClass() != null;
    }

    /** Switch (or first-choose) the active class. Both trees' ranks are preserved; only activity changes. */
    public void setCurrentClass(@Nullable ClassId c) {
        this.currentClass = c == null ? "" : c.id;
    }

    /** Current rank of a node in a tree ({@code ""} tree = universal); 0 if locked. */
    public int rankOf(String tree, String node) {
        Map<String, Integer> m = tree.isEmpty() ? this.universal : this.classTrees.get(tree);
        return m == null ? 0 : m.getOrDefault(node, 0);
    }

    /** Whether a node has at least rank 1 in a tree ({@code ""} tree = universal). */
    public boolean isUnlocked(String tree, String node) {
        return rankOf(tree, node) > 0;
    }

    /** The (mutable) rank map for a tree ({@code ""} = universal), creating it if absent. */
    public Map<String, Integer> ranksOf(String tree) {
        return tree.isEmpty() ? this.universal : this.classTrees.computeIfAbsent(tree, k -> new HashMap<>());
    }

    /** Add (or spend, when negative) talent points; clamped at 0. */
    public void addTP(int amount) {
        this.totalTP = Math.max(0, this.totalTP + amount);
    }
}
