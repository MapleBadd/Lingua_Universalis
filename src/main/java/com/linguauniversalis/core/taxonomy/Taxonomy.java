package com.linguauniversalis.core.taxonomy;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魔物分类学阶元注册表（设计汇总 §13，模块化版本）。
 *
 * <p>每一阶元（域/界/门/纲/目/科）都是一张可扩展的类目表：任何代码（含附属模组）都可
 * 用 {@link #register} 注册/替换自己的类目，或 {@link #unregister} 移除类目，而无需改动本类
 * 或枚举。内置类目由 {@link #registerDefaults()} 注册（等价于旧枚举常量，保留 <code>Domain.MATERIAL
 * </code> 式引用以便阅读）。
 *
 * <p>行为模板（如领地巡游的算法）不属于分类学本身，见
 * {@code core/behavior} 的模板模块；分类学只负责"有哪些类目、各自叫什么"。
 */
public final class Taxonomy {
    private Taxonomy() {
    }

    /**
     * **六个标签族**（原"阶元"）—— 分类学不再是树，而是"每个族挑一个标签"的六元组。
     *
     * <p>族名与机器名（用于数据包路径 / 翻译键）：
     * <ul>
     *   <li>{@code ESSENCE} 本质 / {@code essence}</li>
     *   <li>{@code STRUCTURE} 构造 / {@code structure}</li>
     *   <li>{@code MAGIC} 魔力 / {@code magic}</li>
     *   <li>{@code HABIT} 习性 / {@code habit}</li>
     *   <li>{@code ELEMENT} 元素 / {@code element}</li>
     *   <li>{@code FORM} 形态 / {@code form}</li>
     * </ul>
     */
    public enum Family {
        ESSENCE("essence", "本质"),
        STRUCTURE("structure", "构造"),
        MAGIC("magic", "魔力"),
        HABIT("habit", "习性"),
        ELEMENT("element", "元素"),
        FORM("form", "形态");

        private final String id;
        public final String zh;

        Family(String id, String zh) {
            this.id = id;
            this.zh = zh;
        }

        /** 机器名（数据包目录名 / 翻译键后缀）。 */
        public String id() {
            return id;
        }

        /** 翻译键：{@code gui.lingua_universalis.codex.family.<id>}。 */
        public String translationKey() {
            return "gui.lingua_universalis.codex.family." + id;
        }
    }

    /** 一个分类学类目（不可变值对象）。 */
    public static final class Taxon {
        private final Family family;
        private final String id;
        private final String latin;
        private final String zh;
        private final String element;

        Taxon(Family family, String id, String latin, String zh, String element) {
            this.family = family;
            this.id = id;
            this.latin = latin;
            this.zh = zh;
            this.element = element;
        }

        public Family family() {
            return family;
        }


        /** 稳定 id（如 "territorialis"、"umbra"）——供模板注册表/存档引用。 */
        public String id() {
            return id;
        }

        public String latin() {
            return latin;
        }

        public String zh() {
            return zh;
        }

        /** 元素标签 id（仅目 familia 有；其它为 null）。 */
        public String element() {
            return element;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Taxon other)) {
                return false;
            }
            return family == other.family && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return 31 * family.hashCode() + id.hashCode();
        }

        @Override
        public String toString() {
            return family.name() + ":" + id;
        }
    }

    // ------------------------------------------------------------------ 注册表
    private static final Map<Family, Map<String, Taxon>> REGISTRY = new EnumMap<>(Family.class);

    static {
        for (Family rank : Family.values()) {
            REGISTRY.put(rank, new LinkedHashMap<>());
        }
    }

    /** 造一个类目（不注册；常用 {@link #register} 放入对应阶元表）。 */
    public static Taxon taxon(Family family, String id, String latin, String zh) {
        return new Taxon(family, id, latin, zh, null);
    }

    /** 造一个目（带元素标签）。 */
    public static Taxon familia(String id, String latin, String zh, String element) {
        return new Taxon(Family.ELEMENT, id, latin, zh, element);
    }

    /** 注册/替换一个类目；id 冲突时覆盖。 */
    public static void register(Taxon taxon) {
        if (taxon == null || taxon.id() == null || taxon.id().isEmpty()) {
            throw new IllegalArgumentException("taxon id required");
        }
        REGISTRY.get(taxon.family()).put(taxon.id(), taxon);
    }

    /** 移除一个类目（供"减少类目"/试验）；返回是否曾存在。 */
    public static boolean unregister(Family rank, String id) {
        return REGISTRY.get(rank).remove(id) != null;
    }

    public static boolean contains(Family rank, String id) {
        return REGISTRY.get(rank).containsKey(id);
    }

    /** 取类目；不存在返回 null（模板层应提供安全默认）。 */
    public static Taxon get(Family rank, String id) {
        return REGISTRY.get(rank).get(id);
    }

    /** 取类目；不存在抛异常（防拼写漂移）。 */
    public static Taxon require(Family rank, String id) {
        Taxon taxon = REGISTRY.get(rank).get(id);
        if (taxon == null) {
            throw new IllegalArgumentException("unknown taxonomy " + rank + ":" + id);
        }
        return taxon;
    }

    public static Collection<Taxon> all(Family rank) {
        return Collections.unmodifiableCollection(REGISTRY.get(rank).values());
    }

    public static int count(Family rank) {
        return REGISTRY.get(rank).size();
    }

    // ------------------------------------------------------------------ 内置类目（旧枚举等价物）
    /** 域 Regnum —— 生命本质。 */
    public static final class Domain {
        public static final Taxon MATERIAL = taxon(Family.ESSENCE, "material", "Materiale", "物质");
        public static final Taxon ELEMENTAL = taxon(Family.ESSENCE, "elemental", "Elementale", "元素");
        public static final Taxon CHAOTIC = taxon(Family.ESSENCE, "chaotic", "Chaoticum", "混沌");

        private Domain() {
        }
    }

    /** 界 Phylum —— 基础构造。 */
    public static final class Kingdom {
        public static final Taxon CHORDATA = taxon(Family.STRUCTURE, "chordata", "Chordata", "脊索");
        public static final Taxon CRUSTACEA = taxon(Family.STRUCTURE, "crustacea", "Crustacea", "外壳");
        public static final Taxon AMORPHA = taxon(Family.STRUCTURE, "amorpha", "Amorpha", "流形");
        public static final Taxon PHYTA = taxon(Family.STRUCTURE, "phyta", "Phyta", "植生");

        private Kingdom() {
        }
    }

    /** 门 Classis —— 魔力运用方式。 */
    public static final class MagicClassis {
        public static final Taxon EXOSPIRA = taxon(Family.MAGIC, "exospira", "Exospira", "外生息");
        public static final Taxon ENDOPYRA = taxon(Family.MAGIC, "endopyra", "Endopyra", "内燃");
        public static final Taxon SYMPATHO = taxon(Family.MAGIC, "sympatho", "Sympatho", "交感");
        public static final Taxon ENTROPO = taxon(Family.MAGIC, "entropo", "Entropo", "熵噬");

        private MagicClassis() {
        }
    }

    /** 纲 Ordo —— 社会习性（行为模板挂在纲上，见 core/behavior）。 */
    public static final class SocialOrdo {
        public static final Taxon MIMETICUS = taxon(Family.HABIT, "mimeticus", "Mimeticus", "拟态同化");
        public static final Taxon TERRITORIALIS = taxon(Family.HABIT, "territorialis", "Territorialis", "领地巡游");
        public static final Taxon NOMADICUS = taxon(Family.HABIT, "nomadicus", "Nomadicus", "独行自生");
        public static final Taxon COLONIALIS = taxon(Family.HABIT, "colonialis", "Colonialis", "巢穴共生");
        public static final Taxon PACTUM = taxon(Family.HABIT, "pactum", "Pactum", "游牧契约");

        private SocialOrdo() {
        }
    }

    /** 目 Familia —— 亲和的能量属性；每目绑定一个元素标签。 */
    public static final class ElementFamilia {
        public static final Taxon IGNIS = familia("ignis", "Ignis", "炽焰", "ignis");
        public static final Taxon AQUA = familia("aqua", "Aqua", "深流", "aqua");
        public static final Taxon ORDO = familia("ordo", "Ordo", "规序", "ordo");
        public static final Taxon CHAOS = familia("chaos", "Chaos", "流变", "chaos");
        public static final Taxon LUX = familia("lux", "Lux", "辉耀", "lux");
        public static final Taxon UMBRA = familia("umbra", "Umbra", "幽暗", "umbra");
        public static final Taxon VITA = familia("vita", "Vita", "涣生", "vita");

        private ElementFamilia() {
        }
    }

    /** 科 Sectio —— 外形特征。 */
    public static final class FormaSectio {
        public static final Taxon SQUAMAE = taxon(Family.FORM, "squamae", "Squamae", "鳞");
        public static final Taxon PELLIS = taxon(Family.FORM, "pellis", "Pellis", "绒");
        public static final Taxon PLUMAE = taxon(Family.FORM, "plumae", "Plumae", "羽");
        public static final Taxon PALPI = taxon(Family.FORM, "palpi", "Palpi", "触肢");
        public static final Taxon GELATA = taxon(Family.FORM, "gelata", "Gelata", "胶质");
        public static final Taxon MYCETA = taxon(Family.FORM, "myceta", "Myceta", "菌丝");

        private FormaSectio() {
        }
    }

    /** 注册全部内置类目（幂等；主类与冒烟测试入口调用一次）。 */
    public static void registerDefaults() {
        register(Domain.MATERIAL);
        register(Domain.ELEMENTAL);
        register(Domain.CHAOTIC);

        register(Kingdom.CHORDATA);
        register(Kingdom.CRUSTACEA);
        register(Kingdom.AMORPHA);
        register(Kingdom.PHYTA);

        register(MagicClassis.EXOSPIRA);
        register(MagicClassis.ENDOPYRA);
        register(MagicClassis.SYMPATHO);
        register(MagicClassis.ENTROPO);

        register(SocialOrdo.MIMETICUS);
        register(SocialOrdo.TERRITORIALIS);
        register(SocialOrdo.NOMADICUS);
        register(SocialOrdo.COLONIALIS);
        register(SocialOrdo.PACTUM);

        register(ElementFamilia.IGNIS);
        register(ElementFamilia.AQUA);
        register(ElementFamilia.ORDO);
        register(ElementFamilia.CHAOS);
        register(ElementFamilia.LUX);
        register(ElementFamilia.UMBRA);
        register(ElementFamilia.VITA);

        register(FormaSectio.SQUAMAE);
        register(FormaSectio.PELLIS);
        register(FormaSectio.PLUMAE);
        register(FormaSectio.PALPI);
        register(FormaSectio.GELATA);
        register(FormaSectio.MYCETA);
    }
}
