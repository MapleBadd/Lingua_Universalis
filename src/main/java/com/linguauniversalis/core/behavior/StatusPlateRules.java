package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.ModConstants;

/**
 * 头顶状态牌规则（纯规则，无 MC 依赖）。
 *
 * <p>魔物娘头顶的"名牌"显示她的即时状态（替代命名牌）：
 * <pre>
 *   主动 / 被动            常态：显示当前战斗模式
 *   休眠                   休眠中（优先级高于模式）
 *   战败 42 秒             倒地锁血期：剩余秒数倒计时
 *   濒死                   倒地锁血结束（可被急救箱救起，再被打即死）
 * </pre>
 * 优先级：倒地 &gt; 休眠 &gt; 模式。
 */
public final class StatusPlateRules {
    private StatusPlateRules() {
    }

    /** 状态牌显示的状态种类。 */
    public enum StatusKind {
        /** 常态：显示战斗模式（被动/主动）。 */
        MODE,
        /** 休眠。 */
        DORMANT,
        /** 战败（倒地锁血中，带倒计时秒数）。 */
        DEFEATED,
        /** 濒死（倒地且锁血结束）。 */
        DYING
    }

    /**
     * 状态牌内容。
     *
     * @param kind       状态种类
     * @param mode       战斗模式（{@link StatusKind#MODE} 时使用）
     * @param seconds    剩余秒数（{@link StatusKind#DEFEATED} 时使用）
     */
    public record Status(StatusKind kind, CombatModeRules.CombatMode mode, int seconds) {
    }

    /** 常态/休眠/战败/濒死的判定（倒地优先，其次休眠）。 */
    public static Status statusOf(boolean dormant, boolean downed, long downedLockTicks,
                                  CombatModeRules.CombatMode mode) {
        CombatModeRules.CombatMode shown = mode == null ? CombatModeRules.DEFAULT_MODE : mode;
        if (downed) {
            if (downedLockTicks > 0) {
                return new Status(StatusKind.DEFEATED, shown, secondsFromTicks(downedLockTicks));
            }
            return new Status(StatusKind.DYING, shown, 0);
        }
        if (dormant) {
            return new Status(StatusKind.DORMANT, shown, 0);
        }
        return new Status(StatusKind.MODE, shown, 0);
    }

    /**
     * 客户端用：同步过来的是<b>秒</b>（不是 tick），直接按秒判定。
     *
     * @param downedSeconds 同步的倒地锁血剩余秒数（0 = 锁血结束）
     */
    public static Status statusForDisplay(boolean dormant, boolean downed, int downedSeconds,
                                         CombatModeRules.CombatMode mode) {
        CombatModeRules.CombatMode shown = mode == null ? CombatModeRules.DEFAULT_MODE : mode;
        if (downed) {
            return downedSeconds > 0
                    ? new Status(StatusKind.DEFEATED, shown, downedSeconds)
                    : new Status(StatusKind.DYING, shown, 0);
        }
        if (dormant) {
            return new Status(StatusKind.DORMANT, shown, 0);
        }
        return new Status(StatusKind.MODE, shown, 0);
    }

    /** tick → 向上取整的秒数（0 tick = 0 秒）。 */
    public static int secondsFromTicks(long ticks) {
        if (ticks <= 0) {
            return 0;
        }
        return (int) ((ticks + ModConstants.TICKS_PER_SECOND - 1) / ModConstants.TICKS_PER_SECOND);
    }

    /** 状态牌在多远内显示（格）。超出则完全不画，避免远处一片文字。 */
    public static final double PLATE_VISIBLE_RADIUS_BLOCKS = 24.0;

    /** 是否显示状态牌（参数为到相机的平方距离）。 */
    public static boolean shouldShow(double distanceToCameraSq) {
        return distanceToCameraSq <= PLATE_VISIBLE_RADIUS_BLOCKS * PLATE_VISIBLE_RADIUS_BLOCKS;
    }

    /** 该状态是否"值得无条件显示"（倒地/休眠：即使玩家没看她也要能看到倒计时）。 */
    public static boolean isUrgent(Status status) {
        return status != null && status.kind() != StatusKind.MODE;
    }
}
