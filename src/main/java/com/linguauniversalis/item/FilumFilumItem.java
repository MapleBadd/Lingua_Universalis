package com.linguauniversalis.item;

import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 命缕（特殊物品行为）：已充能的命缕对凋灵玫瑰使用 → 玫瑰吸收命缕化为「绽放之刺」，
 * 个体快照被写入绽放之刺的方块实体；手中的命缕被消耗（不产生复制品）。
 */
public class FilumFilumItem extends Item {
    public FilumFilumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!Boolean.TRUE.equals(stack.get(LURegistries.FILUM_CHARGED.get()))) {
            return super.useOn(context);
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != Blocks.WITHER_ROSE) {
            return super.useOn(context);
        }
        String snapshot = stack.get(LURegistries.GIRL_SNAPSHOT.get());
        if (snapshot == null) {
            return super.useOn(context); // 没有个体快照的充能命缕不能用于仪式
        }
        // 玫瑰吸收命缕 → 绽放之刺；快照写入方块实体
        level.setBlock(pos, LURegistries.SPINA_FLORENS.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof SpinaFlorensBlockEntity be) {
            be.setSnapshot(snapshot);
            be.setChanged();
        }
        stack.shrink(1); // 命缕被吸收消耗
        return InteractionResult.SUCCESS;
    }
}
