package com.srqingchen.eden.dimension;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Cross-dimension travel between the persistent safe hub ({@code eden:ark}) and the raid world
 * ({@code eden:raid_overworld}), plus the "arrive into the ark on join" wiring.
 * <p>All travel goes through {@link ServerPlayer#teleportTo(ServerLevel, double, double, double,
 * Set, float, float, boolean)} with an empty {@link Relative} set (absolute coordinates). The safe
 * standing Y is resolved from the {@code MOTION_BLOCKING} heightmap after forcing the target chunk
 * to generate, so we never drop players into ungenerated terrain.
 * <p>The per-raid "fresh random seed" rebuild (region wipe + {@code neoforge:seed_override}) layers
 * on top of this in a later hardening step; for MVP the raid dimension is a persistent vanilla-
 * overworld-generated world.
 */
public class DimensionManager {
    /** Landing spot in the ark (flat platform centred on the origin). */
    private static final double ARK_X = 0.5D;
    private static final double ARK_Z = 0.5D;
    /** Drop-in point for raids; noise terrain, safe Y resolved on arrival. */
    private static final double RAID_X = 0.5D;
    private static final double RAID_Z = 0.5D;

    /** Resolve a registered dimension's {@link ServerLevel} for a player, or {@code null}. */
    @Nullable
    public static ServerLevel levelFor(ServerPlayer player, ResourceKey<Level> dimension) {
        MinecraftServer server = player.level().getServer();
        return server == null ? null : server.getLevel(dimension);
    }

    /**
     * Teleport a player to a dimension at (x, z), resolving a safe standing Y from the motion-
     * blocking heightmap. Returns false (and logs) if the dimension is not registered.
     */
    public static boolean teleport(ServerPlayer player, ResourceKey<Level> dimension, double x, double z) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        ServerLevel target = server.getLevel(dimension);
        if (target == null) {
            EdenProtocol.LOGGER.warn("[Eden] target dimension {} is not registered", dimension.identifier());
            return false;
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        // Force the destination chunk to full status so the heightmap is valid before resolving Y.
        target.getChunk(blockX >> 4, blockZ >> 4);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
        boolean ok = player.teleportTo(target, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
        if (ok) {
            // The raid world is rebuilt in place (same dimension KEY, brand-new ServerLevel instance).
            // Block breaking and block interaction run against gameMode.level, so re-bind the player
            // (entity level + game mode) to the exact target instance. If gameMode.level lagged on the
            // previous, now-closed instance, the server acts on the wrong world and rolls every break back
            // ("breaks then instantly restores"), which is exactly the reported symptom.
            player.setServerLevel(target);
        }
        return ok;
    }

    /** Send a player to the safe ark hub. */
    public static boolean enterArk(ServerPlayer player) {
        // The ark is a void world; make sure the placeholder spawn platform exists before we resolve the
        // landing Y from the heightmap (otherwise there is nothing to stand on and the player falls).
        ServerLevel ark = levelFor(player, EdenDimensions.ARK);
        if (ark != null) {
            ArkHubBuilder.ensurePlatform(ark);
        }
        return teleport(player, EdenDimensions.ARK, ARK_X, ARK_Z);
    }

    /** Send a player into the polluted raid world. */
    public static boolean enterRaid(ServerPlayer player) {
        return teleport(player, EdenDimensions.RAID_OVERWORLD, RAID_X, RAID_Z);
    }

    /** Send a player into the purified paradise (campaign-victory reward world; normal overworld terrain). */
    public static boolean enterParadise(ServerPlayer player) {
        return teleport(player, EdenDimensions.PARADISE, 0.5D, 0.5D);
    }

    /**
     * On join, pull players who arrive in a vanilla dimension (fresh join / default world spawn)
     * into the ark. Players already in the ark or mid-raid are left untouched, so relogging never
     * yanks someone out of an active expedition.
     */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> current = player.level().dimension();
        if (current.equals(EdenDimensions.ARK) || current.equals(EdenDimensions.RAID_OVERWORLD)) {
            return;
        }
        if (enterArk(player)) {
            EdenProtocol.LOGGER.info("[Eden] {} arrived in the ark", player.getName().getString());
        }
    }
}
