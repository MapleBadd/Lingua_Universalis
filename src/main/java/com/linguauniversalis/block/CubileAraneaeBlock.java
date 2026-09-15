package com.linguauniversalis.block;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 蜘蛛巢心（阿拉克涅的巢穴方块 / 物种巢穴变量 {@code cubile_araneae}）。
 *
 * <p>它同时是<b>阿拉克涅的锚点</b>：区块被加载后，由方块实体确认巢穴附近是否已有阿拉克涅，
 * 若没有则<b>生成一只并让它认领该巢心</b>（"固定生成一只、不被刷新"；死亡后不再补生成）。
 */
public class CubileAraneaeBlock extends Block implements EntityBlock {

    public CubileAraneaeBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CubileAraneaeBlockEntity(LURegistries.CUBILE_ARANEAE_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type == LURegistries.CUBILE_ARANEAE_BE.get()) {
            return (lvl, pos, st, be) ->
                    CubileAraneaeBlockEntity.serverTick(lvl, pos, st, (CubileAraneaeBlockEntity) be);
        }
        return null;
    }
}
