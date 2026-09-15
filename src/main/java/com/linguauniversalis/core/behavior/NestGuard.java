package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 领地巡游纲模板 —— 巢心守卫实现（阿拉克涅等物种使用，设计汇总 §11）。
 *
 * <p>几何/判定模型，供 AI 层调用。领地水平半径与威胁半径<b>不写死在本类</b>，
 * 而是取 {@link OrdoTemplates#TERRITORIALIS} 的模板默认值，并允许按物种档案参数覆盖：
 * <ul>
 *   <li>领地范围：巢心水平 {@code territoryHorizontal} 格（无限高圆柱）；被带离会尝试返回；</li>
 *   <li>威胁范围：巢心 {@code threatRadius} 格球形半径 —— 与领地范围恒定不变（各状态）；</li>
 *   <li>主动攻击玩家（威胁范围内）：野生攻击所有玩家；友善只豁免友善对象；伙伴不主动攻击任何玩家；</li>
 *   <li>红线：触碰/攻击巢心会反击；伙伴本人触碰/破坏巢心豁免，且伙伴不因巢心丢失而狂暴；</li>
 *   <li>狂暴 enraged：巢心被毁、或（在领地外且巢心区块被卸载）→ 无差别攻击，直到巢心区块重载/巢心重建。</li>
 * </ul>
 */
public final class NestGuard {
    /** 模板默认：领地水平半径（格）。 */
    public static final double DEFAULT_TERRITORY_HORIZONTAL_BLOCKS =
            OrdoTemplates.TERRITORIALIS.defaultTerritoryHorizontal();
    /** 模板默认：威胁半径（格）。 */
    public static final double DEFAULT_THREAT_RADIUS_BLOCKS =
            OrdoTemplates.TERRITORIALIS.defaultThreatRadius();

    private final long nestX;
    private final long nestY;
    private final long nestZ;
    private final double territoryHorizontal;
    private final double threatRadius;

    /** 使用模板默认值（24/12）创建（兼容旧调用/冒烟）。 */
    public NestGuard(long nestX, long nestY, long nestZ) {
        this(nestX, nestY, nestZ,
                OrdoTemplates.TERRITORIALIS.defaultTerritoryHorizontal(),
                OrdoTemplates.TERRITORIALIS.defaultThreatRadius());
    }

    public NestGuard(long nestX, long nestY, long nestZ,
                     double territoryHorizontal, double threatRadius) {
        this.nestX = nestX;
        this.nestY = nestY;
        this.nestZ = nestZ;
        this.territoryHorizontal = territoryHorizontal;
        this.threatRadius = threatRadius;
    }

    /** 按物种档案（领地巡游纲 + 参数覆盖）创建巢心守卫。 */
    public static NestGuard fromProfile(SpeciesProfile profile, long nestX, long nestY, long nestZ) {
        OrdoTemplates.OrdoTemplate t = OrdoTemplates.of(profile);
        return new NestGuard(nestX, nestY, nestZ,
                t.effectiveTerritory(profile), t.effectiveThreat(profile));
    }

    /** 目标是否处于领地范围（水平 ≤领地半径，无限高圆柱）。 */
    public boolean inTerritory(double x, double z) {
        double dx = x - nestX;
        double dz = z - nestZ;
        return dx * dx + dz * dz <= territoryHorizontal * territoryHorizontal;
    }

    /** 目标是否处于威胁范围（球形半径，含高度）。 */
    public boolean inThreatSphere(double x, double y, double z) {
        double dx = x - nestX;
        double dy = y - nestY;
        double dz = z - nestZ;
        return dx * dx + dy * dy + dz * dz <= threatRadius * threatRadius;
    }

    /**
     * 主动攻击玩家判定（在威胁范围内且无红线时）。
     *
     * @param companionStage      是否为伙伴及以上（永久，含誓约）
     * @param playerIsBoundPlayer 该玩家是否即友善对象/伙伴玩家
     */
    public boolean shouldActivelyAttackPlayer(boolean companionStage, boolean playerIsBoundPlayer) {
        return !companionStage && !playerIsBoundPlayer;
    }

    /**
     * 巢心红线：触碰/攻击巢心方块时是否攻击该玩家。
     * 伙伴本人豁免；其余玩家一律触发。
     */
    public boolean shouldAttackOnNestTouch(boolean companionStage, boolean playerIsBoundPlayer) {
        return !(companionStage && playerIsBoundPlayer);
    }

    /**
     * 狂暴状态更新（等价于 {@link #updateEnrage(boolean, boolean, boolean, boolean, boolean)}
     * 且 rageWithoutNest 恒为 true，保留旧语义）。
     */
    public static boolean updateEnrage(boolean wasEnraged, boolean companionStage, boolean nestExists,
                                       boolean nestChunkLoaded, boolean outsideTerritory) {
        return updateEnrage(wasEnraged, companionStage, true, nestExists, nestChunkLoaded, outsideTerritory);
    }

    /**
     * 狂暴状态更新（含"无巢狂暴"开关，设计汇总 §11 / OrdoTemplates.ragesWithoutNest）。
     *
     * @param wasEnraged       上一状态
     * @param companionStage   伙伴及以上（永不因巢心丢失狂暴）
     * @param rageWithoutNest  该个体是否会在"没有巢穴"时狂暴（刷怪蛋生成个体 = false）
     * @param nestExists       巢心方块是否存在
     * @param nestChunkLoaded  巢心所在区块是否加载
     * @param outsideTerritory 是否位于领地范围外
     * @return 本 tick 应处的狂暴状态
     */
    public static boolean updateEnrage(boolean wasEnraged, boolean companionStage, boolean rageWithoutNest,
                                       boolean nestExists, boolean nestChunkLoaded, boolean outsideTerritory) {
        if (companionStage) {
            return false;
        }
        if (!nestExists) {
            return rageWithoutNest; // 无巢：依开关（普通野生 true→狂暴；刷怪蛋个体 false→不狂暴）
        }
        if (outsideTerritory && !nestChunkLoaded) {
            return true;
        }
        if (nestExists && nestChunkLoaded) {
            return false;
        }
        return wasEnraged;
    }
}
