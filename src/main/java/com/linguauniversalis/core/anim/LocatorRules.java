package com.linguauniversalis.core.anim;

/**
 * 魔物娘模型的**定位分组（locator）**约定 —— 纯规则，无 MC 依赖，可冒烟测试。
 *
 * <p>工作流约定（**以后新增物种默认照此办理**）：
 * <ul>
 *   <li><b>眼睛坐标</b>以模型里的 {@value #EYES_LOCATOR} 骨骼为准：它的枢轴（模型单位 1/16 格）
 *       就是她的视线高度，实体档案里的 {@code species.eyeHeight} 必须等于 {@code pivotY / 16}；</li>
 *   <li><b>手持物品</b>以 <b>挂在 {@code RightPaw} / {@code LeftPaw} 下的 Paw 定位骨骼</b>为准：
 *       主手物品画在右爪定位点、副手物品画在左爪定位点（见 {@link #pawLocatorSide}）。</li>
 * </ul>
 *
 * <p>模型里这些定位骨骼以**普通骨骼**形式存在（没有 cube，只有 pivot），所以既能在
 * GeckoLib 里按名字取到，也能用父骨骼识别。注意美术侧命名可能不统一（例如左爪定位骨骼
 * 被命名为 {@code RightPawLocator2}），因此代码里**优先按父骨骼判定左右手**，
 * 名字只作为兜底。
 */
public final class LocatorRules {
    private LocatorRules() {
    }

    /** 眼睛定位骨骼名（视线高度来源）。 */
    public static final String EYES_LOCATOR = "EyesLocator";
    /** 旧版视线定位骨骼名（保留兼容：没有 EyesLocator 时退回它）。 */
    public static final String VIEW_LOCATOR = "ViewLocator";
    /** 右手爪骨骼名（其下的定位骨骼 = 主手物品挂点）。 */
    public static final String RIGHT_PAW_BONE = "RightPaw";
    /** 左手爪骨骼名（其下的定位骨骼 = 副手物品挂点）。 */
    public static final String LEFT_PAW_BONE = "LeftPaw";
    /** 定位骨骼名的通用后缀。 */
    public static final String LOCATOR_SUFFIX = "Locator";
    /** 模型单位 → 格（Blockbench 的 1 单位 = 1/16 格）。 */
    public static final double MODEL_UNITS_PER_BLOCK = 16.0;

    /** 手性：主手 / 副手（与模型的左/右爪对应）。 */
    public enum PawSide {
        /** 主手（右爪）：武器、工具通常在这只手上。 */
        MAIN,
        /** 副手（左爪）：吃东西的位置。 */
        OFF
    }

    /**
     * 名字是否像定位骨骼（**包含** Locator，忽略大小写）。
     *
     * <p>用"包含"而不是"结尾"，是因为美术侧可能叫 {@code RightPawLocator2} 这种带数字后缀的名字。
     */
    public static boolean isLocatorName(String boneName) {
        return boneName != null
                && boneName.toLowerCase(java.util.Locale.ROOT)
                        .contains(LOCATOR_SUFFIX.toLowerCase(java.util.Locale.ROOT));
    }

    /** 是否眼睛定位骨骼（{@code EyesLocator}，兼容旧的 {@code ViewLocator}）。 */
    public static boolean isEyesLocator(String boneName) {
        return EYES_LOCATOR.equalsIgnoreCase(boneName) || VIEW_LOCATOR.equalsIgnoreCase(boneName);
    }

    /**
     * 由定位骨骼的**父骨骼名**判定它属于哪只手；判定不出来返回 {@code null}。
     *
     * <p>这是左右手的**权威判据**（美术命名可能不统一，父骨骼不会错）。
     */
    public static PawSide pawLocatorSide(String parentBoneName) {
        if (RIGHT_PAW_BONE.equalsIgnoreCase(parentBoneName)) {
            return PawSide.MAIN;
        }
        if (LEFT_PAW_BONE.equalsIgnoreCase(parentBoneName)) {
            return PawSide.OFF;
        }
        return null;
    }

    /** 某只手定位骨骼的"期望名字"（{@code RightPawLocator} / {@code LeftPawLocator}）。 */
    public static String pawLocatorName(PawSide side) {
        return side == PawSide.OFF
                ? LEFT_PAW_BONE + LOCATOR_SUFFIX
                : RIGHT_PAW_BONE + LOCATOR_SUFFIX;
    }

    /** 模型单位换算成格（例：{@code 35.47642 → 2.217276}）。 */
    public static double modelUnitsToBlocks(double modelUnits) {
        return modelUnits / MODEL_UNITS_PER_BLOCK;
    }

    /** 由眼睛定位骨骼的 pivotY（模型单位）得到视线高度（格）。 */
    public static double eyeHeightFromPivot(double pivotYModelUnits) {
        return modelUnitsToBlocks(pivotYModelUnits);
    }

    /** 档案里的视线高度是否与模型定位骨骼一致（容差默认 1e-4 格）。 */
    public static boolean eyeHeightMatches(double profileEyeHeight, double pivotYModelUnits) {
        return Math.abs(profileEyeHeight - eyeHeightFromPivot(pivotYModelUnits)) < 1.0e-4;
    }
}
