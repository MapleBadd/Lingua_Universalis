package com.linguauniversalis.core.behavior;

/**
 * 物种档案可覆盖模板参数的<b>集中键目录</b>（设计汇总 §13）。
 *
 * <p>所有"物种参数覆盖模板默认值"的键集中在此，便于附属模组发现可覆盖项，
 * 也避免物种/模板各自写裸字符串造成漂移。
 *
 * <p>读取入口：{@link com.linguauniversalis.core.species.SpeciesProfile#templateParam(String, double)}
 * 与 {@link com.linguauniversalis.core.species.SpeciesProfile#templateFlag(String, boolean)}；
 * 各模板提供默认值（如 {@link OrdoTemplates#TERRITORIALIS}）。
 */
public final class TemplateKeys {
    private TemplateKeys() {
    }

    // ------------------------------------------------------------------ 纲·领地巡游 territorialis
    /** 领地水平半径（格）。 */
    public static final String ORDO_TERRITORY_HORIZONTAL = "ordo.territoryHorizontal";
    /** 威胁（主动攻击玩家）球形半径（格）。 */
    public static final String ORDO_THREAT_RADIUS = "ordo.threatRadius";

    // ------------------------------------------------------------------ 纲·拟态同化 mimeticus
    /** 野生主动猎杀亡灵半径（格；0 = 不猎）。 */
    public static final String ORDO_UNDEAD_HUNT_RADIUS = "ordo.undeadHuntRadius";

    // ------------------------------------------------------------------ 纲·领地巡游 territorialis
    /** 野生找不到巢穴时是否狂暴（默认 true；刷怪蛋生成个体在实体层单独置 false）。 */
    public static final String ORDO_RAGE_WITHOUT_NEST = "ordo.rageWithoutNest";
    /**
     * 【同种族敌意·物种变量】巢穴威胁半径内是否攻击自己的同种族魔物娘
     * （按物种习性：阿拉克涅 = true —— 守巢敌视一切生物，同族入侵威胁范围一视同仁）。
     */
    public static final String ORDO_NEST_THREAT_ATTACKS_OWN_KIND = "ordo.nestThreatAttacksOwnKind";
    /**
     * 【同种族敌意·物种变量】狂暴（enraged）时是否攻击自己的同种族魔物娘
     * （按物种习性：阿拉克涅 = true —— 狂暴敌视一切，含同族）。
     */
    public static final String ORDO_ENRAGED_ATTACKS_OWN_KIND = "ordo.enragedAttacksOwnKind";

    // ------------------------------------------------------------------ 物种能力（设计 §11/§12）
    /** 蛛网：冷却 tick（0=该物种无此能力）。 */
    public static final String ABILITY_WEB_COOLDOWN_TICKS = "ability.web.cooldownTicks";
    /** 蛛网效果持续 tick（默认 100 = 5 秒）。 */
    public static final String ABILITY_WEB_EFFECT_TICKS = "ability.web.effectTicks";
    /** 蛛网效果等级（放大器；1 = 减速 II / 挖掘疲劳 II）。 */
    public static final String ABILITY_WEB_AMPLIFIER = "ability.web.amplifier";
    /** 蛛网施放距离（格）。 */
    public static final String ABILITY_WEB_RANGE = "ability.web.range";

    /** 蛛丝拉拽：冷却 tick（0=无此能力）。 */
    public static final String ABILITY_SILK_COOLDOWN_TICKS = "ability.silk.cooldownTicks";
    /** 蛛丝拉拽伤害。 */
    public static final String ABILITY_SILK_DAMAGE = "ability.silk.damage";
    /** 蛛丝拉拽施放距离（格）。 */
    public static final String ABILITY_SILK_RANGE = "ability.silk.range";

    /** 近战毒牙：中毒持续 tick（0=近战不带毒）。 */
    public static final String ABILITY_POISON_TICKS = "ability.poison.ticks";
    /** 近战中毒等级（放大器；1 = 中毒 II）。 */
    public static final String ABILITY_POISON_AMPLIFIER = "ability.poison.amplifier";

    /** 暗影箭：冷却 tick（0=无此能力）。 */
    public static final String ABILITY_SHADOW_BOLT_COOLDOWN_TICKS = "ability.shadowBolt.cooldownTicks";
    /** 暗影箭伤害。 */
    public static final String ABILITY_SHADOW_BOLT_DAMAGE = "ability.shadowBolt.damage";
    /** 暗影箭最小距离（格；更近则改用近战）。 */
    public static final String ABILITY_SHADOW_BOLT_MIN_RANGE = "ability.shadowBolt.minRange";

    /** 流血（绒科）：命中刷新时长 tick。 */
    public static final String ABILITY_BLEED_REFRESH_TICKS = "ability.bleed.refreshTicks";
    /** 流血结算间隔 tick（每间隔造成一次等于层级的物理伤害）。 */
    public static final String ABILITY_BLEED_TICK_INTERVAL_TICKS = "ability.bleed.tickIntervalTicks";

    /** 弹射物闪避：冷却 tick（0=无此能力）。 */
    public static final String ABILITY_DODGE_COOLDOWN_TICKS = "ability.dodge.cooldownTicks";
    /** 弹射物闪避：免疫窗口 tick。 */
    public static final String ABILITY_DODGE_INVULN_TICKS = "ability.dodge.invulnTicks";

    // ------------------------------------------------------------------ 物种·形态（伪装）
    /** 是否有伪装形态（物种变量；如猫又=双尾黑猫伪装）。 */
    public static final String FLAG_SPECIES_DISGUISE = "species.disguise";
    /** 人形状态"无事"多久后恢复伪装形态（tick；默认 1200 = 60 秒）。 */
    public static final String SPECIES_DISGUISE_REVERT_TICKS = "species.disguiseRevertTicks";

    // ------------------------------------------------------------------ 物种·社交习性（设计 §12 猫又）
    /** 是否会偷鱼（物种变量；仅游荡状态触发）。 */
    public static final String FLAG_SPECIES_STEALS_FISH = "species.stealsFish";
    /** 偷鱼间隔（tick；默认 6000 = 5 分钟）。 */
    public static final String SPECIES_FISH_STEAL_INTERVAL_TICKS = "species.fishStealIntervalTicks";
    /** 是否会偷村民绿宝石（物种变量；每天 50%、1–3 颗）。 */
    public static final String FLAG_SPECIES_STEALS_EMERALDS = "species.stealsEmeralds";
    /** 目·亡灵视野：视野内出现亡灵每天一次 +1 心情（幽暗目默认 true）。 */
    public static final String FLAG_FAMILIA_UNDEAD_SIGHT_MOOD = "familia.undeadSightMood";

    // ------------------------------------------------------------------ 物种·体型（碰撞箱）
    /** 碰撞箱宽度（格；默认 0.6）。 */
    public static final String SPECIES_BODY_WIDTH = "species.bodyWidth";
    /** 碰撞箱高度（格；默认 1.8）。 */
    public static final String SPECIES_BODY_HEIGHT = "species.bodyHeight";
    /**
     * 视线高度（格；默认 1.62）。猫又按模型 {@code ViewLocator} 骨骼枢轴的 Y 值折算：
     * {@code pivotY / 16}（模型 1 单位 = 1/16 格）。
     */
    public static final String SPECIES_EYE_HEIGHT = "species.eyeHeight";

    /**
     * 移动速度倍率（物种变量；默认 1.0）。用于在通用基础速度上做物种级加减速，
     * 例如猫又爬行需要比基础快 50% → 1.5。
     */
    public static final String SPECIES_MOVE_SPEED_SCALE = "species.moveSpeedScale";
    /** 慢速档（walk）倍率；**所有物种默认 1.0**。 */
    public static final String SPECIES_WALK_SPEED_SCALE = "species.walkSpeedScale";
    /** 最快速度档倍率（追人/打敌人时用）；按物种不同，猫又 = 1.3。 */
    public static final String SPECIES_FAST_SPEED_SCALE = "species.fastSpeedScale";

    // ------------------------------------------------------------------ 物种·自然生成（设计 §12）
    /**
     * 【自然生成·物种变量】原版自然刷出猫时，转换为该物种野生个体的概率
     * （0 = 不转换，未声明即 0；猫又 = 0.5）。
     */
    public static final String SPECIES_VILLAGE_CAT_CONVERSION = "species.villageCatConversionChance";

    // ------------------------------------------------------------------ 界 kingdom（模板默认特性）
    /** 跳跃倍率（脊索界默认玩家 2 倍）。 */
    public static final String KINGDOM_JUMP_SCALE = "kingdom.jumpScale";
    /** 是否自动踏上 1 格高方块（脊索界）。 */
    public static final String FLAG_KINGDOM_AUTO_STEP_UP = "kingdom.autoStepUp";
    /** 是否免疫暴击（流形界）。 */
    public static final String FLAG_KINGDOM_CRIT_IMMUNE = "kingdom.critImmune";
    /** 是否免疫摔落（流形界）。 */
    public static final String FLAG_KINGDOM_FALL_IMMUNE = "kingdom.fallImmune";
    /** 是否不能穿护甲（流形界）。 */
    public static final String FLAG_KINGDOM_NO_ARMOR = "kingdom.noArmor";

    // ------------------------------------------------------------------ 门 classis（模板默认特性）
    /** 是否带"巢穴附近增益"（外生息门：回复/力量/速度/抗性）。 */
    public static final String FLAG_CLASSIS_NEST_BUFF = "classis.nestBuff";

    // ------------------------------------------------------------------ 科 sectio（战斗模板默认特性）
    /** 近战是否带流血（绒科）。 */
    public static final String FLAG_SECTIO_BLEED = "sectio.bleed";
    /** 是否周期性闪避弹射物并侧/后位移（绒科）。 */
    public static final String FLAG_SECTIO_PROJECTILE_DODGE = "sectio.projectileDodge";
    /** 近战是否高击退/击飞（鳞科）。 */
    public static final String FLAG_SECTIO_KNOCKBACK = "sectio.knockback";
    /** 近战是否为冲锋效果（羽科）。 */
    public static final String FLAG_SECTIO_CHARGE = "sectio.charge";
    /** 是否空中缓慢下落（羽科）。 */
    public static final String FLAG_SECTIO_SLOW_FALL = "sectio.slowFall";
    /** 攻击是否带中毒/凋零等状态（胶质科）。 */
    public static final String FLAG_SECTIO_STATUS_HIT = "sectio.statusHit";
    /** 是否拥有额外附肢槽（触肢科；实际槽位由档案 hotbar 描述）。 */
    public static final String FLAG_SECTIO_EXTRA_LIMBS = "sectio.extraLimbs";
    /** 是否有范围滞留型效果（菌丝科）。 */
    public static final String FLAG_SECTIO_AREA_EFFECT = "sectio.areaEffect";
}
