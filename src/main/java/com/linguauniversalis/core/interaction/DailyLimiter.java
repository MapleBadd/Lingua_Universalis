package com.linguauniversalis.core.interaction;

/**
 * 每日奖励限额接口：+数值奖励按"玩家 × 魔物娘 × 行为 × 游戏日"只发一次。
 *
 * <p>两种实现：
 * <ul>
 *   <li>{@link DailyInteractionTracker} —— 会话内（Set 式，测试/内存用）；</li>
 *   <li>{@link PersistentDailyLimiter} —— 随实体持久化（map 式：行为×玩家 → 最后奖励日）。</li>
 * </ul>
 */
public interface DailyLimiter {
    boolean canGain(String behavior, String actorUuid, String girlUuid, long gameDay);

    boolean tryGain(String behavior, String actorUuid, String girlUuid, long gameDay);
}
