package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.state.MonsterGirlState;

/**
 * 敌意 / 目标选择纯规则（设计汇总 §11/§12/§13，MC 无关）。
 *
 * <p>目标选择由<b>物种档案 → 纲模板（{@link OrdoTemplates}）</b>驱动。两个距离输入：
 * <ul>
 *   <li>{@code distToNestSq} —— 目标到<b>巢穴方块</b>的距离平方。领地巡游纲（阿拉克涅等）的
 *       威胁范围以巢心为圆心，<b>不是</b>以魔物娘本体为圆心；无巢（= -1）时不具备守巢攻击；</li>
 *   <li>{@code selfDistSq} —— 目标到魔物娘本体的距离平方（近战/被惹反击/拟态猎亡灵用本体）。</li>
 * </ul>
 *
 * <ul>
 *   <li>阿拉克涅（领地巡游纲）：野生/友善在巢心威胁范围内敌视<b>一切生物</b>（玩家/亡灵/普通生物
 *       一视同仁，仅豁免绑玩家）；数值范围来自模板默认 + 档案参数覆盖；</li>
 *   <li>猫又（拟态同化纲）：一般不主动攻击玩家；野生在亡灵狩猎半径内主动猎亡灵；被招惹会反击；</li>
 *   <li>心情低落：只攻击近战范围内的非玩家生物，不追远敌；</li>
 *   <li>伙伴：永不主动攻击玩家；被非绑定者招惹自卫反击；</li>
 *   <li>狂暴（enraged）：攻击视野内一切（领地物种无巢/巢毁时进入，见 NestGuard）。</li>
 *   <li>同种族敌意是<b>物种变量</b>（OrdoTemplates：巢穴威胁内/狂暴时是否攻击同种族魔物娘），
 *       由 AI 层按目标 speciesId 过滤，规则层只给"同种族与否"之外的统一敌意判定。</li>
 * </ul>
 *
 * <p>新增纲物种无需改本类——注册/选用纲模板并设置档案参数即可（模块化）。
 */
public final class HostilityRules {
    /** 低落模板近战判定的最大距离。 */
    public static final double LOW_MOOD_MELEE_RADIUS = 3.0;

    private HostilityRules() {
    }

    /** 候选目标类别（由调用方把 MC 实体归入其一）。 */
    public enum TargetKind {
        PLAYER,
        UNDEAD,
        OTHER_MOB
    }

    /** 无巢心标记（distToNestSq 传入该值时表示该个体没有巢）。 */
    public static final double NO_NEST = -1.0;

    /**
     * 兼容 speciesId 调用（含巢心距离）的便捷入口。
     */
    public static boolean shouldTarget(String speciesId, MonsterGirlState st,
                                       TargetKind kind, double distToNestSq, double selfDistSq,
                                       boolean inMelee, boolean targetIsBoundOwner,
                                       boolean provokedByTarget, boolean enraged) {
        return shouldTarget(com.linguauniversalis.core.species.SpeciesRegistry.get(speciesId),
                st, kind, distToNestSq, selfDistSq, inMelee, targetIsBoundOwner,
                provokedByTarget, enraged);
    }

    /**
     * 兼容旧 8 参 speciesId 调用（把巢心距离视为本体距离，供冒烟/旧调用）。
     */
    public static boolean shouldTarget(String speciesId, MonsterGirlState st,
                                       TargetKind kind, double distSq, boolean inMelee,
                                       boolean targetIsBoundOwner, boolean provokedByTarget,
                                       boolean enraged) {
        return shouldTarget(com.linguauniversalis.core.species.SpeciesRegistry.get(speciesId),
                st, kind, distSq, distSq, inMelee, targetIsBoundOwner, provokedByTarget, enraged);
    }

    /**
     * 兼容旧签名：把巢心距离视为本体距离（相当于"无巢但威胁跟随本体"的近似，供冒烟/旧调用）。
     */
    public static boolean shouldTarget(SpeciesProfile profile, MonsterGirlState st,
                                       TargetKind kind, double distSq, boolean inMelee,
                                       boolean targetIsBoundOwner, boolean provokedByTarget,
                                       boolean enraged) {
        return shouldTarget(profile, st, kind, distSq, distSq, inMelee,
                targetIsBoundOwner, provokedByTarget, enraged);
    }

    /**
     * 判定是否把该目标视为可攻击对象。
     *
     * @param profile            物种档案
     * @param st                 个体状态（档位/心情/倒地/休眠等）
     * @param kind               目标类别
     * @param distToNestSq       目标到巢穴方块的距离平方（{@link #NO_NEST} = 无巢）
     * @param selfDistSq         目标到本体的距离平方
     * @param inMelee            是否已在近战距离内
     * @param targetIsBoundOwner 目标是否为她的绑玩家（友善/伙伴对象）
     * @param provokedByTarget   目标是否刚攻击过她（受击反击窗口内）
     * @param enraged            狂暴状态（领地物种无巢/巢毁；伙伴恒 false）
     * @return true = 应当追击/攻击该目标
     */
    public static boolean shouldTarget(SpeciesProfile profile, MonsterGirlState st,
                                       TargetKind kind, double distToNestSq, double selfDistSq,
                                       boolean inMelee, boolean targetIsBoundOwner,
                                       boolean provokedByTarget, boolean enraged) {
        if (st.downed() || st.dormant()) {
            return false; // 倒地/休眠不战斗
        }

        // 低落·随机攻击模板：只打近战范围内的非玩家生物；不追远敌
        if (st.isDepressed()) {
            return kind != TargetKind.PLAYER && inMelee;
        }

        boolean companion = st.isCompanion();
        // 伙伴：永不主动攻击玩家；只对非绑定者招惹自卫
        if (companion) {
            return provokedByTarget && !targetIsBoundOwner;
        }

        // 狂暴（仅非伙伴可达）：攻击视野内一切（AI 层扫描半径兜底距离）；
        // 但即便狂暴也豁免绑玩家（友善对象/伙伴不被自己人打）
        if (enraged) {
            return !targetIsBoundOwner;
        }

        // 被招惹：反击（友善/伙伴的绑玩家除外）
        if (provokedByTarget && !targetIsBoundOwner) {
            return true;
        }

        // 主动行为：交给档案所属纲模板（+ 档案参数）
        OrdoTemplates.OrdoTemplate ordo = OrdoTemplates.of(profile);

        // 领地巡游纲（守卫巢穴）：威胁范围以巢心为圆心，敌视范围内<b>一切生物</b>
        // （玩家/亡灵/普通生物一视同仁），仅豁免绑玩家；无巢（NO_NEST）则不守巢。
        if (ordo.guardsTerritoryAgainstPlayers()) {
            if (targetIsBoundOwner) {
                return false;
            }
            if (distToNestSq < 0 || distToNestSq == NO_NEST) {
                return false;
            }
            double threat = ordo.effectiveThreat(profile);
            return distToNestSq <= threat * threat;
        }

        switch (kind) {
            case PLAYER -> {
                return false; // 非守卫纲对玩家的主动行为在此模板内无默认值
            }
            case UNDEAD -> {
                if (ordo.wildHuntsUndead()) {
                    double hunt = ordo.effectiveUndeadHunt(profile);
                    return hunt > 0 && selfDistSq <= hunt * hunt;
                }
                return false;
            }
            case OTHER_MOB -> {
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 敌方扫描建议半径（格）：取纲模板的有效威胁/亡灵狩猎半径，至少覆盖近战范围。
     * 供 AI 层粗筛候选实体。
     */
    public static double maxScanRadius(SpeciesProfile profile) {
        OrdoTemplates.OrdoTemplate ordo = OrdoTemplates.of(profile);
        double r = Math.max(ordo.effectiveThreat(profile), ordo.effectiveUndeadHunt(profile));
        if (r <= 0) {
            r = LOW_MOOD_MELEE_RADIUS; // 无主动狩猎的纲：仅近战自卫半径
        }
        return r;
    }
}
