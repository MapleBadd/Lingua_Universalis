package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 双档移动速度规则（纯规则，无 MC 依赖；可冒烟测试）。
 *
 * <p>魔物娘有两档速度：
 * <ul>
 *   <li><b>walk（慢速）</b>：基础移速 × 物种 walk 倍率 —— <b>所有物种默认都是 1.0</b>
 *       （即"默认 walk 速度 = 现在猫又的速度"）；</li>
 *   <li><b>fast（最快）</b>：基础移速 × 物种 fastest 倍率，<b>按物种不同</b>
 *       （猫又 = 1.7，约等于玩家疾跑的 1.3 倍）。</li>
 * </ul>
 *
 * <p>倍率可在配置文件里热改：全局 {@code speed.walkScale} / {@code speed.fastScale}，
 * 或按物种覆盖 {@code nekomata.speed.fastScale=1.8}；不写（或 ≤0）则用物种档案里的值。
 *
 * <p>切换条件：
 * <ol>
 *   <li><b>攻击敌人时</b> → fast；</li>
 *   <li><b>跟随玩家</b>且距离 <b>超过 8 格</b> → fast，<b>一路追到"跟随最低距离"（6 格）才降回 walk</b>；</li>
 *   <li>其余（游荡 / 野生且无敌对目标 / 跟随但在 8 格内）→ walk。</li>
 * </ol>
 *
 * <p>8 格到 6 格之间是**滞回带**（追上过程中保持 fast），所以不会出现"刚进 8 格就减速、
 * 被玩家一跑又加速"的抖动。
 */
public final class MovementSpeedRules {
    private MovementSpeedRules() {
    }

    /** 跟随时的"加速距离"（格）：超过它追赶就用最快速度。 */
    public static final double FOLLOW_FAST_DISTANCE = 8.0;
    /**
     * 跟随的"最低距离"（格）：跟到这个距离就算跟到了——魔物娘会停步，速度也降回 walk。
     *
     * <p>这也是实体侧 {@code tickFollow} 的停步距离；两边共用这一个常量，改一处即可（保持一致）。
     */
    public static final double FOLLOW_STOP_DISTANCE = 6.0;
    /** 停步距离平方（实体侧判据用）。 */
    public static final double FOLLOW_STOP_DIST_SQ = FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE;
    /** 默认 walk 倍率（所有物种默认一致）。 */
    public static final double DEFAULT_WALK_SPEED_SCALE = 1.0;
    /** 默认最快倍率（未声明的物种与 walk 相同）。 */
    public static final double DEFAULT_FAST_SPEED_SCALE = 1.0;

    /** 物种的 walk 速度倍率。 */
    public static double walkSpeedScale(SpeciesProfile profile) {
        return positive(profile.templateParam(TemplateKeys.SPECIES_WALK_SPEED_SCALE,
                DEFAULT_WALK_SPEED_SCALE));
    }

    /** 物种的最快速度倍率（猫又 = 1.7 ≈ 玩家疾跑的 1.3 倍）。 */
    public static double fastSpeedScale(SpeciesProfile profile) {
        double walk = walkSpeedScale(profile);
        double fast = positive(profile.templateParam(TemplateKeys.SPECIES_FAST_SPEED_SCALE,
                DEFAULT_FAST_SPEED_SCALE));
        return Math.max(walk, fast);
    }

    /**
     * 当前是否该用最快速度。
     *
     * @param hasAttackTarget 是否正在攻击敌人
     * @param followingOwner  是否处于"跟随玩家"状态（有命令权且命令为跟随）
     * @param distSqToOwner   与绑定玩家的距离平方（不跟随时忽略）
     */
    public static boolean useFastSpeed(boolean hasAttackTarget, boolean followingOwner, double distSqToOwner) {
        return useFastSpeed(hasAttackTarget, followingOwner, distSqToOwner, false);
    }

    /**
     * 带滞回的判据（实体每 tick 用这个）。
     *
     * @param currentlyFast 当前是否已经在最快档：是的话要一路追到 {@link #FOLLOW_STOP_DISTANCE} 以内才降档
     */
    public static boolean useFastSpeed(boolean hasAttackTarget, boolean followingOwner, double distSqToOwner,
            boolean currentlyFast) {
        if (hasAttackTarget) {
            return true;
        }
        if (!followingOwner) {
            return false;
        }
        double threshold = currentlyFast ? FOLLOW_STOP_DISTANCE : FOLLOW_FAST_DISTANCE;
        return distSqToOwner > threshold * threshold;
    }

    /**
     * 当前档位的最终倍率：配置值 &gt; 0 时用配置值（热改），否则回落物种档案里的值。
     *
     * @param fast           是否最快档
     * @param configuredWalk 配置里的 {@code speed.walkScale}（≤0 = 不写）
     * @param configuredFast 配置里的 {@code speed.fastScale}（≤0 = 不写）
     */
    public static double speedScale(boolean fast, double configuredWalk, double configuredFast,
            SpeciesProfile profile) {
        double configured = fast ? configuredFast : configuredWalk;
        if (configured > 0) {
            return configured;
        }
        return fast ? fastSpeedScale(profile) : walkSpeedScale(profile);
    }

    private static double positive(double value) {
        return value <= 0 ? 1.0 : value;
    }
}
