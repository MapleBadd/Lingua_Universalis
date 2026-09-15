package com.linguauniversalis.core.anim;

/**
 * 动画播放规则（纯规则，无 MC/GeckoLib 依赖；可冒烟测试）。
 *
 * <p>通用约定（对全部魔物娘动画生效）：
 * <ul>
 *   <li><b>main_ 前缀 = 主要动画</b>：同一时间只播一个；切换时由 GeckoLib 做过渡
 *       （{@link #MAIN_TRANSITION_TICKS}）；</li>
 *   <li><b>不带 main_ 前缀 = 混合动画</b>：满足条件时叠加在当前主要动画上；</li>
 *   <li><b>random_ 前缀 = 随机动画</b>：每秒 {@link #RANDOM_CHANCE_PER_SECOND} 概率触发"某系列"的一个动画；
 *       同系列播放期间不能再次触发（不同系列可并行）；系列名 = 去掉 random_ 前缀与结尾数字；</li>
 *   <li>{@code main_jump}：<b>主动跳跃</b>时播放一遍（末帧停住），播完自动转 {@code main_airborne}；</li>
 *   <li>{@code main_airborne}：非飞行/游泳时悬空触发，<b>优先级高于 walk/run/sprint</b>；</li>
 *   <li>游泳/潜行动画暂缺，先以 walk 代替。</li>
 * </ul>
 */
public final class AnimationRules {
    private AnimationRules() {
    }

    // ------------------------------------------------------------------ 动画名（与资源文件一致）
    public static final String MAIN_IDLE = "main_idle";
    public static final String MAIN_WALK = "main_walk";
    public static final String MAIN_JUMP = "main_jump";
    public static final String MAIN_AIRBORNE = "main_airborne";
    public static final String MAIN_EAT = "main_eat";
    public static final String MAIN_SIT = "main_sit";
    /** 未启用（占位）：猫又的 playing1 / hiss1。 */
    public static final String MAIN_PLAYING1 = "main_playing1";
    public static final String MAIN_HISS1 = "main_hiss1";

    /** 常驻混合动画（一直在播）。 */
    public static final String BLEND_CONSTANT = "constant";
    /** 尾巴混合动画（按心情/状态常驻；目前只有 swing）。 */
    public static final String BLEND_TAIL_SWING = "tail_swing";
    /** 摸头触发的混合动画。 */
    public static final String BLEND_PET = "pet";
    /**
     * 随机眨眼（具体动画名，带系列序号）——系列键即去掉结尾数字的 {@code random_blink}
     * （见 {@link #randomSeriesOf(String)}，用于同系列互斥）。
     */
    public static final String RANDOM_BLINK = "random_blink1";
    /** 随机小动作（具体动画名；系列键 {@code random_idle}）。 */
    public static final String RANDOM_IDLE = "random_idle1";

    /**
     * 资源文件里必须存在的全部动画名（供冒烟测试与资源文件对表，防止改名后静默失效）。
     *
     * <p>新增/改名动画时同步这里与 {@code geckolib/animations/<species>.animation.json}。
     */
    public static final String[] REQUIRED_ANIMATIONS = {
            MAIN_IDLE, MAIN_WALK, MAIN_JUMP, MAIN_AIRBORNE, MAIN_EAT, MAIN_SIT,
            MAIN_PLAYING1, MAIN_HISS1,
            BLEND_CONSTANT, BLEND_TAIL_SWING, BLEND_PET,
            RANDOM_BLINK, RANDOM_IDLE,
    };

    // ------------------------------------------------------------------ 参数
    /** 主要动画过渡时长（tick）。 */
    public static final int MAIN_TRANSITION_TICKS = 4;
    /** 随机动画每秒触发概率（3%）。 */
    public static final double RANDOM_CHANCE_PER_SECOND = 0.03;
    /** 随机动画投掷间隔（tick，1 秒）。 */
    public static final int RANDOM_ROLL_INTERVAL_TICKS = 20;
    /**
     * 主动跳跃动画的播放时长（tick）。
     *
     * <p>规则：**跳跃动画必须播完一遍才衔接下一个动画** —— 主控制器在 {@code jumpAnimActive} 期间
     * 恒返回 {@code main_jump}（优先级最高），时长取"动画全长 0.4167 秒 ≈ 8.3 tick"并留 2 tick 余量，
     * 保证末帧（{@code hold_on_last_frame}）也播到，之后再交给 {@code main_airborne}。
     */
    public static final int JUMP_ANIM_TICKS = 10;

    /**
     * 主要动画选择（优先级从高到低）：
     * <ol>
     *   <li>{@code main_jump}（主动跳跃播一遍，末帧停住 → 播完自然转 airborne）；</li>
     *   <li>{@code main_airborne}（非飞行/游泳时悬空，优先级高于 walk/run/sprint，避免空中穿帮）；</li>
     *   <li>{@code main_eat}（进食）；</li>
     *   <li>{@code main_sit}（休眠/静坐）；</li>
     *   <li>{@code main_walk}（移动；游泳/潜行暂时也用它）；</li>
     *   <li>{@code main_idle}（其余情况）。</li>
     * </ol>
     */
    public static String mainAnimation(boolean jumpAnimActive, boolean airborne, boolean eating,
                                       boolean sitting, boolean moving) {
        if (jumpAnimActive) {
            return MAIN_JUMP;
        }
        if (airborne) {
            return MAIN_AIRBORNE;
        }
        if (eating) {
            return MAIN_EAT;
        }
        if (sitting) {
            return MAIN_SIT;
        }
        if (moving) {
            return MAIN_WALK;
        }
        return MAIN_IDLE;
    }

    /**
     * 悬空判定（规则 6）：没有飞行、没有游泳/浸水，且不在地面。
     *
     * @param onGround   是否在地面
     * @param inWater    是否在水/熔岩中（游泳）
     * @param flying     是否处于飞行（如滑翔）
     */
    public static boolean isAirborne(boolean onGround, boolean inWater, boolean flying) {
        return !onGround && !inWater && !flying;
    }

    /** 刚登录/重载后判定为悬空所需的持续时间（tick）：过滤"进游戏头两帧还没落地"造成的动画重置。 */
    public static final int AIRBORNE_SETTLE_TICKS = 4;
    /** 判定为"真的在坠落"的掉落距离（格）：超过它就直接算悬空，不用等 {@link #AIRBORNE_SETTLE_TICKS}。 */
    public static final double AIRBORNE_FALL_DISTANCE = 0.5;

    /**
     * 是否应展示 {@code main_airborne}（在 {@link #isAirborne} 基础上加"稳定"条件）。
     *
     * <p>为什么要加：实体刚进入世界（进退游戏/区块加载）时 {@code onGround()} 会先为 false 一两 tick，
     * 直接按悬空处理会让每次进游戏都先播一两秒 airborne，穿帮。这里要求"连续离地若干 tick"
     * 或者"已经在往下掉"，真实跳跃/坠落几乎立刻满足。
     */
    public static boolean shouldShowAirborne(boolean airborne, double fallDistance, int airborneTicks) {
        return airborne && (airborneTicks >= AIRBORNE_SETTLE_TICKS || fallDistance > AIRBORNE_FALL_DISTANCE);
    }

    /** 判定"主动起跳"所需的最小向上速度（格/tick）。 */
    public static final double ACTIVE_JUMP_MIN_UPWARD_SPEED = 0.08;

    /** 走路动画默认播放倍速（动画按"非真实移速"制作时用来对齐，见配置 animation.walkSpeed）。 */
    public static final double DEFAULT_WALK_ANIMATION_SPEED = 1.0;

    /**
     * 各主要动画的播放倍速：目前只对 {@code main_walk} 生效（制作动画时没按真实移速配步频）。
     *
     * @param animationName  当前主要动画名
     * @param walkSpeedScale {@code main_walk} 的倍速（其它动画恒为 1.0）
     */
    public static double mainAnimationSpeed(String animationName, double walkSpeedScale) {
        if (!MAIN_WALK.equals(animationName)) {
            return 1.0;
        }
        return walkSpeedScale <= 0 ? 1.0 : walkSpeedScale;
    }

    /**
     * 是否处于"主动跳跃"（用于播一遍 {@code main_jump}）。
     *
     * <p>不要只看 {@code isJumping()}：那是 AI 通过 {@code JumpControl} 请求跳跃的标记，
     * 而本模组脊索界自带"自动踏上 1 格"，寻路时常常直接走上去不请求跳跃，
     * 于是 {@code isJumping()} 常年为 false、跳跃动画永不触发。
     * 这里改为"离地 + 正在向上运动"来判定（被动掉落是向下运动，不会被误判）。
     */
    public static boolean isActiveJump(boolean onGround, boolean inWater, boolean flying,
                                       double deltaY, boolean jumpRequested) {
        if (onGround || inWater || flying) {
            return false;
        }
        return jumpRequested || deltaY > ACTIVE_JUMP_MIN_UPWARD_SPEED;
    }

    /** 随机动画系列名：去掉 {@code random_} 前缀与结尾数字（random_blink / random_blink2 → blink）。 */
    public static String randomSeriesOf(String animationName) {
        String name = animationName;
        if (name.startsWith("random_")) {
            name = name.substring("random_".length());
        }
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end);
    }

    /**
     * 随机动画计时器（每个<b>系列</b>一个实例）。
     *
     * <p>规则 4：每秒有 3% 概率触发该系列的一个动画；某动画播完之前，<b>同系列</b>不能再次触发
     * （不同系列可以同时触发）。
     */
    public static final class RandomSeries {
        private final String animation;
        private final int durationTicks;
        private long playingUntilTick = -1L;
        private long lastRollTick = -1L;
        private int triggerCount;

        public RandomSeries(String animation, int durationTicks) {
            this.animation = animation;
            this.durationTicks = Math.max(1, durationTicks);
        }

        public String animation() {
            return animation;
        }

        /** 累计触发次数（调试用）。 */
        public int triggerCount() {
            return triggerCount;
        }

        /** 当前是否正在播放该系列的动画。 */
        public boolean isPlaying(long nowTick) {
            return playingUntilTick >= 0 && nowTick < playingUntilTick;
        }

        /**
         * 每秒投掷一次；命中概率则开始播放本系列的动画。
         *
         * @param nowTick 当前 tick（任意单调递增计数即可，客户端用 tickCount）
         * @param roll    [0,1) 随机数
         * @return 本 tick 该系列是否应在播放（含正在播放中）
         */
        public boolean poll(long nowTick, double roll) {
            if (isPlaying(nowTick)) {
                return true; // 同系列播放期间不重复触发，但持续播放
            }
            if (lastRollTick >= 0 && nowTick - lastRollTick < RANDOM_ROLL_INTERVAL_TICKS) {
                return false; // 本秒已经投掷过
            }
            lastRollTick = nowTick;
            if (roll < RANDOM_CHANCE_PER_SECOND) {
                playingUntilTick = nowTick + durationTicks;
                triggerCount++;
                return true;
            }
            return false;
        }
    }
}
