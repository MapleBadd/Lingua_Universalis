package com.linguauniversalis.core.taxonomy;

import com.linguauniversalis.core.ModConstants;

/**
 * 档位：野生 → 友善 → 伙伴 → 誓约。
 *
 * <p>规则（设计汇总 §2）：
 * <ul>
 *   <li>数值带只决定"进入下一档"（进档看数值）；</li>
 *   <li>友善好感 <10 时立即退回野生（清除友善对象信息）；</li>
 *   <li>伙伴首次到达 100 后永久，好感回落不再变野生；命令权需要好感 ≥100；</li>
 *   <li>誓约需好感 200 + 誓约协议书，好感锁定 200；</li>
 *   <li>伙伴在好感&心情双 0 且 24h 无互动后进入休眠（{@link #isDormant} 由状态机另行管理）。</li>
 * </ul>
 */
public enum RelationshipStage {
    WILD("wild"),
    FRIENDLY("friendly"),
    COMPANION("companion"),
    VOWED("vowed");

    private final String englishId;

    RelationshipStage(String englishId) {
        this.englishId = englishId;
    }

    public String englishId() {
        return englishId;
    }

    /** 根据当前好感数值判定"应当处于"的档位（野生/友善/伙伴；誓约需额外条件）。 */
    public static RelationshipStage stageByAffection(int affection) {
        if (affection < ModConstants.FRIENDLY_MIN) {
            return WILD;
        }
        if (affection < ModConstants.COMPANION_MIN) {
            return FRIENDLY;
        }
        return COMPANION;
    }

    /** 数值带是否已足够进入伙伴。 */
    public static boolean affectionReachesCompanion(int affection) {
        return affection >= ModConstants.COMPANION_MIN;
    }

    /** 是否具备誓约资格（好感 200，且当前为伙伴或誓约）。 */
    public static boolean affectionReachesVow(int affection) {
        return affection >= ModConstants.VOW_MIN;
    }

    /** 命令权：伙伴档及以上且好感 ≥100（伙伴好感回落 <100 会失去命令权）。 */
    public static boolean canCommand(RelationshipStage stage, int affection) {
        if (stage != COMPANION && stage != VOWED) {
            return false;
        }
        return affection >= ModConstants.COMPANION_MIN;
    }
}
