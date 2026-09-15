package com.linguauniversalis.core.interaction;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 每日互动奖励跟踪：+数值奖励按"玩家 × 魔物娘 × 行为 × 每游戏日"只发放一次
 * （设计汇总 §7 每日次数口径）。行为本身可无限重复，只是不重复给数值。
 *
 * <p>统计键以字符串拼接 UUID 与游戏日数；调用方（服务端）需在每 tick/事件中提供
 * 正确的 gameDay（= gameTime / 24000L）。本类不做任何 MC 依赖，可单测。
 */
public final class DailyInteractionTracker implements DailyLimiter {
    private final Set<Key> used = new HashSet<>();

    public DailyInteractionTracker() {
    }

    /** 行为 id（pet/feed/gift/…，物种行为等）。 */
    public boolean canGain(String behavior, String actorUuid, String girlUuid, long gameDay) {
        return !used.contains(new Key(behavior, actorUuid, girlUuid, gameDay));
    }

    /** 记录一次已发放的每日奖励。 */
    public void markGained(String behavior, String actorUuid, String girlUuid, long gameDay) {
        used.add(new Key(behavior, actorUuid, girlUuid, gameDay));
    }

    /** 尝试领取：可领取则记录并返回 true。 */
    public boolean tryGain(String behavior, String actorUuid, String girlUuid, long gameDay) {
        if (!canGain(behavior, actorUuid, girlUuid, gameDay)) {
            return false;
        }
        markGained(behavior, actorUuid, girlUuid, gameDay);
        return true;
    }

    public void clear() {
        used.clear();
    }

    private record Key(String behavior, String actor, String girl, long day) {
        private Key {
            Objects.requireNonNull(behavior);
            Objects.requireNonNull(actor);
            Objects.requireNonNull(girl);
        }
    }
}
