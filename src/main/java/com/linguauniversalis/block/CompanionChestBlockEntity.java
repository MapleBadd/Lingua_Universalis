package com.linguauniversalis.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.linguauniversalis.menu.CompanionChestMenu;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 伙伴宝箱方块实体：**直接继承原版 {@link ChestBlockEntity}** + GeckoLib 开合动画。
 *
 * <p>为什么继承原版：容器逻辑、开合计数与同步、音效、被遮挡判定、
 * <b>AI 寻路语义</b>（原版箱子不会被寻路当成"可跨越的矮障碍"）全部由原版负责。
 * 我们只覆盖三件事：<b>格数 54</b>、<b>自定义界面</b>、<b>GeckoLib 模型与开合动画</b>。
 *
 * <p>开合状态直接读原版同步好的 {@code getOpenNess(...)}（由 {@code openersCounter}
 * 经方块事件同步到客户端），不再自己镜像方块状态。
 */
public class CompanionChestBlockEntity extends ChestBlockEntity implements GeoBlockEntity {
    /** 54 格 = 6 行 × 9 列（与原版大箱子一致，但**不能合成大箱子**）。 */
    public static final int SIZE = 54;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** 是否曾经打开过：放置瞬间保持默认姿态，不播动画。 */
    /** 开箱 / 关箱动画（复用同一个对象：每次新建可能被判成"新动画"而反复从头播）。 */
    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("close");

    /** 盖子当前动画状态（true = 已切到"开"）；只在状态变化时触发一次。 */
    private boolean lidOpen;

    private boolean hasOpened;

    /**
     * 自己的 54 格背包。
     *
     * <p>为什么必须覆盖 {@code getItems()}：原版 {@code ChestBlockEntity} 的底层列表是
     * <b>固定 27 格</b>初始化出来的，光把 {@code getContainerSize()} 改 54 并不会扩容 ——
     * 打开界面后操作第 28 格就会 {@code ArrayIndexOutOfBoundsException}，服务端直接踢人。
     */
    private NonNullList<ItemStack> slots = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.slots;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.slots = items;
    }

    public CompanionChestBlockEntity(BlockPos pos, BlockState state) {
        super(LURegistries.COMPANION_CHEST_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- 容器

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.lingua_universalis.chest_of_companions");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new CompanionChestMenu(containerId, playerInventory, this, this.worldPosition);
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    /**
     * 开合动画：{@code open} 把盖子从 0° 转到 -77.5°，{@code close} 是同一段关键帧反向。
     *
     * <p>状态取自原版 {@code getOpenNess}；从未打开过（刚放下）时保持模型默认姿态、不播动画。
     */
    @Override
    public void registerControllers(com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CompanionChestBlockEntity>("lid", 2, state -> {
            CompanionChestBlockEntity chest = state.animatable();
            // 读方块状态 open（服务端在 startOpen/stopOpen 里镜像，会同步到客户端）
            boolean open = chest.getBlockState()
                    .getOptionalValue(CompanionChestBlock.OPEN).orElse(false);
            if (open) {
                chest.hasOpened = true;
                return state.setAndContinue(OPEN_ANIM);
            }
            if (!chest.hasOpened) {
                // 刚放下 / 从没开过：保持模型默认（合上）姿态，不播动画
                return PlayState.STOP;
            }
            return state.setAndContinue(CLOSE_ANIM);
        }));
    }

    /**
     * 当前是否有人开着这个箱子（**用原版同步过的 openersCounter**）。
     *
     * <p>为什么不用 {@code getOpenNess}：它是给原版渲染插值用的浮点数，
     * 关箱过程中仍会持续 &gt; 0，动画控制器会误以为还开着、反复重播 {@code open}，
     * 结果就是"关掉界面后盖子又掀开、卡在开启状态"。
     */
    /**
     * 自己维护的开合计数（**不用原版 openersCounter**）。
     *
     * <p>为什么不用原版：原版计数只要有一次 {@code stopOpen} 没被调用（关界面时玩家掉线、
     * 服务端强制关菜单等），就会**永久泄漏 +1**。泄漏之后：{@code startOpen} 看到"已经有人开着"
     * 就不再播开箱音、状态也不变化 → 表现为"第一次开合正常，之后既没动画也没开箱音，
     * 但关箱音还在"。
     */
    private int luOpeners;

    /** 打开箱子：计数 0→1 时播开箱音并把方块状态置为「开」。 */
    @Override
    public void startOpen(net.minecraft.world.entity.ContainerUser user) {
        // 自愈：进入本方法前若"当前没有其它玩家开着它"，说明上一次计数泄漏了（掉线等），清零
        if (this.getEntitiesWithContainerOpen().isEmpty()) {
            this.luOpeners = 0;
        }
        super.startOpen(user);
        if (this.luOpeners == 0) {
            playLidSound(net.minecraft.sounds.SoundEvents.COPPER_CHEST_OPEN);
        }
        this.luOpeners++;
        setOpenState(true);
    }

    /** 关闭箱子：计数降到 0 时播关箱音并把方块状态置为「合」。 */
    @Override
    public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
        super.stopOpen(user);
        this.luOpeners = Math.max(0, this.luOpeners - 1);
        if (this.luOpeners == 0) {
            playLidSound(net.minecraft.sounds.SoundEvents.COPPER_CHEST_CLOSE);
            setOpenState(false);
        }
    }

    /** 方块实体被移除 / 区块卸载：清掉计数，避免残留导致下次开合失灵。 */
    @Override
    public void setRemoved() {
        super.setRemoved();
        this.luOpeners = 0;
    }

    /** 当前开合计数（本类自己维护）。 */
    public int openCount() {
        return this.luOpeners;
    }

    /** 播放铜箱子的开/关音效（服务端播放，附近玩家都能听到）。 */
    private void playLidSound(net.minecraft.sounds.SoundEvent sound) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.level.playSound(null, this.worldPosition, sound,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                this.level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /** 把"是否打开"写进方块状态（渲染读它，客户端才能看到动画）。 */
    private void setOpenState(boolean open) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.hasProperty(CompanionChestBlock.OPEN)
                && state.getValue(CompanionChestBlock.OPEN) != open) {
            this.level.setBlock(this.worldPosition,
                    state.setValue(CompanionChestBlock.OPEN, open),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    public boolean isOpenSynced() {
        return this.level != null
                && ChestBlockEntity.getOpenCount(this.level, this.worldPosition) > 0;
    }

    /** 是否曾经被打开过（调试/测试用）。 */
    public boolean hasEverBeenOpened() {
        return this.hasOpened;
    }

    // ---------------------------------------------------------------- 伙伴收纳用的助手

    /** 尝试把物品塞进箱子，返回塞不下的部分（空 = 全部塞入）。 */
    public ItemStack insert(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < SIZE && !remaining.isEmpty(); i++) {
            ItemStack existing = getItem(i);
            if (existing.isEmpty()) {
                setItem(i, remaining.copy());
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int moved = Math.min(space, remaining.getCount());
                    existing.grow(moved);
                    remaining.shrink(moved);
                    setItem(i, existing);
                }
            }
        }
        setChanged();
        return remaining;
    }

    /** 找到第一个满足条件的物品（不移除）。 */
    public ItemStack findFirst(java.util.function.Predicate<ItemStack> filter) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 取走一个满足条件的物品（数量 1）。 */
    public ItemStack takeOne(java.util.function.Predicate<ItemStack> filter) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                setChanged();
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 把箱子里的东西全部撒到地上（破坏方块时调用）。 */
    public void dropContents() {
        if (this.level == null) {
            return;
        }
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.world.Containers.dropItemStack(this.level,
                    this.worldPosition.getX() + 0.5D,
                    this.worldPosition.getY() + 0.5D,
                    this.worldPosition.getZ() + 0.5D,
                    stack);
            setItem(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
