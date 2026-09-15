package com.linguauniversalis.core;

/**
 * 数值口径常量（对齐《万象牧语-设计汇总.md》）。
 *
 * <p>所有"可调项"集中于此，方便后续迁移到服务端配置文件（Phase 4+）。
 */
public final class ModConstants {
    private ModConstants() {
    }

    // ---------------------------------------------------------------- 属性范围
    public static final int AFFECTION_MAX = 200;
    public static final int MOOD_MAX = 100;
    public static final int MOOD_DEFAULT = 70;
    public static final int SATIETY_MAX = 20;
    public static final int SYNERGY_MAX = 100;

    // ---------------------------------------------------------------- 档位阈值
    /** 0–9 野生；10 起友善；100 起伙伴（首次到达后永久）；200 可誓约。 */
    public static final int FRIENDLY_MIN = 10;
    public static final int COMPANION_MIN = 100;
    public static final int VOW_MIN = AFFECTION_MAX;

    // ---------------------------------------------------------------- 心情
    /** 心情 <20 → 心情低落 depressed。 */
    public static final int MOOD_DEPRESSED_BELOW = 20;
    /** 低落·随机攻击模板：击杀生物后回升到的目标心情。 */
    public static final int MOOD_RECOVER_AFTER_KILL = 30;
    /** 低落恢复判定窗口（秒）。 */
    public static final long MOOD_RECOVER_WINDOW_SECONDS = 120;

    /** 摸头心情回复（每日一次）。 */
    public static final int PET_MOOD_GAIN = 10;

    /** 饱食 ≤ 此值触发心情 -1（每日一次）。 */
    public static final int SATIETY_LOW = 6;

    // 活动不足判定：每分钟记录一次坐标，连续 60 次记录中最大跨度 <16 格 → 当日活动不足；
    // 连续 3 天活动不足 → 第 3 天结束心情 -1，此后持续则每天 -1。
    public static final int ACTIVITY_SAMPLE_INTERVAL_MINUTES = 1;
    public static final int ACTIVITY_SAMPLE_COUNT = 60;
    public static final int ACTIVITY_MIN_SPAN_BLOCKS = 16;
    public static final int ACTIVITY_INACTIVE_DAYS = 3;
    public static final int MOOD_LOSS_PER_INACTIVE_DAY = 1;

    // ---------------------------------------------------------------- 好感增减
    public static final int AFFECTION_PER_FEED = 1;
    public static final int AFFECTION_PER_GIFT = 2;
    public static final int AFFECTION_PER_PET = 1;
    /** 被玩家攻击一次 -1（每次命中）。 */
    public static final int AFFECTION_LOSS_PER_ATTACK = 1;
    /** 连续 N 天无互动开始衰减。 */
    public static final int AFFECTION_NO_INTERACTION_GRACE_DAYS = 3;
    public static final int AFFECTION_LOSS_PER_NEGLECT_DAY = 1;
    public static final int AFFECTION_LOSS_SATIETY_LOW_DAILY = 1;
    /** 玩家离线时衰减缩放到 10%（3 天 → 30 天）。 */
    public static final float OFFLINE_DECAY_SCALE = 0.1f;

    // ---------------------------------------------------------------- 每日上限
    /** 每个 (玩家 × 魔物娘 × 行为) 每天只给一次数值奖励。 */
    public static final long DAY_TICKS = 24000L;

    // ---------------------------------------------------------------- 交互手感
    /**
     * 通用右键交互去抖兜底窗口（tick，0.1 秒）。<br>
     * 主要防重手段在实体层：客户端 mobInteract 镜像服务端分支并返回 CONSUME
     * （26.2 原生机制：原版 mobInteract 返回 Success 型结果即停止"副手再试"），
     * 使一次点按只发一个交互包。此处窗口仅为兜底：同一次点按若仍被重复分发
     * （极少见路径），0.1s 内只放行一次，避免"一次点按连做两次"；高频点击不受影响。
     */
    public static final long INTERACT_DEBOUNCE_TICKS = 2L;

    // ---------------------------------------------------------------- 饱食/心情 tick（占位速率，待配置化）
    /** 饱食每自然下降 1 点的间隔（tick），默认 10 分钟。 */
    public static final long SATIETY_DECAY_TICKS_PER_POINT = 12000L;
    /** 饥饿伤害间隔（tick），饱食 ≤0 时每 4 秒 1 点。 */
    public static final long STARVATION_DAMAGE_EVERY_TICKS = 80L;
    /** 心情"活动不足"采样间隔（tick），1 分钟。 */
    public static final long ACTIVITY_SAMPLE_INTERVAL_TICKS = 1200L;
    /** 饱食自愈：每 N tick 消耗 1 点饱食并回复 20% 上限生命（无仇恨/非倒地/非休眠）。 */
    public static final long REGEN_CONSUME_EVERY_TICKS = 40L;
    /** 饱食自愈每次回复上限生命的比例。 */
    public static final float REGEN_HEAL_FRACTION = 0.20f;

    // ---------------------------------------------------------------- 休眠 dormant
    public static final long DORMANT_WAIT_MS = 24L * 60 * 60 * 1000;

    // ---------------------------------------------------------------- 战败 / 急救
    /** MC 世界每秒钟的 tick 数。 */
    public static final long TICKS_PER_SECOND = 20L;
    /** 倒地锁血时长（秒）：期间不受任何伤害、清除状态、扑灭火焰、浮于水面。 */
    public static final int KNOCKDOWN_IMMUNE_SECONDS = 60;
    /** 倒地锁血时长（tick）。 */
    public static final long KNOCKDOWN_LOCK_TICKS = KNOCKDOWN_IMMUNE_SECONDS * TICKS_PER_SECOND;
    /** 急救箱：回复最大生命 10% + 6 点饱食；只能对倒地且锁血结束的魔物娘使用。 */
    public static final float MEDKIT_HEAL_FRACTION = 0.10f;
    public static final int MEDKIT_SATIETY = 6;

    // ---------------------------------------------------------------- 敌意 / 近战（Phase 4 物种专属行为）
    /** 敌意目标扫描间隔（tick）。 */
    public static final long HOSTILE_SCAN_EVERY_TICKS = 10L;
    /** 近战攻击间隔（tick，约 1 秒一刀）。 */
    public static final long MELEE_COOLDOWN_TICKS = 20L;
    /** 近战攻击判定距离（方块）。 */
    public static final double MELEE_REACH_BLOCKS = 2.4;
    /** 受击反击记忆窗口（tick，5 秒）。 */
    public static final long PROVOKED_MEMORY_TICKS = 100L;
}
