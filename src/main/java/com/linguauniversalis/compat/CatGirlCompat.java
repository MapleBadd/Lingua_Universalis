package com.linguauniversalis.compat;

import com.linguauniversalis.core.behavior.CatTraitRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.world.entity.Entity;

/**
 * "猫系魔物娘"判定（给 Mixin 与原版式逻辑共用）。
 *
 * <p>原版把"猫"写死成 {@code Cat.class}（苦力怕的 {@code AvoidEntityGoal<Cat>}、
 * 幻翼俯冲目标里的 {@code getEntitiesOfClass(Cat.class, …)}）。我们的魔物娘不是 {@code Cat} 实例，
 * 所以由 Mixin 在这两处**按同一判定把"猫系魔物娘"也算作猫**——原版的逻辑原样跑，只是"谁算猫"多了一种。
 */
public final class CatGirlCompat {
    private CatGirlCompat() {
    }

    /** 该实体是否是"猫系魔物娘"（物种变量 {@code species.catTraits} 为真）。 */
    public static boolean isCatGirl(Entity entity) {
        if (!(entity instanceof MonsterGirlEntity girl)) {
            return false;
        }
        try {
            return CatTraitRules.hasCatTraits(girl.profile());
        } catch (Throwable ignored) {
            // 物种档案异常（数据包写坏等）时：当作"不是猫"，绝不把异常抛进原版 AI 目标循环
            return false;
        }
    }

    /**
     * 找半径内最近的猫系魔物娘并让她**哈气**（原版幻翼怕猫时会调 {@code Cat#hiss()}）。
     *
     * @return 是否找到了猫系魔物娘（找到即已发出哈气声）
     */
    public static boolean hissNearestCatGirl(Entity center, double radius) {
        if (center == null || center.level() == null || center.level().isClientSide()) {
            return false;
        }
        MonsterGirlEntity nearest = null;
        double bestSq = radius * radius;
        for (MonsterGirlEntity girl : center.level().getEntitiesOfClass(MonsterGirlEntity.class,
                center.getBoundingBox().inflate(radius), CatGirlCompat::isCatGirl)) {
            double distSq = center.distanceToSqr(girl);
            if (distSq <= bestSq) {
                bestSq = distSq;
                nearest = girl;
            }
        }
        if (nearest == null) {
            return false;
        }
        nearest.playHissIfCat();
        return true;
    }

    /** 以 {@code center} 为中心、半径 {@code radius} 格内有没有猫系魔物娘。 */
    public static boolean isCatGirlNear(Entity center, double radius) {
        if (center == null || center.level() == null) {
            return false;
        }
        double radiusSq = radius * radius;
        for (MonsterGirlEntity girl : center.level().getEntitiesOfClass(MonsterGirlEntity.class,
                center.getBoundingBox().inflate(radius), CatGirlCompat::isCatGirl)) {
            if (center.distanceToSqr(girl) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
