package com.srqingchen.eden.item;

import com.srqingchen.eden.block.ReturnPodBlockEntity;
import com.srqingchen.eden.registry.EdenBlocks;
import com.srqingchen.eden.registry.EdenItems;
import com.srqingchen.eden.system.RaidWorldFeatures;
import com.srqingchen.eden.talent.TalentSystem;
import com.srqingchen.eden.util.EdenMessages;
import com.srqingchen.eden.util.EdenMessages.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The scanner (扫描器, 功能清单 §11): the crew's intel tool. Right-click in a raid world to read a
 * situation report: nearby pollution density (taint fraction of the surface sample), threats (hostiles
 * in scan range), and the bearing + distance to the nearest still-standing extraction point. Each scan
 * also peels one more layer off the raid's hidden affixes (逐步揭示). Consumes durability; the insight
 * card halves the cooldown and the prospector's scan talent widens the range.
 */
public class ScannerItem extends Item {
    /** Ticks of scanner cooldown (halved with the insight card equipped). */
    private static final int COOLDOWN_TICKS = 200;
    /** Surface sample radius (blocks) for the pollution-density readout. */
    private static final int DENSITY_RADIUS = 24;
    /** Threat scan radius (blocks). */
    private static final double THREAT_RADIUS = 48.0;
    /** Prospector scan talent (pro_scan) bonus range. */
    private static final double PRO_BONUS = 8.0;

    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel raid)) {
            return InteractionResult.PASS;
        }
        if (!raid.dimension().equals(com.srqingchen.eden.dimension.EdenDimensions.RAID_OVERWORLD)) {
            EdenMessages.send(sp, Type.WARNING, "eden.msg.scanner_not_in_raid");
            return InteractionResult.PASS;
        }
        scan(raid, sp, stack);
        boolean insight = CurioCards.isEquipped(sp, (CardItem) EdenItems.CARD_INSIGHT.get());
        sp.getCooldowns().addCooldown(stack, insight ? COOLDOWN_TICKS / 2 : COOLDOWN_TICKS);
        return InteractionResult.CONSUME;
    }

    private void scan(ServerLevel raid, ServerPlayer sp, ItemStack stack) {
        // Durability: one tick per scan; creative scans are free.
        if (!sp.isCreative()) {
            stack.hurtAndBreak(1, sp, EquipmentSlot.MAINHAND);
        }
        raid.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);

        // 1) Pollution density: fraction of tainted surface blocks in the sample ring.
        int tainted = 0;
        int sampled = 0;
        for (int dx = -DENSITY_RADIUS; dx <= DENSITY_RADIUS; dx += 4) {
            for (int dz = -DENSITY_RADIUS; dz <= DENSITY_RADIUS; dz += 4) {
                int x = sp.blockPosition().getX() + dx;
                int z = sp.blockPosition().getZ() + dz;
                if (!raid.hasChunkAt(x, z)) {
                    continue;
                }
                int y = raid.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockState surface = raid.getBlockState(new BlockPos(x, y, z));
                sampled++;
                if (surface.is(EdenBlocks.TAINTED_GRASS.get())
                        || surface.is(EdenBlocks.TAINTED_SOIL.get())
                        || surface.is(EdenBlocks.TAINTED_STONE.get())) {
                    tainted++;
                }
            }
        }
        int densityPct = sampled == 0 ? 0 : Math.round(100f * tainted / sampled);

        // 2) Threats: hostiles inside the (possibly talent-widened) radius.
        double threatRange = THREAT_RADIUS + (TalentSystem.hasMech(sp, "pro_scan") ? PRO_BONUS : 0.0);
        List<Monster> hostiles = raid.getEntitiesOfClass(Monster.class,
                new AABB(sp.blockPosition()).inflate(threatRange), Monster::isAlive);
        long closeThreats = hostiles.stream().filter(m -> m.distanceToSqr(sp) <= 24.0 * 24.0).count();

        // 3) Nearest extraction point + charging pods (distance + compass bearing).
        BlockPos nearest = RaidWorldFeatures.nearestExtractionPoint(raid, sp.position());
        EdenMessages.send(sp, Type.INFO, "eden.msg.scanner_density", densityPct);
        EdenMessages.send(sp, hostiles.isEmpty() || closeThreats == 0 ? Type.INFO : Type.WARNING,
                "eden.msg.scanner_threats", hostiles.size(), (int) closeThreats);
        if (nearest != null) {
            EdenMessages.send(sp, Type.SUCCESS, "eden.msg.scanner_extract",
                    bearing(sp.blockPosition(), nearest),
                    (int) Math.sqrt(sp.blockPosition().distSqr(nearest)));
        } else {
            EdenMessages.send(sp, Type.WARNING, "eden.msg.scanner_no_extract");
        }
        ReturnPodBlockEntity charging = ReturnPodBlockEntity.findChargingPodNear(raid, sp.position(), 1e6);
        if (charging != null && charging.isActive()) {
            EdenMessages.send(sp, Type.SUCCESS, "eden.msg.scanner_charging",
                    (int) (charging.getChargeProgress() / ReturnPodBlockEntity.CHARGE_MAX * 100f));
        }

        // 4) Intel: reveal one more hidden affix (§11 逐步揭示).
        com.srqingchen.eden.system.AffixSystem.revealNextAffix(sp, "eden.msg.affix_scanned");

        // Scan pulse particle ring so the readout has a visual beat.
        raid.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2, sp.getZ(),
                24, 2.0, 0.4, 2.0, 0.02);
    }

    /** Human compass bearing from {@code from} to {@code to} (N / NE / E / ... in the viewer's locale). */
    static Component bearing(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));   // 0=E, 90=S (MC -Z is north)
        if (angle < 0) {
            angle += 360;
        }
        // Rotate so 0 = north (-Z), then bucket into 8 compass sectors.
        int sector = (int) Math.floor(((angle + 270.0) % 360.0) / 45.0);
        String key = switch (sector) {
            case 0 -> "eden.dir.north";
            case 1 -> "eden.dir.northeast";
            case 2 -> "eden.dir.east";
            case 3 -> "eden.dir.southeast";
            case 4 -> "eden.dir.south";
            case 5 -> "eden.dir.southwest";
            case 6 -> "eden.dir.west";
            default -> "eden.dir.northwest";
        };
        return Component.translatable(key);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 net.minecraft.world.item.component.TooltipDisplay display,
                                 java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("eden.tooltip.scanner").withStyle(
                net.minecraft.ChatFormatting.DARK_AQUA));
    }
}
