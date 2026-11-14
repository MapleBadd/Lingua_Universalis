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

    /**
     * 玩家命令是否<b>压过"会移动的自主行为"</b>（去床上睡、去伙伴宝箱跑腿）。
     *
     * <p>优先级口径（玩家命令优先）：
     * <ul>
     *   <li>{@link CommandMode#STANDBY 待机}：站定不动 —— 不再自己走去睡觉、不再自己去箱子存取；</li>
     *   <li>{@link CommandMode#FOLLOW 跟随}：跟着主人走（睡觉/跑腿同样让位）；</li>
     *   <li>{@link CommandMode#WANDER 游荡}：允许自主活动，睡眠/跑腿照常生效。</li>
     * </ul>
     * <b>例外</b>（这些不算"自主移动"，仍照常执行）：原地进食、被攻击后自卫/反击、倒地、休眠。
     */
    public static boolean blocksAutonomousMovement(String obeyModeId) {
        return CommandMode.STANDBY.englishId.equals(obeyModeId)
                || CommandMode.FOLLOW.englishId.equals(obeyModeId);
    }

    /** 待命回位容差（格）：离待命点超过它才走回去，避免原地抖动。 */
    public static final double STANDBY_RETURN_RADIUS_BLOCKS = 1.5;

    /**
     * 待命时是否该走回待命点。
     *
     * @param standbyMode 当前是否为"待机"命令
     * @param blocked     是否有更优先的事情（战斗/取食/进食/倒地/休眠/睡觉）
     * @param distSq      到待命点的平方距离
     */
    public static boolean shouldReturnToStandby(boolean standbyMode, boolean blocked, double distSq) {
        return standbyMode && !blocked
                && distSq > STANDBY_RETURN_RADIUS_BLOCKS * STANDBY_RETURN_RADIUS_BLOCKS;
    }
}
