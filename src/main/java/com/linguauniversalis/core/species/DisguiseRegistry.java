package com.linguauniversalis.core.species;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变形形态注册表（纯数据，无 MC 依赖；可被附属模组调用）。
 *
 * <p>物种档案用 {@link com.linguauniversalis.core.behavior.TemplateKeys#SPECIES_DISGUISE_FORM}
 * 指向这里的某个 id。没登记的形态 id 会被 {@link #get(String)} 返回 {@code null}，
 * 实体侧据此表现为"没有变形"（不会崩、不会乱画）。
 */
public final class DisguiseRegistry {
    private static final Map<String, DisguiseForm> FORMS = new LinkedHashMap<>();

    private DisguiseRegistry() {
    }

    /** 登记/替换一个形态。 */
    public static void register(DisguiseForm form) {
        if (form != null) {
            FORMS.put(form.id(), form);
        }
    }

    /** 按 id 取形态；未登记返回 null。 */
    public static DisguiseForm get(String id) {
        return id == null ? null : FORMS.get(id.trim());
    }

    public static boolean contains(String id) {
        return get(id) != null;
    }

    public static Collection<DisguiseForm> all() {
        return java.util.List.copyOf(FORMS.values());
    }

    /** 清空（测试用）。 */
    public static void clear() {
        FORMS.clear();
    }

    /** 内置形态：猫又的双尾黑猫伪装（原版猫渲染）。 */
    public static final String NEKOMATA_CAT = "nekomata_cat";

    /** 登记内置形态（与 {@link BuiltinSpecies#registerAll()} 同时调用即可）。 */
    public static void registerDefaults() {
        // 猫形：碰撞箱按原版猫（0.6 × 0.7），视线高度 0.4 格
        register(new DisguiseForm(NEKOMATA_CAT, DisguiseForm.KIND_VANILLA_CAT,
                "nekomata_cat", "minecraft:textures/entity/cat/cat_all_black.png", "",
                0.6, 0.7, 0.4));
    }
}
