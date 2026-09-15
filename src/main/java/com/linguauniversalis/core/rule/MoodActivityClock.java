package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.ModConstants;

import java.util.ArrayDeque;

/**
 * 心情"活动不足"判定（纯逻辑，服务端以分钟驱动）。
 *
 * <p>口径（设计汇总 §3 心情降低途径 1）：
 * 每分钟记录一次自身坐标；若连续 60 次记录中任意两点的水平距离都 &lt;16 格，判定该小时
 * 活动不足；整日（采样足量时）判定为活动不足 → 连续 3 天活动不足则第 3 天结束心情 -1，
 * 此后每天仍活动不足则每天 -1。
 *
 * <p>实现口径说明：以"滚动 60 样本窗口内是否出现过 ≥16 格跨度"作为当日活动判定；
 * 采样不足一天（当日不足 60 次）按"活动充分"处理（宽松，避免误伤）。
 */
public final class MoodActivityClock {
    private final ArrayDeque<int[]> window = new ArrayDeque<>(); // (x, z)
    private int currentDay = -1;
    private int totalSamplesToday;
    private boolean activeThisDay;
    private int inactiveStreakDays;

    public MoodActivityClock() {
    }

    /** 记录一次坐标采样。day = 游戏日（gameTime / 24000）。 */
    public void recordSample(int day, int x, int z) {
        if (currentDay == -1) {
            currentDay = day;
        }
        if (day != currentDay) {
            // 跨日：先结算上一天（返回值忽略，由调用方按天结算），再开始新的一天
            settlePreviousDay();
            currentDay = day;
            window.clear();
            totalSamplesToday = 0;
            activeThisDay = false;
        }
        window.addLast(new int[]{x, z});
        totalSamplesToday++;
        if (window.size() > ModConstants.ACTIVITY_SAMPLE_COUNT) {
            window.removeFirst();
        }
        if (window.size() >= ModConstants.ACTIVITY_SAMPLE_COUNT && spanSquared(window) >= spanSquaredThreshold()) {
            activeThisDay = true;
        }
    }

    /**
     * 游戏日结束时结算：返回本日应扣的心情值（0 或 1）。
     */
    public int endOfDay(int day) {
        if (day != currentDay) {
            return 0; // 未对本日采样（如未加载），不扣
        }
        boolean dayActive = totalSamplesToday < ModConstants.ACTIVITY_SAMPLE_COUNT || activeThisDay;
        inactiveStreakDays = dayActive ? 0 : inactiveStreakDays + 1;
        int loss = inactiveStreakDays >= ModConstants.ACTIVITY_INACTIVE_DAYS
                ? ModConstants.MOOD_LOSS_PER_INACTIVE_DAY : 0;
        window.clear();
        totalSamplesToday = 0;
        activeThisDay = false;
        currentDay = -1;
        return loss;
    }

    /** 当前连续活动不足天数（供调试/UI）。 */
    public int inactiveStreakDays() {
        return inactiveStreakDays;
    }

    private void settlePreviousDay() {
        // 跨日场景的结算由调用方在每个游戏日结束显式调用 endOfDay；此处避免重复扣减，
        // 仅丢弃采样，并将"跨日"视为已处理。
        window.clear();
        totalSamplesToday = 0;
        activeThisDay = false;
    }

    private static long spanSquared(ArrayDeque<int[]> pts) {
        long max = 0;
        java.util.List<int[]> list = java.util.List.copyOf(pts);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                long dx = list.get(i)[0] - list.get(j)[0];
                long dz = list.get(i)[1] - list.get(j)[1];
                max = Math.max(max, dx * dx + dz * dz);
            }
        }
        return max;
    }

    private static long spanSquaredThreshold() {
        long b = ModConstants.ACTIVITY_MIN_SPAN_BLOCKS;
        return b * b;
    }
}
