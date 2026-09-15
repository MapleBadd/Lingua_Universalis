package com.linguauniversalis.core.state;

/**
 * 魔物娘状态快照（不可变 DTO）——用于服务端 → 客户端的同步（HUD/百晓镜/魔物娘 GUI）。
 * 数据均为原始类型/字符串，可直接映射为网络数据包或 JSON。
 */
public record GirlStateSnapshot(
        int affection,
        int mood,
        int satiety,
        int synergy,
        String stageId,
        boolean companionUnlocked,
        boolean vowed,
        boolean dormant,
        boolean depressed,
        boolean downed,
        boolean downedImmune,
        String boundPlayerUuid) {

    public static GirlStateSnapshot from(MonsterGirlState state) {
        return new GirlStateSnapshot(
                state.affection(),
                state.mood(),
                state.satiety(),
                state.synergy(),
                state.stageId(),
                state.companionUnlocked(),
                state.vowed(),
                state.dormant(),
                state.isDepressed(),
                state.downed(),
                state.isDownedImmune(),
                state.boundPlayerUuid());
    }
}
