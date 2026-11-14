package com.linguauniversalis.codex;

import com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind;
import com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia;
import com.linguauniversalis.core.encyclopedia.ObservationRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端图鉴（按玩家 UUID）——初稿/百晓镜的观测记录入口。
 *
 * <p><b>存档归属</b>：图鉴保存到**当前存档自己的文件**
 * （{@code <世界目录>/lingua_universalis/codex.txt}），不再写进玩家实体 NBT，
 * 因此**不会跨存档继承**。要把图鉴带到另一个存档，用管理员命令
 * {@code /lu codex export}（导出到全局目录 {@code <游戏目录>/lingua_universalis_codex/}）
 * 再在新存档里 {@code /lu codex import}。
 *
 * <p>文件格式：每行 {@code <玩家UUID>=<Base64>}，简单可读、便于备份。
 */
public final class ServerCodex {
    private static final Map<String, PlayerEncyclopedia> CODE = new ConcurrentHashMap<>();
    private static final Map<String, String> PERSISTED = new ConcurrentHashMap<>();

    private static Path worldFile;
    private static Path exportDir;

    private ServerCodex() {
    }

    /** 服务器启动：定位当前存档的图鉴文件并读取。 */
    public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
        worldFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("lingua_universalis").resolve("codex.txt");
        exportDir = server.getServerDirectory().resolve("lingua_universalis_codex");
        PERSISTED.clear();
        CODE.clear();
        if (!Files.exists(worldFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(worldFile, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String uuid = line.substring(0, eq).trim();
                String data = line.substring(eq + 1).trim();
                if (uuid.isEmpty() || data.isEmpty()) {
                    continue;
                }
                PERSISTED.put(uuid, data);
                CODE.put(uuid, decode(data));
            }
        } catch (IOException | RuntimeException failure) {
            com.linguauniversalis.LinguaUniversalis.LOGGER.warn("[codex] failed to read {}", worldFile, failure);
        }
    }

    /** 把内存里的图鉴写回当前存档（只写有变化的条目）。 */
    public static void flushFile() {
        if (worldFile == null) {
            return;
        }
        Map<String, String> snapshot = new LinkedHashMap<>(PERSISTED);
        for (Map.Entry<String, PlayerEncyclopedia> entry : CODE.entrySet()) {
            snapshot.put(entry.getKey(), encode(entry.getValue()));
        }
        // 安全阀：内存里没有任何图鉴，而磁盘上已有文件 → 多半是"没加载成功"，
        // 这时**不要**用空数据覆盖文件（否则重进游戏图鉴就没了）。
        if (snapshot.isEmpty() && worldFile != null && Files.exists(worldFile)) {
            return;
        }
        if (snapshot.equals(PERSISTED)) {
            return;
        }
        try {
            Files.createDirectories(worldFile.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            Files.writeString(worldFile, sb.toString(), StandardCharsets.UTF_8);
            PERSISTED.clear();
            PERSISTED.putAll(snapshot);
        } catch (IOException failure) {
            com.linguauniversalis.LinguaUniversalis.LOGGER.warn("[codex] failed to write {}", worldFile, failure);
        }
    }

    /** 导出某玩家图鉴到全局目录（跨存档交换用），失败返回 null。 */
    public static Path exportToGlobal(String playerUuid) {
        if (exportDir == null) {
            return null;
        }
        PlayerEncyclopedia book = CODE.get(playerUuid);
        if (book == null) {
            return null;
        }
        try {
            Files.createDirectories(exportDir);
            Path target = exportDir.resolve(playerUuid + ".txt");
            Files.writeString(target, encode(book), StandardCharsets.UTF_8);
            return target;
        } catch (IOException failure) {
            com.linguauniversalis.LinguaUniversalis.LOGGER.warn("[codex] export failed", failure);
            return null;
        }
    }

    /** 从全局目录导入某玩家图鉴（覆盖当前存档里的那份），返回是否成功。 */
    public static boolean importFromGlobal(String playerUuid) {
        if (exportDir == null) {
            return false;
        }
        Path source = exportDir.resolve(playerUuid + ".txt");
        if (!Files.exists(source)) {
            return false;
        }
        try {
            String data = Files.readString(source, StandardCharsets.UTF_8).trim();
            if (data.isEmpty()) {
                return false;
            }
            CODE.put(playerUuid, decode(data));
            flushFile();
            return true;
        } catch (IOException | RuntimeException failure) {
            com.linguauniversalis.LinguaUniversalis.LOGGER.warn("[codex] import failed", failure);
            return false;
        }
    }

    private static String encode(PlayerEncyclopedia book) {
        byte[] raw = com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.encode(book);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static PlayerEncyclopedia decode(String data) {
        return com.linguauniversalis.core.encyclopedia.EncyclopediaPersistence.decode(
                Base64.getDecoder().decode(data));
    }

    /** 玩家进服：图鉴已在服务器启动时按存档读入，这里只确保有一条记录。 */
    public static void load(net.minecraft.server.level.ServerPlayer player) {
        of(player.getStringUUID());
    }

    /** 退服：刷盘。 */
    public static void save(net.minecraft.server.level.ServerPlayer player) {
        flushFile();
    }

    /** 定期/关服：刷盘。 */
    public static void saveAll(net.minecraft.server.MinecraftServer server) {
        flushFile();
    }

    public static void clear(String playerUuid) {
        CODE.remove(playerUuid);
    }

    public static PlayerEncyclopedia of(String playerUuid) {
        return CODE.computeIfAbsent(playerUuid, k -> new PlayerEncyclopedia());
    }

    public static void sight(String playerUuid, String speciesId) {
        ObservationRules.onSighting(of(playerUuid), speciesId);
    }

    public static void zoomObserve(String playerUuid, String speciesId) {
        ObservationRules.onZoomObserve(of(playerUuid), speciesId);
    }

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