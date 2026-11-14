package com.linguauniversalis.mixin;

import com.linguauniversalis.compat.CatGirlCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让原版"苦力怕躲猫"也躲"猫系魔物娘"——只拓宽被躲类型，**仍然只有一条目标**。
 *
 * <p>原版 Creeper#registerGoals()（26.2 字节码实测）：
 *   goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Ocelot.class, 6.0F, 1.0D, 1.2D));
 *   goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Cat.class,    6.0F, 1.0D, 1.2D));
 *
 * <p>这里把"躲 Cat"那一条换成**原版同一个目标类、原版同款带谓词构造器**：
 * (PathfinderMob, Class, float, double, double, Predicate)。被躲类型放宽为 LivingEntity，
 * 谓词 = "是猫 或 猫系魔物娘"。因为走的是原版构造器，跑动/停止/寻路/视线判定全部由原版实现，
 * 行为与真猫一致；躲豹猫那条原样重建。
 *
 * <p>为什么不用"给 AvoidEntityGoal 注入 canUse"：那样得手工填 toAvoid 并强撑 canContinueToUse，
 * 会让原版 MOVE 目标被锁住（表现为苦力怕站着不动却照样爆炸）。交回原版构造器最稳。
 */
@Mixin(Creeper.class)
public abstract class CreeperCatAvoidMixin {

    @Redirect(method = "registerGoals",
            at = @At(value = "NEW", target = "net/minecraft/world/entity/ai/goal/AvoidEntityGoal"),
            require = 1)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private AvoidEntityGoal<?> linguaUniversalis$widenCatAvoid(PathfinderMob mob, Class<?> avoidClass,
                                                               float distance, double walkSpeed,
                                                               double sprintSpeed) {
        if (avoidClass == Cat.class) {
            // 原版同款 6 参构造器：被躲类型放宽，参数原样传入
            return new AvoidEntityGoal<LivingEntity>(mob, LivingEntity.class, distance, walkSpeed, sprintSpeed,
                    (LivingEntity entity) -> entity instanceof Cat || CatGirlCompat.isCatGirl(entity));
        }
        return new AvoidEntityGoal<LivingEntity>(mob, (Class<LivingEntity>) avoidClass,
                distance, walkSpeed, sprintSpeed);
    }
}