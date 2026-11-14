package com.linguauniversalis.core.behavior;

/**
 * 注意力规则（纯规则，无 MC 依赖）—— 什么状态下她<b>不主动目视玩家</b>。
 *
 * <p>设计意图：注视玩家是"闲着没事才做的事"。正在进食、正在战斗、睡觉/休眠、被打倒（战败）
 * 这些状态下她有更该看的东西（食物、敌人、梦、地面），硬盯着玩家会很出戏。
 *
 * <p>与"看着谁"分开：战斗时她仍然会看向<b>敌人</b>（战斗逻辑自己 {@code setLookAt(target)}），
 * 本规则只决定要不要执行原版的"注视玩家"目标。
 */
public final class AttentionRules {
    private AttentionRules() {
    }

    /**
     * 是否应该执行"注视玩家"。
     *
     * @param downed   是否倒地（战败/濒死）
     * @param dormant  是否休眠（睡着）
     * @param depressed 是否心情低落
     * @param eating   是否正在进食（咀嚼动画窗口）
     * @param fighting 是否正在战斗（有交战目标）
     */
    public static boolean shouldLookAtPlayers(boolean downed, boolean dormant, boolean depressed,
                                              boolean eating, boolean fighting, boolean sleeping) {
        return !downed && !dormant && !depressed && !eating && !fighting && !sleeping;
    }

    /** 是否应该继续随机张望（同上，但战斗时不四处看，避免边打边甩头）。 */
    public static boolean shouldGlanceAround(boolean downed, boolean dormant, boolean eating, boolean fighting,
                                             boolean sleeping) {
        return !downed && !dormant && !eating && !fighting && !sleeping;
    }

    /**
     * 这三个状态下她<b>完全不主动面朝任何生物</b>（睡觉 / 休眠 / 战败）。
     *
     * <p>为什么需要单独一条：原版 {@code LookControl} 不只"看玩家"——一旦有目标（被打、看到敌人、
     * 或被别的 AI 塞了 {@code setLookAt}）她就会转头。睡觉/休眠/战败时应当"脸保持自己的朝向"，
     * 因此在 tick 末尾把头部朝向强制拉回身体朝向、俯仰归零。
     */
    public static boolean suppressesFacing(boolean downed, boolean dormant, boolean sleeping) {
        return downed || dormant || sleeping;
    }
}
