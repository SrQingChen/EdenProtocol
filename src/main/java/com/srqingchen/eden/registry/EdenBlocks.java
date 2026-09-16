package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.block.ChronicleWallBlock;
import com.srqingchen.eden.block.LaunchPadBlock;
import com.srqingchen.eden.block.LockerBlock;
import com.srqingchen.eden.block.ParadiseGateBlock;
import com.srqingchen.eden.block.PollutionCoreBlock;
import com.srqingchen.eden.block.ReturnPodBlock;
import com.srqingchen.eden.block.ReturnPodExtensionBlock;
import com.srqingchen.eden.block.ShopBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tainted terrain blocks that resurface the raid overworld. Functional blocks (launch pad, shop,
 * rift altar, return pod, locator block) are registered in their own feature milestones because
 * they need BlockEntity/behaviour.
 */
public class EdenBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EdenProtocol.MODID);

    public static final DeferredBlock<Block> TAINTED_SOIL = BLOCKS.registerSimpleBlock("tainted_soil",
            p -> p.mapColor(MapColor.DIRT).strength(0.5f).sound(SoundType.GRAVEL));

    public static final DeferredBlock<Block> TAINTED_STONE = BLOCKS.registerSimpleBlock("tainted_stone",
            p -> p.mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> TAINTED_GRASS = BLOCKS.registerSimpleBlock("tainted_grass",
            p -> p.mapColor(MapColor.GRASS).strength(0.6f).sound(SoundType.GRASS));

    /** Tainted cobblestone: what tainted stone drops when mined, mirroring vanilla stone -> cobblestone. */
    public static final DeferredBlock<Block> TAINTED_COBBLESTONE = BLOCKS.registerSimpleBlock("tainted_cobblestone",
            p -> p.mapColor(MapColor.STONE).strength(1.5f, 6.0f).requiresCorrectToolForDrops());

    /** Deployable extraction objective (placed by the locator item). */
    public static final DeferredBlock<ReturnPodBlock> RETURN_POD = BLOCKS.registerBlock("return_pod",
            ReturnPodBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0f, 3.0f).noOcclusion().lightLevel(s -> 6));

    /**
     * Invisible filler cells above the return pod, giving its ~4-block-tall model real per-cell collision and
     * targeting. Technical block: no item, never in a creative tab, only placed by the locator together with
     * the pod base. Same hardness as the pod so breaking any cell feels consistent.
     */
    public static final DeferredBlock<ReturnPodExtensionBlock> RETURN_POD_EXTENSION = BLOCKS.registerBlock("return_pod_extension",
            ReturnPodExtensionBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0f, 3.0f).noOcclusion());

    /** Ark launch pad: right-click to enter a scout raid. */
    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.registerBlock("launch_pad",
            LaunchPadBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0f, 3.0f).lightLevel(s -> 8));

    /** Ark requisition terminal: right-click for the shop menu. */
    public static final DeferredBlock<ShopBlock> SHOP = BLOCKS.registerBlock("shop",
            ShopBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0f, 3.0f).lightLevel(s -> 6));

    /** Raid-world objective: mining it lowers the shared campaign pollution (guards spawn on approach). */
    public static final DeferredBlock<PollutionCoreBlock> POLLUTION_CORE = BLOCKS.registerBlock("pollution_core",
            PollutionCoreBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(8.0f, 9.0f).lightLevel(s -> 7));

    /** Campaign-victory landmark: right-click to cross between the ark and the purified paradise. */
    public static final DeferredBlock<ParadiseGateBlock> PARADISE_GATE = BLOCKS.registerBlock("paradise_gate",
            ParadiseGateBlock::new, p -> p.mapColor(MapColor.EMERALD).strength(20.0f).lightLevel(s -> 13)
                    .requiresCorrectToolForDrops());

    /** Personal stash terminal (§13): every player sees their own 27-slot SavedData locker. */
    public static final DeferredBlock<LockerBlock> LOCKER = BLOCKS.registerBlock("locker",
            LockerBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0f, 3.0f).lightLevel(s -> 4));

    /** Chronicle wall (§13) = the campaign panel (§14): pollution gauge, stage, tallies, highlights. */
    public static final DeferredBlock<ChronicleWallBlock> CHRONICLE_WALL = BLOCKS.registerBlock("chronicle_wall",
            ChronicleWallBlock::new, p -> p.mapColor(MapColor.COLOR_CYAN).strength(3.0f, 3.0f).lightLevel(s -> 5));
}
