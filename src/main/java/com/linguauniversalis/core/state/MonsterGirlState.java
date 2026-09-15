package com.linguauniversalis.core.state;

import com.linguauniversalis.core.ModConstants;

/**
 * 魔物娘的养成状态数据（纯数据对象，服务端权威）。
 *
 * <p>字段与规则对应设计汇总 §1/§2/§3。序列化/实体挂载在 Phase 2/4 完成
 * （届时用 Data Component 或实体附件持久化）。
 */
public final class MonsterGirlState {
    private int affection;   // 好感度 0–200
    private int mood;        // 心情 0–100（初始 70）
    private int satiety;     // 饱食 0–20
    private int synergy;     // 默契 0–100（誓约后启用，占位）

    /** 友善对象 / 伙伴（绑定的玩家）UUID；null 表示野生。 */
    private String boundPlayerUuid;

    /** 是否伙伴已达成（首次 100 后永久为 true，用于黏性判定）。 */
    private boolean companionUnlocked;

    /** 是否誓约（好感锁定 200 且消耗誓约协议书）。 */
    private boolean vowed;

    /** 休眠标记（伙伴双 0 + 24h 无互动后进入；可由绑玩家摸头唤醒）。 */
    private boolean dormant;

    /** 倒地（战败）标记；仅非野生魔物娘可进入。 */
    private boolean downed;
    /** 倒地锁血剩余 tick；>0 期间不受任何伤害、清除状态、扑灭火焰、浮于水面。 */
    private long downedLockTicks;

    public MonsterGirlState() {
        this.affection = 0;
        this.mood = ModConstants.MOOD_DEFAULT;
        this.satiety = ModConstants.SATIETY_MAX;
        this.synergy = 0;
    }

    // ------------------------------------------------------------- 读写与钳制
    public int affection() {
        return affection;
    }

    public void setAffection(int value) {
        // 誓约后好感锁定 200，不再随互动/衰减变化。
        this.affection = vowed ? ModConstants.AFFECTION_MAX : clamp(value, 0, ModConstants.AFFECTION_MAX);
    }

    public void addAffection(int delta) {
        setAffection(affection + delta);
    }

    public int mood() {
        return mood;
    }

    public void setMood(int value) {
        this.mood = clamp(value, 0, ModConstants.MOOD_MAX);
    }

    public void addMood(int delta) {
        setMood(mood + delta);
    }

    public int satiety() {
        return satiety;
    }

    public void setSatiety(int value) {
        this.satiety = clamp(value, 0, ModConstants.SATIETY_MAX);
    }

    public void addSatiety(int delta) {
        setSatiety(satiety + delta);
    }

    public int synergy() {
        return synergy;
    }

    public void setSynergy(int value) {
        this.synergy = clamp(value, 0, ModConstants.SYNERGY_MAX);
    }

    public String boundPlayerUuid() {
        return boundPlayerUuid;
    }

    public void setBoundPlayerUuid(String uuid) {
        this.boundPlayerUuid = uuid;
    }

    public boolean companionUnlocked() {
        return companionUnlocked;
    }

    public void setCompanionUnlocked(boolean unlocked) {
        this.companionUnlocked = unlocked;
    }

    public boolean vowed() {
        return vowed;
    }

    public void setVowed(boolean vowed) {
        this.vowed = vowed;
        if (vowed) {
            this.affection = ModConstants.AFFECTION_MAX; // 锁 200
        }
    }

    public boolean dormant() {
        return dormant;
    }

    public void setDormant(boolean dormant) {
        this.dormant = dormant;
    }

    // ------------------------------------------------------------- 倒地（战败）
    public boolean downed() {
        return downed;
    }

    public void setDowned(boolean downed) {
        this.downed = downed;
        if (!downed) {
            this.downedLockTicks = 0;
        }
    }

    public long downedLockTicks() {
        return downedLockTicks;
    }

    public void setDownedLockTicks(long ticks) {
        this.downedLockTicks = Math.max(0, ticks);
    }

    /** 锁血保护中（不可受伤、不可治疗）。 */
    public boolean isDownedImmune() {
        return downed && downedLockTicks > 0;
    }

    /** 倒地且锁血已结束：急救箱此时才可用；再次被攻击会直接击杀。 */
    public boolean isDownedVulnerable() {
        return downed && downedLockTicks == 0;
    }

    // ------------------------------------------------------------- 查询
    /** 是否处于心情低落（<20）。 */
    public boolean isDepressed() {
        return mood < ModConstants.MOOD_DEPRESSED_BELOW;
    }

    /** 饱食过低（触发好感/心情 -1 的阈值判定）。 */
    public boolean isSatietyLow() {
        return satiety <= ModConstants.SATIETY_LOW;
    }

    /** 是否已达成伙伴（黏性：一旦解锁永久为真，不再回落）。 */
    public boolean isCompanion() {
        return companionUnlocked || affection >= ModConstants.COMPANION_MIN;
    }

    /** 当前档位 id（wild/friendly/companion/vowed），用于同步与展示。 */
    public String stageId() {
        if (vowed) {
            return "vowed";
        }
        if (isCompanion()) {
            return "companion";
        }
        if (boundPlayerUuid != null && affection >= ModConstants.FRIENDLY_MIN) {
            return "friendly";
        }
        return "wild";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
