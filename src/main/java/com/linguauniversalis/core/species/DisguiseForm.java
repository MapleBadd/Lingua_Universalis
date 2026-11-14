package com.linguauniversalis.core.species;

/**
 * 变形（伪装）形态定义 —— 纯数据，无 MC 依赖。
 *
 * <p>为什么要独立成数据：变形是**通用玩法**而不是猫又的私有逻辑。以后任何物种只要在模型里
 * 做一份第二形态的资源、在这里登记一条 {@code DisguiseForm}、并在物种档案里用
 * {@link com.linguauniversalis.core.behavior.TemplateKeys#SPECIES_DISGUISE_FORM} 指向它，
 * 就能获得同一套"野生伪装 → 现形 → 静默后恢复"的完整流程（触发条件见
 * {@link com.linguauniversalis.core.behavior.DisguiseRules}），<b>不需要改实体或渲染器代码</b>。
 *
 * <p>两条渲染路线（{@link #renderKind()}）：
 * <ul>
 *   <li>{@link #KIND_VANILLA_CAT}：套用原版猫的模型/贴图/动画（猫又的双尾黑猫伪装走这条，
 *       客户端实现见 {@code client/CatFormRenderer}）；</li>
 *   <li>{@link #KIND_GECKO}：走 GeckoLib —— 用本形态自己的 {@link #modelPath()} /
 *       {@link #texturePath()} / {@link #animationPath()} 换掉主渲染器的资源（同一套骨骼动画控制器），
 *       这是"新物种加第二形态"的默认做法：只要丢资源 + 登记一条，就能跑。</li>
 * </ul>
 *
 * @param id            形态 id（存档/同步用，全局唯一）
 * @param renderKind    渲染路线（{@link #KIND_VANILLA_CAT} / {@link #KIND_GECKO}）
 * @param modelPath     GeckoLib 模型资源路径（KIND_GECKO 用；裸名字，如 {@code nekomata_cat}）
 * @param texturePath   贴图路径（KIND_GECKO 用；如 {@code lingua_universalis:textures/entity/x.png}）
 * @param animationPath GeckoLib 动画资源路径（KIND_GECKO 用；空串 = 复用本体动画）
 * @param width         该形态的碰撞箱宽（格）
 * @param height        该形态的碰撞箱高（格）
 * @param eyeHeight     该形态的视线高度（格）
 */
public record DisguiseForm(String id, String renderKind, String modelPath, String texturePath,
                           String animationPath, double width, double height, double eyeHeight) {

    /** 渲染路线：原版猫（猫又的猫形伪装）。 */
    public static final String KIND_VANILLA_CAT = "vanilla_cat";
    /** 渲染路线：GeckoLib 模型换形（推荐给新物种的第二形态）。 */
    public static final String KIND_GECKO = "gecko";

    public DisguiseForm {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("disguise form id must not be empty");
        }
        renderKind = renderKind == null || renderKind.trim().isEmpty() ? KIND_GECKO : renderKind.trim();
        modelPath = modelPath == null ? "" : modelPath.trim();
        texturePath = texturePath == null ? "" : texturePath.trim();
        animationPath = animationPath == null ? "" : animationPath.trim();
        if (width <= 0) {
            width = 0.6;
        }
        if (height <= 0) {
            height = 1.0;
        }
        if (eyeHeight <= 0) {
            eyeHeight = height * 0.8;
        }
    }

    /** 是否 GeckoLib 换形（渲染器据此决定要不要读本形态的资源）。 */
    public boolean isGecko() {
        return KIND_GECKO.equalsIgnoreCase(renderKind);
    }
}
