package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 伙伴休眠（dormant）计时器（纯逻辑，服务端以真实毫秒驱动）。
 *
 * <p>口径（设计汇总 §2）：
 * 伙伴在好感与心情双 0 且连续 24h 无任何友好互动（含陌生人）后进入休眠；
 * 任何友好互动会重置计时；休眠后只有绑定的玩家上线并摸头才能唤醒（由调用方判定后
 * 调用 {@link #wakeByBoundPlayer}）。
 */
public final class DormancyClock {
    private long accumulatedMs;
    private long lastUpdateMs = -1;

    public DormancyClock() {
    }

    public enum Event {
        NONE,
        ENTERED_DORMANT
    }

    /**
     * 推进休眠计时。
     *
     * @param friendlyInteraction 本 tick 内是否有任何友好互动（含陌生人）
     */
    public Event update(MonsterGirlState state, boolean friendlyInteraction, long nowMs) {
        if (!state.isCompanion() || state.dormant()) {
            reset();
            return Event.NONE;
        }
        boolean bothZero = state.affection() == 0 && state.mood() == 0;
        if (!bothZero || friendlyInteraction) {
            reset();
            return Event.NONE;
        }
        if (lastUpdateMs < 0) {
            lastUpdateMs = nowMs;
            return Event.NONE;
        }
        accumulatedMs += Math.max(0L, nowMs - lastUpdateMs);
        lastUpdateMs = nowMs;
        if (accumulatedMs >= ModConstants.DORMANT_WAIT_MS) {
            state.setDormant(true);
            reset();
            return Event.ENTERED_DORMANT;
        }
        return Event.NONE;
    }

    /**
     * 绑定的玩家上线后对其摸头 → 唤醒。
     *
     * @return 是否确实唤醒
     */
    public boolean wakeByBoundPlayer(MonsterGirlState state, boolean boundPlayerOnline) {
        if (state.dormant() && boundPlayerOnline) {
            state.setDormant(false);
            reset();
            return true;
        }
        return false;
    }

    private void reset() {
        accumulatedMs = 0;
        lastUpdateMs = -1;
    }
}
