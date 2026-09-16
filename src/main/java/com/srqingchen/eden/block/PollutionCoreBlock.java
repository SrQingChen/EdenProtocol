package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pollution core (污染核心): a raid-world objective that roots the taint. Mining it out (it is tough -
 * hardness 8) pays rich research salvage and permanently lowers the shared campaign pollution
 * ({@link com.srqingchen.eden.system.PollutionCoreSystem} listens to the break). Its guardians spawn on
 * approach, not on placement, so stumbling into one is a mid-raid event. Purely visual ambient venting
 * here; all logic lives in the system.
 */
public class PollutionCoreBlock extends Block {
    public static final MapCodec<PollutionCoreBlock> CODEC = simpleCodec(PollutionCoreBlock::new);

    public PollutionCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Client-side ambient: taint mist coils and purple wisps so the core reads as malignant. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }
        level.addParticle(net.minecraft.core.particles.ParticleTypes.WITCH,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getY() + 1.05,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                0.0, 0.03, 0.0);
        if (random.nextInt(2) == 0) {
            level.addParticle(com.srqingchen.eden.registry.EdenParticles.TAINT_MIST.get(),
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6,
                    pos.getY() + 0.3 + random.nextDouble() * 1.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6,
                    0.0, 0.012, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 0.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    0.0, 0.02, 0.0);
        }
    }
}
