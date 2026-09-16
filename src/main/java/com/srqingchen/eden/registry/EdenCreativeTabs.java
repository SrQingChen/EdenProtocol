package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Single creative tab listing all Eden Protocol content (expanded as milestones add items/blocks). */
public class EdenCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EdenProtocol.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EDEN_TAB =
            CREATIVE_MODE_TABS.register("eden_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eden"))
                    .icon(() -> EdenItems.TAINT_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Blocks
                        output.accept(EdenBlocks.TAINTED_SOIL.get());
                        output.accept(EdenBlocks.TAINTED_STONE.get());
                        output.accept(EdenBlocks.TAINTED_COBBLESTONE.get());
                        output.accept(EdenBlocks.TAINTED_GRASS.get());
                        output.accept(EdenItems.RETURN_POD_ITEM.get());
                        output.accept(EdenItems.LAUNCH_PAD_ITEM.get());
                        output.accept(EdenItems.SHOP_ITEM.get());
                        output.accept(EdenItems.POLLUTION_CORE_ITEM.get());
                        output.accept(EdenItems.PARADISE_GATE_ITEM.get());
                        output.accept(EdenItems.LOCKER_ITEM.get());
                        output.accept(EdenItems.CHRONICLE_WALL_ITEM.get());
                        output.accept(EdenItems.RIFT_ALTAR_ITEM.get());
                        // Items
                        output.accept(EdenItems.LOCATOR.get());
                        output.accept(EdenItems.TAINT_CRYSTAL.get());
                        output.accept(EdenItems.EDEN_CELL.get());
                        output.accept(EdenItems.ESSENCE.get());
                        output.accept(EdenItems.TAINTED_ORE.get());
                        output.accept(EdenItems.TAINTED_INGOT.get());
                        output.accept(EdenItems.SAMPLE_FLORA.get());
                        output.accept(EdenItems.SAMPLE_FAUNA.get());
                        output.accept(EdenItems.SAMPLE_MINERAL.get());
                        output.accept(EdenItems.RELIC_SHARD.get());
                        output.accept(EdenItems.SALVAGE_TECH.get());
                        output.accept(EdenItems.SALVAGE_ARTIFACT.get());
                        output.accept(EdenItems.INSURANCE.get());
                        output.accept(EdenItems.CARD_PACK.get());
                        output.accept(EdenItems.DIFFICULTY_EDITOR.get());
                        // Erosion relief + raid gamble goods (§19.5) + ancient relics.
                        output.accept(EdenItems.PURIFIER.get());
                        output.accept(EdenItems.HUNTER_BEACON_T1.get());
                        output.accept(EdenItems.GREED_TAIL.get());
                        output.accept(EdenItems.RELIC_WRAITH.get());
                        output.accept(EdenItems.RELIC_DEEP.get());
                        output.accept(EdenItems.RELIC_STALKER.get());
                        // Intel tool (§11).
                        output.accept(EdenItems.SCANNER.get());
                        // Cards: pure ladder, then curses.
                        output.accept(EdenItems.CARD_SWIFT.get());
                        output.accept(EdenItems.CARD_VITALITY.get());
                        output.accept(EdenItems.CARD_BULWARK.get());
                        output.accept(EdenItems.CARD_EDGE.get());
                        output.accept(EdenItems.CARD_FORTUNE.get());
                        output.accept(EdenItems.CARD_FISH.get());
                        output.accept(EdenItems.CARD_LEECH.get());
                        output.accept(EdenItems.CARD_EMBER.get());
                        output.accept(EdenItems.CARD_PURITY.get());
                        output.accept(EdenItems.CARD_IRONHIDE.get());
                        output.accept(EdenItems.CARD_MENDING.get());
                        output.accept(EdenItems.CARD_AEGIS.get());
                        output.accept(EdenItems.CARD_DAWN.get());
                        output.accept(EdenItems.CARD_INSIGHT.get());
                        output.accept(EdenItems.CARD_PATHFINDER.get());
                        output.accept(EdenItems.CARD_RETREAT.get());
                        output.accept(EdenItems.CARD_BEACON.get());
                        output.accept(EdenItems.CARD_BLOODTHIRST.get());
                        output.accept(EdenItems.CARD_GALE.get());
                        output.accept(EdenItems.CARD_TITAN.get());
                        output.accept(EdenItems.CARD_GREED.get());
                        output.accept(EdenItems.CARD_GLASS.get());
                        output.accept(EdenItems.CARD_RESONANCE.get());
                    })
                    .build());
}
