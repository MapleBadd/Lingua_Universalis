package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * 物种专属战斗能力参数与状态机（纯规则，无 MC 依赖；设计汇总 §11/§12）。
 *
 * <p>能力由<b>物种档案参数</b>驱动（键集中见 {@link TemplateKeys}）：参数为 0 表示该物种没有该能力，
 * 因此新增物种只需写档案参数即可获得能力，无需改代码（模块化）。
 *
 * <ul>
 *   <li>阿拉克涅：蛛网（减速+挖掘疲劳）、蛛丝拉拽（伤害+拉向自己）、近战毒牙（中毒）；</li>
 *   <li>猫又：近战流血（等级叠加、命中刷新）、暗影箭（对远处敌人魔法伤害）、弹射物闪避。</li>
 * </ul>
 *
 * <p>本类只做数值与计时判定；状态效果/位移/伤害施加由实体层执行。
 */
public final class SpeciesAbilities {
    private SpeciesAbilities() {
    }

    // ------------------------------------------------------------------ 参数读取（0 = 无该能力）
    /** 蛛网冷却 tick。 */
    public static long webCooldownTicks(SpeciesProfile p) {
        return Math.max(0L, (long) p.templateParam(TemplateKeys.ABILITY_WEB_COOLDOWN_TICKS, 0));
    }

    /** 蛛网效果持续 tick（默认 100 = 5 秒）。 */
    public static int webEffectTicks(SpeciesProfile p) {
        return Math.max(0, (int) p.templateParam(TemplateKeys.ABILITY_WEB_EFFECT_TICKS, 100));
    }

    /** 蛛网效果等级（放大器，默认 1 = 减速 II）。 */
    public static int webAmplifier(SpeciesProfile p) {
        return Math.max(0, (int) p.templateParam(TemplateKeys.ABILITY_WEB_AMPLIFIER, 1));
    }

    /** 蛛网施放距离（格，默认 8）。 */
    public static double webRange(SpeciesProfile p) {
        return Math.max(0.0, p.templateParam(TemplateKeys.ABILITY_WEB_RANGE, 8.0));
    }

    /** 蛛丝拉拽冷却 tick。 */
    public static long silkCooldownTicks(SpeciesProfile p) {
        return Math.max(0L, (long) p.templateParam(TemplateKeys.ABILITY_SILK_COOLDOWN_TICKS, 0));
    }

    /** 蛛丝拉拽伤害（默认 10）。 */
    public static float silkDamage(SpeciesProfile p) {
        return (float) Math.max(0.0, p.templateParam(TemplateKeys.ABILITY_SILK_DAMAGE, 10.0));
    }

    /** 蛛丝拉拽施放距离（格，默认 12）。 */
    public static double silkRange(SpeciesProfile p) {
        return Math.max(0.0, p.templateParam(TemplateKeys.ABILITY_SILK_RANGE, 12.0));
    }

    /** 近战中毒持续 tick（0 = 无毒）。 */
    public static int poisonTicks(SpeciesProfile p) {
        return Math.max(0, (int) p.templateParam(TemplateKeys.ABILITY_POISON_TICKS, 0));
    }

    /** 近战中毒等级（放大器，默认 1 = 中毒 II）。 */
    public static int poisonAmplifier(SpeciesProfile p) {
        return Math.max(0, (int) p.templateParam(TemplateKeys.ABILITY_POISON_AMPLIFIER, 1));
    }

    /** 暗影箭冷却 tick。 */
    public static long shadowBoltCooldownTicks(SpeciesProfile p) {
        return Math.max(0L, (long) p.templateParam(TemplateKeys.ABILITY_SHADOW_BOLT_COOLDOWN_TICKS, 0));
    }

    /** 暗影箭伤害（未设参数时回落档案的战斗面板远程伤害）。 */
    public static float shadowBoltDamage(SpeciesProfile p) {
        return (float) Math.max(0.0, p.templateParam(
                TemplateKeys.ABILITY_SHADOW_BOLT_DAMAGE, p.rangedDamage()));
    }

    /** 暗影箭最小距离（格，默认 8；更近则用近战）。 */
    public static double shadowBoltMinRange(SpeciesProfile p) {
        return Math.max(0.0, p.templateParam(TemplateKeys.ABILITY_SHADOW_BOLT_MIN_RANGE, 8.0));
    }

    /** 流血命中刷新时长 tick（0 = 无流血能力）。 */
    public static long bleedRefreshTicks(SpeciesProfile p) {
        return Math.max(0L, (long) p.templateParam(TemplateKeys.ABILITY_BLEED_REFRESH_TICKS, 0));
    }

    /** 流血结算间隔 tick（默认 40 = 2 秒）。 */
    public static long bleedTickIntervalTicks(SpeciesProfile p) {
        return Math.max(1L, (long) p.templateParam(TemplateKeys.ABILITY_BLEED_TICK_INTERVAL_TICKS, 40));
    }

    /** 弹射物闪避冷却 tick（0 = 无闪避能力）。 */
    public static long dodgeCooldownTicks(SpeciesProfile p) {
        return Math.max(0L, (long) p.templateParam(TemplateKeys.ABILITY_DODGE_COOLDOWN_TICKS, 0));
    }

    /** 弹射物闪避免疫窗口 tick（默认 10）。 */
    public static long dodgeInvulnTicks(SpeciesProfile p) {
        return Math.max(1L, (long) p.templateParam(TemplateKeys.ABILITY_DODGE_INVULN_TICKS, 10));
    }

    // ------------------------------------------------------------------ 弹道（蛛网/蛛丝/暗影箭）
    /**
     * 弹道种类。三种能力都以<b>发射弹道</b>的形式实现（视觉先复用原版箭）。
     *
     * <p>重要口径（设计约定）：这里的"弹道"与原版"弹射物"概念不等价 ——
     * <b>魔法类弹道（暗影箭）不按弹射物结算伤害</b>，因此不受弹射物保护附魔影响；
     * 蛛丝拉拽属于物理弹道，按弹射物结算；蛛网不造成伤害。
     */
    public enum BoltKind {
        /** 蛛网：命中施加 减速 + 挖掘疲劳（无伤害）。 */
        WEB("web", false),
        /** 蛛丝拉拽：命中造成物理伤害并把目标拉向发射者（按弹射物结算）。 */
        SILK("silk", true),
        /** 暗影箭：命中造成魔法伤害（<b>不按弹射物结算</b>，不吃弹射物保护）。 */
        SHADOW("shadow", false);

        private final String id;
        private final boolean projectileDamage;

        BoltKind(String id, boolean projectileDamage) {
            this.id = id;
            this.projectileDamage = projectileDamage;
        }

        /** 存档/网络用的稳定 id。 */
        public String id() {
            return id;
        }

        /** 该弹道的伤害是否按"弹射物"结算（影响弹射物保护附魔等）。 */
        public boolean dealsProjectileDamage() {
            return projectileDamage;
        }

        /** 由 id 反查（未知返回 null）。 */
        public static BoltKind byId(String id) {
            for (BoltKind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return null;
        }
    }

    /** 弹道伤害：蛛网 0；蛛丝取蛛丝伤害；暗影箭取暗影箭伤害（未设参数回落面板远程伤害）。 */
    public static float boltDamage(SpeciesProfile p, BoltKind kind) {
        return switch (kind) {
            case WEB -> 0f;
            case SILK -> silkDamage(p);
            case SHADOW -> shadowBoltDamage(p);
        };
    }

    /** 弹道飞行速度（格/tick 量级，参数化前的常量）。 */
    public static double boltSpeed(BoltKind kind) {
        return switch (kind) {
            case WEB -> 0.8;
            case SILK -> 1.0;
            case SHADOW -> 1.2;
        };
    }

    /** 弹道最长存活 tick（超过即消失，避免残留）。 */
    public static int boltMaxLifeTicks(BoltKind kind) {
        return switch (kind) {
            case WEB -> 60;
            case SILK -> 60;
            case SHADOW -> 80;
        };
    }

    // ------------------------------------------------------------------ 冷却计时
    /** 单个能力的冷却计时（按能力 id 记录上次使用 tick）。 */
    public static final class Cooldowns {
        private final Map<String, Long> lastUsed = new HashMap<>();

        /** 能力是否就绪（冷却已过）。 */
        public boolean ready(String abilityId, long nowTick, long cooldownTicks) {
            if (cooldownTicks <= 0) {
                return false; // 参数 0 = 该物种没有这个能力
            }
            Long last = lastUsed.get(abilityId);
            return last == null || nowTick - last >= cooldownTicks;
        }

        /** 标记该能力刚刚使用（随后进入冷却）。 */
        public void use(String abilityId, long nowTick) {
            lastUsed.put(abilityId, nowTick);
        }

        /** 上次使用 tick；从未使用返回 -1。 */
        public long lastUsedTick(String abilityId) {
            Long last = lastUsed.get(abilityId);
            return last == null ? -1L : last;
        }

        /** 剩余冷却 tick（用于调试/HUD；无冷却或不存在的返回 0）。 */
        public long remaining(String abilityId, long nowTick, long cooldownTicks) {
            Long last = lastUsed.get(abilityId);
            if (last == null) {
                return 0L;
            }
            return Math.max(0L, cooldownTicks - (nowTick - last));
        }
    }

    // ------------------------------------------------------------------ 流血（猫又绒科）
    /**
     * 流血状态（针对单个受害者）：命中刷新时长并<b>层级 +1（无上限）</b>；
     * 每 {@code tickIntervalTicks} 结算一次，伤害 = 层级（物理伤害，由实体层施加）。
     */
    public static final class Bleed {
        private int level;
        private long expireTick = -1L;
        private long nextDamageTick = -1L;

        /** 命中时叠加：层级 +1，刷新持续时间；返回叠加后的层级。 */
        public int onHit(long nowTick, long refreshTicks) {
            if (refreshTicks <= 0) {
                return 0;
            }
            level = level + 1;
            expireTick = nowTick + refreshTicks;
            if (nextDamageTick < 0 || nextDamageTick > nowTick) {
                nextDamageTick = nowTick + 1; // 首次命中后很快开始结算
            }
            return level;
        }

        /** 是否已过期（应清除状态）。 */
        public boolean expired(long nowTick) {
            return expireTick < 0 || nowTick > expireTick;
        }

        public int level() {
            return level;
        }

        public long expireTick() {
            return expireTick;
        }

        /**
         * 到期结算：若到达结算点返回本次应造成的伤害（=层级），否则返回 0。
         *
         * @param intervalTicks 结算间隔 tick
         */
        public int pollDamage(long nowTick, long intervalTicks) {
            if (expired(nowTick) || nextDamageTick < 0 || nowTick < nextDamageTick) {
                return 0;
            }
            nextDamageTick = nowTick + Math.max(1L, intervalTicks);
            return level;
        }

        /** 清除（目标死亡/切换目标）。 */
        public void clear() {
            level = 0;
            expireTick = -1L;
            nextDamageTick = -1L;
        }
    }
}
