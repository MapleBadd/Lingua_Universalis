package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 心情低落·随机攻击模板的"击杀回心情"计时（纯逻辑）。
 *
 * <p>口径（设计汇总 §3「低落·随机攻击」模板）：心情 <20 时强制随机游荡并攻击近战范围内
 * 的非玩家生物；击杀生物后 1–2 分钟内心情回升至 30（若已 &gt;30 保持不变）。
 */
public final class LowMoodRecoveryTracker {
    private long lastKillMs = -1;

    public LowMoodRecoveryTracker() {
    }

    /** 记录一次（低落状态下的）击杀时刻。 */
    public void onKill(long nowMs) {
        lastKillMs = nowMs;
    }

    /**
     * 逐 tick 推进：若处于低落且在击杀后恢复窗口内，把心情抬到 30。
     *
     * @return 是否在本 tick 内完成了恢复
     */
    public boolean update(MonsterGirlState state, long nowMs) {
        if (!state.isDepressed() || lastKillMs < 0) {
            return false;
        }
        long elapsed = nowMs - lastKillMs;
        if (elapsed > ModConstants.MOOD_RECOVER_WINDOW_SECONDS * 1000L) {
            lastKillMs = -1;
            return false;
        }
        if (state.mood() < ModConstants.MOOD_RECOVER_AFTER_KILL) {
            state.setMood(ModConstants.MOOD_RECOVER_AFTER_KILL);
            lastKillMs = -1;
            return true;
        }
        return false;
    }
}
