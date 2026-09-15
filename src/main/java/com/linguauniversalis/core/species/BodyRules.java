package com.linguauniversalis.core.species;

import com.linguauniversalis.core.behavior.TemplateKeys;

/**
 * 体型（碰撞箱与视线高度）规则（纯规则，无 MC 依赖；可冒烟测试）。
 *
 * <p>全部走物种变量（{@link TemplateKeys#SPECIES_BODY_WIDTH} /
 * {@link TemplateKeys#SPECIES_BODY_HEIGHT} / {@link TemplateKeys#SPECIES_EYE_HEIGHT}），
 * 未声明时用原版人形默认值。
 *
 * <p>视线高度按模型 {@code ViewLocator} 之类骨骼的枢轴折算（模型 1 单位 = 1/16 格），
 * 并乘上渲染缩放——渲染缩放改了模型大小，眼睛位置理应跟着走。
 * 注意：<b>不做钳制</b>，若模型比碰撞箱高，眼高就会高于箱顶（这是刻意的：以模型为准）。
 */
public final class BodyRules {
    private BodyRules() {
    }

    /** 默认碰撞箱宽（格，原版玩家同款）。 */
    public static final double DEFAULT_WIDTH = 0.6;
    /** 默认碰撞箱高（格）。 */
    public static final double DEFAULT_HEIGHT = 1.8;
    /** 默认视线高度（格，原版玩家 1.62）。 */
    public static final double DEFAULT_EYE_HEIGHT = 1.62;

    /** 体型三元组（宽/高/眼高，单位格）。 */
    public record Size(float width, float height, float eyeHeight) {
    }

    /**
     * 取物种体型（渲染缩放按 1.0、体型用档案值）。
     */
    public static Size of(SpeciesProfile profile) {
        return of(profile, 1.0, -1, -1, -1);
    }

    /**
     * 取物种体型。
     *
     * @param renderScale 模型渲染缩放（{@code <= 0} 视为 1.0）；只影响视线高度
     */
    public static Size of(SpeciesProfile profile, double renderScale) {
        return of(profile, renderScale, -1, -1, -1);
    }

    /**
     * 取物种体型，允许用配置里的值覆盖宽/高/眼高（{@code <= 0} = 用档案值）。
     *
     * <p>这样新模型接入时可以纯靠 {@code config/lingua_universalis.properties} 调碰撞箱与视线，
     * 不必改代码（调好后建议再写回物种档案作为默认值）。
     */
    public static Size of(SpeciesProfile profile, double renderScale,
                          double overrideWidth, double overrideHeight, double overrideEyeHeight) {
        double scale = renderScale > 0 ? renderScale : 1.0;
        double width = overrideWidth > 0
                ? overrideWidth
                : Math.max(0.05, profile.templateParam(TemplateKeys.SPECIES_BODY_WIDTH, DEFAULT_WIDTH));
        double height = overrideHeight > 0
                ? overrideHeight
                : Math.max(0.05, profile.templateParam(TemplateKeys.SPECIES_BODY_HEIGHT, DEFAULT_HEIGHT));
        double eye = overrideEyeHeight > 0
                ? overrideEyeHeight * scale
                : profile.templateParam(TemplateKeys.SPECIES_EYE_HEIGHT, DEFAULT_EYE_HEIGHT) * scale;
        return new Size((float) width, (float) height, (float) Math.max(0.01, eye));
    }
}
