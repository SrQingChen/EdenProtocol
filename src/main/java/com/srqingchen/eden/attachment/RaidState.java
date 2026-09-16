package com.srqingchen.eden.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player raid state, held by the {@code eden:raid_state} attachment. Serialized with the player so a
 * mid-raid relog (or server restart) resumes the expedition instead of silently dropping it: erosion, the
 * difficulty, the collapse timer and the diet accumulator all survive. {@code downed} is intentionally NOT
 * persisted - a player who relogs comes back standing rather than stuck bleeding out.
 */
public class RaidState {
    /** Persists raid progress across relog/restart (registered in EdenAttachments; downed resets on load). */
    public static final MapCodec<RaidState> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.fieldOf("inRaid").forGetter(s -> s.inRaid),
            Codec.STRING.fieldOf("difficulty").forGetter(s -> s.difficulty),
            Codec.LONG.fieldOf("raidStartTick").forGetter(s -> s.raidStartTick),
            Codec.FLOAT.fieldOf("erosion").forGetter(s -> s.erosion),
            Codec.INT.fieldOf("erosionLevel").forGetter(s -> s.erosionLevel),
            Codec.FLOAT.fieldOf("foodDrainAccum").forGetter(s -> s.foodDrainAccum),
            Codec.STRING.listOf().fieldOf("affixes").forGetter(s -> s.affixes),
            Codec.BOOL.optionalFieldOf("greedTail", false).forGetter(s -> s.greedTail),
            Codec.BOOL.optionalFieldOf("usedTotem", false).forGetter(s -> s.usedTotem),
            Codec.STRING.listOf().optionalFieldOf("revealedAffixes", List.of()).forGetter(s -> s.revealedAffixes),
            Codec.BOOL.optionalFieldOf("soloRun", false).forGetter(s -> s.soloRun)
    ).apply(inst, (inRaid, difficulty, raidStartTick, erosion, erosionLevel, foodDrainAccum, affixes,
                   greedTail, usedTotem, revealedAffixes, soloRun) -> {
        RaidState s = new RaidState();
        s.inRaid = inRaid;
        s.difficulty = difficulty;
        s.raidStartTick = raidStartTick;
        s.erosion = erosion;
        s.erosionLevel = erosionLevel;
        s.foodDrainAccum = foodDrainAccum;
        s.affixes = new ArrayList<>(affixes);
        s.greedTail = greedTail;
        s.usedTotem = usedTotem;
        s.revealedAffixes = new ArrayList<>(revealedAffixes);
        s.soloRun = soloRun;
        return s;
    }));

    /** Whether the player is currently inside an active raid dimension. */
    public boolean inRaid = false;
    /** Active difficulty id (matches an entity_modifier profile); MVP only uses "scout". */
    public String difficulty = "scout";
    /** Game time (tick) when the current raid started, for the 30-minute hard limit. */
    public long raidStartTick = 0L;
    /** Current erosion value (0 .. erosionMax). */
    public float erosion = 0f;
    /** Derived erosion level (0 .. difficulty max), drives per-tick erosion damage. */
    public int erosionLevel = 0;
    /** Fractional food-point accumulator for the diet system's whole-unit foodLevel drain. */
    public float foodDrainAccum = 0f;
    /** Whether the player is downed (a lethal blow was absorbed, awaiting a teammate revive). */
    public boolean downed = false;
    /** This raid's random affix ids (1-3 distinct, rolled on entry); drive AffixSystem + client ambience. */
    public List<String> affixes = new ArrayList<>();
    /** Which raid dimension this expedition runs in ("overworld" / "nether" / "end"). */
    public String raidDim = "overworld";
    /** Corrosion affix stacks (transient, like downed): each stack cuts armor & toughness by 70%. */
    public int corrosionStacks = 0;
    /** Game time when the current corrosion stacks expire (transient). */
    public long corrosionExpireTick = 0L;
    /** Game time of the last damage taken (transient); drives the out-of-combat regen talent. */
    public long lastCombatTick = 0L;
    /** Greed's Tail (§19.5) consumed this raid: -5% speed paid up front, settlement x2 on success / keep-ratio halved on failure. */
    public boolean greedTail = false;
    /** The once-per-raid totem talent (uni_surv_totem / van_totem) has already fired this raid. */
    public boolean usedTotem = false;
    /** Affix ids revealed so far (§11 逐步揭示): first on entry, then one per scanner sweep / 8 min / insight card. */
    public List<String> revealedAffixes = new ArrayList<>();
    /** Entered this raid alone at the start (§9 单人局被动补偿: settlement +20%). */
    public boolean soloRun = false;
    /** Visited this expedition's hope oasis (transient; first-entry chime only). */
    public boolean oasisVisited = false;
    /** Took any damage this raid (transient; clears the perfect-extraction bonus when set). */
    public boolean tookDamage = false;

    public void reset() {
        this.inRaid = false;
        this.difficulty = "scout";
        this.raidStartTick = 0L;
        this.erosion = 0f;
        this.erosionLevel = 0;
        this.foodDrainAccum = 0f;
        this.downed = false;
        this.affixes.clear();
        this.corrosionStacks = 0;
        this.corrosionExpireTick = 0L;
        this.lastCombatTick = 0L;
        this.greedTail = false;
        this.usedTotem = false;
        this.revealedAffixes.clear();
        this.soloRun = false;
        this.oasisVisited = false;
        this.tookDamage = false;
        this.raidDim = "overworld";
    }
}
