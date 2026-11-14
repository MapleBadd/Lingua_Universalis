package com.linguauniversalis.event;

import com.linguauniversalis.core.taxonomy.Taxonomy;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.Map;

/**
 * 分类学标签的**数据包加载器**：服务端资源重载时读
 * {@code data/<namespace>/taxonomy/<family>/*.json}，校验后注册进 {@link Taxonomy}。
 *
 * <p>文件格式（family 由所在目录决定）：
 * <pre>
 * {
 *   "name":  "物质",          // 显示名（必填）
 *   "latin": "Materiale",     // 拉丁学名（可选，缺省用 id）
 *   "element": "material"     // 仅 element（元素）族使用（可选）
 * }
 * </pre>
 * 文件名（去 {@code .json}）即标签 id；同名标签以数据包为准（覆盖内置，内置仍是兜底）。
 *
 * <p>之所以做成 {@link ResourceManagerReloadListener}：{@code /reload} 时能跟着重载，
 * 不依赖客户端；校验失败只打日志、跳过该条，不影响启动。
 */
public final class TaxonomyDataLoader implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("lingua_universalis/taxonomy");

    /** 上一次加载的数据包标签（族名, id），重载时用于清理。 */
    private static final java.util.List<String[]> LOADED_TAGS = new java.util.ArrayList<>();

    /** NeoForge 事件入口：把加载器挂到服务端资源重载链上。 */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.parse("lingua_universalis:taxonomy_tags"), new TaxonomyDataLoader());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        // 重载一致性：撤掉上一批数据包标签
        for (String[] previous : LOADED_TAGS) {
            Taxonomy.unregister(Taxonomy.Family.valueOf(previous[0]), previous[1]);
        }
        LOADED_TAGS.clear();
        int loaded = 0;
        int skipped = 0;
        for (Taxonomy.Family family : Taxonomy.Family.values()) {
            String dir = "taxonomy/" + family.id();
            Map<Identifier, Resource> found = resources.listResources(dir,
                    id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
                String path = entry.getKey().getPath();
                String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    var json = GsonHelper.parse(reader);
                    String name = GsonHelper.getAsString(json, "name", "");
                    if (name.isEmpty()) {
                        LOGGER.warn("[taxonomy] skip {}: missing 'name'", entry.getKey());
                        skipped++;
                        continue;
                    }
                    String latin = GsonHelper.getAsString(json, "latin", id);
                    String element = GsonHelper.getAsString(json, "element", null);
                    Taxonomy.Taxon taxon = element != null && family == Taxonomy.Family.ELEMENT
                            ? Taxonomy.familia(id, latin, name, element)
                            : Taxonomy.taxon(family, id, latin, name);
                    Taxonomy.register(taxon);
                    LOADED_TAGS.add(new String[]{family.name(), id});
                    loaded++;
                } catch (Exception failure) {
                    LOGGER.warn("[taxonomy] failed to load {}: {}", entry.getKey(), failure.toString());
                    skipped++;
                }
            }
        }
        if (loaded > 0 || skipped > 0) {
            LOGGER.info("[taxonomy] datapack tags: loaded={} skipped={}", loaded, skipped);
        }
    }
}
