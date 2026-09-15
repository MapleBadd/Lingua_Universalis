package com.linguauniversalis.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 魔物娘的快捷栏 + 背包容器（连续容器：{@code 0..快捷栏数-1} = 快捷栏，其后 = 背包；
 * 槽位含义见 {@link com.linguauniversalis.core.behavior.InventoryRules}）。
 *
 * <p>为什么不用原版 {@code SimpleContainer#storeAsItemList/fromItemList}：那对方法读档时会把物品
 * **压紧到最前面**（逐个 {@code addItem}），槽位位置会变；这里自己存「槽位索引数组 + 物品列表」，
 * 读档后物品还在原来的格子里。
 */
public class GirlInventory extends SimpleContainer {
    private static final String TAG_SLOT_INDEXES = "LUInvSlots";
    private static final String TAG_ITEMS = "LUInvItems";
    private static final int[] NO_SLOTS = new int[0];

    public GirlInventory(int size) {
        super(Math.max(1, size));
    }

    /** 存档：只写非空格，另存它们的槽位索引。 */
    public void save(ValueOutput out) {
        List<Integer> slotIndexes = new ArrayList<>();
        ValueOutput.TypedOutputList<ItemStack> items = out.list(TAG_ITEMS, ItemStack.CODEC);
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                slotIndexes.add(i);
                items.add(stack);
            }
        }
        int[] indexes = new int[slotIndexes.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = slotIndexes.get(i);
        }
        out.putIntArray(TAG_SLOT_INDEXES, indexes);
    }

    /** 读档：按槽位索引放回原格。 */
    public void load(ValueInput in) {
        clearContent();
        int[] indexes = in.getIntArray(TAG_SLOT_INDEXES).orElse(NO_SLOTS);
        int i = 0;
        for (ItemStack stack : in.listOrEmpty(TAG_ITEMS, ItemStack.CODEC)) {
            int slot = i < indexes.length ? indexes[i] : i;
            if (!stack.isEmpty() && slot >= 0 && slot < getContainerSize()) {
                setItem(slot, stack);
            }
            i++;
        }
    }

    /** 取出全部内容并清空（死亡掉落用）。 */
    public List<ItemStack> drainContents() {
        List<ItemStack> dropped = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                dropped.add(stack.copy());
                setItem(i, ItemStack.EMPTY);
            }
        }
        clearContent();
        return dropped;
    }

    // ---------------------------------------------------------------- 区间操作（快捷栏 / 背包）
    /** 指定区间里是否还有空位。 */
    public boolean hasFreeSlot(int startInclusive, int endExclusive) {
        return firstEmptyIn(startInclusive, endExclusive) >= 0;
    }

    /** 整个容器（快捷栏 + 背包）里是否还有空位。 */
    public boolean hasFreeSlot() {
        return hasFreeSlot(0, getContainerSize());
    }

    /** 区间内第一个空格；没有返回 -1。 */
    public int firstEmptyIn(int startInclusive, int endExclusive) {
        for (int i = clampStart(startInclusive); i < Math.min(endExclusive, getContainerSize()); i++) {
            if (getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 把整叠物品放进指定区间（先并到同类堆上，再找空格），返回**放不下的剩余**。
     *
     * <p>与 {@link net.minecraft.world.SimpleContainer#addItem} 的区别：只在给定区间里找位置，
     * 所以可以"只往背包放"、"只往快捷栏放"。
     */
    public ItemStack addIntoRange(ItemStack stack, int startInclusive, int endExclusive) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();
        int start = clampStart(startInclusive);
        int end = Math.min(endExclusive, getContainerSize());
        // 1) 并入同类堆
        for (int i = start; i < end && !remaining.isEmpty(); i++) {
            ItemStack existing = getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            setChanged();
        }
        // 2) 找空格整叠放入
        for (int i = start; i < end && !remaining.isEmpty(); i++) {
            if (!getItem(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), getMaxStackSize()));
            ItemStack placed = remaining.copy();
            placed.setCount(moved);
            setItem(i, placed);
            remaining.shrink(moved);
        }
        return remaining;
    }

    /**
     * 把某个格位的整叠物品搬到指定区间（例如"手上 → 背包"）；一点都放不下时原样留在原格。
     *
     * @return true = 该格已搬空
     */
    public boolean moveIntoRange(int index, int startInclusive, int endExclusive) {
        if (index < 0 || index >= getContainerSize()) {
            return false;
        }
        ItemStack stack = getItem(index);
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack leftover = addIntoRange(stack, startInclusive, endExclusive);
        if (leftover.getCount() == stack.getCount()) {
            return false; // 一点都放不下
        }
        setItem(index, leftover.isEmpty() ? ItemStack.EMPTY : leftover);
        setChanged();
        return leftover.isEmpty();
    }

    /** 交换两个格位（武器换手、把食物挪到副手槽都用它）。 */
    public void swapSlots(int first, int second) {
        if (first == second || first < 0 || second < 0
                || first >= getContainerSize() || second >= getContainerSize()) {
            return;
        }
        ItemStack a = getItem(first);
        ItemStack b = getItem(second);
        setItem(first, b);
        setItem(second, a);
        setChanged();
    }

    private int clampStart(int startInclusive) {
        return Math.max(0, Math.min(startInclusive, getContainerSize()));
    }

    /**
     * 换容量（物种档案变化时）：能装下的搬过去，装不下的**作为溢出返回**由调用方负责掉落。
     */
    public GirlInventory resized(int newSize, List<ItemStack> overflowOut) {
        GirlInventory resized = new GirlInventory(newSize);
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack leftover = resized.addItem(stack.copy());
            if (!leftover.isEmpty()) {
                overflowOut.add(leftover);
            }
        }
        return resized;
    }
}
