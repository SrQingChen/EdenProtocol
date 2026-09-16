package com.srqingchen.eden.block;

import com.mojang.serialization.MapCodec;
import com.srqingchen.eden.data.LockerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.UUID;

/**
 * The ark locker terminal (§13 个人储物柜): right-click to open YOUR private 27-slot stash. The block is
 * a shared terminal; the menu served is a vanilla {@link ChestMenu} (3 rows) over a write-through view
 * of the player's {@link LockerData} slots, so items land in SavedData the moment they move and the
 * familiar chest UI needs no custom screen.
 */
public class LockerBlock extends Block {
    public static final MapCodec<LockerBlock> CODEC = simpleCodec(LockerBlock::new);

    public LockerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        LockerData data = LockerData.get(level.getServer());
        sp.openMenu(new LockerMenuProvider(sp.getUUID(), sp.getName().getString(), data));
        return InteractionResult.SUCCESS;
    }

    /** Serves a 3-row chest menu bound to the opener's own locker slots. */
    private record LockerMenuProvider(UUID owner, String ownerName, LockerData data) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("eden.locker.title", ownerName);
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
            return ChestMenu.threeRows(id, playerInv, new LockerContainer(data.slotsOf(owner), data));
        }
    }

    /** A write-through {@link Container} view: slot moves persist via {@link LockerData#setDirty}. */
    private static final class LockerContainer implements Container {
        private final List<ItemStack> slots;
        private final LockerData data;

        LockerContainer(List<ItemStack> slots, LockerData data) {
            this.slots = slots;
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return this.slots.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : this.slots) {
                if (!s.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return this.slots.get(index);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack split = this.slots.get(index).split(count);
            setChanged();
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack out = this.slots.get(index);
            this.slots.set(index, ItemStack.EMPTY);
            setChanged();
            return out;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            this.slots.set(index, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            this.data.setDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < this.slots.size(); i++) {
                this.slots.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}
