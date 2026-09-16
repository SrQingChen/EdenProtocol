package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.network.EdenNetwork;
import com.srqingchen.eden.registry.EdenAttachments;
import com.srqingchen.eden.registry.EdenEffects;
import com.srqingchen.eden.system.RaidWorldFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.srqingchen.eden.attachment.RaidState;

/**
 * 污染积液 (tainted sludge, 功能清单 §10 v2 浊潮生态): a creeping, liquid-like hazard pooled around
 * pollution cores and boss lairs and summoned by 浊雨. Implemented as a collisionless spreading BLOCK
 * (not a vanilla fluid) so the whole pipeline - placement, spread, decay, effects - stays mod-side:
 * <ul>
 *   <li>{@code LEVEL 0..3}: 3 = source (never decays), lower levels are creeping edges.</li>
 *   <li>random ticks spread it: downwards keeps the level, sideways drops one, into dead ends it dies.</li>
 *   <li>standing in it applies {@code eden:pollution}, slows the entity and ticks erosion on raiders.</li>
 * </ul>
 */
public class TaintedSludgeBlock extends Block {
    public static final MapCodec<TaintedSludgeBlock> CODEC = simpleCodec(TaintedSludgeBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    private static final VoxelShape EMPTY = Shapes.empty();

    public TaintedSludgeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 3));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    /** Liquid: no placement by players (code + rain only), nothing collides with it. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EMPTY;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EMPTY;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int lvl = state.getValue(LEVEL);
        if (random.nextInt(4) != 0) {
            return;   // slow creep - the hazard advances over minutes, not ticks
        }
        // Falling straight down keeps its strength (a drip column).
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            level.setBlock(below, this.defaultBlockState().setValue(LEVEL, lvl), 3);
            return;
        }
        if (lvl <= 0) {
            return;   // the creeping edge has nowhere further to go
        }
        // Sideways creep into air one level weaker.
        BlockPos[] sides = {pos.north(), pos.south(), pos.east(), pos.west()};
        BlockPos target = sides[random.nextInt(sides.length)];
        if (level.getBlockState(target).isAir()) {
            level.setBlock(target, this.defaultBlockState().setValue(LEVEL, lvl - 1), 3);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                  net.minecraft.world.entity.InsideBlockEffectApplier applier, boolean moved) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer sp)) {
            return;
        }
        sp.addEffect(new MobEffectInstance(EdenEffects.POLLUTION, 100, 0, true, true));
        if (sp.tickCount % 20 == 0 && RaidWorldFeatures.isRaidLevel((ServerLevel) level)) {
            RaidState rs = sp.getData(EdenAttachments.RAID_STATE);
            if (rs.inRaid) {
                rs.erosion = Math.min(com.srqingchen.eden.EdenConfig.EROSION_MAX.get(), rs.erosion + 0.4f);
                EdenNetwork.syncTo(sp);
            }
        }
        if (sp.tickCount % 40 == 0) {
            sp.setDeltaMovement(sp.getDeltaMovement().multiply(0.6, 0.8, 0.6));
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    public static BlockState source() {
        return com.srqingchen.eden.registry.EdenBlocks.TAINTED_SLUDGE.get().defaultBlockState().setValue(LEVEL, 3);
    }

    public static BlockState edge(int level) {
        return com.srqingchen.eden.registry.EdenBlocks.TAINTED_SLUDGE.get().defaultBlockState()
                .setValue(LEVEL, Math.max(0, Math.min(3, level)));
    }
}
