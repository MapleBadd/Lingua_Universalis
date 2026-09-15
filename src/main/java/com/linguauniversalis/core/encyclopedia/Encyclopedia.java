package com.linguauniversalis.core.encyclopedia;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 图鉴（初稿 / 百晓镜录入）——按玩家持久化存储的纯数据模型。
 *
 * <p>口径（设计汇总 §10）：图鉴记录"碰见"的野生魔物娘，并在了解过程中逐步解锁条目；
 * 解锁条目由 {@link ObservationRules} 定义（目击/缩放观测/喜爱物互动等）。
 * 存档最终挂到玩家持久数据（PlayerData），此处仅做纯数据层。
 */
public final class Encyclopedia {
    private Encyclopedia() {
    }

    /** 知识/条目类型。 */
    public enum Kind {
        /** 目击：已记录该物种（初稿仅收录"碰见"过的）。 */
        SIGHT,
        /** 基本信息：血/心情/饱食等实时数据可见（百晓镜基础观测）。 */
        BASIC_INFO,
        /** 分类学位置（百晓镜缩放观测）。 */
        TAXONOMY,
        /** 敌意/红线等行为条目（缩放观测的详细信息）。 */
        HOSTILITY,
        /** 喜好/偏好条目（通过喜爱物互动发现）。 */
        PREFERENCES
    }

    public static final class PlayerEncyclopedia {
        private final Map<String, Set<Kind>> bySpecies = new LinkedHashMap<>();

        public PlayerEncyclopedia() {
        }

        /** 目击记录（幂等）。 */
        public boolean recordSighting(String speciesId) {
            return add(speciesId, Kind.SIGHT);
        }

        /** 解锁某物种的一个知识条目；返回是否新增。 */
        public boolean add(String speciesId, Kind kind) {
            return bySpecies.computeIfAbsent(speciesId, k -> EnumSet.noneOf(Kind.class)).add(kind);
        }

        public boolean isUnlocked(String speciesId, Kind kind) {
            Set<Kind> kinds = bySpecies.get(speciesId);
            return kinds != null && kinds.contains(kind);
        }

        public boolean hasSighted(String speciesId) {
            return bySpecies.containsKey(speciesId);
        }

        /** 某物种已解锁条目（只读）。 */
        public Set<Kind> knownKinds(String speciesId) {
            Set<Kind> kinds = bySpecies.get(speciesId);
            return kinds == null ? Collections.emptySet() : Collections.unmodifiableSet(kinds);
        }

        /** 已目击物种 id（插入序，只读）。 */
        public java.util.Collection<String> sightedSpecies() {
            return Collections.unmodifiableCollection(bySpecies.keySet());
        }

        public int sightedCount() {
            return bySpecies.size();
        }
    }
}
