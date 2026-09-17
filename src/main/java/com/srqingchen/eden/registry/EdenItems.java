package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.item.CardItem;
import com.srqingchen.eden.item.CardQuality;
import com.srqingchen.eden.item.CardPackItem;
import com.srqingchen.eden.item.DifficultyEditorItem;
import com.srqingchen.eden.item.LocatorItem;
import com.srqingchen.eden.item.PurifierItem;
import com.srqingchen.eden.item.ScannerItem;
import com.srqingchen.eden.item.StatusCardItem;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Salvage/material items plus block items. The card item (ICurioItem) is registered in the card
 * milestone because it needs Curios + the card data component.
 */
public class EdenItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EdenProtocol.MODID);

    // --- Extraction / energy ---
    public static final DeferredItem<LocatorItem> LOCATOR = ITEMS.registerItem("locator", p -> new LocatorItem(p.stacksTo(16)));
    public static final DeferredItem<Item> TAINT_CRYSTAL = ITEMS.registerSimpleItem("taint_crystal", p -> p.stacksTo(64));

    // ---------- v2 浊潮生态 boss materials (rift-altar ascend fuel) ----------

    /** 孢子母树掉落：裂隙祭坛升星材料。 */
    public static final DeferredItem<Item> SPORE_SAC = ITEMS.registerSimpleItem("spore_sac", p -> p.stacksTo(16));

    /** 浊鳞巨物掉落：裂隙祭坛升星材料。 */
    public static final DeferredItem<Item> TAINTED_SCALE = ITEMS.registerSimpleItem("tainted_scale", p -> p.stacksTo(16));

    /** 深渊掘凿者掉落：裂隙祭坛升星材料。 */
    public static final DeferredItem<Item> EXCAVATOR_CLAW = ITEMS.registerSimpleItem("excavator_claw", p -> p.stacksTo(16));
    public static final DeferredItem<Item> EDEN_CELL = ITEMS.registerSimpleItem("eden_cell", p -> p.stacksTo(16));

    // --- Salvage / research materials ---
    public static final DeferredItem<Item> ESSENCE = ITEMS.registerSimpleItem("essence", p -> p.stacksTo(64));
    public static final DeferredItem<Item> TAINTED_ORE = ITEMS.registerSimpleItem("tainted_ore", p -> p.stacksTo(64));
    public static final DeferredItem<Item> TAINTED_INGOT = ITEMS.registerSimpleItem("tainted_ingot", p -> p.stacksTo(64));
    public static final DeferredItem<Item> SAMPLE_FLORA = ITEMS.registerSimpleItem("sample_flora", p -> p.stacksTo(64));
    public static final DeferredItem<Item> SAMPLE_FAUNA = ITEMS.registerSimpleItem("sample_fauna", p -> p.stacksTo(64));
    public static final DeferredItem<Item> SAMPLE_MINERAL = ITEMS.registerSimpleItem("sample_mineral", p -> p.stacksTo(64));
    public static final DeferredItem<Item> RELIC_SHARD = ITEMS.registerSimpleItem("relic_shard", p -> p.stacksTo(64));

    // ---------- S1 矿洞季:《三位起草人》残片 (批C) ----------

    /** 残片（肃/融/铭 ×6 页，身份在 eden:fragment 组件里；右键解读）。 */
    public static final DeferredItem<com.srqingchen.eden.item.FragmentItem> FRAGMENT =
            ITEMS.registerItem("fragment", p -> new com.srqingchen.eden.item.FragmentItem(p.stacksTo(16)));
    public static final DeferredItem<Item> SALVAGE_TECH = ITEMS.registerSimpleItem("salvage_tech", p -> p.stacksTo(64));
    public static final DeferredItem<Item> SALVAGE_ARTIFACT = ITEMS.registerSimpleItem("salvage_artifact", p -> p.stacksTo(16));

    // --- Utility ---
    public static final DeferredItem<Item> INSURANCE = ITEMS.registerSimpleItem("insurance", p -> p.stacksTo(1));
    public static final DeferredItem<CardPackItem> CARD_PACK = ITEMS.registerItem("card_pack", p -> new CardPackItem(p.stacksTo(16)));
    /** Right-click to open the difficulty editor (entity_modifier profile + return-pod charge speeds per difficulty). */
    public static final DeferredItem<DifficultyEditorItem> DIFFICULTY_EDITOR =
            ITEMS.registerItem("difficulty_editor", p -> new DifficultyEditorItem(p.stacksTo(1)));
    /** Erosion relief: right-click to purge 30 erosion (raid only); consumable. */
    public static final DeferredItem<PurifierItem> PURIFIER = ITEMS.registerItem("purifier",
            p -> new PurifierItem(p.stacksTo(16)));
    /** Intel tool (§11): right-click in a raid to read pollution density, threats, extraction bearings. */
    public static final DeferredItem<ScannerItem> SCANNER = ITEMS.registerItem("scanner",
            p -> new ScannerItem(p.stacksTo(1).durability(24)));

    // --- Raid gamble items (功能清单 §19.5) ---
    /** Hunter Beacon T1: consumed on raid entry, spawns a far-away super-champed hunter mob with rich drops. */
    public static final DeferredItem<Item> HUNTER_BEACON_T1 = ITEMS.registerSimpleItem("hunter_beacon_t1", p -> p.stacksTo(4));
    /** Greed's Tail: consumed on raid entry; -5% move speed this raid, settlement +50% / failure keep -50%. */
    public static final DeferredItem<Item> GREED_TAIL = ITEMS.registerSimpleItem("greed_tail", p -> p.stacksTo(4));
    // --- Ancient relics (终古遗物): earned by in-raid challenge feats, kept through success AND failure,
    //     consumed when unlocking LARGE talent nodes. ---
    public static final DeferredItem<Item> RELIC_WRAITH = ITEMS.registerSimpleItem("relic_wraith", p -> p.stacksTo(1));
    public static final DeferredItem<Item> RELIC_DEEP = ITEMS.registerSimpleItem("relic_deep", p -> p.stacksTo(1));
    public static final DeferredItem<Item> RELIC_STALKER = ITEMS.registerSimpleItem("relic_stalker", p -> p.stacksTo(1));

    // --- Cards (Curios "card" slot; passive attribute modifiers, no tick code) ---
    // Pure face: straight buffs.
    public static final DeferredItem<CardItem> CARD_SWIFT = ITEMS.registerItem("card_swift",
            p -> new CardItem(p.stacksTo(1), "card_swift", CardQuality.COMMON, List.of(
                    new CardItem.Modifier(Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))));
    public static final DeferredItem<CardItem> CARD_VITALITY = ITEMS.registerItem("card_vitality",
            p -> new CardItem(p.stacksTo(1), "card_vitality", CardQuality.COMMON, List.of(
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_BULWARK = ITEMS.registerItem("card_bulwark",
            p -> new CardItem(p.stacksTo(1), "card_bulwark", CardQuality.COMMON, List.of(
                    new CardItem.Modifier(Attributes.ARMOR, 4.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_EDGE = ITEMS.registerItem("card_edge",
            p -> new CardItem(p.stacksTo(1), "card_edge", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_FORTUNE = ITEMS.registerItem("card_fortune",
            p -> new CardItem(p.stacksTo(1), "card_fortune", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.LUCK, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    // Curse face: a large buff paired with a downside.
    public static final DeferredItem<CardItem> CARD_BLOODTHIRST = ITEMS.registerItem("card_bloodthirst",
            p -> new CardItem(p.stacksTo(1), "card_bloodthirst", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 6.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.MAX_HEALTH, -4.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_GALE = ITEMS.registerItem("card_gale",
            p -> new CardItem(p.stacksTo(1), "card_gale", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.MOVEMENT_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new CardItem.Modifier(Attributes.ARMOR, -3.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_TITAN = ITEMS.registerItem("card_titan",
            p -> new CardItem(p.stacksTo(1), "card_titan", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 10.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.MOVEMENT_SPEED, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))));
    // §19.2 curse cards - each with a Tab-key active (see CurseCardSystem).
    public static final DeferredItem<CardItem> CARD_GREED = ITEMS.registerItem("card_greed",
            p -> new CardItem(p.stacksTo(1), "card_greed", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.LUCK, 4.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.MAX_HEALTH, -2.0, AttributeModifier.Operation.ADD_VALUE)), "greed"));
    public static final DeferredItem<CardItem> CARD_GLASS = ITEMS.registerItem("card_glass",
            p -> new CardItem(p.stacksTo(1), "card_glass", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    new CardItem.Modifier(Attributes.ARMOR, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)), "glass"));
    public static final DeferredItem<CardItem> CARD_RESONANCE = ITEMS.registerItem("card_resonance",
            p -> new CardItem(p.stacksTo(1), "card_resonance", CardQuality.CURSE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.ARMOR_TOUGHNESS, -4.0, AttributeModifier.Operation.ADD_VALUE)), "resonance"));
    // Net-light / status / trigger / legendary cards (v1 card pool).
    public static final DeferredItem<CardItem> CARD_PURITY = ITEMS.registerItem("card_purity",
            p -> new CardItem(p.stacksTo(1), "card_purity", CardQuality.EPIC, List.of(
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<StatusCardItem> CARD_FISH = ITEMS.registerItem("card_fish",
            p -> new StatusCardItem(p.stacksTo(1), "card_fish", CardQuality.RARE, List.of(),
                    List.of(new StatusCardItem.Effect(MobEffects.NIGHT_VISION, 0),
                            new StatusCardItem.Effect(MobEffects.DOLPHINS_GRACE, 0))));
    public static final DeferredItem<StatusCardItem> CARD_IRONHIDE = ITEMS.registerItem("card_ironhide",
            p -> new StatusCardItem(p.stacksTo(1), "card_ironhide", CardQuality.EPIC, List.of(),
                    List.of(new StatusCardItem.Effect(MobEffects.RESISTANCE, 0))));
    public static final DeferredItem<CardItem> CARD_LEECH = ITEMS.registerItem("card_leech",
            p -> new CardItem(p.stacksTo(1), "card_leech", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_EMBER = ITEMS.registerItem("card_ember",
            p -> new CardItem(p.stacksTo(1), "card_ember", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.ATTACK_KNOCKBACK, 1.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_MENDING = ITEMS.registerItem("card_mending",
            p -> new CardItem(p.stacksTo(1), "card_mending", CardQuality.EPIC, List.of(
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_AEGIS = ITEMS.registerItem("card_aegis",
            p -> new CardItem(p.stacksTo(1), "card_aegis", CardQuality.EPIC, List.of(
                    new CardItem.Modifier(Attributes.ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_DAWN = ITEMS.registerItem("card_dawn",
            p -> new CardItem(p.stacksTo(1), "card_dawn", CardQuality.LEGENDARY, List.of(
                    new CardItem.Modifier(Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE),
                    new CardItem.Modifier(Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE))));
    // Intel + extraction cards (§8 撤离 / 情报类专用卡, §11 信息与情报博弈).
    public static final DeferredItem<CardItem> CARD_INSIGHT = ITEMS.registerItem("card_insight",
            p -> new CardItem(p.stacksTo(1), "card_insight", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_PATHFINDER = ITEMS.registerItem("card_pathfinder",
            p -> new CardItem(p.stacksTo(1), "card_pathfinder", CardQuality.RARE, List.of(
                    new CardItem.Modifier(Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL))));
    public static final DeferredItem<CardItem> CARD_RETREAT = ITEMS.registerItem("card_retreat",
            p -> new CardItem(p.stacksTo(1), "card_retreat", CardQuality.EPIC, List.of(
                    new CardItem.Modifier(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE))));
    public static final DeferredItem<CardItem> CARD_BEACON = ITEMS.registerItem("card_beacon",
            p -> new CardItem(p.stacksTo(1), "card_beacon", CardQuality.EPIC, List.of(
                    new CardItem.Modifier(Attributes.ARMOR, 1.0, AttributeModifier.Operation.ADD_VALUE))));

    // --- Block items ---
    public static final DeferredItem<BlockItem> TAINTED_SOIL_ITEM = ITEMS.registerSimpleBlockItem("tainted_soil", EdenBlocks.TAINTED_SOIL);
    public static final DeferredItem<BlockItem> TAINTED_STONE_ITEM = ITEMS.registerSimpleBlockItem("tainted_stone", EdenBlocks.TAINTED_STONE);
    public static final DeferredItem<BlockItem> TAINTED_GRASS_ITEM = ITEMS.registerSimpleBlockItem("tainted_grass", EdenBlocks.TAINTED_GRASS);
    public static final DeferredItem<BlockItem> TAINTED_COBBLESTONE_ITEM = ITEMS.registerSimpleBlockItem("tainted_cobblestone", EdenBlocks.TAINTED_COBBLESTONE);
    public static final DeferredItem<BlockItem> RETURN_POD_ITEM = ITEMS.registerSimpleBlockItem("return_pod", EdenBlocks.RETURN_POD);
    public static final DeferredItem<BlockItem> LAUNCH_PAD_ITEM = ITEMS.registerSimpleBlockItem("launch_pad", EdenBlocks.LAUNCH_PAD);
    public static final DeferredItem<BlockItem> SHOP_ITEM = ITEMS.registerSimpleBlockItem("shop", EdenBlocks.SHOP);
    public static final DeferredItem<BlockItem> RIFT_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("rift_altar", EdenBlocks.RIFT_ALTAR);
    public static final DeferredItem<BlockItem> POLLUTION_CORE_ITEM = ITEMS.registerSimpleBlockItem("pollution_core", EdenBlocks.POLLUTION_CORE);
    public static final DeferredItem<BlockItem> PARADISE_GATE_ITEM = ITEMS.registerSimpleBlockItem("paradise_gate", EdenBlocks.PARADISE_GATE);
    public static final DeferredItem<BlockItem> LOCKER_ITEM = ITEMS.registerSimpleBlockItem("locker", EdenBlocks.LOCKER);
    public static final DeferredItem<BlockItem> CHRONICLE_WALL_ITEM = ITEMS.registerSimpleBlockItem("chronicle_wall", EdenBlocks.CHRONICLE_WALL);
}
