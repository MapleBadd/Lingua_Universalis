package com.linguauniversalis.core.rule;

import com.linguauniversalis.core.species.SpeciesProfile.SpawnType;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 饱食/进食与自给规则（设计汇总 §4）。
 *
 * <p>口径：
 * <ul>
 *   <li>饥饿伤害仅在饱食 ≤0 时发生；倒地锁血期间免疫；休眠时饱食冻结，若已为 0 则继续慢慢饿死；</li>
 *   <li>自给：野生与"友善（特殊野生）"在饱食 <50% 时可自主觅食——
 *       固定刷新型从巢穴方块取食，自然刷新型"无中生有"；伙伴不无中生有，
 *       需投喂或从附近的伙伴宝箱取食（AI 层实现）；</li>
 *   <li>不饿（饱食已满）时不捡/不吃（存储动作由物品层处理）。</li>
 * </ul>
 */
public final class HungerRules {
    /** 饱食 <50%（20 的一半 → <10）触发自给觅食。 */
    public static final int FORAGE_BELOW = 10;

    private HungerRules() {
    }

    /** 是否处于饥饿（饱食 ≤0）。 */
    public static boolean isStarving(MonsterGirlState state) {
        return state.satiety() <= 0;
    }

    /**
     * 饥饿伤害当前是否会造成影响（锁血保护期间免疫；休眠时若饱食已 0 会继续挨饿直至饿死）。
     */
    public static boolean starvationDamageActive(MonsterGirlState state) {
        return isStarving(state) && !state.isDownedImmune();
    }

    /** 是否想吃东西（有饱食缺口）。 */
    public static boolean wantsToEat(MonsterGirlState state) {
        return state.satiety() < com.linguauniversalis.core.ModConstants.SATIETY_MAX;
    }

    /** 是否需要自主觅食（<50%）。 */
    public static boolean needsForage(MonsterGirlState state) {
        return state.satiety() < FORAGE_BELOW;
    }

    /**
     * 能否自主觅食（无中生有/巢穴取食）：伙伴及以上不可（需玩家管理）；
     * 野生与友善（特殊野生，执行野生 AI）可 —— 二者均非伙伴。
     */
    public static boolean canSelfForage(MonsterGirlState state) {
        return !state.isCompanion();
    }

    /**
     * 固定刷新型的自给来源：是否满足"巢穴方块在场"（由 AI 层提供布尔）。
     * 自然刷新型则总是可"无中生有"。
     */
    public static boolean canForageFromNest(MonsterGirlState state, SpawnType spawnType, boolean nestExists) {
        if (!canSelfForage(state) || !needsForage(state)) {
            return false;
        }
        return switch (spawnType) {
            case FIXED -> nestExists;
            case NATURAL -> true;
        };
    }
}
