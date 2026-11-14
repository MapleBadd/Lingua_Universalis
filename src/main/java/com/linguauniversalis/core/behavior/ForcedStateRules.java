package com.linguauniversalis.core.behavior;

/**
 * 调试用「状态切换棒」的强制状态规则（纯规则，无 MC 依赖）。
 *
 * <p>用途：把一只魔物娘强行钉在某个状态上（正常 / 休眠 / 战败），或让她**回归自然**，便于测试表现与流程。
 *
 * <p>四个档位（循环顺序如下）：
 * <ol>
 *   <li>{@link ForcedState#NORMAL} <b>自然</b>：不强制。切到这一档时会清掉强制造成的休眠/倒地，
 *       之后完全交给自然逻辑（该睡就睡、该被冷落就休眠）——**不持续保持**；</li>
 *   <li>{@link ForcedState#FORCED_NORMAL} <b>强制正常</b>：每 tick 清掉休眠/倒地，钉在"正常"；</li>
 *   <li>{@link ForcedState#DORMANT} <b>强制休眠</b>：每 tick 重新置为休眠；</li>
 *   <li>{@link ForcedState#DOWNED} <b>强制战败</b>：每 tick 保持倒地 + 满额锁血。</li>
 * </ol>
 */
public final class ForcedStateRules {
    private ForcedStateRules() {
    }

    /** 强制状态。 */
    public enum ForcedState {
        /** 自然：不强制（切过来时清一次强制痕迹，之后完全交给自然逻辑）。 */
        NORMAL("normal"),
        /** 强制正常：一直保持正常（不被休眠/战败影响）。 */
        FORCED_NORMAL("forced_normal"),
        /** 强制休眠。 */
        DORMANT("dormant"),
        /** 强制战败（倒地 + 锁血）。 */
        DOWNED("downed");

        private final String id;

        ForcedState(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 是否强制休眠。 */
        public boolean holdsDormant() {
            return this == DORMANT;
        }

        /** 是否强制战败。 */
        public boolean holdsDowned() {
            return this == DOWNED;
        }

        /** 是否为"自然"（不持续施加）。 */
        public boolean isNatural() {
            return this == NORMAL;
        }

        /** 由 id 解析；未知/空 → {@link #NORMAL}（宁可回归自然也钉错状态）。 */
        public static ForcedState byId(String id) {
            if (id != null) {
                for (ForcedState state : values()) {
                    if (state.id.equalsIgnoreCase(id.trim())) {
                        return state;
                    }
                }
            }
            return NORMAL;
        }
    }

    /** 切换棒的下一个状态（右键一次循环一格）。 */
    public static ForcedState next(ForcedState current) {
        ForcedState[] all = ForcedState.values();
        ForcedState safe = current == null ? ForcedState.NORMAL : current;
        return all[(safe.ordinal() + 1) % all.length];
    }

    /** 状态名翻译键（{@code item.lingua_universalis.debug_state_stick.state.<id>}）。 */
    public static String labelKey(ForcedState state) {
        return "item.lingua_universalis.debug_state_stick.state." + (state == null ? "normal" : state.id());
    }
}
