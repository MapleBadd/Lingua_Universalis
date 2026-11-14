package com.linguauniversalis.entity;

import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;

/**
 * 魔物娘"看人 / 四处看看"的行为目标——**直接复用原版宠物用的那两个 AI 目标**
 * （狼、猫都是 {@link LookAtPlayerGoal} + {@link RandomLookAroundGoal}），
 * 这里只加了一道"倒地/休眠/低落时不看人"的门槛，其余行为与判定全部沿用原版：
 *
 * <ul>
 *   <li>玩家进入 8 格 → 转头看他（原版的转头速度、视线判定、随机概率都由原版自己管）；</li>
 *   <li>没有玩家在附近 → 原版 {@code RandomLookAroundGoal} 让她随机四处张望。</li>
 * </ul>
 *
 * <p>不再像以前那样每 tick 由实体代码强行 {@code setLookAt(玩家)} —— 那种写法会让她
 * **永远死盯**玩家、连转身看别处都不会。头骨的朝向仍然由实体同步的
 * {@code yHeadRot} / {@code xRot} 驱动（见 {@code NekomataRenderer}），所以这套原版目标
 * 会自然地带动头部转向。
 */
public final class GirlLookGoals {
    private GirlLookGoals() {
    }

    /** 原版"注视玩家"目标（狼/猫同款：8 格内）。 */
    public static final class LookAtPlayer extends LookAtPlayerGoal {
        private final MonsterGirlEntity girl;

        public LookAtPlayer(MonsterGirlEntity girl) {
            super(girl, Player.class, 8.0F);
            this.girl = girl;
        }

        @Override
        public boolean canUse() {
            return this.girl.wantsToLookAround() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.girl.wantsToLookAround() && super.canContinueToUse();
        }
    }

    /** 原版"四处张望"目标。 */
    public static final class RandomLookAround extends RandomLookAroundGoal {
        private final MonsterGirlEntity girl;

        public RandomLookAround(MonsterGirlEntity girl) {
            super(girl);
            this.girl = girl;
        }

        @Override
        public boolean canUse() {
            return this.girl.wantsToGlanceAround() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.girl.wantsToGlanceAround() && super.canContinueToUse();
        }
    }
}
