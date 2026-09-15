package com.linguauniversalis.menu;

import com.linguauniversalis.block.CompanionChestBlockEntity;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 伙伴宝箱容器菜单：**54 格**（6 行 × 9）+ 玩家背包（原版大箱子的布局口径）。
 *
 * <p>布局与 {@code generic_54.png} 贴图逐像素对齐：箱子 6 行从 y=18 起，玩家背包标签 y=126，
 * 玩家 3 行从 y=140 起、快捷栏 y=198，面板总高 222。
 */
public class CompanionChestMenu extends AbstractContainerMenu {
    /** 箱子格数。 */
    public static final int CHEST_SIZE = CompanionChestBlockEntity.SIZE;
    /** 箱子区第一行 y（与 generic_54 对齐）。 */
    private static final int CHEST_Y = 18;
    /** 玩家背包区第一行 y。 */
    private static final int PLAYER_INV_Y = 140;

    private final Container container;
    private final BlockPos pos;

    /** 服务端：直接引用方块实体里的容器。 */
    public CompanionChestMenu(int containerId, Inventory playerInventory, Container container, BlockPos pos) {
        super(LURegistries.COMPANION_CHEST_MENU.get(), containerId);
        this.container = container;
        this.pos = pos;
        checkContainerSize(container, CHEST_SIZE);
        container.startOpen(playerInventory.player);

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, col + row * 9, 8 + col * 18, CHEST_Y + row * 18));
            }
        }
        addStandardInventorySlots(playerInventory, 8, PLAYER_INV_Y);
    }

    /** 客户端：从缓冲解出方块坐标，再取本地那份方块实体。 */
    public static CompanionChestMenu fromNetwork(int containerId, Inventory playerInventory,
            RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Container container = playerInventory.player.level().getBlockEntity(pos)
                instanceof CompanionChestBlockEntity chest
                        ? chest
                        : new SimpleContainer(CHEST_SIZE);
        return new CompanionChestMenu(containerId, playerInventory, container, pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < CHEST_SIZE) {
            if (!moveItemStackTo(stack, CHEST_SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, CHEST_SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
