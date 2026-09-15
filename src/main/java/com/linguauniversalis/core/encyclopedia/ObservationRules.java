package com.linguauniversalis.core.encyclopedia;

import com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind;
import com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia;

import java.util.Collection;
import java.util.List;

/**
 * 观测/互动 → 图鉴条目解锁规则（设计汇总 §3 可发现性与 §10 初稿/百晓镜）。
 *
 * <ul>
 *   <li>首次目击（recordSighting）：SIGHT + BASIC_INFO；</li>
 *   <li>百晓镜缩放观测：TAXONOMY + HOSTILITY；</li>
 *   <li>投喂/送礼命中喜爱物：PREFERENCES（她"喜欢这个"被记录）。</li>
 * </ul>
 */
public final class ObservationRules {
    private ObservationRules() {
    }

    public static final List<Kind> SIGHT_KINDS = List.of(Kind.SIGHT, Kind.BASIC_INFO);
    public static final List<Kind> ZOOM_KINDS = List.of(Kind.TAXONOMY, Kind.HOSTILITY);
    public static final List<Kind> LIKED_INTERACTION_KINDS = List.of(Kind.PREFERENCES);

    /** 目击：记录 + 解锁基础信息。 */
    public static void onSighting(PlayerEncyclopedia book, String speciesId) {
        apply(book, speciesId, SIGHT_KINDS);
    }

    /** 百晓镜缩放观测：解锁分类学位置与敌意/红线条目。 */
    public static void onZoomObserve(PlayerEncyclopedia book, String speciesId) {
        apply(book, speciesId, ZOOM_KINDS);
    }

    /** 喜爱物互动（投喂/送礼命中偏好）：解锁喜好条目。 */
    public static void onLikedInteraction(PlayerEncyclopedia book, String speciesId) {
        apply(book, speciesId, LIKED_INTERACTION_KINDS);
    }

    private static void apply(PlayerEncyclopedia book, String speciesId, Collection<Kind> kinds) {
        for (Kind kind : kinds) {
            book.add(speciesId, kind);
        }
    }
}
