package com.linguauniversalis.core.species;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物种注册表：id → {@link SpeciesProfile}。
 * 服务端加载后可通过 {@link #get(String)} 查询任意已注册物种的档案数据。
 */
public final class SpeciesRegistry {
    private static final Map<String, SpeciesProfile> REGISTRY = new LinkedHashMap<>();

    private SpeciesRegistry() {
    }

    public static void register(SpeciesProfile profile) {
        if (REGISTRY.containsKey(profile.id())) {
            throw new IllegalArgumentException("Duplicate species id: " + profile.id());
        }
        REGISTRY.put(profile.id(), profile);
    }

    /** 移除一个物种（数据包重载时清理上一次注册的条目用）。 */
    public static boolean remove(String id) {
        return REGISTRY.remove(id) != null;
    }

    /** 注册或覆盖（数据包用：同 id 文件覆盖内置档案）。 */
    public static void registerOrReplace(SpeciesProfile profile) {
        REGISTRY.put(profile.id(), profile);
    }

    public static SpeciesProfile get(String id) {
        SpeciesProfile profile = REGISTRY.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown species id: " + id);
        }
        return profile;
    }

    public static boolean contains(String id) {
        return REGISTRY.containsKey(id);
    }

    public static Collection<SpeciesProfile> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
