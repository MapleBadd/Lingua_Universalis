package com.linguauniversalis.core.taxonomy;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个物种的**六个分类学标签**（每族一个）—— 这是分类学的最终形态：
 * 不是一棵树，而是"六族各挑一个标签"的六元组。
 *
 * <p>为什么做成值对象：图鉴、模板匹配、筛选、数据包校验都需要"读这六个标签"，
 * 而这些操作不该各自去 {@code SpeciesProfile} 上抓六个字段。把六元组收在一处之后：
 * <ul>
 *   <li>{@link #isComplete()} / {@link #missingFamilies()} 给出"缺哪族"；</li>
 *   <li>{@link #validate()} 抓"标签挂错族"（例如把形态标签填进本质槽）；</li>
 *   <li>{@link #byFamily(Taxonomy.Family)} 让渲染/筛选按下标循环，不用 switch。</li>
 * </ul>
 */
public record TaxonomyTagSet(
        Taxonomy.Taxon essence,
        Taxonomy.Taxon structure,
        Taxonomy.Taxon magic,
        Taxonomy.Taxon habit,
        Taxonomy.Taxon element,
        Taxonomy.Taxon form) {

    /** 按族取标签（缺则 null）。 */
    public Taxonomy.Taxon byFamily(Taxonomy.Family family) {
        return switch (family) {
            case ESSENCE -> essence;
            case STRUCTURE -> structure;
            case MAGIC -> magic;
            case HABIT -> habit;
            case ELEMENT -> element;
            case FORM -> form;
        };
    }

    /** 六族是否都填了。 */
    public boolean isComplete() {
        return missingFamilies().isEmpty();
    }

    /** 缺哪些族（按枚举顺序）。 */
    public List<Taxonomy.Family> missingFamilies() {
        List<Taxonomy.Family> missing = new ArrayList<>();
        for (Taxonomy.Family family : Taxonomy.Family.values()) {
            if (byFamily(family) == null) {
                missing.add(family);
            }
        }
        return missing;
    }

    /**
     * 校验：返回问题清单（空 = 合法）。
     *
     * <p>两类问题：**缺族**、**标签挂错族**（taxon.family() 与槽位不一致）。
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();
        for (Taxonomy.Family family : Taxonomy.Family.values()) {
            Taxonomy.Taxon taxon = byFamily(family);
            if (taxon == null) {
                issues.add("missing family " + family.id());
                continue;
            }
            if (taxon.family() != family) {
                issues.add("tag '" + taxon.id() + "' belongs to " + taxon.family().id()
                        + " but sits in " + family.id());
            }
        }
        return issues;
    }

    /** 显示用的六个标签中文名（按族顺序，跳过空位）。 */
    public List<String> zhList() {
        List<String> out = new ArrayList<>();
        for (Taxonomy.Family family : Taxonomy.Family.values()) {
            Taxonomy.Taxon taxon = byFamily(family);
            if (taxon != null) {
                out.add(taxon.zh());
            }
        }
        return out;
    }
}
