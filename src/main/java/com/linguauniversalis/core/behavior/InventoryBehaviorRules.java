package com.linguauniversalis.core.behavior;

/**
 * 魔物娘「物品栏行为」规则（纯规则，无 MC 依赖；可冒烟测试）——设计 §6。
 *
 * <p>覆盖四件事：
 * <ol>
 *   <li><b>拾取</b>：不饿不捡食物、背包满时不捡（含快捷栏在内都没空位）；</li>
 *   <li><b>入包</b>：捡起的东西先在快捷栏（手上）待 {@link InventoryRules#LIFT_TO_BACKPACK_DELAY_TICKS}
 *       tick，有空间就放进背包；</li>
 *   <li><b>进食</b>：饱食低于 {@link #EAT_SATIETY_THRESHOLD} 才吃；食物一律放副手食用；</li>
 *   <li><b>武器</b>：面板伤害高于自身近战才拿在手上；耐久降到 {@link #WEAPON_WORN_OUT_RATIO}
 *       以下就放回背包换别的（没有就徒手）。</li>
 * </ol>
 *
 * <p>另外还有与**伙伴宝箱**的联动参数（就近存取的距离与检查间隔）。
 */
public final class InventoryBehaviorRules {
    private InventoryBehaviorRules() {
    }

    // ---------------------------------------------------------------- 拾取
    /** 饱食高于此值时**不捡食物**（"不饿就不捡"）。 */
    public static final int PICKUP_FOOD_SATIETY_GATE = 8;

    /**
     * 是否愿意捡起这个物品。
     *
     * @param isFood        是否食物
     * @param satiety       当前饱食度
     * @param hasFreeSlot   快捷栏 + 背包里是否有空位
     */
    public static boolean shouldPickUp(boolean isFood, int satiety, boolean hasFreeSlot) {
        if (!hasFreeSlot) {
            return false; // 背包满时不捡
        }
        return !isFood || satiety < PICKUP_FOOD_SATIETY_GATE;
    }

    /** 捡起的东西是否该从快捷栏（手上）挪进背包了。 */
    public static boolean shouldLiftToBackpack(long pickedUpTick, long now, boolean backpackHasSpace) {
        if (pickedUpTick < 0 || !backpackHasSpace) {
            return false;
        }
        return now - pickedUpTick >= InventoryRules.LIFT_TO_BACKPACK_DELAY_TICKS;
    }

    // ---------------------------------------------------------------- 进食
    /** 饱食低于此值就去吃东西（吃一次补食物营养值）。 */
    public static final int EAT_SATIETY_THRESHOLD = 12;
    /** 两次进食之间的最短间隔（tick），防止站在食物堆里连续吞。 */
    public static final int EAT_COOLDOWN_TICKS = 100;
    /** 咀嚼时长（tick，与原版吃食物一致）。 */
    public static final int CHEW_TICKS = 32;

    /** 是否饿到该自己找吃的了。 */
    public static boolean isHungry(int satiety) {
        return satiety < EAT_SATIETY_THRESHOLD;
    }

    // ---------------------------------------------------------------- 武器
    /** 耐久消耗比例达到这个值就算"快坏了"，换回背包。 */
    public static final double WEAPON_WORN_OUT_RATIO = 0.10;

    /** 面板伤害是否值得拿在手上（要比自身近战高才拿）。 */
    public static boolean isWeaponUpgrade(double weaponDamage, double bareDamage) {
        return weaponDamage > bareDamage;
    }

    /** 武器是否已经"耐久 ≤10%"（不可损坏的物品永远算没坏）。 */
    public static boolean isWornOut(int damageValue, int maxDamage) {
        if (maxDamage <= 0) {
            return false;
        }
        return (double) damageValue >= maxDamage * (1.0 - WEAPON_WORN_OUT_RATIO);
    }

    // ---------------------------------------------------------------- 伙伴宝箱
    /** 找箱子的搜索半径（格）：超过这个距离的箱子她根本不理。 */
    public static final double CHEST_SEARCH_RADIUS_BLOCKS = 8.0;
    /** **存取必须走到箱子 2 格内**（只是能看见还不够）。 */
    public static final double CHEST_USE_RADIUS_BLOCKS = 2.0;
    /** 检查"要不要去箱子那儿"的间隔（tick，2 秒）——比存取本身频繁，免得她磨蹭半天才动身。 */
    public static final int CHEST_CHECK_INTERVAL_TICKS = 40;
    /** 跑腿去箱子的时限（tick，10 秒）：走不到就放弃，回去干正事。 */
    public static final int CHEST_ERRAND_TIMEOUT_TICKS = 200;
    /** 跑腿途中重下寻路指令的间隔（tick）。 */
    public static final int CHEST_ERRAND_REPATH_TICKS = 20;

    /** 是否已经在箱子 2 格内（可以存取了）。 */
    public static boolean withinChestReach(double distanceSq) {
        return distanceSq <= CHEST_USE_RADIUS_BLOCKS * CHEST_USE_RADIUS_BLOCKS;
    }

    /**
     * **要不要为箱子跑一趟**：只有"真要取食"或"真有东西可存"才去。
     *
     * <p>这条很关键：如果按"背包满"就出发、但执行时发现里面全是她舍不得放的东西（喜爱食物），
     * 就会变成"走过去 → 什么都没存 → 回去跟随 → 又走过去"的来回跑。
     * 判断"有没有东西可存"必须用与执行时**同一套过滤**（见调用方 {@code hasDepositableItems}）。
     */
    public static boolean shouldVisitChest(boolean wantsFood, boolean hasDepositableItems) {
        return wantsFood || hasDepositableItems;
    }

    /** 空跑一趟之后的冷却（tick，30 秒）：箱子满了 / 没东西可放时别在原地反复跑。 */
    public static final int CHEST_IDLE_COOLDOWN_TICKS = 600;

    /** 是否该把背包里的东西存进伙伴宝箱：伙伴档 + 背包（不含快捷栏）已经没有空位。 */
    public static boolean shouldDepositToChest(boolean companion, boolean backpackHasSpace) {
        return companion && !backpackHasSpace;
    }
}
