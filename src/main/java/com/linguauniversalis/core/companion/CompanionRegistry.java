package com.linguauniversalis.core.companion;

import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家侧的"我的魔物娘"登记表（**持久化**）：谁是伙伴、谁已誓约。
 *
 * <p>为什么需要它：绑定/誓约状态本身只存在<b>实体</b>上（{@code boundPlayerUuid} / {@code vowed()}），
 * 实体没加载时（人在别的维度、区块没开、暂时死亡）就查不到。这里给每个玩家记一份
 * `实体 uuid → 物种/名字/是否誓约` 的清单，随玩家数据存档一起保存。
 *
 * <p>清单会**双向自愈**：
 * <ul>
 *   <li>{@link #syncFromWorld(ServerPlayer)} —— 把当前所有维度里"绑定到该玩家"的实体并进清单（补齐漏登记）；</li>
 *   <li>{@link #removeByEntity(String)} —— 实体死亡/移除时划掉对应条目。</li>
 * </ul>
 *
 * <p>编码格式（存进玩家 {@code getPersistentData()} 的字符串）：
 * {@code uuid|species|name|bonded|vowed;uuid|...}（名字里的分隔符会被替换掉）。
 */
public final class CompanionRegistry {
    /** 玩家持久化数据里的键。 */
    private static final String TAG = "LUCompanions";
    private static final String RECORD_SEP = ";";
    private static final String FIELD_SEP = "|";

    private static final Map<String, List<Entry>> CACHE = new ConcurrentHashMap<>();

    private CompanionRegistry() {
    }

    /** 一条登记：某个魔物娘实体的身份与状态。 */
    public record Entry(String entityUuid, String speciesId, String name, boolean vowed) {
    }

    // ------------------------------------------------------------------ 读写
    public static List<Entry> list(String playerUuid) {
        return CACHE.computeIfAbsent(playerUuid, k -> new ArrayList<>());
    }

    /** 登记一只（绑定/誓约时调用），已存在则更新。 */
    public static void register(String playerUuid, MonsterGirlEntity girl) {
        Entry entry = new Entry(girl.getStringUUID(), girl.speciesId(),
                sanitize(girl.getName().getString()), girl.girlState().vowed());
        List<Entry> entries = list(playerUuid);
        entries.removeIf(e -> e.entityUuid().equals(entry.entityUuid()));
        entries.add(entry);
    }

    /** 实体死亡/移除：从所有玩家的清单里划掉。 */
    public static void removeByEntity(String entityUuid) {
        for (List<Entry> entries : CACHE.values()) {
            entries.removeIf(e -> e.entityUuid().equals(entityUuid));
        }
    }

    /**
     * 扫描所有已加载维度，把"绑定到该玩家"的实体并进清单（补登记 + 刷新誓约状态）。
     *
     * @return 扫描到的数量
     */
    public static int syncFromWorld(ServerPlayer player, MinecraftServer server) {
        String uuid = player.getStringUUID();
        int found = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof MonsterGirlEntity girl
                        && uuid.equals(girl.girlState().boundPlayerUuid())) {
                    register(uuid, girl);
                    found++;
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ 持久化
    public static void load(ServerPlayer player) {
        String encoded = player.getPersistentData().getStringOr(TAG, "");
        List<Entry> entries = new ArrayList<>();
        if (!encoded.isEmpty()) {
            for (String record : encoded.split(RECORD_SEP)) {
                if (record.isEmpty()) {
                    continue;
                }
                String[] parts = record.split("\\" + FIELD_SEP);
                if (parts.length < 4) {
                    continue;
                }
                entries.add(new Entry(parts[0], parts[1], parts[2], Boolean.parseBoolean(parts[3])));
            }
        }
        CACHE.put(player.getStringUUID(), entries);
    }

    public static void save(ServerPlayer player) {
        List<Entry> entries = CACHE.get(player.getStringUUID());
        if (entries == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (out.length() > 0) {
                out.append(RECORD_SEP);
            }
            out.append(entry.entityUuid()).append(FIELD_SEP)
                    .append(entry.speciesId()).append(FIELD_SEP)
                    .append(sanitize(entry.name())).append(FIELD_SEP)
                    .append(entry.vowed());
        }
        player.getPersistentData().putString(TAG, out.toString());
    }

    public static void saveAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            save(player);
        }
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.replace(FIELD_SEP, " ").replace(RECORD_SEP, " ");
    }
}
