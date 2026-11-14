package com.linguauniversalis.item;

import com.linguauniversalis.core.behavior.CombatModeRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 哨子：右键把玩家半径 32 格内<b>所有魔物娘</b>的"被动 / 主动"战斗模式一起切换。
 *
 * <p>口径：
 * <ul>
 *   <li>决定方向的是<b>最近的那只</b>：把她反过来，其余全部跟随（避免"各切各的"变成一锅粥）；</li>
 *   <li>被动 = 只挨打后反击；主动 = 主动帮主人打"主人打的 / 打主人的"生物；</li>
 *   <li>半径与切换规则在 {@link CombatModeRules}（纯规则层，有断言）。</li>
 * </ul>
 */
public class WhistleItem extends Item {
    /** 使用后的撒音冷却（tick）：避免连点刷屏。 */
    private static final int COOLDOWN_TICKS = 10;

    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // 客户端只做预测：真正切换由服务端权威执行
            return InteractionResult.SUCCESS;
        }
        double radius = CombatModeRules.WHISTLE_RADIUS_BLOCKS;
        List<MonsterGirlEntity> girls = level.getEntitiesOfClass(MonsterGirlEntity.class,
                player.getBoundingBox().inflate(radius),
                girl -> girl.isAlive() && CombatModeRules.withinWhistleRange(player.distanceToSqr(girl)));
        if (girls.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "[Lingua Universalis] Whistle: no monster girl within " + (int) radius + " blocks."));
            return InteractionResult.SUCCESS;
        }
        // 以最近的一只为基准决定切换方向
        MonsterGirlEntity nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (MonsterGirlEntity girl : girls) {
            double distSq = player.distanceToSqr(girl);
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = girl;
            }
        }
        CombatModeRules.CombatMode target =
                CombatModeRules.toggle(nearest == null ? CombatModeRules.DEFAULT_MODE : nearest.combatMode());
        int changed = 0;
        for (MonsterGirlEntity girl : girls) {
            if (girl.combatMode() != target) {
                girl.setCombatMode(target);
                changed++;
            }
        }
        String modeName = target == CombatModeRules.CombatMode.AGGRESSIVE ? "aggressive" : "passive";
        player.sendSystemMessage(Component.literal("[Lingua Universalis] Whistle: " + girls.size()
                + " girl(s) -> " + modeName + " (" + changed + " switched)"));
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0f, 1.6f);
        return InteractionResult.SUCCESS;
    }
}
