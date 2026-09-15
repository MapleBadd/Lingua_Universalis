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
 * <p>行为模板（如领地巡游纲的算法）不属于分类学本身，见
 * {@code core/behavior} 的模板模块；分类学只负责"有哪些类目、各自叫什么"。
 */
public final class Taxonomy {
    private Taxonomy() {
    }

    /** 阶元位阶。位阶本身是固定六层；每一层内的类目可扩展。 */
    public enum Rank {
        DOMAIN("域"),
        KINGDOM("界"),
        CLASSIS("门"),
        ORDO("纲"),
        FAMILIA("目"),
        SECTIO("科");

        public final String zh;

        Rank(String zh) {
            this.zh = zh;
        }
    }

    /** 一个分类学类目（不可变值对象）。 */
    public static final class Taxon {
        private final Rank rank;
        private final String id;
        private final String latin;
        private final String zh;
        private final String element;

        Taxon(Rank rank, String id, String latin, String zh, String element) {
            this.rank = rank;
            this.id = id;
            this.latin = latin;
            this.zh = zh;
            this.element = element;
        }

        public Rank rank() {
            return rank;
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
            return rank == other.rank && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return 31 * rank.hashCode() + id.hashCode();
        }

        @Override
        public String toString() {
            return rank.name() + ":" + id;
        }
    }

    // ------------------------------------------------------------------ 注册表
    private static final Map<Rank, Map<String, Taxon>> REGISTRY = new EnumMap<>(Rank.class);

    static {
        for (Rank rank : Rank.values()) {
            REGISTRY.put(rank, new LinkedHashMap<>());
        }
    }

    /** 造一个类目（不注册；常用 {@link #register} 放入对应阶元表）。 */
    public static Taxon taxon(Rank rank, String id, String latin, String zh) {
        return new Taxon(rank, id, latin, zh, null);
    }

    /** 造一个目（带元素标签）。 */
    public static Taxon familia(String id, String latin, String zh, String element) {
        return new Taxon(Rank.FAMILIA, id, latin, zh, element);
    }

    /** 注册/替换一个类目；id 冲突时覆盖。 */
    public static void register(Taxon taxon) {
        if (taxon == null || taxon.id() == null || taxon.id().isEmpty()) {
            throw new IllegalArgumentException("taxon id required");
        }
        REGISTRY.get(taxon.rank()).put(taxon.id(), taxon);
    }

    /** 移除一个类目（供"减少类目"/试验）；返回是否曾存在。 */
    public static boolean unregister(Rank rank, String id) {
        return REGISTRY.get(rank).remove(id) != null;
    }

    public static boolean contains(Rank rank, String id) {
        return REGISTRY.get(rank).containsKey(id);
    }

    /** 取类目；不存在返回 null（模板层应提供安全默认）。 */
    public static Taxon get(Rank rank, String id) {
        return REGISTRY.get(rank).get(id);
    }

    /** 取类目；不存在抛异常（防拼写漂移）。 */
    public static Taxon require(Rank rank, String id) {
        Taxon taxon = REGISTRY.get(rank).get(id);
        if (taxon == null) {
            throw new IllegalArgumentException("unknown taxonomy " + rank + ":" + id);
        }
        return taxon;
    }

    public static Collection<Taxon> all(Rank rank) {
        return Collections.unmodifiableCollection(REGISTRY.get(rank).values());
    }

    public static int count(Rank rank) {
        return REGISTRY.get(rank).size();
    }

    // ------------------------------------------------------------------ 内置类目（旧枚举等价物）
    /** 域 Regnum —— 生命本质。 */
    public static final class Domain {
        public static final Taxon MATERIAL = taxon(Rank.DOMAIN, "material", "regnum materiale", "物质域");
        public static final Taxon ELEMENTAL = taxon(Rank.DOMAIN, "elemental", "regnum elementale", "元素域");
        public static final Taxon CHAOTIC = taxon(Rank.DOMAIN, "chaotic", "regnum chaoticum", "混沌域");

        private Domain() {
        }
    }

    /** 界 Phylum —— 基础构造。 */
    public static final class Kingdom {
        public static final Taxon CHORDATA = taxon(Rank.KINGDOM, "chordata", "phylum chordata", "脊索界");
        public static final Taxon CRUSTACEA = taxon(Rank.KINGDOM, "crustacea", "phylum crustacea", "外壳界");
        public static final Taxon AMORPHA = taxon(Rank.KINGDOM, "amorpha", "phylum amorpha", "流形界");
        public static final Taxon PHYTA = taxon(Rank.KINGDOM, "phyta", "phylum phyta", "植生界");

        private Kingdom() {
        }
    }

    /** 门 Classis —— 魔力运用方式。 */
    public static final class MagicClassis {
        public static final Taxon EXOSPIRA = taxon(Rank.CLASSIS, "exospira", "classis exospira", "外生息门");
        public static final Taxon ENDOPYRA = taxon(Rank.CLASSIS, "endopyra", "classis endopyra", "内燃门");
        public static final Taxon SYMPATHO = taxon(Rank.CLASSIS, "sympatho", "classis sympatho", "交感门");
        public static final Taxon ENTROPO = taxon(Rank.CLASSIS, "entropo", "classis entropo", "熵噬门");

        private MagicClassis() {
        }
    }

    /** 纲 Ordo —— 社会习性（行为模板挂在纲上，见 core/behavior）。 */
    public static final class SocialOrdo {
        public static final Taxon MIMETICUS = taxon(Rank.ORDO, "mimeticus", "ordo mimeticus", "拟态同化纲");
        public static final Taxon TERRITORIALIS = taxon(Rank.ORDO, "territorialis", "ordo territorialis", "领地巡游纲");
        public static final Taxon NOMADICUS = taxon(Rank.ORDO, "nomadicus", "ordo nomadicus", "独行自生纲");
        public static final Taxon COLONIALIS = taxon(Rank.ORDO, "colonialis", "ordo colonialis", "巢穴共生纲");
        public static final Taxon PACTUM = taxon(Rank.ORDO, "pactum", "ordo pactum", "游牧契约纲");

        private SocialOrdo() {
        }
    }

    /** 目 Familia —— 亲和的能量属性；每目绑定一个元素标签。 */
    public static final class ElementFamilia {
        public static final Taxon IGNIS = familia("ignis", "familia ignis", "炽焰目", "ignis");
        public static final Taxon AQUA = familia("aqua", "familia aqua", "深流目", "aqua");
        public static final Taxon ORDO = familia("ordo", "familia ordo", "规序目", "ordo");
        public static final Taxon CHAOS = familia("chaos", "familia chaos", "流变目", "chaos");
        public static final Taxon LUX = familia("lux", "familia lux", "辉耀目", "lux");
        public static final Taxon UMBRA = familia("umbra", "familia umbra", "幽暗目", "umbra");
        public static final Taxon VITA = familia("vita", "familia vita", "涣生目", "vita");

        private ElementFamilia() {
        }
    }

    /** 科 Sectio —— 外形特征。 */
    public static final class FormaSectio {
        public static final Taxon SQUAMAE = taxon(Rank.SECTIO, "squamae", "sectio squamae", "鳞科");
        public static final Taxon PELLIS = taxon(Rank.SECTIO, "pellis", "sectio pellis", "绒科");
        public static final Taxon PLUMAE = taxon(Rank.SECTIO, "plumae", "sectio plumae", "羽科");
        public static final Taxon PALPI = taxon(Rank.SECTIO, "palpi", "sectio palpi", "触肢科");
        public static final Taxon GELATA = taxon(Rank.SECTIO, "gelata", "sectio gelata", "胶质科");
        public static final Taxon MYCETA = taxon(Rank.SECTIO, "myceta", "sectio myceta", "菌丝科");

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
