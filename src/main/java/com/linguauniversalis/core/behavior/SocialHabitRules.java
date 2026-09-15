package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.loot.WeightedGiftTable;
import com.linguauniversalis.core.species.SpeciesProfile;

import java.util.Random;

/**
 * 猫又社交习性规则（纯规则，无 MC 依赖；设计汇总 §12）。
 *
 * <ul>
 *   <li><b>偷鱼</b>：5 分钟/次，只偷目标玩家<b>背包</b>里的鱼（主手/副手/护甲除外，含自己绑定的玩家）；
 *       偷后跑远约 8 格吃掉、不囤积；<b>仅游荡状态触发</b>（跟随/待命不偷）；</li>
 *   <li><b>村庄偷窃</b>：每天 50% 概率偷村民绿宝石 1–3 颗；</li>
 *   <li><b>睡醒赠礼</b>：成功偷窃后，在玩家下一次睡醒时赠送礼物；礼物表各 25%（腐肉/骨头/铁锭/火药），
 *       若当天偷到过村民绿宝石则必送绿宝石（一次性，需再偷才能再送）；</li>
 *   <li><b>猫形陪睡</b>：伙伴非待命时以猫形态陪玩家睡觉；</li>
 *   <li><b>亡灵视野</b>：视野内出现亡灵生物每天一次 +1 心情。</li>
 * </ul>
 */
public final class SocialHabitRules {
    private SocialHabitRules() {
    }

    /** 偷鱼默认间隔（tick）：5 分钟。 */
    public static final long DEFAULT_FISH_STEAL_INTERVAL_TICKS = 6000L;
    /** 偷鱼目标玩家的最大距离（格）。 */
    public static final double FISH_STEAL_RANGE = 8.0;
    /** 偷到后跑开去吃掉的半径（格）。 */
    public static final double FLEE_DISTANCE = 8.0;
    /** 村民偷窃的判定距离（格）。 */
    public static final double VILLAGER_STEAL_RANGE = 8.0;
    /** 每天偷村民绿宝石的概率。 */
    public static final double EMERALD_STEAL_CHANCE = 0.5;
    /** 偷到的绿宝石数量范围。 */
    public static final int EMERALD_MIN = 1;
    public static final int EMERALD_MAX = 3;
    /** 亡灵视野心情判定半径（格）。 */
    public static final double UNDEAD_SIGHT_RANGE = 16.0;
    /** 亡灵视野心情增益。 */
    public static final int UNDEAD_SIGHT_MOOD_GAIN = 1;
    /** 背包槽位范围（原版玩家背包：快捷栏 0–8、主背包 9–35、护甲 36–39、副手 40）。 */
    public static final int BACKPACK_SLOT_MIN = 9;
    public static final int BACKPACK_SLOT_MAX = 35;

    // ------------------------------------------------------------------ 物种变量
    /** 是否会偷鱼（物种变量）。 */
    public static boolean stealsFish(SpeciesProfile profile) {
        return profile.templateFlag(TemplateKeys.FLAG_SPECIES_STEALS_FISH, false);
    }

    /** 偷鱼间隔（tick）。 */
    public static long fishStealIntervalTicks(SpeciesProfile profile) {
        return Math.max(200L, (long) profile.templateParam(
                TemplateKeys.SPECIES_FISH_STEAL_INTERVAL_TICKS, DEFAULT_FISH_STEAL_INTERVAL_TICKS));
    }

    /** 是否会偷村民绿宝石（物种变量）。 */
    public static boolean stealsEmeralds(SpeciesProfile profile) {
        return profile.templateFlag(TemplateKeys.FLAG_SPECIES_STEALS_EMERALDS, false);
    }

    /** 亡灵视野是否给心情（幽暗目默认 true）。 */
    public static boolean undeadSightMood(SpeciesProfile profile) {
        return profile.featureFlag(TemplateKeys.FLAG_FAMILIA_UNDEAD_SIGHT_MOOD, false);
    }

    // ------------------------------------------------------------------ 偷鱼
    /**
     * 该槽位是否可被偷：只偷背包（9–35），不偷快捷栏/主手/副手/护甲。
     */
    public static boolean isStealableSlot(int slot) {
        return slot >= BACKPACK_SLOT_MIN && slot <= BACKPACK_SLOT_MAX;
    }

    /**
     * 现在能否尝试偷鱼。
     *
     * @param wanderMode   是否处于游荡状态（跟随/待命不偷；野生无命令视为游荡）
     * @param playerNearby 目标玩家是否在 {@link #FISH_STEAL_RANGE} 内
     * @param nowTick      当前 tick
     * @param lastStealTick 上次偷窃 tick（-1 = 从未）
     * @param intervalTicks 偷窃间隔
     */
    public static boolean canStealFish(boolean wanderMode, boolean playerNearby,
                                       long nowTick, long lastStealTick, long intervalTicks) {
        if (!wanderMode || !playerNearby) {
            return false;
        }
        return lastStealTick < 0 || nowTick - lastStealTick >= intervalTicks;
    }

    // ------------------------------------------------------------------ 村庄偷窃
    /** 当天是否触发村民偷窃（传 [0,1) 随机数，便于测试）。 */
    public static boolean villageStealHappens(double roll) {
        return roll < EMERALD_STEAL_CHANCE;
    }

    /** 偷到的绿宝石数量（传 [0,1) 随机数，范围 1–3）。 */
    public static int emeraldAmount(double roll) {
        int span = EMERALD_MAX - EMERALD_MIN + 1;
        int idx = (int) Math.floor(Math.max(0.0, Math.min(0.999999, roll)) * span);
        return EMERALD_MIN + idx;
    }

    // ------------------------------------------------------------------ 亡灵视野
    /** 亡灵视野心情是否应结算（每天一次；需真的看得见）。 */
    public static boolean undeadSightMoodTrigger(boolean undeadVisible, long gameDay, long lastMoodDay) {
        return undeadVisible && gameDay != lastMoodDay;
    }

    // ------------------------------------------------------------------ 睡醒赠礼
    /**
     * 睡醒赠礼选择：当天偷到过绿宝石 → 绿宝石；否则按礼物表（各 25%）投掷。
     *
     * @param emeraldArmed 是否持有"偷到绿宝石"标记
     */
    public static String pickGift(boolean emeraldArmed, WeightedGiftTable table, Random random) {
        if (emeraldArmed) {
            return WeightedGiftTable.EMERALD;
        }
        return table.roll(random);
    }

    // ------------------------------------------------------------------ 猫形陪睡
    /**
     * 伙伴是否应以猫形态陪睡：伙伴档 + 非待机 + 绑玩家正在睡觉 + 在附近。
     *
     * @param distSq 与绑玩家的距离平方
     */
    public static boolean shouldSleepAsCat(boolean companion, boolean standingBy, boolean ownerSleeping,
                                           double distSq, double rangeBlocks) {
        return companion && !standingBy && ownerSleeping && distSq <= rangeBlocks * rangeBlocks;
    }

    /** 陪睡判定半径（格）。 */
    public static final double SLEEP_AS_CAT_RANGE = 8.0;
}
