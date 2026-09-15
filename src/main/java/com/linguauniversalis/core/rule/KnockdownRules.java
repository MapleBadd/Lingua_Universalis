package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 战败 / 倒地规则（纯逻辑；伤害结算由实体层负责）。
 *
 * <p>口径（设计汇总 §5）：
 * <ul>
 *   <li>仅非野生魔物娘可倒地：致命伤害 → 倒地，锁血 ≥1HP 共 60 秒，
 *       期间不受任何伤害、持续清除状态效果、扑灭火焰、浮于水面；</li>
 *   <li>锁血期间急救箱不可用（会有红字提示且不消耗）；</li>
 *   <li>锁血结束后仍倒地（1HP、无治疗）：此时可使用急救箱
 *       （回复 10% 最大生命 + 6 点饱食并退出倒地）；再次被攻击则击杀；</li>
 *   <li>若死于饥饿"算活该"——即锁血结束后饱食仍为 0 时饥饿伤害会致死。</li>
 * </ul>
 */
public final class KnockdownRules {
    private KnockdownRules() {
    }

    /** 判定一次致命伤害是否触发倒地（野生——未绑定任何玩家——不可倒地，直接死亡）。 */
    public static boolean shouldKnockdown(MonsterGirlState state) {
        return state.boundPlayerUuid() != null && !state.downed();
    }

    /** 进入倒地：设置锁血倒计时（60 秒）。 */
    public static void beginKnockdown(MonsterGirlState state) {
        if (state.downed()) {
            return;
        }
        state.setDowned(true);
        state.setDownedLockTicks(ModConstants.KNOCKDOWN_LOCK_TICKS);
    }

    /** 推进倒地状态一个 tick；锁血结束后返回 true 表示进入"脆弱期"（可用急救箱/会被补刀）。 */
    public static boolean tickKnockdown(MonsterGirlState state) {
        if (!state.downed()) {
            return false;
        }
        if (state.downedLockTicks() > 0) {
            state.setDownedLockTicks(state.downedLockTicks() - 1);
        }
        return state.downedLockTicks() == 0;
    }

    /** 急救箱是否可用：倒地且锁血结束。 */
    public static boolean canUseMedkit(MonsterGirlState state) {
        return state.isDownedVulnerable();
    }

    /**
     * 使用急救箱：回复 10% 最大生命 + 6 点饱食，退出倒地。
     * 返回恢复后的生命值（由调用方应用到实体）；锁血未结束时返回 -1（不可用）。
     */
    public static float applyMedkit(MonsterGirlState state, float maxHealth) {
        if (!canUseMedkit(state)) {
            return -1f;
        }
        state.addSatiety(ModConstants.MEDKIT_SATIETY);
        state.setDowned(false);
        return maxHealth * ModConstants.MEDKIT_HEAL_FRACTION;
    }

    /**
     * 倒地脆弱期再次被攻击 → 击杀（返回 true，调用方据此执行死亡与命缕掉落）。
     * 处于锁血保护中则不会死亡。
     */
    public static boolean isLethalWhileDowned(MonsterGirlState state) {
        return state.isDownedVulnerable();
    }
}
