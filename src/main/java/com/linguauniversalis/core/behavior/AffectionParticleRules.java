package com.linguauniversalis.core.behavior;

/**
 * 好感度变化的<b>粒子反馈</b>规则（纯规则，无 MC 依赖）。
 *
 * <p>口径：好感<b>增加</b>时在她周围冒爱心粒子，好感<b>减少</b>时冒"村民愤怒"粒子。
 * 与"环境粒子发射器"（{@link AmbientParticleRules}，按物种变量持续冒粒子）是两套东西：
 * 这里是**事件驱动的一次性爆发**，由实体在 tick 里比较前后好感值来触发。
 */
public final class AffectionParticleRules {
    private AffectionParticleRules() {
    }

    /** 好感增加时的粒子（原版爱心）。 */
    public static final String GAIN_PARTICLE = "minecraft:heart";
    /** 好感减少时的粒子（原版村民愤怒云）。 */
    public static final String LOSS_PARTICLE = "minecraft:angry_villager";

    /** 一次爆发的粒子数。 */
    public static final int BURST_COUNT = 5;
    /** 爆发半径（格，水平散布）。 */
    public static final double BURST_RADIUS = 0.45;
    /** 爆发高度（格，相对脚底）。 */
    public static final double BURST_Y_OFFSET = 1.6;
    /** 爆发速度（向外飘）。 */
    public static final double BURST_SPEED = 0.02;

    /** 一次好感变化该冒哪种粒子；没变化返回 null。 */
    public static String particleFor(int previousAffection, int currentAffection) {
        if (currentAffection > previousAffection) {
            return GAIN_PARTICLE;
        }
        if (currentAffection < previousAffection) {
            return LOSS_PARTICLE;
        }
        return null;
    }

    /** 变化幅度（绝对值；用于决定是否值得冒粒子 / 未来做分级）。 */
    public static int delta(int previousAffection, int currentAffection) {
        return Math.abs(currentAffection - previousAffection);
    }
}
