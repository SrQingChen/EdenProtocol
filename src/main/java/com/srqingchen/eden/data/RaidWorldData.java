package com.srqingchen.eden.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-EXPEDITION world state, stored in the raid overworld's own data storage so it lives and dies with
 * the shared raid world: every rebuild wipes {@code dimensions/eden/raid_overworld}, so a fresh world has
 * no data here and the feature seeder knows to roll new evacuation points / oasis / cores. Holds the
 * positions the scanner and the pathfinding HUD query; the block state itself remains the source of truth
 * (a launched or griefed pod is lazily pruned from the list on lookup).
 */
public class RaidWorldData extends SavedData {
    /** One ecology lair: its anchor position, its kind and whether its boss already woke. */
    public record Lair(BlockPos pos, int kind, boolean awake) {}

    public static final Codec<Lair> LAIR_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Lair::pos),
            Codec.INT.optionalFieldOf("kind", 0).forGetter(Lair::kind),
            Codec.BOOL.optionalFieldOf("awake", false).forGetter(Lair::awake)
    ).apply(inst, Lair::new));

    public static final int LAIR_SPORE = 0;
    public static final int LAIR_LEVIATHAN = 1;
    public static final int LAIR_EXCAVATOR = 2;

    public static final Codec<RaidWorldData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("seeded", false).forGetter(d -> d.seeded),
            BlockPos.CODEC.listOf().optionalFieldOf("extraction_points", List.of()).forGetter(d -> d.extractionPoints),
            Codec.BOOL.optionalFieldOf("has_oasis", false).forGetter(d -> d.hasOasis),
            BlockPos.CODEC.optionalFieldOf("oasis_pos", BlockPos.ZERO).forGetter(d -> d.oasisPos),
            BlockPos.CODEC.listOf().optionalFieldOf("cores", List.of()).forGetter(d -> d.cores),
            BlockPos.CODEC.listOf().optionalFieldOf("awake_cores", List.of()).forGetter(d -> d.awakeCores),
            LAIR_CODEC.listOf().optionalFieldOf("lairs", List.of()).forGetter(d -> d.lairs),
            Codec.INT.optionalFieldOf("rain_state", 0).forGetter(d -> d.rainState),
            Codec.LONG.optionalFieldOf("rain_at", 0L).forGetter(d -> d.rainAt),
            Codec.LONG.optionalFieldOf("rain_end", 0L).forGetter(d -> d.rainEnd)
    ).apply(inst, RaidWorldData::new));

    public static final SavedDataType<RaidWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "raid_world"), RaidWorldData::new, CODEC);

    /** Set once evacuation points / oasis / cores have been rolled for this expedition. */
    private boolean seeded;
    /** Base positions of the 2-4 pre-deployed public return pods. */
    private List<BlockPos> extractionPoints = new ArrayList<>();
    /** The hope oasis (净土口袋): erosion pauses and decays inside its radius. */
    private boolean hasOasis;
    private BlockPos oasisPos = BlockPos.ZERO;
    /** Pollution core (污染核心) base positions, destroyed by the crew to lower campaign pollution. */
    private List<BlockPos> cores = new ArrayList<>();
    /** Cores whose guardians already spawned (persistence: a relog must not re-wake a spent core). */
    private List<BlockPos> awakeCores = new ArrayList<>();
    /** Ecology lairs (v2 浊潮生态): spore mound / leviathan pond / excavator burrow. */
    private List<Lair> lairs = new ArrayList<>();
    /** Acid-rain state machine (v2 浊雨): 0 idle, 1 scheduled, 2 warned, 3 active, 4 done. */
    private int rainState;
    private long rainAt;
    private long rainEnd;

    public RaidWorldData() {
    }

    private RaidWorldData(boolean seeded, List<BlockPos> extractionPoints, boolean hasOasis,
                          BlockPos oasisPos, List<BlockPos> cores, List<BlockPos> awakeCores,
                          List<Lair> lairs, int rainState, long rainAt, long rainEnd) {
        this.seeded = seeded;
        this.extractionPoints = new ArrayList<>(extractionPoints);
        this.hasOasis = hasOasis;
        this.oasisPos = oasisPos;
        this.cores = new ArrayList<>(cores);
        this.awakeCores = new ArrayList<>(awakeCores);
    }

    public boolean seeded() {
        return this.seeded;
    }

    public void markSeeded() {
        this.seeded = true;
        setDirty();
    }

    public List<BlockPos> extractionPoints() {
        return this.extractionPoints;
    }

    public void addExtractionPoint(BlockPos pos) {
        this.extractionPoints.add(pos.immutable());
        setDirty();
    }

    public boolean hasOasis() {
        return this.hasOasis;
    }

    public BlockPos oasisPos() {
        return this.oasisPos;
    }

    public void setOasis(BlockPos pos) {
        this.hasOasis = true;
        this.oasisPos = pos.immutable();
        setDirty();
    }

    public List<BlockPos> cores() {
        return this.cores;
    }

    public void addCore(BlockPos pos) {
        this.cores.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> awakeCores() {
        return this.awakeCores;
    }

    public boolean isCoreAwake(BlockPos pos) {
        for (BlockPos p : this.awakeCores) {
            if (p.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public void markCoreAwake(BlockPos pos) {
        if (!isCoreAwake(pos)) {
            this.awakeCores.add(pos.immutable());
            setDirty();
        }
    }

    public List<Lair> lairs() {
        return this.lairs;
    }

    public void addLair(BlockPos pos, int kind) {
        this.lairs.add(new Lair(pos.immutable(), kind, false));
        setDirty();
    }

    public void markLairAwake(BlockPos pos) {
        for (int i = 0; i < this.lairs.size(); i++) {
            Lair l = this.lairs.get(i);
            if (l.pos().equals(pos) && !l.awake()) {
                this.lairs.set(i, new Lair(l.pos(), l.kind(), true));
                setDirty();
            }
        }
    }

    public int rainState() {
        return this.rainState;
    }

    public void setRainState(int state) {
        this.rainState = state;
        setDirty();
    }

    public long rainAt() {
        return this.rainAt;
    }

    public void setRain(long at) {
        this.rainAt = at;
        setDirty();
    }

    public long rainEnd() {
        return this.rainEnd;
    }

    public void setRainEnd(long at) {
        this.rainEnd = at;
        setDirty();
    }

    /** Fetch (or create) this expedition world's data from the raid overworld's storage. */
    public static RaidWorldData get(ServerLevel raidLevel) {
        return raidLevel.getDataStorage().computeIfAbsent(TYPE);
    }
}
