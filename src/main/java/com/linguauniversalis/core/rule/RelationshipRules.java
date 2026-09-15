package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 档位推进与好感衰减规则（纯规则，无 MC 依赖）。
 *
 * <p>口径（设计汇总 §2）：
 * <ul>
 *   <li>友善好感 &lt;10 → 立即变野生并清除友善对象（未达成伙伴前）；</li>
 *   <li>好感首次达到 100 → 解锁伙伴（永久黏性，不再回野生）；</li>
 *   <li>好感 200 且为伙伴 → 可誓约（好感锁定 200）；</li>
 *   <li>好感衰减：连续 3 天无互动后每天 -1；玩家离线时速度降至 10%；被玩家攻击每次 -1；
 *       饱食 &lt;6 时每天 -1。</li>
 * </ul>
 */
public final class RelationshipRules {
    private RelationshipRules() {
    }

    /** 玩家每次攻击造成的好感损失（誓约后锁定无效，由 {@link MonsterGirlState#setAffection} 保证）。 */
    public static void onPlayerAttack(MonsterGirlState state) {
        if (!state.vowed()) {
            state.addAffection(-ModConstants.AFFECTION_LOSS_PER_ATTACK);
        }
    }

    /** 饱食过低导致的每日好感 -1（调用方应确保每游戏日至多调用一次）。 */
    public static void onSatietyLowDaily(MonsterGirlState state) {
        if (!state.vowed() && state.isSatietyLow()) {
            state.addAffection(-ModConstants.AFFECTION_LOSS_SATIETY_LOW_DAILY);
        }
    }

    /**
     * 冷落衰减（返回应扣的好感量，可为小数，由调用方累积为整数应用）。
     *
     * @param consecutiveNoInteractionDays 距上次互动的连续天数（0 = 当天互动过）
     * @param ownerOffline                 绑玩家是否离线（离线时衰减 ×0.1）
     */
    public static double neglectLoss(int consecutiveNoInteractionDays, boolean ownerOffline) {
        int overdue = Math.max(0, consecutiveNoInteractionDays - ModConstants.AFFECTION_NO_INTERACTION_GRACE_DAYS);
        double scale = ownerOffline ? ModConstants.OFFLINE_DECAY_SCALE : 1.0;
        return overdue * ModConstants.AFFECTION_LOSS_PER_NEGLECT_DAY * scale;
    }

    /**
     * 档位一致性修正：每次数值变化后调用。
     * <ul>
     *   <li>未达成伙伴且好感 &lt;10 → 清除友善对象（退回野生）；</li>
     *   <li>好感 ≥100 且未解锁 → 解锁伙伴（永久）。</li>
     * </ul>
     * 返回修正后是否发生"回退到野生"。
     */
    public static boolean enforceCoherence(MonsterGirlState state) {
        boolean reverted = false;
        if (!state.isCompanion() && state.affection() < ModConstants.FRIENDLY_MIN) {
            if (state.boundPlayerUuid() != null) {
                state.setBoundPlayerUuid(null); // 退野生，清除友善对象
                reverted = true;
            }
        }
        if (state.affection() >= ModConstants.COMPANION_MIN && !state.companionUnlocked()) {
            state.setCompanionUnlocked(true); // 首次 100 → 永久伙伴
        }
        return reverted;
    }

    /** 好感达到 100 时把该玩家绑定为伙伴（伙伴继承自友善对象，目标玩家不变）。 */
    public static boolean tryPromoteToCompanion(MonsterGirlState state, String boundPlayerUuid) {
        if (state.affection() >= ModConstants.COMPANION_MIN && !state.companionUnlocked()) {
            if (boundPlayerUuid != null) {
                state.setBoundPlayerUuid(boundPlayerUuid);
            }
            state.setCompanionUnlocked(true);
            return true;
        }
        return false;
    }

    /** 誓约：伙伴 + 好感 200（+ 使用誓约协议书由调用方保证）。 */
    public static boolean tryVow(MonsterGirlState state) {
        if (state.isCompanion() && state.affection() >= ModConstants.VOW_MIN && !state.vowed()) {
            state.setCompanionUnlocked(true);
            state.setVowed(true);
            return true;
        }
        return false;
    }

    /** 命令权：伙伴档且好感 ≥100（伙伴好感回落 <100 失去命令权但仍是伙伴）。 */
    public static boolean canCommand(MonsterGirlState state) {
        return state.isCompanion() && state.affection() >= ModConstants.COMPANION_MIN;
    }
}
