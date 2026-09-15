package com.linguauniversalis.core.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 可持久化的每日限额器：以"行为 × 玩家"为键记录最后一次奖励的游戏日。
 *
 * <p>按实体挂载（每只魔物娘一份），girlUuid 参数可忽略（隐含为宿主）。
 * 编码为分隔字符串随实体存档保存，重进存档后当天奖励不会重置。
 *
 * <p>编码：条目 {@code behavior\u0001actor\u0001day}，条目间以 {@code \u0002} 分隔；
 * {@link #snapshotEncoded(long)} 保存时会丢弃早于 (currentDay-1) 的旧记录以控制体积。
 */
public final class PersistentDailyLimiter implements DailyLimiter {
    /** key = behavior + \u0001 + actor */
    private final Map<String, Long> lastRewardDay = new HashMap<>();

    public PersistentDailyLimiter() {
    }

    @Override
    public boolean canGain(String behavior, String actorUuid, String girlUuid, long gameDay) {
        Long last = lastRewardDay.get(key(behavior, actorUuid));
        return last == null || last != gameDay;
    }

    @Override
    public boolean tryGain(String behavior, String actorUuid, String girlUuid, long gameDay) {
        if (!canGain(behavior, actorUuid, girlUuid, gameDay)) {
            return false;
        }
        lastRewardDay.put(key(behavior, actorUuid), gameDay);
        return true;
    }

    public void clear() {
        lastRewardDay.clear();
    }

    /** 编码（仅保留 currentDay-1 及之后的记录）。 */
    public String snapshotEncoded(long currentDay) {
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, Long> e : lastRewardDay.entrySet()) {
            if (e.getValue() >= currentDay - 1) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\u0002');
            }
            sb.append(e.getKey()).append('\u0001').append(e.getValue());
        }
        return sb.toString();
    }

    /** 从 {@link #snapshotEncoded(long)} 解码；清空旧值。 */
    public void fromEncoded(String encoded) {
        clear();
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        for (String entry : encoded.split("\u0002", -1)) {
            String[] parts = entry.split("\u0001", -1);
            if (parts.length == 3) {
                try {
                    lastRewardDay.put(parts[0] + "\u0001" + parts[1], Long.parseLong(parts[2]));
                } catch (NumberFormatException ignored) {
                    // 跳过损坏条目
                }
            }
        }
    }

    private static String key(String behavior, String actorUuid) {
        Objects.requireNonNull(behavior);
        Objects.requireNonNull(actorUuid);
        return behavior + "\u0001" + actorUuid;
    }
}
