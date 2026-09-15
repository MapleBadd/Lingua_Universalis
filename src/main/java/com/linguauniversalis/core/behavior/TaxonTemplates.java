package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.taxonomy.Taxonomy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 分类阶元默认特性表（设计汇总 §13 的"模板特性=默认值"，推广到域/界/门/纲/目/科全阶元）。
 *
 * <p>每个已注册类目（Taxon）可挂一份默认数值/开关（键见 {@link TemplateKeys}）；
 * 未注册类目或未设置的键返回空（由调用方给兜底值）。物种侧读取优先级：
 * <b>物种档案覆盖 &gt; 所属各阶元类目的默认特性 &gt; 调用方兜底</b>（见
 * {@link com.linguauniversalis.core.species.SpeciesProfile#featureNumber(String, double)}）。
 *
 * <p>本表只存"默认值"，不含行为逻辑；行为由各模板模块（如 {@link OrdoTemplates}）实现。
 */
public final class TaxonTemplates {
    private static final Map<Taxonomy.Taxon, Entry> TABLE = new java.util.LinkedHashMap<>();

    private TaxonTemplates() {
    }

    /** 一个类目的默认特性：数值键 + 开关键。 */
    public static final class Entry {
        private final Map<String, Double> numbers;
        private final Map<String, Boolean> flags;

        Entry(Map<String, Double> numbers, Map<String, Boolean> flags) {
            this.numbers = Map.copyOf(numbers);
            this.flags = Map.copyOf(flags);
        }

        public double number(String key, double fallback) {
            Double v = numbers.get(key);
            return v == null ? fallback : v;
        }

        public boolean flag(String key, boolean fallback) {
            Boolean v = flags.get(key);
            return v == null ? fallback : v;
        }

        public boolean hasNumber(String key) {
            return numbers.containsKey(key);
        }

        public boolean hasFlag(String key) {
            return flags.containsKey(key);
        }
    }

    public static final class Builder {
        private final Map<String, Double> numbers = new HashMap<>();
        private final Map<String, Boolean> flags = new HashMap<>();

        private Builder() {
        }

        public Builder number(String key, double value) {
            numbers.put(key, value);
            return this;
        }

        public Builder flag(String key, boolean value) {
            flags.put(key, value);
            return this;
        }

        Entry build() {
            return new Entry(numbers, flags);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 为一个类目注册默认特性（覆盖旧值）。 */
    public static void register(Taxonomy.Taxon taxon, Entry entry) {
        if (taxon == null) {
            throw new IllegalArgumentException("taxon required");
        }
        TABLE.put(taxon, entry);
    }

    public static void register(Taxonomy.Taxon taxon, Builder builder) {
        register(taxon, builder.build());
    }

    /** 取类目默认特性；未注册返回空表（安全默认）。 */
    public static Entry of(Taxonomy.Taxon taxon) {
        if (taxon == null) {
            return new Entry(Map.of(), Map.of());
        }
        Entry entry = TABLE.get(taxon);
        return entry == null ? new Entry(Map.of(), Map.of()) : entry;
    }

    public static boolean isRegistered(Taxonomy.Taxon taxon) {
        return taxon != null && TABLE.containsKey(taxon);
    }

    public static Collection<Taxonomy.Taxon> registeredTaxa() {
        return Collections.unmodifiableSet(TABLE.keySet());
    }

    /**
     * 数值默认查询：先看类目默认，再看兜底（物种覆盖在 SpeciesProfile 层处理）。
     */
    public static double numberDefault(Taxonomy.Taxon taxon, String key, double fallback) {
        return of(taxon).number(key, fallback);
    }

    /** 开关默认查询。 */
    public static boolean flagDefault(Taxonomy.Taxon taxon, String key, boolean fallback) {
        return of(taxon).flag(key, fallback);
    }

    // ------------------------------------------------------------------ 内置默认特性（§13 查询表）
    /** 注册内置阶元默认特性（主类与冒烟入口调用一次；幂等）。 */
    public static void registerDefaults() {
        // 界（Kingdom）
        register(Taxonomy.Kingdom.CHORDATA, TaxonTemplates.builder()
                .number(TemplateKeys.KINGDOM_JUMP_SCALE, 2.0)
                .flag(TemplateKeys.FLAG_KINGDOM_AUTO_STEP_UP, true));
        register(Taxonomy.Kingdom.AMORPHA, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_KINGDOM_CRIT_IMMUNE, true)
                .flag(TemplateKeys.FLAG_KINGDOM_FALL_IMMUNE, true)
                .flag(TemplateKeys.FLAG_KINGDOM_NO_ARMOR, true));
        register(Taxonomy.Kingdom.PHYTA, TaxonTemplates.builder()); // 自愈/畏高温低温：数值型，后续战斗层实现
        register(Taxonomy.Kingdom.CRUSTACEA, TaxonTemplates.builder()); // 护甲/韧性由物种档案显式给出（数值占位）

        // 门（Classis）
        register(Taxonomy.MagicClassis.EXOSPIRA, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_CLASSIS_NEST_BUFF, true));

        // 科（Sectio）
        register(Taxonomy.FormaSectio.PELLIS, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_BLEED, true)
                .flag(TemplateKeys.FLAG_SECTIO_PROJECTILE_DODGE, true));
        register(Taxonomy.FormaSectio.SQUAMAE, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_KNOCKBACK, true));
        register(Taxonomy.FormaSectio.PLUMAE, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_CHARGE, true)
                .flag(TemplateKeys.FLAG_SECTIO_SLOW_FALL, true));
        register(Taxonomy.FormaSectio.PALPI, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_EXTRA_LIMBS, true));
        register(Taxonomy.FormaSectio.GELATA, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_STATUS_HIT, true));
        register(Taxonomy.FormaSectio.MYCETA, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_SECTIO_AREA_EFFECT, true));

        // 目（Familia）：幽暗目 —— 视野内出现亡灵每天一次 +1 心情（猫又等）
        register(Taxonomy.ElementFamilia.UMBRA, TaxonTemplates.builder()
                .flag(TemplateKeys.FLAG_FAMILIA_UNDEAD_SIGHT_MOOD, true));

        // 域/目：无额外默认数值（元素标签已在 Taxonomy 上）
    }
}
