package com.linguauniversalis.client;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.linguauniversalis.core.anim.LocatorRules;

/**
 * 手上物品的**定位组解析**：按 {@link LocatorRules} 的约定在模型里找"该画在哪根骨骼上"。
 *
 * <p>约定要点（详见 {@link LocatorRules}）：
 * <ul>
 *   <li>主手物品 = 挂在 {@code RightPaw} 下、名字里带 {@code Locator} 的空骨骼；</li>
 *   <li>副手物品 = 挂在 {@code LeftPaw} 下的同类骨骼；</li>
 *   <li>左右手<b>按父骨骼判定</b>，不按名字——美术侧可能把左爪定位骨骼命名成
 *       {@code RightPawLocator2}，按名字找会把副手物品画到右手上。</li>
 * </ul>
 *
 * <p>模型没有做定位组时返回 {@code null}：渲染侧据此<b>不画</b>手上物品（不猜位置），
 * 新物种在补上定位组之前只是"看不到手上物品"，不会出错。
 */
public final class HeldItemLocators {
    private HeldItemLocators() {
    }

    /**
     * 找某只手对应的定位骨骼。
     *
     * @return 定位骨骼；模型里没有（或不是这个物种的模型）时返回 {@code null}
     */
    public static GeoBone find(BakedGeoModel model, LocatorRules.PawSide side) {
        if (model == null || side == null) {
            return null;
        }
        // 1) 先试约定名字（美术按约定命名时这里就命中，省一次全树遍历）
        GeoBone preferred = model.getBone(LocatorRules.pawLocatorName(side)).orElse(null);
        if (preferred != null && side == sideOf(preferred)) {
            return preferred;
        }
        // 2) 再按父骨骼全树找（命名不统一时的权威判据）
        for (GeoBone topLevel : model.topLevelBones()) {
            GeoBone hit = search(topLevel, side);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 深度优先找第一个满足"属于这只手的定位骨骼"的骨骼。 */
    private static GeoBone search(GeoBone bone, LocatorRules.PawSide side) {
        if (bone == null) {
            return null;
        }
        if (LocatorRules.isLocatorName(bone.name())
                && !LocatorRules.isEyesLocator(bone.name())
                && side == sideOf(bone)) {
            return bone;
        }
        for (GeoBone child : bone.children()) {
            GeoBone hit = search(child, side);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 由定位骨骼的父骨骼判定它属于哪只手（判定不出来 = null）。 */
    private static LocatorRules.PawSide sideOf(GeoBone locator) {
        GeoBone parent = locator.parent();
        return parent == null ? null : LocatorRules.pawLocatorSide(parent.name());
    }
}
