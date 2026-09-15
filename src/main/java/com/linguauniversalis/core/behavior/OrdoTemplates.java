package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纲（Ordo）行为模板注册表（模块化，设计汇总 §13）。
 *
 * <p>「领地巡游纲」等纲目行为是一份<b>算法模板</b>，物种通过其档案的
 * {@link SpeciesProfile#socialOrdo()} 选中模板，并用物种级参数
 * （territory 半径 / threat 半径 / 亡灵狩猎半径…）覆盖模板默认值。
 * 新增纲 = 注册一个模板实现；新增同纲物种 = 只写档案参数，不复制逻辑。
 *
 * <p>模板键（物种参数覆盖用）：
 * <ul>
 *   <li>{@link #KEY_TERRITORY_HORIZONTAL} —— 领地水平半径（格）；</li>
 *   <li>{@link #KEY_THREAT_RADIUS} —— 威胁（主动攻击）球形半径（格）；</li>
 *   <li>{@link #KEY_UNDEAD_HUNT_RADIUS} —— 野生主动猎杀亡灵的半径（格，0=不猎）。</li>
 * </ul>
 */
public final class OrdoTemplates {
    /** 物种参数覆盖键一律走集中目录 {@link TemplateKeys}。 */
    public static final String KEY_TERRITORY_HORIZONTAL = TemplateKeys.ORDO_TERRITORY_HORIZONTAL;
    public static final String KEY_THREAT_RADIUS = TemplateKeys.ORDO_THREAT_RADIUS;
    public static final String KEY_UNDEAD_HUNT_RADIUS = TemplateKeys.ORDO_UNDEAD_HUNT_RADIUS;

    private static final Map<String, OrdoTemplate> REGISTRY = new LinkedHashMap<>();

    private OrdoTemplates() {
    }

    /** 纲行为模板。物种档案的 socialOrdo().id() 对应模板 taxonId()。 */
    public interface OrdoTemplate {
        String taxonId();

        /** 领地守卫：是否会在威胁范围内主动攻击玩家（如领地巡游纲）。 */
        default boolean guardsTerritoryAgainstPlayers() {
            return false;
        }

        /** 拟态被动：一般不主动攻击人类（玩家），只在被招惹时反击。 */
        default boolean passiveToHumanPlayers() {
            return false;
        }

        /** 野生是否主动猎杀亡灵（如猫又）。 */
        default boolean wildHuntsUndead() {
            return false;
        }

        /** 模板默认：领地水平半径。 */
        default double defaultTerritoryHorizontal() {
            return 0;
        }

        /** 模板默认：威胁半径。 */
        default double defaultThreatRadius() {
            return 0;
        }

        /** 模板默认：亡灵狩猎半径（0=该模板不提供此习性）。 */
        default double defaultUndeadHuntRadius() {
            return 0;
        }

        /**
         * 该纲下的野生魔物娘是否会在<b>找不到巢穴</b>时狂暴。
         * 模板默认 true（领地巡游纲等守巢纲：无巢即狂暴，直到认领到巢心）；
         * 只有用刷怪蛋生成的该类个体在生成时才置为 false（见实体层 finalizeSpawn），
         * 避免"刷怪蛋一出场就狂暴"。物种级可用 TemplateKeys.ORDO_RAGE_WITHOUT_NEST 覆盖。
         */
        default boolean ragesWithoutNest(SpeciesProfile profile) {
            return profile.templateFlag(
                    com.linguauniversalis.core.behavior.TemplateKeys.ORDO_RAGE_WITHOUT_NEST, true);
        }

        /**
         * 【同种族敌意·物种变量】巢穴威胁半径内是否攻击自己的同种族魔物娘。
         * 读取顺序：物种档案显式值 → 该纲模板默认习性。领地守巢纲默认 true
         * （守巢敌视一切生物，同族入侵威胁范围一视同仁 —— 阿拉克涅即此习性）；
         * 其余纲默认 false。物种级可用 TemplateKeys.ORDO_NEST_THREAT_ATTACKS_OWN_KIND 覆盖。
         */
        default boolean nestThreatAttacksOwnKind(SpeciesProfile profile) {
            return profile.templateFlag(
                    com.linguauniversalis.core.behavior.TemplateKeys.ORDO_NEST_THREAT_ATTACKS_OWN_KIND,
                    defaultNestThreatAttacksOwnKind());
        }

        /** 纲模板默认：巢穴威胁内是否攻击同种族（默认 false，领地守巢纲覆写为 true）。 */
        default boolean defaultNestThreatAttacksOwnKind() {
            return false;
        }

        /**
         * 【同种族敌意·物种变量】狂暴（enraged）时是否攻击自己的同种族魔物娘。
         * 读取顺序：物种档案显式值 → 该纲模板默认习性。领地守巢纲默认 true
         * （狂暴敌视一切，含同族 —— 阿拉克涅即此习性）；其余纲默认 false。
         * 物种级可用 TemplateKeys.ORDO_ENRAGED_ATTACKS_OWN_KIND 覆盖。
         */
        default boolean enragedAttacksOwnKind(SpeciesProfile profile) {
            return profile.templateFlag(
                    com.linguauniversalis.core.behavior.TemplateKeys.ORDO_ENRAGED_ATTACKS_OWN_KIND,
                    defaultEnragedAttacksOwnKind());
        }

        /** 纲模板默认：狂暴时是否攻击同种族（默认 false，领地守巢纲覆写为 true）。 */
        default boolean defaultEnragedAttacksOwnKind() {
            return false;
        }

        /** 由模板默认值与物种参数求得实际领地水平半径。 */
        default double effectiveTerritory(SpeciesProfile profile) {
            return profile.templateParam(KEY_TERRITORY_HORIZONTAL, defaultTerritoryHorizontal());
        }

        /** 实际威胁半径。 */
        default double effectiveThreat(SpeciesProfile profile) {
            return profile.templateParam(KEY_THREAT_RADIUS, defaultThreatRadius());
        }

        /** 实际亡灵狩猎半径（0 = 不猎）。 */
        default double effectiveUndeadHunt(SpeciesProfile profile) {
            return profile.templateParam(KEY_UNDEAD_HUNT_RADIUS, defaultUndeadHuntRadius());
        }
    }

    // ------------------------------------------------------------------ 内置模板
    /** 领地巡游纲：默认领地 24 / 威胁 12（物种参数可覆盖）。 */
    public static final OrdoTemplate TERRITORIALIS = new OrdoTemplate() {
        @Override
        public String taxonId() {
            return "territorialis";
        }

        @Override
        public boolean guardsTerritoryAgainstPlayers() {
            return true;
        }

        @Override
        public double defaultTerritoryHorizontal() {
            return 24.0;
        }

        @Override
        public double defaultThreatRadius() {
            return 12.0;
        }

        @Override
        public boolean defaultNestThreatAttacksOwnKind() {
            return true; // 领地守巢纲：威胁内敌视一切生物（同族一视同仁）
        }

        @Override
        public boolean defaultEnragedAttacksOwnKind() {
            return true; // 领地守巢纲：狂暴敌视一切（同族一视同仁）
        }
    };

    /** 拟态同化纲：对人类被动；猫又类物种经档案参数开启亡灵狩猎（默认半径 16）。 */
    public static final OrdoTemplate MIMETICUS = new OrdoTemplate() {
        @Override
        public String taxonId() {
            return "mimeticus";
        }

        @Override
        public boolean passiveToHumanPlayers() {
            return true;
        }

        @Override
        public boolean wildHuntsUndead() {
            return true;
        }

        @Override
        public double defaultUndeadHuntRadius() {
            return 16.0;
        }
    };

    /** 未知/中性纲：无默认主动攻击行为（附属模组新增纲需自行注册模板）。 */
    public static final OrdoTemplate NEUTRAL = new OrdoTemplate() {
        @Override
        public String taxonId() {
            return "";
        }
    };

    public static void register(OrdoTemplate template) {
        REGISTRY.put(template.taxonId(), template);
    }

    /** 取模板；未注册时返回中性模板（安全默认：什么都不主动做）。 */
    public static OrdoTemplate get(String taxonId) {
        if (taxonId == null) {
            return NEUTRAL;
        }
        OrdoTemplate t = REGISTRY.get(taxonId);
        return t == null ? NEUTRAL : t;
    }

    /** 按物种档案的纲选取模板（未注册纲→中性）。 */
    public static OrdoTemplate of(SpeciesProfile profile) {
        return get(profile.socialOrdo().id());
    }

    public static Collection<OrdoTemplate> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** 注册内置模板（主类与冒烟入口调用一次；幂等）。 */
    public static void registerDefaults() {
        register(TERRITORIALIS);
        register(MIMETICUS);
    }
}
