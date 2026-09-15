package com.linguauniversalis.block;

import com.linguauniversalis.menu.CompanionChestMenu;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * 伙伴宝箱（54 格共享收纳，设计 §6）——**模型暂时用原版末影箱代替**（资源里 parent 到
 * {@code minecraft:block/ender_chest}），方块行为与普通箱子一致：右键打开容器界面。
 *
 * <p>"不能合成大箱子"是天然的：这是本模组自己的方块，不存在原版双箱合并逻辑。
 */
public class CompanionChestBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 方块编解码器（26.2 的方块必须有 codec）。 */
    public static final com.mojang.serialization.MapCodec<CompanionChestBlock> CODEC =
            simpleCodec(CompanionChestBlock::new);

    public CompanionChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompanionChestBlockEntity(pos, state);
    }

    /** 右键打开容器界面（空手右键即可，与箱子一致）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CompanionChestBlockEntity chest) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new CompanionChestMenu(
                                    containerId, inventory, chest, pos),
                            Component.translatable("block.lingua_universalis.chest_of_companions")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 掉落物：箱子被破坏时把里面的东西撒出来（与原版箱子一致）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CompanionChestBlockEntity chest) {
            chest.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 供 {@code LURegistries} 构造方块实体类型时引用。 */
    public static net.minecraft.world.level.block.entity.BlockEntityType<CompanionChestBlockEntity> type() {
        return LURegistries.COMPANION_CHEST_BE.get();
    }
}
