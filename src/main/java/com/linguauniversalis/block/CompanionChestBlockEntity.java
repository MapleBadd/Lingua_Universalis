package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 伙伴宝箱方块实体：**54 格**共享收纳（设计 §6，多只魔物娘可以共用同一个箱子）。
 *
 * <p>存档方式与她的背包一致：存「槽位索引数组 + 物品列表」，读档后物品**留在原来的格子**里
 * （原版 {@code storeAsItemList/fromItemList} 会把物品压紧到最前面）。
 */
public class CompanionChestBlockEntity extends BlockEntity implements Container,
        net.minecraft.world.level.block.entity.LidBlockEntity {
    /** 54 格 = 6 行 × 9 列（与原版大箱子一致，但**不能合成大箱子**）。 */
    public static final int SIZE = 54;

    private static final String TAG_SLOT_INDEXES = "LUChestSlots";
    private static final String TAG_ITEMS = "LUChestItems";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CompanionChestBlockEntity(BlockPos pos, BlockState state) {
        super(LURegistries.COMPANION_CHEST_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- Container
    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /**
     * 盖子开合程度：**占位版恒为 0（不播开盖动画）**。
     *
     * <p>要实现开盖动画得走方块事件同步（原版 {@code ChestBlockEntity} 那样把 openCount 发给客户端），
     * 占位阶段不值得为它加一套同步，所以箱子一直是"合着"的样子。
     */
    @Override
    public float getOpenNess(float partialTick) {
        return 0.0F;
    }

    // ---------------------------------------------------------------- 存档
    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        List<Integer> slotIndexes = new ArrayList<>();
        ValueOutput.TypedOutputList<ItemStack> list = out.list(TAG_ITEMS, ItemStack.CODEC);
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                slotIndexes.add(i);
                list.add(stack);
            }
        }
        int[] indexes = new int[slotIndexes.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = slotIndexes.get(i);
        }
        out.putIntArray(TAG_SLOT_INDEXES, indexes);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        int[] indexes = in.getIntArray(TAG_SLOT_INDEXES).orElse(new int[0]);
        int i = 0;
        for (ItemStack stack : in.listOrEmpty(TAG_ITEMS, ItemStack.CODEC)) {
            int slot = i < indexes.length ? indexes[i] : i;
            if (!stack.isEmpty() && slot >= 0 && slot < SIZE) {
                items.set(slot, stack);
            }
            i++;
        }
    }

    // ---------------------------------------------------------------- 供魔物娘存取
    /** 就近存入：把整叠物品塞进箱子，返回放不下的剩余。 */
    public ItemStack insert(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < SIZE && !remaining.isEmpty(); i++) {
            ItemStack existing = items.get(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount();
            int moved = Math.min(room, remaining.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
                setChanged();
            }
        }
        for (int i = 0; i < SIZE && !remaining.isEmpty(); i++) {
            if (!items.get(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            items.set(i, placed);
            remaining.shrink(moved);
            setChanged();
        }
        return remaining;
    }

    /** 找一个匹配的物品（饿时从箱子里取食物用）；没找到返回 null。 */
    @Nullable
    public ItemStack findFirst(java.util.function.Predicate<ItemStack> filter) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** 取出指定物品 1 个（用于取食）。 */
    @Nullable
    public ItemStack takeOne(java.util.function.Predicate<ItemStack> filter) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || !filter.test(stack)) {
                continue;
            }
            ItemStack taken = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
            setChanged();
            return taken;
        }
        return null;
    }

    /** 里面的东西全部撒到地上（被破坏时）。 */
    public void dropContents() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        for (ItemStack stack : new ArrayList<>(items)) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(this.level,
                        this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), stack);
            }
        }
        clearContent();
    }
}
