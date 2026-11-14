package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 伙伴宝箱方块：**直接继承原版 {@link ChestBlock}**（54 格共用收纳）。
 *
 * <p>继承原版拿到的正是之前自己实现时踩坑的部分：
 * <ul>
 *   <li>碰撞箱整格、选中框 14/16 —— 与原版箱子**逐项一致**，AI 寻路不会把它当成"可跨越的矮障碍"；</li>
 *   <li>上方被实心方块遮挡时打不开；</li>
 *   <li>开箱/关箱音效（这里用**铜箱子**音色）、开合计数同步；</li>
 *   <li>双箱合并判定（本方块永远是单体：{@code type} 只有 SINGLE）。</li>
 * </ul>
 *
 * <p>只覆盖三处：外观交给 GeckoLib（{@link RenderShape#INVISIBLE} + 方块实体渲染器）、
 * 方块实体类型、以及"猫又坐在上面时不许打开"这条本模组规则。
 */
public class CompanionChestBlock extends ChestBlock {
    /** 方块编解码器（26.2 的方块必须有 codec）。 */
    public static final com.mojang.serialization.MapCodec<CompanionChestBlock> CODEC =
            simpleCodec(CompanionChestBlock::new);

    /** 是否有人开着（渲染镜像：开合动画读它，方块状态会同步到客户端）。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty OPEN =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("open");

    public CompanionChestBlock(Properties properties) {
        // 原版构造接管开合音效：用铜箱子的开/关音色
        super(() -> LURegistries.COMPANION_CHEST_BE.get(),
                SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block,
                    BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(OPEN, false);
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends ChestBlock> codec() {
        // 与父类同款（方块本身没有额外序列化字段）
        return ChestBlock.CODEC;
    }

    /** 外观由 GeckoLib 渲染器绘制。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanionChestBlockEntity(pos, state);
    }

    /**
     * 打开箱子：原版逻辑（含"上方被遮挡则打不开"）之外，多一条本模组规则 ——
     * **猫又坐在箱子上时不许打开**（放一个"锁住"的声音提示）。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (isSatOnByNekomata(level, pos)) {
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.CHEST_LOCKED,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /** 破坏方块时把内容物撒出来（与原版箱子一致）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CompanionChestBlockEntity chest) {
            chest.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 是否有一只猫又正坐在箱子正上方。 */
    private static boolean isSatOnByNekomata(Level level, BlockPos pos) {
        net.minecraft.world.phys.AABB top = new net.minecraft.world.phys.AABB(
                pos.getX(), pos.getY() + 0.9D, pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 2.2D, pos.getZ() + 1.0D);
        for (com.linguauniversalis.entity.MonsterGirlEntity girl
                : level.getEntitiesOfClass(com.linguauniversalis.entity.MonsterGirlEntity.class, top)) {
            if ("nekomata".equals(girl.speciesId())) {
                return true;
            }
        }
        return false;
    }

    /** 供 {@code LURegistries} 构造方块实体类型时引用。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<CompanionChestBlockEntity> type() {
        return LURegistries.COMPANION_CHEST_BE.get();
    }
}
