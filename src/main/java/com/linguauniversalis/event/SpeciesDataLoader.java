package com.linguauniversalis.event;

import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.core.taxonomy.Taxonomy;
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
 * 物种档案的**数据包加载器**：`data/<ns>/species/<id>.json` → {@link SpeciesProfile}。
 *
 * <p>这是"B 方案：物种档案数据包化"的实现：内置物种仍在代码里（兜底），
 * 数据包里的同 id 文件会**覆盖**内置档案（方便整合包改数值），新 id 则是新增物种。
 *
 * <p>最小文件示例：
 * <pre>
 * {
 *   "name": "灯守",
 *   "latin": "Luminis",
 *   "taxonomy": { "essence": "elemental", "structure": "amorpha", "magic": "sympatho",
 *                 "habit": "colonialis", "element": "lux", "form": "gelata" },
 *   "spawn": "natural",
 *   "spawn_tags": ["洞窟"],
 *   "backpack": 6,
 *   "hotbar": ["main_hand", "off_hand"],
 *   "health": 18, "speed": "NORMAL", "armor": 0, "toughness": 0,
 *   "melee": 2, "ranged": 0,
 *   "liked_foods": ["minecraft:glow_berries"]
 * }
 * </pre>
 *
 * <p>校验策略与分类学加载器一致：缺字段/未知标签 → **打日志跳过该文件**，不影响启动。
 */
public final class SpeciesDataLoader implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("lingua_universalis/species");

    /** 最近一次加载成功的数据包物种 id（供 {@code /lu species list} 查看）。 */
    private static final java.util.List<String> LOADED = new java.util.ArrayList<>();
    /** 最近一次加载的问题清单（文件 → 原因）。 */
    private static final java.util.List<String> PROBLEMS = new java.util.ArrayList<>();

    public static java.util.List<String> loadedIds() {
        return java.util.List.copyOf(LOADED);
    }

    public static java.util.List<String> problems() {
        return java.util.List.copyOf(PROBLEMS);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.parse("lingua_universalis:species_profiles"), new SpeciesDataLoader());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        int loaded = 0;
        int skipped = 0;
        // 重载一致性：先把上一批数据包物种撤掉，避免删除 JSON 后残留脏数据
        for (String previous : LOADED) {
            SpeciesRegistry.remove(previous);
        }
        LOADED.clear();
        PROBLEMS.clear();
        Map<Identifier, Resource> found = resources.listResources("species",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                var json = GsonHelper.parse(reader);
                SpeciesProfile profile = parse(id, json);
                if (profile == null) {
                    skipped++;
                    continue;
                }
                SpeciesRegistry.registerOrReplace(profile);
                LOADED.add(id);
                java.util.List<String> unknown = unknownTemplateKeys(json);
                if (!unknown.isEmpty()) {
                    String warning = id + ": unknown template keys " + String.join(", ", unknown);
                    PROBLEMS.add(warning);
                    LOGGER.warn("[species] {}", warning);
                }
                loaded++;
            } catch (Exception failure) {
                String problem = entry.getKey() + ": " + failure.getMessage();
                PROBLEMS.add(problem);
                LOGGER.warn("[species] failed to load {}: {}", entry.getKey(), failure.toString());
                skipped++;
            }
        }
        if (loaded > 0 || skipped > 0) {
            LOGGER.info("[species] datapack profiles: loaded={} skipped={}", loaded, skipped);
        }
    }

    /** 解析一个物种文件；任一必要字段缺失/非法则返回 null（跳过该文件）。 */
    private static SpeciesProfile parse(String id, com.google.gson.JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", "");
        if (name.isEmpty()) {
            LOGGER.warn("[species] {}: missing 'name'", id);
            return null;
        }
        String latin = GsonHelper.getAsString(json, "latin", id);

        if (!json.has("taxonomy") || !json.get("taxonomy").isJsonObject()) {
            LOGGER.warn("[species] {}: missing 'taxonomy'", id);
            return null;
        }
        var tags = json.getAsJsonObject("taxonomy");
        Taxonomy.Taxon essence = tag(tags, Taxonomy.Family.ESSENCE);
        Taxonomy.Taxon structure = tag(tags, Taxonomy.Family.STRUCTURE);
        Taxonomy.Taxon magic = tag(tags, Taxonomy.Family.MAGIC);
        Taxonomy.Taxon habit = tag(tags, Taxonomy.Family.HABIT);
        Taxonomy.Taxon element = tag(tags, Taxonomy.Family.ELEMENT);
        Taxonomy.Taxon form = tag(tags, Taxonomy.Family.FORM);
        if (essence == null || structure == null || magic == null || habit == null
                || element == null || form == null) {
            LOGGER.warn("[species] {}: unknown taxonomy tag id", id);
            return null;
        }

        SpeciesProfile.Builder builder = SpeciesProfile.builder(id)
                .name(name, latin)
                .taxonomy(essence, structure, magic, habit, element, form);

        String spawn = GsonHelper.getAsString(json, "spawn", "natural");
        builder.spawn("fixed".equalsIgnoreCase(spawn)
                ? SpeciesProfile.SpawnType.FIXED : SpeciesProfile.SpawnType.NATURAL);

        if (json.has("spawn_tags")) {
            var arr = json.getAsJsonArray("spawn_tags");
            String[] values = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                values[i] = arr.get(i).getAsString();
            }
            builder.spawnTags(values);
        }
        // 自然生成规则（可选）：biomes / spawn_weight / spawn_group / spawn_y / spawn_light
        if (json.has("biomes") || json.has("spawn_weight")) {
            java.util.List<String> biomes = new java.util.ArrayList<>();
            if (json.has("biomes")) {
                for (var biome : json.getAsJsonArray("biomes")) {
                    biomes.add(biome.getAsString());
                }
            }
            int weight = GsonHelper.getAsInt(json, "spawn_weight", 10);
            int group = GsonHelper.getAsInt(json, "spawn_group", 1);
            int minY = 50;
            int maxY = 200;
            if (json.has("spawn_y")) {
                var range = json.getAsJsonArray("spawn_y");
                if (range.size() >= 2) {
                    minY = range.get(0).getAsInt();
                    maxY = range.get(1).getAsInt();
                }
            }
            int light = GsonHelper.getAsInt(json, "spawn_light", 15);
            builder.spawnRule(new com.linguauniversalis.core.species.SpawnRule(
                    java.util.List.copyOf(biomes), weight, group, minY, maxY, light));
        }
        if (json.has("backpack")) {
            builder.backpack(GsonHelper.getAsInt(json, "backpack", 4));
        }
        if (json.has("hotbar")) {
            var arr = json.getAsJsonArray("hotbar");
            String[] values = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                values[i] = arr.get(i).getAsString();
            }
            builder.hotbar(values);
        }
        String nest = GsonHelper.getAsString(json, "nest_block", "");
        if (nest.isEmpty()) {
            builder.nestBlockNone();
        } else {
            builder.nestBlock(nest);
        }
        if (json.has("health") || json.has("speed")) {
            String speedName = GsonHelper.getAsString(json, "speed", "NORMAL");
            SpeciesProfile.SpeedTier tier = SpeciesProfile.SpeedTier.NORMAL;
            for (SpeciesProfile.SpeedTier candidate : SpeciesProfile.SpeedTier.values()) {
                if (candidate.name().equalsIgnoreCase(speedName)) {
                    tier = candidate;
                }
            }
            builder.stats(GsonHelper.getAsFloat(json, "health", 20.0F), tier);
        }
        if (json.has("armor") || json.has("toughness")) {
            builder.armor(GsonHelper.getAsInt(json, "armor", 0), GsonHelper.getAsInt(json, "toughness", 0));
        }
        String moodLow = GsonHelper.getAsString(json, "mood_low", "");
        if (!moodLow.isEmpty()) {
            builder.moodLow(moodLow);
        }
        // 通用模板入口：flags / params / texts（keys 见 TemplateKeys.*）
        if (json.has("template_flags")) {
            for (var e : json.getAsJsonObject("template_flags").entrySet()) {
                builder.templateFlag(e.getKey(), e.getValue().getAsBoolean());
            }
        }
        if (json.has("template_params")) {
            for (var e : json.getAsJsonObject("template_params").entrySet()) {
                builder.templateParam(e.getKey(), e.getValue().getAsDouble());
            }
        }
        if (json.has("template_texts")) {
            for (var e : json.getAsJsonObject("template_texts").entrySet()) {
                builder.templateText(e.getKey(), e.getValue().getAsString());
            }
        }
        if (json.has("liked_foods")) {
            var arr = json.getAsJsonArray("liked_foods");
            String[] values = new String[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                values[i] = arr.get(i).getAsString();
            }
            builder.likeFood(values);
        }
        return builder.build();
    }

    /** 反射收集 {@code TemplateKeys} 里声明的全部 key，用于校验数据包写错的字段名。 */
    private static java.util.Set<String> knownTemplateKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (var field : com.linguauniversalis.core.behavior.TemplateKeys.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    field.setAccessible(true);
                    keys.add((String) field.get(null));
                } catch (Exception ignored) {
                    // 读不到就跳过，不影响加载
                }
            }
        }
        return keys;
    }

    /** 数据包里出现的未知 template key（拼错时能立刻发现，而不是静默忽略）。 */
    private static java.util.List<String> unknownTemplateKeys(com.google.gson.JsonObject json) {
        java.util.Set<String> known = knownTemplateKeys();
        java.util.List<String> unknown = new java.util.ArrayList<>();
        for (String section : new String[]{"template_flags", "template_params", "template_texts"}) {
            if (!json.has(section) || !json.get(section).isJsonObject()) {
                continue;
            }
            for (var e : json.getAsJsonObject(section).entrySet()) {
                if (!known.contains(e.getKey())) {
                    unknown.add(e.getKey());
                }
            }
        }
        return unknown;
    }

    /** 按族取标签 id → Taxon（不存在的返回 null）。 */
    private static Taxonomy.Taxon tag(com.google.gson.JsonObject tags, Taxonomy.Family family) {
        String tagId = GsonHelper.getAsString(tags, family.id(), "");
        if (tagId.isEmpty()) {
            return null;
        }
        for (Taxonomy.Taxon taxon : Taxonomy.all(family)) {
            if (taxon.id().equals(tagId)) {
                return taxon;
            }
        }
        return null;
    }
}
