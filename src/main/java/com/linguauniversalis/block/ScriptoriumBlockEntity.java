package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 誊写台方块实体：**内部容器**（1 格）存放放上去的初稿。
 *
 * <p>为什么用容器而不是方块状态：放上去的是一本**真实的物品**，以后要支持"取回时保留 NBT"
 * （图鉴进度写在初稿上）就必须用容器；方块状态只保留一个 {@code has_book} 作为**渲染用的镜像**
 * （由 {@link #setChanged()} 同步），这样客户端渲染不需要额外的方块实体同步包。
 */
public class ScriptoriumBlockEntity extends BlockEntity
        implements Container, com.geckolib.animatable.GeoBlockEntity {
    private static final String TAG_BOOK = "Book";
    /** 台面上只有一格。 */
    public static final int SLOT_BOOK = 0;

    private final com.geckolib.animatable.instance.AnimatableInstanceCache geoCache =
            com.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private ItemStack book = ItemStack.EMPTY;
    /** 诊断用：控制器状态是否已记录 / 上次记录的 hasBook。 */
    public boolean controlStateLogged;
    public boolean lastControlHadBook;

    public ScriptoriumBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ 容器
    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.book.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == SLOT_BOOK ? this.book : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != SLOT_BOOK || this.book.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.book.split(amount);
        if (this.book.isEmpty()) {
            this.book = ItemStack.EMPTY;
        }
        this.setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != SLOT_BOOK) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.book;
        this.book = ItemStack.EMPTY;
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_BOOK) {
            return;
        }
        this.book = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.setChanged();
    }

    /** 放一本初稿上去（成功返回 true）。 */
    public boolean putBook(ItemStack stack) {
        if (!this.book.isEmpty() || stack.isEmpty()) {
            return false;
        }
        this.book = stack.copyWithCount(1);
        this.setChanged();
        return true;
    }

    /** 把台面上的书拿走（空则返回 {@link ItemStack#EMPTY}）。 */
    public ItemStack takeBook() {
        ItemStack taken = this.book;
        this.book = ItemStack.EMPTY;
        this.setChanged();
        return taken;
    }

    public boolean hasBook() {
        return !this.book.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.book = ItemStack.EMPTY;
        this.setChanged();
    }

    /**
     * 内容变化：镜像到方块状态 {@code has_book} + 通知客户端重绘 + 标记存档。
     *
     * <p>镜像的好处是**渲染只读方块状态**，不需要为"台面上有没有书"设计同步包。
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (state.hasProperty(ScriptoriumBlock.HAS_BOOK)) {
            boolean has = hasBook();
            if (state.getValue(ScriptoriumBlock.HAS_BOOK) != has) {
                this.level.setBlock(this.worldPosition, state.setValue(ScriptoriumBlock.HAS_BOOK, has),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    // ------------------------------------------------------------------ 存档
    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!this.book.isEmpty()) {
            out.store(TAG_BOOK, ItemStack.CODEC, this.book);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.book = in.read(TAG_BOOK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public com.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    /**
     * 台面上的书：**静态**摆件，不播任何动画。
     *
     * <p>有书 → 控制器 {@code STOP}（骨骼保持模型默认姿态 = 合着的书，见
     * {@code geckolib/models/scriptorium_book.geo.json}）；
     * 没书 → 循环 {@code empty}（把书骨骼缩放为 0，看不见）。
     *
     * <p>开合/翻页动画已按需求全部移除；"有没有书"读**方块状态**（会同步到客户端）。
     */
    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new com.geckolib.animation.AnimationController<ScriptoriumBlockEntity>(
                "book", 0,
                state -> {
                    net.minecraft.world.level.block.state.BlockState blockState =
                            state.animatable().getBlockState();
                    boolean hasBook = blockState.hasProperty(ScriptoriumBlock.HAS_BOOK)
                            && blockState.getValue(ScriptoriumBlock.HAS_BOOK);
                    if (hasBook) {
                        // 有书：播 show（一帧把书的缩放钉回 1）。
                        // 不能用 STOP：STOP 只停止推进，**不会**撤销上一个动画残留的变换，
                        // 之前空台播过 empty（scale=0），所以放上书后书仍然隐形。
                        return state.setAndContinue(com.geckolib.animation.RawAnimation.begin()
                                .thenLoop("show"));
                    }
                    return state.setAndContinue(com.geckolib.animation.RawAnimation.begin()
                            .thenLoop("empty"));
                }));
    }

}
