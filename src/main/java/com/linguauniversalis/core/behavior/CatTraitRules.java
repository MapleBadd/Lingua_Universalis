package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 「猫系特性」规则（纯规则，无 MC 依赖）。
 *
 * <p>物种开关：{@link TemplateKeys#FLAG_SPECIES_CAT_TRAITS}（猫又 = true）。开启后她会带来猫的行为特征：
 * <ul>
 *   <li>把附近的**苦力怕**（原版本来就怕猫）赶走；</li>
 *   <li>把附近的**幻翼**赶走（原版幻翼并不怕猫，这条是本模组加的"猫系特性"）；</li>
 *   <li>任何被数据包写进 {@code #lingua_universalis:scared_of_cats} 实体类型标签的生物，
 *       同样会被她赶走 —— 这是给**其他模组**留的接口。</li>
 * </ul>
 *
 * <p><b>幻翼</b>走原版口径：原版 {@code Phantom$PhantomSweepAttackGoal#canContinueToUse} 每 20 tick 在
 * 自身碰撞箱 <b>inflate(16)</b> 内查找 {@code Cat}，找到就让幻翼<b>放弃俯冲</b>（{@code stop()} 里清目标、
 * 攻击阶段回到 CIRCLE）。我们对猫系魔物娘照这套判定做：16 格内有她就让幻翼放弃当前目标。
 *
 * <p><b>苦力怕</b>走原版口径的另一半：原版苦力怕在 6 格内用 {@code AvoidEntityGoal<Cat>} 躲猫。
 * 原版那个目标把类型写死成 {@code Cat.class}，我们无法让她"是 Cat"，因此复用的是<b>同一个原版目标类</b>
 * （{@code AvoidEntityGoal}，距离/速度与原版一致），只把被躲的判定换成"猫系魔物娘"。
 * 名单由实体类型标签 {@code #lingua_universalis:scared_of_cats} 给出，数据包可扩展（其他模组的怪也能加进来）。
 */
public final class CatTraitRules {
    private CatTraitRules() {
    }

    /** 该物种是否具备猫系特性。 */
    public static boolean hasCatTraits(SpeciesProfile profile) {
        return profile != null && profile.templateFlag(TemplateKeys.FLAG_SPECIES_CAT_TRAITS, false);
    }

    /** 苦力怕式避让半径（格）：原版苦力怕的 "AvoidEntityGoal<Cat>" 就是 6 格。 */
    public static final double SCARE_RADIUS_BLOCKS = 6.0;

    /**
     * 幻翼"怕猫"的判定半径（格）——<b>抄原版数值</b>：{@code Phantom$PhantomSweepAttackGoal}
     * 每 20 tick 在自身碰撞箱 <b>inflate(16)</b> 的范围内找 {@code Cat}，找到就让幻翼放弃这次俯冲。
     */
    public static final double PHANTOM_CAT_RADIUS_BLOCKS = 16.0;

    /** 幻翼重新检查"附近有没有猫"的间隔（tick）：原版是 {@code catSearchTick = tickCount + 20}。 */
    public static final int PHANTOM_CAT_SEARCH_INTERVAL_TICKS = 20;
    /** 逃跑走速 / 疾跑速度倍率（与 AvoidEntityGoal 的参数一致）。 */
    public static final double FLEE_WALK_SPEED = 1.0;
    public static final double FLEE_SPRINT_SPEED = 1.2;

    /** 给别的生物追加避让目标时用的优先级（越小越优先；3 与原版苦力怕避猫同级）。 */
    public static final int AUGMENT_GOAL_PRIORITY = 3;

    /** 扫描间隔（tick）：不必每 tick 扫，1 秒一次足够。 */
    public static final int SCAN_INTERVAL_TICKS = 20;

    /** 需要被驱赶的生物类型标签（数据包可扩展：`#lingua_universalis:scared_of_cats`）。 */
    public static final String SCARED_OF_CATS_TAG = "lingua_universalis:scared_of_cats";

    /** "猫系魔物娘"标签（供其他模组/数据包识别，见 {@code data/lingua_universalis/tags/entity_type/cats.json}）。 */
    public static final String CATS_TAG = "lingua_universalis:cats";

    /** 该距离内的目标生物才值得处理（苦力怕式避让半径）。 */
    public static boolean withinScareRange(double distanceSq) {
        return distanceSq <= SCARE_RADIUS_BLOCKS * SCARE_RADIUS_BLOCKS;
    }

    /** 该距离内的幻翼才需要"放弃俯冲"（原版 16 格口径）。 */
    public static boolean withinPhantomCatRange(double distanceSq) {
        return distanceSq <= PHANTOM_CAT_RADIUS_BLOCKS * PHANTOM_CAT_RADIUS_BLOCKS;
    }
}
