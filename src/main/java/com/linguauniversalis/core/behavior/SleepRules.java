package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 睡眠规则（纯规则，无 MC 依赖）。
 *
 * <p>物种标签（{@link TemplateKeys}）：
 * <ul>
 *   <li>{@code species.nocturnal}（是/否）：<b>夜行性</b> —— 决定她"什么时候主动睡觉"。
 *       夜行性 = 白天睡、夜里活动；非夜行性 = 夜里睡、白天活动；</li>
 *   <li>{@code species.sizeClass}（0/1/2/3）：<b>尺寸</b> —— 小 / 中 / 大 / 超大，
 *       决定她能睡哪种魔物床（床与她的尺寸必须一致）。</li>
 * </ul>
 *
 * <p>睡觉去向（{@link #intent}）：
 * <ul>
 *   <li>有巢穴方块 → 在<b>巢心 3 格内</b>就地睡（不需要床）；</li>
 *   <li>无巢穴方块 + 伙伴 → 走到<b>尺寸匹配的魔物床</b>上睡；</li>
 *   <li>无巢穴方块 + 野生/友善 → <b>不睡</b>（没有她能睡的地方）；</li>
 *   <li>正在跟随玩家、倒地、心情低落、休眠（已经睡着的状态）→ 不进入睡眠流程。</li>
 * </ul>
 */
public final class SleepRules {
    private SleepRules() {
    }

    /** 体型档：小 / 中 / 大 / 超大（与魔物床的尺寸一一对应）。 */
    public enum SizeClass {
        SMALL(0, "small", "小"),
        MEDIUM(1, "medium", "中"),
        LARGE(2, "large", "大"),
        HUGE(3, "huge", "超大");

        private final int id;
        private final String key;
        private final String zh;

        SizeClass(int id, String key, String zh) {
            this.id = id;
            this.key = key;
            this.zh = zh;
        }

        public int id() {
            return id;
        }

        public String key() {
            return key;
        }

        public String zh() {
            return zh;
        }

        /** 由 0..3 解析（越界夹取）。 */
        public static SizeClass byId(int id) {
            SizeClass[] all = values();
            if (id < 0) {
                return SMALL;
            }
            return id >= all.length ? HUGE : all[id];
        }
    }

    /** 一天的总 tick（原版 24000）。 */
    public static final long TICKS_PER_DAY = 24000L;
    /** 白天区间 [0, 12000)。 */
    public static final long DAY_END = 12000L;

    /** 巢穴睡眠半径（格）：巢心 3 格内。 */
    public static final double NEST_SLEEP_RADIUS_BLOCKS = 3.0;
    /** 找床半径（格）。 */
    public static final double BED_SEARCH_RADIUS_BLOCKS = 16.0;
    /**
     * 躺下时脚离床方块底面的高度（格）—— 取原版 {@code LivingEntity#setPosToBed} 的 0.6875。
     *
     * <p>比床的碰撞箱顶面（0.5625）略高，靠重力落到床面上 ⇒ 判定为"接地"，
     * 不会出现"脚陷进床里 → onGround=false → 播悬空动画"。
     */
    public static final double BED_SURFACE_Y_OFFSET = 0.6875;
    /** 离床多近算"到床边"（格）。 */
    public static final double BED_REACH_BLOCKS = 1.6;
    /** 找床重算间隔（tick）。 */
    public static final int BED_SEARCH_INTERVAL_TICKS = 40;

    /** 该物种是否夜行性（默认 false = 昼行，夜里睡）。 */
    public static boolean nocturnal(SpeciesProfile profile) {
        return profile != null && profile.templateFlag(TemplateKeys.FLAG_SPECIES_NOCTURNAL, false);
    }

    /** 该物种的尺寸档（默认 1 = 中；对应"中魔物床"）。 */
    public static SizeClass sizeOf(SpeciesProfile profile) {
        if (profile == null) {
            return SizeClass.MEDIUM;
        }
        return SizeClass.byId((int) Math.round(
                profile.templateParam(TemplateKeys.SPECIES_SIZE_CLASS, SizeClass.MEDIUM.id())));
    }

    /**
     * 睡眠时段两端的缓冲（tick）：日出后 1000t（50 秒）才睡、日落前 1000t 就醒。
     *
     * <p>为什么要缓冲：不缓冲的话一到 tick 0 / 12000 就集体躺下/集体起床，和玩家"看着太阳"的
     * 直观感受不同；留一段过渡也更像生物而不是开关。
     */
    public static final long SLEEP_WINDOW_MARGIN_TICKS = 1000L;

    /**
     * 现在是不是她的睡眠时段（夜行性白天睡、昼行性夜里睡），两端各留
     * {@link #SLEEP_WINDOW_MARGIN_TICKS} 的缓冲。
     */
    public static boolean isSleepTime(long dayTime, boolean nocturnal) {
        long time = Math.floorMod(dayTime, TICKS_PER_DAY);
        boolean day = time < DAY_END;
        long sinceEdge = day ? time : time - DAY_END;                  // 距离本段起点的 tick
        long untilEdge = day ? DAY_END - time : TICKS_PER_DAY - time;  // 距离本段终点的 tick
        boolean inWindow = sinceEdge >= SLEEP_WINDOW_MARGIN_TICKS && untilEdge > SLEEP_WINDOW_MARGIN_TICKS;
        return nocturnal ? (day && inWindow) : (!day && inWindow);
    }

    /**
     * 一张床（两格）的**中心**相对"某一半方块坐标"的偏移（格）。
     *
     * <p>玩家/村民躺下时是躺在两格交界处，而不是某一半里 —— 之前直接用其中一个方块的 +0.5 会偏床头。
     *
     * @param towardHeadOnly 该方块是床头(true)还是床尾(false)
     * @param facingX        床朝向的 X 分量（-1/0/1）
     * @param facingZ        床朝向的 Z 分量（-1/0/1）
     * @return 长度 2 的数组 {dx, dz}
     */
    public static double[] bedCenterOffset(boolean towardHeadOnly, int facingX, int facingZ) {
        // 床朝向指向"床头"：从床尾走到中心 = 沿朝向走半格；从床头走到中心 = 反方向半格
        double sign = towardHeadOnly ? -0.5 : 0.5;
        return new double[]{sign * facingX, sign * facingZ};
    }

    /** 床与她的尺寸是否匹配（必须一致，不能"大床睡小个子"）。 */
    public static boolean bedMatchesSize(SizeClass girlSize, SizeClass bedSize) {
        return girlSize == bedSize;
    }

    /** 睡觉去向。 */
    public enum Intent {
        /** 不睡（不在睡眠时段 / 没有可睡的地方）。 */
        NONE,
        /** 去巢心附近睡。 */
        NEST,
        /** 去尺寸匹配的魔物床睡。 */
        BED
    }

    /**
     * 这一次她该往哪睡。
     *
     * @param sleepTime 现在是否睡眠时段
     * @param hasNest   该物种是否有巢穴方块（{@code profile.hasNest()}）
     * @param companion 是否伙伴档
     */
    public static Intent intent(boolean sleepTime, boolean hasNest, boolean companion) {
        if (!sleepTime) {
            return Intent.NONE;
        }
        if (hasNest) {
            return Intent.NEST; // 有巢的在自己的巢里睡（巢心附近就地睡）
        }
        return companion ? Intent.BED : Intent.NONE; // 无巢野生/友善：没有能睡的地方
    }

    /** 是否已经到"可以就地躺下"的位置（巢心半径内）。 */
    public static boolean canSleepAtNest(double distanceSqToNest) {
        return distanceSqToNest <= NEST_SLEEP_RADIUS_BLOCKS * NEST_SLEEP_RADIUS_BLOCKS;
    }

    /** 是否已经走到床边。 */
    public static boolean canSleepOnBed(double distanceSqToBed) {
        return distanceSqToBed <= BED_REACH_BLOCKS * BED_REACH_BLOCKS;
    }
}
