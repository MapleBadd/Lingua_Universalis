package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.rule.RelationshipRules;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 命令与流浪规则（设计汇总 §2 命令权 / §7 Shift+右键 命令循环 / §3 低落不听指挥）。
 *
 * <p>口径：
 * <ul>
 *   <li>命令只有伙伴档可下，且好感 ≥100；心情低落或休眠时不受指挥；</li>
 *   <li>游荡范围：以切换为游荡那一瞬间的位置为中心区块，覆盖中心区块及其周围 8 个区块
 *       （共 9×9 区块）；若被迫离开则主动寻路返回范围（AI 层负责寻路）；</li>
 *   <li>陌生人（非绑玩家）无法命令。</li>
 * </ul>
 */
public final class CommandRules {
    private CommandRules() {
    }

    public enum CommandMode {
        FOLLOW("follow"),
        STANDBY("standby"),
        WANDER("wander");

        public final String englishId;

        CommandMode(String englishId) {
            this.englishId = englishId;
        }
    }

    /** 玩家是否可对魔物娘下达命令（伙伴 + 好感≥100 + 非低落/休眠 + 是绑玩家本人）。 */
    public static boolean canCommand(MonsterGirlState state, String actorUuid) {
        if (state.boundPlayerUuid() == null || !state.boundPlayerUuid().equals(actorUuid)) {
            return false;
        }
        if (state.isDepressed() || state.dormant()) {
            return false;
        }
        return RelationshipRules.canCommand(state);
    }

    /** 心情低落时不受指挥（可被摸头/投喂）。 */
    public static boolean isInsubordinate(MonsterGirlState state) {
        return state.isDepressed() || state.dormant();
    }
}
