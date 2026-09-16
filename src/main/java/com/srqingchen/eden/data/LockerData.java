package com.srqingchen.eden.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player ark lockers (§13 个人储物柜): one private 27-slot stash per UUID, stored server-side in
 * the overworld's SavedData so it survives raids, deaths and restarts. The locker BLOCK is a shared
 * terminal - each player who opens it gets their OWN view (a {@code ChestMenu} over a
 * {@link LockerContainer}), so one wall of lockers serves the whole server.
 */
public class LockerData extends SavedData {

    public static final int SLOTS = 27;

    public static final Codec<LockerData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf())
                    .optionalFieldOf("lockers", Map.of()).forGetter(d -> d.lockers)
    ).apply(inst, LockerData::new));

    public static final SavedDataType<LockerData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(EdenProtocol.MODID, "lockers"), LockerData::new, CODEC);

    /** player UUID -> 27 stacks (empty stacks allowed). */
    private final Map<String, List<ItemStack>> lockers = new HashMap<>();

    public LockerData() {
    }

    private LockerData(Map<String, List<ItemStack>> lockers) {
        this.lockers.putAll(lockers);
    }

    /** The player's 27-slot list, created (empty) on first access. */
    public List<ItemStack> slotsOf(UUID player) {
        return this.lockers.computeIfAbsent(player.toString(), k -> {
            List<ItemStack> list = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                list.add(ItemStack.EMPTY);
            }
            setDirty();
            return list;
        });
    }

    public static LockerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
