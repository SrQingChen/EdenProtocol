package com.srqingchen.eden.season;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.data.CampaignData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Season registry &amp; resolver (批 D). The base mod registers its own S0 during construction;
 * season add-on mods (independent downloads that depend on Eden) register theirs the same way —
 * NeoForge loads dependencies first, so S0 always sits at the head of the list.
 * <p>The ACTIVE season persists as a string id in CampaignData. Worlds saved before 批 D only have
 * the old 1-based {@code season_index}; a blank id resolves through that index (index 1 → the
 * first registered season) and is written back, so old saves land on S0 矿洞季 unchanged.
 */
public final class Seasons {
    private Seasons() {}

    private static final List<SeasonDefinition> REGISTERED = new ArrayList<>();

    /** Register a season (id must be unique). Call from a mod's constructor after Eden loads. */
    public static synchronized void register(SeasonDefinition definition) {
        for (SeasonDefinition existing : REGISTERED) {
            if (existing.id().equals(definition.id())) {
                EdenProtocol.LOGGER.warn("[Eden] duplicate season registration ignored: {}", definition.id());
                return;
            }
        }
        REGISTERED.add(definition);
    }

    /** All registered seasons in registration order (S0 first). */
    public static List<SeasonDefinition> all() {
        return Collections.unmodifiableList(REGISTERED);
    }

    @Nullable
    public static SeasonDefinition byId(Identifier id) {
        for (SeasonDefinition d : REGISTERED) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** The season currently in play; never null once anything is registered (falls back to S0). */
    public static SeasonDefinition current(MinecraftServer server) {
        CampaignData data = CampaignData.get(server);
        String id = data.seasonId();
        if (!id.isBlank()) {
            SeasonDefinition def = byId(Identifier.tryParse(id));
            if (def != null) {
                return def;
            }
        }
        // Legacy save (or never resolved): map the old 1-based index onto registration order,
        // clamped, then persist the resolved id so future lookups are direct.
        int idx = Math.max(1, data.seasonIndex()) - 1;
        SeasonDefinition def = REGISTERED.isEmpty() ? null : REGISTERED.get(Math.min(idx, REGISTERED.size() - 1));
        if (def != null) {
            data.setSeasonId(def.id().toString());
        }
        return def;
    }

    public static boolean isCurrent(MinecraftServer server, Identifier id) {
        SeasonDefinition def = current(server);
        return def != null && def.id().equals(id);
    }

    /** The season after the current one in registration order (null when current is the last). */
    @Nullable
    public static SeasonDefinition next(MinecraftServer server) {
        SeasonDefinition cur = current(server);
        if (cur == null) {
            return null;
        }
        int idx = REGISTERED.indexOf(cur);
        return idx >= 0 && idx + 1 < REGISTERED.size() ? REGISTERED.get(idx + 1) : null;
    }
}
