package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 绽放之刺（设计汇总 §9）：仪式植物。
 * - 无碰撞、小体形（类似花草；模型先用原版虞美人占位）；
 * - 快照存于 {@link SpinaFlorensBlockEntity}（转化时由充能命缕写入）；
 * - 每 10 tick 对重叠的生物造成 10 点"虚空"（magic 真实伤害）伤害；
 * - 击杀 ≥20 血生物的复活判定在 {@code GirlDeathHandler}（死亡事件）中完成。
 */
public class SpinaFlorensBlock extends Block implements EntityBlock {

    public static final float CONTACT_DAMAGE = 10f;
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);

    public SpinaFlorensBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    // ------------------------------------------------------------------ 方块实体
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpinaFlorensBlockEntity(LURegistries.SPINA_FLORENS_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type == LURegistries.SPINA_FLORENS_BE.get()) {
            return (lvl, pos, st, be) -> SpinaFlorensBlockEntity.serverTick(lvl, pos, st,
                    (SpinaFlorensBlockEntity) be);
        }
        return null;
    }
}
