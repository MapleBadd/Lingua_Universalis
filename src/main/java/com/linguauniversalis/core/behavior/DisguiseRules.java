package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 伪装形态规则（纯规则，无 MC 依赖；设计汇总 §12 猫又）。
 *
 * <p>口径：伪装配方是<b>野生习性</b> —— 未成为伙伴的个体默认以伪装形态（双尾黑猫）出现；
 * 以下动作会让她<b>现出人形</b>：
 * <ul>
 *   <li>空手右键（与摸头同一手势：空手且未按 Shift）；</li>
 *   <li>受伤；</li>
 *   <li>主动攻击（近战或发射能力弹道）。</li>
 * </ul>
 * <b>投喂不触发人形</b>（持食物右键不现形）。人形后<b>一段时间无事</b>即恢复伪装形态。
 * 成为伙伴后伪装习性失效（伙伴的猫形态"陪睡"另行处理）。
 */
public final class DisguiseRules {
    private DisguiseRules() {
    }

    /** 触发人形的原因。 */
    public enum Trigger {
        /** 空手右键（摸头手势）。 */
        PET,
        /** 受伤。 */
        DAMAGED,
        /** 主动攻击。 */
        ATTACK
    }

    /** 该物种是否有伪装形态（物种变量，默认 false）。 */
    public static boolean hasDisguise(SpeciesProfile profile) {
        return profile.templateFlag(TemplateKeys.FLAG_SPECIES_DISGUISE, false);
    }

    /** 人形"无事"多久后恢复伪装（tick；默认 1200 = 60 秒，至少 1）。 */
    public static long revertTicks(SpeciesProfile profile) {
        return Math.max(1L, (long) profile.templateParam(TemplateKeys.SPECIES_DISGUISE_REVERT_TICKS, 1200.0));
    }

    /** 该触发是否使伪装失效（现出人形）。三类触发均会现形；本方法保留为显式口径。 */
    public static boolean reveals(Trigger trigger) {
        return trigger == Trigger.PET || trigger == Trigger.DAMAGED || trigger == Trigger.ATTACK;
    }

    /**
     * 当前档位是否仍执行伪装习性：<b>伙伴后失效</b>
     * （伙伴的猫形态是"陪玩家睡觉"的独立行为，不在此规则内）。
     */
    public static boolean disguisesAtStage(boolean companion) {
        return !companion;
    }

    /**
     * 是否应从人形恢复为伪装形态。
     *
     * @param nowTick        当前 tick
     * @param lastRevealTick 最近一次现形 tick（-1 = 从未现形）
     * @param revertTicks    恢复所需静默时长
     */
    public static boolean shouldRevert(long nowTick, long lastRevealTick, long revertTicks) {
        return lastRevealTick >= 0 && nowTick - lastRevealTick >= revertTicks;
    }
}
