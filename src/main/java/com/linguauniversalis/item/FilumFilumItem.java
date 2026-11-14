package com.linguauniversalis.item;

import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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

    /**
     * 悬浮提示：显示这枚命缕对应的魔物娘**物种名**；没有个体快照时**什么都不显示**。
     *
     * <p>快照格式见 {@code MonsterGirlEntity#girlSnapshot()}：
     * {@code 物种id \u0001 命令模式 \u0001 Base64(状态)}，这里只取第一段。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<net.minecraft.network.chat.Component> adder,
                                TooltipFlag flag) {
        String snapshot = stack.get(LURegistries.GIRL_SNAPSHOT.get());
        if (snapshot == null || snapshot.isEmpty()) {
            return; // 没有对应的生物：不显示（空）
        }
        String speciesId = snapshot.split("\u0001", -1)[0];
        if (speciesId.isEmpty()) {
            return;
        }
        String key = "species." + speciesId + ".name";
        net.minecraft.network.chat.Component name =
                net.minecraft.network.chat.Component.translatable(key);
        if (name.getString().equals(key)) {
            // 没有该物种的语言条目：退回物种档案里的中文名；档案也没有就退到 id
            name = com.linguauniversalis.core.species.SpeciesRegistry.contains(speciesId)
                    ? net.minecraft.network.chat.Component.literal(
                            com.linguauniversalis.core.species.SpeciesRegistry.get(speciesId).zhName())
                    : net.minecraft.network.chat.Component.literal(speciesId);
        }
        // 只显示物种名本身（不加"对应："之类的前缀）
        adder.accept(name.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
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
