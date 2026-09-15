package com.linguauniversalis.core.behavior;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 心情低落表现模板注册表（设计汇总 §3）。
 *
 * <p>心情 <20 时进入"心情低落 depressed"，具体行为由模板赋予，物种档案选择其一：
 * <ul>
 *   <li>{@code low_mood_random_attack} —— 低落·随机攻击：强制随机游荡，只攻击近战范围内的
 *       非玩家生物（不主动寻仇）；击杀生物后 1–2 分钟内心情回升至 30。</li>
 * </ul>
 * 目前只实现/注册了随机攻击模板；新增模板=注册一个新的处理器实现，物种侧只需改档案字段。
 */
public final class MoodLowTemplateRegistry {
    public static final String LOW_MOOD_RANDOM_ATTACK = "low_mood_random_attack";

    private static final Map<String, String> REGISTRY = new LinkedHashMap<>();

    private MoodLowTemplateRegistry() {
    }

    /** 注册一个心情低落模板的元信息（name 为人类可读名；行为逻辑由对应 AI 模块实现）。 */
    public static void register(String id, String displayName) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate mood-low template: " + id);
        }
        REGISTRY.put(id, displayName);
    }

    public static boolean contains(String id) {
        return REGISTRY.containsKey(id);
    }

    public static String displayName(String id) {
        String name = REGISTRY.get(id);
        if (name == null) {
            throw new IllegalArgumentException("Unknown mood-low template: " + id);
        }
        return name;
    }

    public static Collection<String> ids() {
        return Collections.unmodifiableCollection(REGISTRY.keySet());
    }

    public static void registerDefaults() {
        register(LOW_MOOD_RANDOM_ATTACK, "低落·随机攻击");
    }
}
