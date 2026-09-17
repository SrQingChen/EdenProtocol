package com.srqingchen.eden.system;

import java.util.Map;

/**
 * Curated "which structure uses this chest table" hints for the loot-table list screen. Vanilla's
 * structure->table links live inside template-pool NBTs, so a reliable reverse map is data we ship
 * ourselves; modded tables simply show no hint. Keys are full table ids, values are lang keys
 * ({@code eden.loot.hint.*}) localised in the lang files.
 */
public final class LootTableHints {
    private LootTableHints() {}

    private static final Map<String, String> HINTS = Map.ofEntries(
            Map.entry("minecraft:chests/abandoned_mineshaft", "eden.loot.hint.mineshaft"),
            Map.entry("minecraft:chests/simple_dungeon", "eden.loot.hint.dungeon"),
            Map.entry("minecraft:chests/stronghold_corridor", "eden.loot.hint.stronghold"),
            Map.entry("minecraft:chests/stronghold_library", "eden.loot.hint.stronghold"),
            Map.entry("minecraft:chests/stronghold_crossing", "eden.loot.hint.stronghold"),
            Map.entry("minecraft:chests/nether_bridge", "eden.loot.hint.nether_fortress"),
            Map.entry("minecraft:chests/end_city_treasure", "eden.loot.hint.end_city"),
            Map.entry("minecraft:chests/village/village_plains_house", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_desert_house", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_savanna_house", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_snowy_house", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_taiga_house", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_butcher", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_fisher", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_fletcher", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_armorer", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_tannery", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_temple", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_toolsmith", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_weaponsmith", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_shepherd", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_cartographer", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_mason", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_snowy", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_desert", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_plains", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_savanna", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/village/village_taiga", "eden.loot.hint.village"),
            Map.entry("minecraft:chests/desert_pyramid", "eden.loot.hint.desert_temple"),
            Map.entry("minecraft:chests/jungle_temple", "eden.loot.hint.jungle_temple"),
            Map.entry("minecraft:chests/woodland_mansion", "eden.loot.hint.mansion"),
            Map.entry("minecraft:chests/igloo_chest", "eden.loot.hint.igloo"),
            Map.entry("minecraft:chests/pillager_outpost", "eden.loot.hint.outpost"),
            Map.entry("minecraft:chests/buried_treasure", "eden.loot.hint.buried_treasure"),
            Map.entry("minecraft:chests/shipwreck_treasure", "eden.loot.hint.shipwreck"),
            Map.entry("minecraft:chests/shipwreck_supply", "eden.loot.hint.shipwreck"),
            Map.entry("minecraft:chests/shipwreck_map", "eden.loot.hint.shipwreck"),
            Map.entry("minecraft:chests/ancient_city", "eden.loot.hint.ancient_city"),
            Map.entry("minecraft:chests/ancient_city_ice_box", "eden.loot.hint.ancient_city"),
            Map.entry("minecraft:chests/trial_chambers/reward", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/trial_chambers/reward_common", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/trial_chambers/reward_rare", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/trial_chambers/reward_unique", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/trial_chambers/supply", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/trial_chambers/corridor", "eden.loot.hint.trial_chamber"),
            Map.entry("minecraft:chests/ruined_portal", "eden.loot.hint.ruined_portal"),
            Map.entry("minecraft:chests/bastion_treasure", "eden.loot.hint.bastion"),
            Map.entry("minecraft:chests/bastion_other", "eden.loot.hint.bastion"),
            Map.entry("minecraft:chests/bastion_hoglin_stable", "eden.loot.hint.bastion"),
            Map.entry("minecraft:chests/bastion_bridge", "eden.loot.hint.bastion"),
            Map.entry("minecraft:chests/spawn_bonus_chest", "eden.loot.hint.bonus"));

    /** Lang key describing where this table is used, or "" when unknown. */
    public static String hintKey(String tableId) {
        return HINTS.getOrDefault(tableId, "");
    }
}
