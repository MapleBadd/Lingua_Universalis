package com.linguauniversalis.codex;

import com.linguauniversalis.core.encyclopedia.Encyclopedia;
import com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind;
import com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia;
import com.linguauniversalis.core.encyclopedia.ObservationRules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端图鉴（按玩家 UUID）——初稿/百晓镜的观测记录入口。
 *
 * <p>当前为服务器内存态；跨会话持久化（随玩家存档/附件）待接入
 * （对应数据层 {@code EncyclopediaPersistence} 已就绪，可直接复用）。
 */
public final class ServerCodex {
    private static final Map<String, PlayerEncyclopedia> CODE = new ConcurrentHashMap<>();

    private ServerCodex() {
    }

    public static PlayerEncyclopedia of(String playerUuid) {
        return CODE.computeIfAbsent(playerUuid, k -> new PlayerEncyclopedia());
    }

    /** 目击记录（幂等）：记录 + 解锁基础信息。 */
    public static void sight(String playerUuid, String speciesId) {
        ObservationRules.onSighting(of(playerUuid), speciesId);
    }

    /** 缩放观测：解锁分类学位置与敌意/红线条目。 */
    public static void zoomObserve(String playerUuid, String speciesId) {
        ObservationRules.onZoomObserve(of(playerUuid), speciesId);
    }

    /** 喜爱物互动（投喂/送礼命中喜好）：解锁"喜好"条目。 */
    public static void likedInteraction(String playerUuid, String speciesId) {
        ObservationRules.onLikedInteraction(of(playerUuid), speciesId);
    }

    public static boolean hasSighted(String playerUuid, String speciesId) {
        return of(playerUuid).hasSighted(speciesId);
    }

    public static boolean isUnlocked(String playerUuid, String speciesId, Kind kind) {
        return of(playerUuid).isUnlocked(speciesId, kind);
    }

    public static int sightedCount(String playerUuid) {
        return of(playerUuid).sightedCount();
    }

    /** 生成该玩家图鉴登记概览（多行文本），供命令/调试与后续 GUI 使用。 */
    public static String describe(String playerUuid) {
        PlayerEncyclopedia book = of(playerUuid);
        StringBuilder sb = new StringBuilder("codex sighted=" + book.sightedCount());
        for (String speciesId : book.sightedSpecies()) {
            sb.append("\n  ").append(speciesId).append(" -> ");
            for (Kind kind : Kind.values()) {
                if (book.isUnlocked(speciesId, kind)) {
                    sb.append(kind.name()).append(" ");
                }
            }
        }
        return sb.toString();
    }
}
