package com.linguauniversalis.item;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 礼物盒（最小实现，替代打包 GUI）：
 * 主手拿礼物盒 + 副手持要送出的物品 → 右键空气 = 把副手物品封装进礼物盒（记录物品 id）。
 * 对魔物娘使用（右键魔物娘）走 {@code MonsterGirlEntity} 的送礼分支：喜爱物 +2 好感/日。
 */
public class PresentCaseItem extends Item {
    public PresentCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // 客户端播放动画；逻辑在服务端
        }
        ItemStack box = player.getItemInHand(hand);
        if (box.getItem() != LURegistries.PRESENT_CASE.get()) {
            return InteractionResult.PASS;
        }
        if (box.has(LURegistries.PRESENT_CONTENT.get())) {
            player.sendSystemMessage(Component.literal(
                    "[present] already packed - give it to a monster girl"));
            return InteractionResult.SUCCESS;
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack content = player.getItemInHand(other);
        if (content.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "[present] hold the gift in your other hand, then right-click air to pack"));
            return InteractionResult.SUCCESS;
        }
        String id = BuiltInRegistries.ITEM.getKey(content.getItem()).toString();
        content.shrink(1);
        box.set(LURegistries.PRESENT_CONTENT.get(), id);
        player.sendSystemMessage(Component.literal("[present] packed: " + id));
        return InteractionResult.SUCCESS;
    }
}
