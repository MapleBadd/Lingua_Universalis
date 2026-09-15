package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 自然生成规则（纯规则，无 MC 依赖；设计汇总 §12「生成」）。
 *
 * <p>本层只管"判据"，不管世界交互；MC 侧（{@code event/GirlNaturalSpawnHandler}）负责
 * 结构查询、实体创建与入世界。
 *
 * <ul>
 *   <li><b>村庄刷猫 → 猫又</b>：原版在「会刷猫的结构」里自然刷出猫时，按物种变量概率
 *       （猫又 = 50%）把这只猫换成该物种的野生个体；未声明该变量的物种 = 不转换（0）；</li>
 *   <li><b>获得过一次好感后不再被自然刷新</b>：野生个体在"从未获得好感且未绑定"时才允许
 *       自然消失；否则转为持久（{@code setPersistenceRequired}），防止养成中断。</li>
 * </ul>
 */
public final class SpawnRules {
    private SpawnRules() {
    }

    /**
     * 原版"会刷猫的结构"结构标签（村庄 + 沼泽小屋）。
     *
     * <p>口径说明：设计写的是"每次刷新猫时 50% 生成猫又"，而原版猫的自然生成位置正是由该标签
     * 决定（村庄与沼泽小屋），因此这里直接沿用该标签，保证"猫真的会刷出来的地方"才会转换。
     */
    public static final String CAT_SPAWN_STRUCTURE_TAG = "minecraft:cats_spawn_in";

    /** 猫又默认转换概率：每次自然刷猫时 50% 生成猫又。 */
    public static final double DEFAULT_VILLAGE_CAT_CONVERSION = 0.5;

    /** 未声明该变量的物种 = 不转换。 */
    public static final double NO_CONVERSION = 0.0;

    // ------------------------------------------------------------------ 物种变量
    /** 自然刷猫时转换为该物种的概率（0 = 该物种不做"猫转换"）。 */
    public static double villageCatConversionChance(SpeciesProfile profile) {
        double raw = profile.templateParam(
                TemplateKeys.SPECIES_VILLAGE_CAT_CONVERSION, NO_CONVERSION);
        if (Double.isNaN(raw)) {
            return NO_CONVERSION;
        }
        return Math.max(0.0, Math.min(1.0, raw));
    }

    /** 该物种是否参与"刷猫转换"。 */
    public static boolean convertsFromCats(SpeciesProfile profile) {
        return villageCatConversionChance(profile) > 0.0;
    }

    // ------------------------------------------------------------------ 转换判定
    /**
     * 该生成原因是否属于"世界自然生成"。
     *
     * <p>MC 侧把 {@code EntitySpawnReason} 折算成这两个布尔后传入（纯层不依赖 MC 枚举）：
     * 自然刷新 {@code NATURAL} 与区块生成 {@code CHUNK_GENERATION} 算，刷怪蛋/刷怪笼/繁殖/
     * 指令/结构模板等一律不算。
     */
    public static boolean isNaturalWorldSpawnFlag(boolean naturalReason, boolean chunkGenerationReason) {
        return naturalReason || chunkGenerationReason;
    }

    /**
     * 这次自然刷出的猫是否应被换成该物种。
     *
     * @param naturalSpawn        是否自然生成（刷怪蛋/刷怪笼/繁殖/指令不算）
     * @param inCatSpawnStructure 是否位于 {@link #CAT_SPAWN_STRUCTURE_TAG} 结构内（村庄/沼泽小屋）
     * @param roll                传 [0,1) 随机数，便于测试
     */
    public static boolean convertsFromCat(SpeciesProfile profile, boolean naturalSpawn,
                                          boolean inCatSpawnStructure, double roll) {
        if (!naturalSpawn || !inCatSpawnStructure) {
            return false;
        }
        double chance = villageCatConversionChance(profile);
        if (chance <= 0.0) {
            return false;
        }
        return roll < chance;
    }

    // ------------------------------------------------------------------ 自然消失 / 持久
    /**
     * 野生个体现在是否允许被自然刷新掉（自然消失）。
     *
     * <p>获得过一次好感后即不再允许（"防养成中断"），已绑定的伙伴同样不允许。
     */
    public static boolean despawnsNaturally(boolean everAffectioned, boolean bound) {
        return !everAffectioned && !bound;
    }

    /** 是否应转为持久个体（不会被自然刷新掉）。 */
    public static boolean shouldBecomePersistent(boolean everAffectioned, boolean bound,
                                                 boolean alreadyPersistent) {
        return !alreadyPersistent && (everAffectioned || bound);
    }

    /**
     * 推进"是否获得过好感"的粘性标记：好感 > 0 即置位，此后即使好感回落到 0 也保持置位。
     *
     * @param affection 当前好感
     * @param already   之前的标记值
     */
    public static boolean everAffectionedNow(int affection, boolean already) {
        return already || affection > 0;
    }
}
