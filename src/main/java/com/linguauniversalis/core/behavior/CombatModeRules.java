package com.linguauniversalis.core.behavior;

/**
 * 战斗模式规则（纯规则，无 MC 依赖）—— 伙伴协同作战的"被动 / 主动"开关。
 *
 * <p>口径：
 * <ul>
 *   <li><b>被动（PASSIVE，默认）</b>：不主动攻击。只有<b>被攻击</b>才会反击（被惹记忆窗口内有效）；</li>
 *   <li><b>主动（AGGRESSIVE）</b>：主动帮主人打——
 *       ① 主人正在攻击的生物；② 攻击了主人的生物。</li>
 * </ul>
 * 模式只影响"<b>帮主人打架</b>"这一件事：野生个体的领地/守巢敌意、以及她自己被惹后的反击，
 * 都不受模式影响（否则把野生个体的既有习性也一并关掉了）。
 *
 * <p>切换手段：哨子（{@code WhistleItem}）右键切换玩家 32 格内的所有魔物娘。
 */
public final class CombatModeRules {
    private CombatModeRules() {
    }

    /** 战斗模式。 */
    public enum CombatMode {
        /** 被动：不主动攻击，仅挨打后反击。 */
        PASSIVE("passive"),
        /** 主动：主动协助主人攻击"主人打的"与"打主人的"生物。 */
        AGGRESSIVE("aggressive");

        private final String id;

        CombatMode(String id) {
            this.id = id;
        }

        /** 稳定 id（同步/存档/指令用）。 */
        public String id() {
            return id;
        }

        /** 由 id 解析；未知 id 回落到 {@link #PASSIVE}（宁可不动手）。 */
        public static CombatMode byId(String id) {
            if (id != null) {
                for (CombatMode mode : values()) {
                    if (mode.id.equalsIgnoreCase(id.trim())) {
                        return mode;
                    }
                }
            }
            return PASSIVE;
        }
    }

    /** 默认模式：被动（不改动现有伙伴"只反击"的行为）。 */
    public static final CombatMode DEFAULT_MODE = CombatMode.PASSIVE;

    /** 哨子作用半径（格，玩家为圆心）。 */
    public static final double WHISTLE_RADIUS_BLOCKS = 32.0;

    /** 哨子是否覆盖该距离（参数为平方距离）。 */
    public static boolean withinWhistleRange(double distanceSq) {
        return distanceSq <= WHISTLE_RADIUS_BLOCKS * WHISTLE_RADIUS_BLOCKS;
    }

    /** 切换：被动 ↔ 主动。 */
    public static CombatMode toggle(CombatMode mode) {
        return mode == CombatMode.AGGRESSIVE ? CombatMode.PASSIVE : CombatMode.AGGRESSIVE;
    }

    /** 该模式是否会主动帮主人打架。 */
    public static boolean assistsOwner(CombatMode mode) {
        return mode == CombatMode.AGGRESSIVE;
    }

    /**
     * 这一 tick 是否该去协助主人。
     *
     * @param mode         当前模式
     * @param bonded       是否有绑定主人（野生个体没有协助对象）
     * @param assistTarget 主人那边算出来的"该打的生物"是否为有效目标
     */
    public static boolean shouldAssist(CombatMode mode, boolean bonded, boolean assistTarget) {
        return assistsOwner(mode) && bonded && assistTarget;
    }

    /** 回防距离（格）：主动模式下离主人超过这个距离就放弃追击、回到主人身边。 */
    public static final double OWNER_RETURN_RADIUS_BLOCKS = 24.0;

    /**
     * 是否该<b>放弃追击回家</b>（主动模式的防脱队）。
     *
     * <p>滞回设计：协助半径 {@link #WHISTLE_RADIUS_BLOCKS}={@code 32} 只是"哨子/协助"的范围，
     * 回防距离取更小的 24 格 —— 这样她追出去一段后会自己回来，不会在 32 格边缘反复横跳。
     *
     * @param mode             战斗模式
     * @param bonded           是否有绑定主人
     * @param fighting         当前是否有交战目标
     * @param distSqToOwner    到主人的平方距离
     */
    public static boolean shouldReturnToOwner(CombatMode mode, boolean bonded, boolean fighting, double distSqToOwner) {
        if (!assistsOwner(mode) || !bonded) {
            return false;
        }
        return distSqToOwner > OWNER_RETURN_RADIUS_BLOCKS * OWNER_RETURN_RADIUS_BLOCKS;
    }

    /**
     * 是否允许"自己主动狩猎"（不依赖主人）。
     *
     * <p>伙伴永远不自己乱打（保持伙伴设定）；野生个体照旧走领地/守巢敌意规则。
     */
    public static boolean mayHuntFreely(boolean bonded) {
        return !bonded;
    }
}
