package com.linguauniversalis.core.anim;

/**
 * 猫形伪装的表现规则（纯规则，无 MC 依赖；设计 §12「形态」）。
 *
 * <p>伪装形态使用<b>原版猫模型 + 原版猫贴图 + 原版猫动画</b>：
 * MC 侧（{@code client/CatFormRenderer}）只负责把实体的同步状态折算成原版
 * {@code CatRenderState} 的猫专属字段（坐/趴/放松），并沿用原版猫的过渡步长
 * （取自原版 {@code Cat#tick} 的 {@code updateLieDownAmount}/{@code updateRelaxStateOneAmount}）。
 */
public final class CatFormRules {
    private CatFormRules() {
    }

    /** 猫形使用的贴图：原版<b>全黑猫</b>（沼泽小屋变种）成人贴图。 */
    public static final String CAT_FORM_TEXTURE = "minecraft:textures/entity/cat/cat_all_black.png";

    /** 猫形阴影半径（原版猫为 0.4，这里略小以贴合猫形碰撞箱 0.6×0.7）。 */
    public static final float CAT_FORM_SHADOW_RADIUS = 0.3F;

    // 原版 Cat 的过渡步长（每 tick 递增/递减量）
    /** 趴下姿态上升步长。 */
    public static final float LIE_RISE = 0.15F;
    /** 趴下姿态回落步长。 */
    public static final float LIE_FALL = 0.22F;
    /** 尾巴趴下姿态上升步长。 */
    public static final float LIE_TAIL_RISE = 0.08F;
    /** 尾巴趴下姿态回落步长。 */
    public static final float LIE_TAIL_FALL = 0.13F;
    /** 放松（趴睡）姿态上升步长。 */
    public static final float RELAX_RISE = 0.10F;
    /** 放松（趴睡）姿态回落步长。 */
    public static final float RELAX_FALL = 0.13F;

    // ------------------------------------------------------------------ 姿态判定
    /**
     * 是否使用原版猫的<b>坐姿</b>：待机（命令"待机"）时坐下；休眠时改趴下（趴下优先）。
     */
    public static boolean useSittingPose(boolean dormant, boolean standingBy) {
        return standingBy && !dormant;
    }

    /**
     * 是否使用原版猫的<b>趴姿</b>：休眠时趴下；以猫形陪玩家睡觉时也趴下。
     */
    public static boolean useLiePose(boolean dormant, boolean sleepingWithOwner) {
        return dormant || sleepingWithOwner;
    }

    // ------------------------------------------------------------------ 姿态过渡
    /** 通用姿态数值过渡：目标为真时按 rise 上升、否则按 fall 回落，钳制在 [0,1]。 */
    public static float stepToward(float current, boolean active, float rise, float fall) {
        float next = active ? current + rise : current - fall;
        return Math.max(0.0F, Math.min(1.0F, next));
    }

    /** 趴姿进度（每 tick 推进一步）。 */
    public static float stepLieAmount(float current, boolean lying) {
        return stepToward(current, lying, LIE_RISE, LIE_FALL);
    }

    /** 尾巴趴姿进度。 */
    public static float stepLieTailAmount(float current, boolean lying) {
        return stepToward(current, lying, LIE_TAIL_RISE, LIE_TAIL_FALL);
    }

    /** 放松（趴睡）进度。 */
    public static float stepRelaxAmount(float current, boolean relaxing) {
        return stepToward(current, relaxing, RELAX_RISE, RELAX_FALL);
    }
}
