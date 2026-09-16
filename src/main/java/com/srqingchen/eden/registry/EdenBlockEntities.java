package com.srqingchen.eden.registry;

import com.srqingchen.eden.EdenProtocol;
import com.srqingchen.eden.block.ReturnPodBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity types. Registered after blocks (see {@link EdenProtocol}) so the pod block is
 * available when the type is built.
 */
public class EdenBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, EdenProtocol.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReturnPodBlockEntity>> RETURN_POD =
            BLOCK_ENTITIES.register("return_pod",
                    () -> new BlockEntityType<>(ReturnPodBlockEntity::new, EdenBlocks.RETURN_POD.get()));
}
