package com.linguauniversalis.core.anim;

/**
 * 头部跟随视角规则（纯规则，无 MC 依赖；可冒烟测试）。
 *
 * <p>把实体的"原版转头"数据折算成模型头部骨骼的旋转增量：<b>只影响旋转</b>，
 * 不碰位置与缩放（{@link #MAX_HEAD_YAW_DEG} / {@link #MAX_HEAD_PITCH_DEG} 为限幅）。
 *
 * <p>与 MC 原版一致的口径：
 * <ul>
 *   <li>偏航 = 头部朝向 − 身体朝向（都按度、先按最短弧插值再取差），限幅 ±{@value #MAX_HEAD_YAW_DEG}°；</li>
 *   <li>俯仰 = 实体自身 X 旋转（正值低头），限幅 ±{@value #MAX_HEAD_PITCH_DEG}°；</li>
 *   <li>渲染用的部分 tick 插值走最短弧（同 {@code Mth.rotLerp}），避免越过 ±180° 时抽搐。</li>
 * </ul>
 */
public final class HeadLookRules {
    private HeadLookRules() {
    }

    /** 偏航限幅（度）：与原版 {@code LivingEntity#getMaxHeadYRot()} 默认值一致。 */
    public static final double MAX_HEAD_YAW_DEG = 75.0;
    /** 俯仰限幅（度）。 */
    public static final double MAX_HEAD_PITCH_DEG = 60.0;

    /** 角度取最短弧归一化到 (-180, 180]。 */
    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    /** 角度按最短弧插值（等价于原版 {@code Mth.rotLerp}）。 */
    public static double lerpDegrees(double from, double to, double delta) {
        return from + wrapDegrees(to - from) * delta;
    }

    /** 头部相对身体的偏航（度），取最短弧并限幅。 */
    public static double netHeadYawDeg(double headYawDeg, double bodyYawDeg) {
        return netHeadYawDeg(headYawDeg, bodyYawDeg, MAX_HEAD_YAW_DEG);
    }

    /** 头部相对身体的偏航（度），取最短弧并按给定上限限幅。 */
    public static double netHeadYawDeg(double headYawDeg, double bodyYawDeg, double maxYawDeg) {
        return clamp(wrapDegrees(headYawDeg - bodyYawDeg), Math.abs(maxYawDeg));
    }

    /** 头部俯仰（度），限幅。 */
    public static double headPitchDeg(double pitchDeg) {
        return headPitchDeg(pitchDeg, MAX_HEAD_PITCH_DEG);
    }

    /** 头部俯仰（度），按给定上限限幅。 */
    public static double headPitchDeg(double pitchDeg, double maxPitchDeg) {
        return clamp(pitchDeg, Math.abs(maxPitchDeg));
    }

    /** 施加符号（±1）：模型轴向与世界轴向不一致时用来翻号。 */
    public static double applySign(double degrees, double sign) {
        return sign < 0 ? -degrees : degrees;
    }

    /** 度 → 弧度（骨骼旋转用）。 */
    public static float toRadians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static double clamp(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }
}
