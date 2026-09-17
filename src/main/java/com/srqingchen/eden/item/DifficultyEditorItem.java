package com.srqingchen.eden.item;

import com.entitymodifier.data.ConfigProfileManager;
import com.srqingchen.eden.data.DifficultyConfigData;
import com.srqingchen.eden.network.EditorDataPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The difficulty editor. Right-click asks the server for the current per-difficulty config plus the list of
 * available entity_modifier profiles; the server replies with an {@link EditorDataPayload} and the client
 * opens {@code DifficultyEditorScreen}, where each raid difficulty can be pointed at a profile and have its
 * return-pod charge speeds, ambient-particle tuning and card-pack quality/star distributions edited. Opening
 * goes through the server (not a direct client {@code setScreen}) so the authoritative config is used.
 * <p>entity_modifier is a hard dependency, so {@link ConfigProfileManager} is referenced directly.
 */
public class DifficultyEditorItem extends Item {
    public DifficultyEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                DifficultyConfigData config = DifficultyConfigData.get(server);
                List<String> profiles = new ArrayList<>(ConfigProfileManager.listProfiles());
                List<String> selected = new ArrayList<>();
                List<Float> charge = new ArrayList<>();
                List<Float> particle = new ArrayList<>();
                List<Integer> density = new ArrayList<>();
                List<Integer> quality = new ArrayList<>();
                List<Integer> star = new ArrayList<>();
                for (String d : DifficultyConfigData.DIFFICULTIES) {
                    DifficultyConfigData.Entry e = config.entryFor(d);
                    selected.add(e.profile());
                    charge.add(e.chargeBase());
                    charge.add(e.chargeCell());
                    charge.add(e.chargeCrystal());
                    particle.add(e.particleScale());
                    particle.add(e.particleChance());
                    particle.add(e.particleSpeed());
                    density.add(e.particleDensity());
                    quality.addAll(e.qualityWeights());
                    star.addAll(e.starWeights());
                }
                // Loot-injection block (editor's 战利品 tab): global rules + flattened per-difficulty pools.
                com.srqingchen.eden.data.LootInjectionData lootData =
                        com.srqingchen.eden.data.LootInjectionData.get(server);
                List<Integer> lootRolls = new ArrayList<>();
                List<String> lootItems = new ArrayList<>();
                List<Integer> lootWeights = new ArrayList<>();
                List<Integer> lootMin = new ArrayList<>();
                List<Integer> lootMax = new ArrayList<>();
                List<Float> lootChance = new ArrayList<>();
                for (String d : DifficultyConfigData.DIFFICULTIES) {
                    com.srqingchen.eden.data.LootInjectionData.PoolConfig pool = lootData.poolFor(d);
                    lootRolls.add(pool.rollsMin());
                    lootRolls.add(pool.rollsMax());
                    List<com.srqingchen.eden.data.LootInjectionData.ItemEntry> entries = new ArrayList<>(pool.items());
                    for (int k = 0; k < com.srqingchen.eden.data.LootInjectionData.MAX_ITEMS; k++) {
                        if (k < entries.size()) {
                            com.srqingchen.eden.data.LootInjectionData.ItemEntry it = entries.get(k);
                            lootItems.add(it.item());
                            lootWeights.add(it.weight());
                            lootMin.add(it.minCount());
                            lootMax.add(it.maxCount());
                            lootChance.add(it.chance());
                        } else {
                            lootItems.add("");
                            lootWeights.add(1);
                            lootMin.add(1);
                            lootMax.add(1);
                            lootChance.add(1.0f);
                        }
                    }
                }
                com.srqingchen.eden.network.LootPayloadBlock lootBlock = new com.srqingchen.eden.network.LootPayloadBlock(
                        lootData.enabled, lootData.allNamespaces, String.join(",", lootData.prefixes),
                        String.join(",", lootData.exclusions), lootRolls, lootItems, lootWeights,
                        lootMin, lootMax, lootChance);
                PacketDistributor.sendToPlayer(sp,
                        new EditorDataPayload(profiles, selected, charge, particle, density, quality, star, lootBlock));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
