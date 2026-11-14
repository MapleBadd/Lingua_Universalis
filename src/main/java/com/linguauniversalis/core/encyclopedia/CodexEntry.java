package com.linguauniversalis.core.encyclopedia;

import com.linguauniversalis.core.species.SpeciesProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 图鉴条目的<b>数据模型</b>（纯数据，无 MC 依赖）—— GUI 以后只是"渲染这张表"。
 *
 * <p>设计意图：把"图鉴里显示什么"和"界面怎么画"彻底分开。
 * <ul>
 *   <li>{@link Encyclopedia.Kind} 决定<b>哪一条解锁了</b>（目击 / 基础信息 / 分类学 / 敌意 / 偏好）；</li>
 *   <li>{@link Section} 决定这一条落在图鉴的<b>哪一页/哪一栏</b>；</li>
 *   <li>{@link Row} 就是最终一个字行：`标签键 → 值`，未解锁时值用 {@link #LOCKED} 占位。</li>
 * </ul>
 * 因此以后做 GUI（书页式 / 列表式 / 卷轴式都行）不需要再碰规则层，只要遍历
 * {@link #documentOf(SpeciesProfile, Set)} 返回的 {@link Section} 列表即可。
 *
 * <p>录入途径（见 {@link ObservationRules}）：目击自动记 {@code SIGHT}；百晓镜观测解锁
 * {@code BASIC_INFO} 与缩放类的 {@code TAXONOMY}/{@code HOSTILITY}；喜爱物互动解锁 {@code PREFERENCES}；
 * 誊写台以后负责把这些"看过的东西"誊成正式条目（本模型就是誊写台的输出格式）。
 */
public final class CodexEntry {
    private CodexEntry() {
    }

    /** 未解锁时显示的值。 */
    public static final String LOCKED = "???";

    /** 图鉴的版块（= GUI 的一页）。 */
    public enum Section {
        /** 封面：名字 / 拉丁名 / 一句话介绍。 */
        IDENTITY("identity"),
        /** 分类学位置（界门纲目科/元素）。 */
        TAXONOMY("taxonomy"),
        /** 生活习性：夜行性、体型、睡眠、社交纲。 */
        LIFESTYLE("lifestyle"),
        /** 战斗：血量/速度档/护甲/面板伤害。 */
        COMBAT("combat"),
        /** 敌意与红线（守巢/狂暴/同族敌意）。 */
        HOSTILITY("hostility"),
        /** 专属能力参数。 */
        ABILITIES("abilities"),
        /** 喜好食物与礼物。 */
        PREFERENCES("preferences"),
        /** 特殊行为（亡灵视野心情 / 偷窃 / 巢穴增益等，并非所有物种都有）。 */
        SPECIAL("special"),
        /** 情绪：低落时的具体行为。 */
        MOOD("mood");

        private final String id;

        Section(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 翻译键前缀：{@code gui.lingua_universalis.codex.section.<id>}。 */
        public String translationKey() {
            return "gui.lingua_universalis.codex.section." + id;
        }
    }

    /**
     * 一行条目。
     *
     * @param labelKey 标签翻译键（{@code gui.lingua_universalis.codex.row.<id>}）
     * @param value    值（已本地化的文本；未解锁时是 {@link #LOCKED}）
     * @param locked   是否尚未解锁（GUI 可以画成灰字/问号）
     */
    public record Row(String labelKey, String value, boolean locked, List<String> tags, String tooltip) {
        /** 无标签块、无提示的普通行。 */
        public Row(String labelKey, String value, boolean locked) {
            this(labelKey, value, locked, List.of(), null);
        }

        public Row(String labelKey, String value, boolean locked, List<String> tags) {
            this(labelKey, value, locked, tags, null);
        }

        /** 该行标签的翻译键。 */
        public String fullLabelKey() {
            return "gui.lingua_universalis.codex.row." + labelKey;
        }
    }

    /** 一页（一个版块 + 它的行）。 */
    public record Page(Section section, List<Row> rows) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    /** 一行条目的构造描述（内部用）。 */
    private record Spec(Section section, String labelId, Encyclopedia.Kind unlockedBy, String value,
                        List<String> tags, String tooltip) {
        Spec(Section section, String labelId, Encyclopedia.Kind unlockedBy, String value) {
            this(section, labelId, unlockedBy, value, List.of(), null);
        }

        Spec(Section section, String labelId, Encyclopedia.Kind unlockedBy, String value, List<String> tags) {
            this(section, labelId, unlockedBy, value, tags, null);
        }

        Spec withTooltip(String text) {
            return new Spec(section, labelId, unlockedBy, value, tags, text);
        }
    }

    /**
     * 为一套物种档案生成"整本图鉴"（已按版块分组、已按解锁状态打码）。
     *
     * @param profile 物种档案
     * @param known   该玩家在该物种上已解锁的条目集合（{@link Encyclopedia.PlayerEncyclopedia#knownKinds}）
     */
    public static List<Page> documentOf(SpeciesProfile profile, Set<Encyclopedia.Kind> known) {
        List<Spec> specs = specsOf(profile);
        List<Page> pages = new ArrayList<>();
        for (Section section : Section.values()) {
            List<Row> rows = new ArrayList<>();
            for (Spec spec : specs) {
                if (spec.section() != section) {
                    continue;
                }
                boolean unlocked = spec.unlockedBy() == null || (known != null && known.contains(spec.unlockedBy()));
                // 未解锁的条目**保留占位**：内容画成一个 `？？？` 标签块，
                // 能力小节连**条目名**一起遮住（能力名本身就是信息），其它小节保留条目名，
                // 这样玩家既能看到"还剩哪些条目"，又不会提前知道内容。
                if (unlocked) {
                    rows.add(new Row(spec.labelId(), spec.value(), false, spec.tags(), spec.tooltip()));
                } else {
                    String labelId = spec.section() == Section.ABILITIES ? "locked_ability" : spec.labelId();
                    rows.add(new Row(labelId, "", true, List.of(LOCKED), null));
                }
            }
            if (!rows.isEmpty()) {
                pages.add(new Page(section, List.copyOf(rows)));
            }
        }
        return pages;
    }

    /**
     * 生成方式的地点标签 → 语言键。
     *
     * <p>数据包/内置档案里写的仍是可以读的中文（村庄、洞穴…），这里映射成固定 key，
     * 由 GUI 按当前语言翻译；表里没有的标签原样返回（数据包自定义地点照常显示）。
     */
    private static String spawnTagKey(String raw) {
        return switch (raw) {
            case "村庄" -> "@codex.spawntag.village";
            case "洞穴" -> "@codex.spawntag.cave";
            case "森林" -> "@codex.spawntag.forest";
            case "平原" -> "@codex.spawntag.plains";
            case "山地" -> "@codex.spawntag.mountain";
            case "沙漠" -> "@codex.spawntag.desert";
            case "沼泽" -> "@codex.spawntag.swamp";
            case "海洋" -> "@codex.spawntag.ocean";
            case "地下" -> "@codex.spawntag.underground";
            case "下界" -> "@codex.spawntag.nether";
            case "末地" -> "@codex.spawntag.end";
            case "夜晚" -> "@codex.value.night";
            case "白天" -> "@codex.value.day";
            default -> raw;
        };
    }

    /** 快捷栏槽位名 → 中文。 */
    private static String humanSlot(String id) {
        return switch (id) {
            case "main_hand" -> "@codex.value.slot.main_hand";
            case "off_hand" -> "@codex.value.slot.off_hand";
            case "appendage1" -> "@codex.value.slot.appendage1";
            case "appendage2" -> "@codex.value.slot.appendage2";
            case "appendage3" -> "@codex.value.slot.appendage3";
            case "appendage4" -> "@codex.value.slot.appendage4";
            default -> id;
        };
    }

    /**
     * 喜爱食物 → **食物种类**（去重、保持稳定顺序），用于字块显示。
     *
     * <p>为什么按种类而不是逐个物品：一只物种的食谱可能列十几样东西，
     * 逐个枚举既占地方又不直观；归类成"鱼 / 兽肉 / 禽肉 / 果实 …"更符合图鉴的写法。
     */
    private static List<String> foodKindsOf(SpeciesProfile p) {
        List<String> kinds = new ArrayList<>();
        for (String itemId : p.likedFoods()) {
            String kind = foodKind(itemId);
            if (kind != null && !kinds.contains(kind)) {
                kinds.add(kind);
            }
        }
        if (kinds.isEmpty()) {
            kinds.add("@codex.value.none");
        }
        return kinds;
    }

    /** 单个物品 id → 食物种类（未知物品归到"其它"）。 */
    private static String foodKind(String itemId) {
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return switch (path) {
            case "cod", "salmon", "tropical_fish", "pufferfish", "cooked_cod", "cooked_salmon" -> "@codex.value.food.fish";
            case "beef", "porkchop", "mutton", "rabbit", "cooked_beef", "cooked_porkchop",
                 "cooked_mutton", "cooked_rabbit", "rotten_flesh" -> "@codex.value.food.meat";
            case "chicken", "cooked_chicken" -> "@codex.value.food.poultry";
            case "apple", "sweet_berries", "glow_berries", "melon_slice", "chorus_fruit" -> "@codex.value.food.fruit";
            case "carrot", "potato", "beetroot", "wheat", "bread", "cookie", "cake" -> "@codex.value.food.crop";
            case "emerald", "diamond", "gold_ingot", "iron_ingot", "lapis_lazuli", "amethyst_shard" -> "@codex.value.food.mineral";
            case "honey_bottle", "sugar", "pumpkin_pie" -> "@codex.value.food.sweet";
            default -> "@codex.value.food.other";
        };
    }

    private static String yesNo(boolean value) {
        return value ? "@codex.value.yes" : "@codex.value.no";
    }

    /** 会偷什么（鱼 / 绿宝石 / 都不偷）。 */
    private static String steals(SpeciesProfile p) {
        boolean fish = p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_STEALS_FISH, false);
        boolean emerald = p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_SPECIES_STEALS_EMERALDS, false);
        if (fish && emerald) {
            return "@codex.value.steal.fish_emerald";
        }
        if (fish) {
            return "@codex.value.steal.fish";
        }
        return emerald ? "@codex.value.steal.emerald" : "@codex.value.none";
    }

    /** 免疫/被动清单（暴击、摔落、穿甲限制）。 */
    private static String immunities(SpeciesProfile p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_KINGDOM_CRIT_IMMUNE, false)) {
            out.add("@codex.value.immune.crit");
        }
        if (p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_KINGDOM_FALL_IMMUNE, false)) {
            out.add("@codex.value.immune.fall");
        }
        if (p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_KINGDOM_NO_ARMOR, false)) {
            out.add("@codex.value.limit.no_armor");
        }
        return out.isEmpty() ? "@codex.value.none" : String.join("、", out);
    }

    /** 该物种的图鉴条目定义（顺序即显示顺序）。 */
    private static List<Spec> specsOf(SpeciesProfile p) {
        List<Spec> specs = new ArrayList<>();
        com.linguauniversalis.core.behavior.OrdoTemplates.OrdoTemplate ordo =
                com.linguauniversalis.core.behavior.OrdoTemplates.of(p);
        com.linguauniversalis.core.behavior.SleepRules.SizeClass size =
                com.linguauniversalis.core.behavior.SleepRules.sizeOf(p);

        // ---- 身份：目击即可见（全部转成人话，不再直接吐 id）
        // ---- 身份：名称/背包/快捷栏 需要基础观测；只有"生成方式"未解锁也显示
        specs.add(new Spec(Section.IDENTITY, "name", Encyclopedia.Kind.BASIC_INFO,
                "@codex.value.name|@species." + p.id() + ".name|@species." + p.id() + ".latin"));
        List<String> spawnTags = new ArrayList<>();
        for (String raw : p.spawnTags()) {
            spawnTags.add(spawnTagKey(raw));
        }
        spawnTags.add(com.linguauniversalis.core.behavior.SleepRules.nocturnal(p)
                ? "@codex.value.night" : "@codex.value.day");
        specs.add(new Spec(Section.IDENTITY, "spawn_type", null,
                String.join(" ", spawnTags), spawnTags));
        specs.add(new Spec(Section.IDENTITY, "backpack", Encyclopedia.Kind.BASIC_INFO, "@codex.value.slots|" + p.backpackSize()));
        specs.add(new Spec(Section.IDENTITY, "hotbar", Encyclopedia.Kind.BASIC_INFO,
                p.hotbarSlotNames().isEmpty() ? "@codex.value.none"
                : "@codex.value.hotbar|" + p.hotbarSlotNames().size() + "|"
                        + String.join("@codex.value.list_sep", p.hotbarSlotNames().stream().map(CodexEntry::humanSlot).toList())));

        // ---- 分类学：需要缩放观测
        specs.add(new Spec(Section.TAXONOMY, "domain", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.domain().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "kingdom", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.kingdom().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "classis", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.magicClassis().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "ordo", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.socialOrdo().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "familia", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.familia().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "sectio", Encyclopedia.Kind.TAXONOMY, "@taxonomy." + p.sectio().id() + ".name"));
        specs.add(new Spec(Section.TAXONOMY, "element", Encyclopedia.Kind.TAXONOMY,
                "@taxonomy." + p.familia().element() + ".name"));

        // ---- 行为 · 习性
        specs.add(new Spec(Section.LIFESTYLE, "activity", Encyclopedia.Kind.BASIC_INFO,
                com.linguauniversalis.core.behavior.SleepRules.nocturnal(p) ? "@codex.value.nocturnal" : "@codex.value.diurnal"));
        specs.add(new Spec(Section.LIFESTYLE, "size_class", Encyclopedia.Kind.BASIC_INFO, "@codex.value.size." + size.key()));
        specs.add(new Spec(Section.LIFESTYLE, "bed", Encyclopedia.Kind.BASIC_INFO, "@codex.value.size." + size.key()));
        specs.add(new Spec(Section.LIFESTYLE, "nest", Encyclopedia.Kind.BASIC_INFO,
                p.hasNest() ? "%block:" + p.nestBlockId() : "@codex.value.none"));

        // ---- 基础 · 战斗
        specs.add(new Spec(Section.COMBAT, "health", Encyclopedia.Kind.BASIC_INFO,
                String.valueOf(Math.round(p.maxHealth()))));
        specs.add(new Spec(Section.COMBAT, "speed", Encyclopedia.Kind.BASIC_INFO,
                "@codex.value.speed." + p.speedTier().name().toLowerCase(java.util.Locale.ROOT)));
        specs.add(new Spec(Section.COMBAT, "armor", Encyclopedia.Kind.BASIC_INFO,
                p.armorValue() + " / " + p.armorToughness()));
        specs.add(new Spec(Section.COMBAT, "melee", Encyclopedia.Kind.BASIC_INFO,
                String.valueOf(Math.round(p.meleeDamage()))));
        specs.add(new Spec(Section.COMBAT, "ranged", Encyclopedia.Kind.BASIC_INFO,
                String.valueOf(Math.round(p.rangedDamage()))));
        specs.add(new Spec(Section.COMBAT, "undead_damage", Encyclopedia.Kind.BASIC_INFO,
                String.format(java.util.Locale.ROOT, "x%.2f", p.undeadDamageMultiplier())));
        specs.add(new Spec(Section.COMBAT, "undead_reduction", Encyclopedia.Kind.BASIC_INFO,
                String.format(java.util.Locale.ROOT, "x%.2f", p.undeadDamageReduction())));
        specs.add(new Spec(Section.COMBAT, "immunities", Encyclopedia.Kind.BASIC_INFO, immunities(p)));

        // ---- 行为 · 敌意（同族敌意 / 狩猎 用标签块表示）
        specs.add(new Spec(Section.HOSTILITY, "nest_threat", Encyclopedia.Kind.HOSTILITY,
                String.valueOf(Math.round(ordo.effectiveThreat(p)))));
        specs.add(new Spec(Section.HOSTILITY, "territory", Encyclopedia.Kind.HOSTILITY,
                String.valueOf(Math.round(p.templateParam(
                        com.linguauniversalis.core.behavior.OrdoTemplates.KEY_TERRITORY_HORIZONTAL,
                        ordo.defaultTerritoryHorizontal())))));
        List<String> ownKind = new ArrayList<>();
        if (ordo.nestThreatAttacksOwnKind(p)) {
            ownKind.add("@codex.value.tag.nest");
        }
        if (ordo.enragedAttacksOwnKind(p)) {
            ownKind.add("@codex.value.tag.rage");
        }
        if (ownKind.isEmpty()) {
            ownKind.add("@codex.value.none");
        }
        specs.add(new Spec(Section.HOSTILITY, "own_kind", Encyclopedia.Kind.HOSTILITY,
                String.join(" ", ownKind), ownKind));
        specs.add(new Spec(Section.HOSTILITY, "passive_to_player", Encyclopedia.Kind.HOSTILITY,
                yesNo(ordo.passiveToHumanPlayers())));
        List<String> hunt = new ArrayList<>();
        if (ordo.wildHuntsUndead()) {
            hunt.add("@codex.value.tag.undead");
        }
        if (hunt.isEmpty()) {
            hunt.add("@codex.value.none");
        }
        specs.add(new Spec(Section.HOSTILITY, "hunt", Encyclopedia.Kind.HOSTILITY,
                String.join(" ", hunt), hunt));

        // ---- 基础 · 能力（只列她真会的能力，全无则一行"无"）
        // 能力：只显示条目名，数值不展示；鼠标悬浮看说明
        List<Spec> abilityRows = new ArrayList<>();
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.webCooldownTicks(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "web", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.web.desc"));
        }
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.silkCooldownTicks(p) > 0
                && com.linguauniversalis.core.behavior.SpeciesAbilities.silkDamage(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "silk", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.silk.desc"));
        }
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.poisonTicks(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "poison", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.poison.desc"));
        }
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.bleedRefreshTicks(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "bleed", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.bleed.desc"));
        }
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.shadowBoltDamage(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "shadow_bolt", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.shadow_bolt.desc"));
        }
        if (com.linguauniversalis.core.behavior.SpeciesAbilities.dodgeCooldownTicks(p) > 0) {
            abilityRows.add(new Spec(Section.ABILITIES, "dodge", Encyclopedia.Kind.PREFERENCES, "")
                    .withTooltip("@codex.ability.dodge.desc"));
        }
        if (abilityRows.isEmpty()) {
            specs.add(new Spec(Section.ABILITIES, "none", Encyclopedia.Kind.PREFERENCES, "@codex.value.none"));
        } else {
            specs.addAll(abilityRows);
        }

        // ---- 基础 · 喜好
        // 喜爱食物：只显示**食物种类**（字块），不再逐个枚举物品
        List<String> foodKinds = foodKindsOf(p);
        specs.add(new Spec(Section.PREFERENCES, "liked_food", Encyclopedia.Kind.PREFERENCES,
                String.join(" ", foodKinds), foodKinds));

        // ---- 行为 · 特殊行为（并非所有物种都有；全无则一行"无"）
        List<Spec> special = new ArrayList<>();
        if (p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_FAMILIA_UNDEAD_SIGHT_MOOD, false)) {
            special.add(new Spec(Section.SPECIAL, "undead_sight_mood", Encyclopedia.Kind.BASIC_INFO,
                    "@codex.value.yes_undead_mood"));
        }
        String steals = steals(p);
        if (!"@codex.value.none".equals(steals)) {
            special.add(new Spec(Section.SPECIAL, "steals", Encyclopedia.Kind.BASIC_INFO, steals));
        }
        if (p.templateFlag(com.linguauniversalis.core.behavior.TemplateKeys.FLAG_CLASSIS_NEST_BUFF, false)) {
            special.add(new Spec(Section.SPECIAL, "nest_buff", Encyclopedia.Kind.BASIC_INFO,
                    "@codex.value.yes_nest_buff"));
        }
        if (com.linguauniversalis.core.behavior.DisguiseRules.hasDisguise(p)) {
            special.add(new Spec(Section.SPECIAL, "disguise", Encyclopedia.Kind.PREFERENCES, "@codex.value.disguise"));
        }
        if (special.isEmpty()) {
            specs.add(new Spec(Section.SPECIAL, "none", Encyclopedia.Kind.BASIC_INFO, "@codex.value.none"));
        } else {
            specs.addAll(special);
        }

        // ---- 行为 · 情绪（低落时的具体行为；原来挤在"身份"里的模板条目搬到这里）
        String lowMood = p.moodLowTemplate();
        if (lowMood == null || lowMood.isEmpty()) {
            specs.add(new Spec(Section.MOOD, "low_mood_none", Encyclopedia.Kind.BASIC_INFO, "@codex.value.none"));
        } else {
            // 防御：数据包可能给出未注册的模板 id，这里不能抛异常（否则界面直接崩）
            String label = com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.contains(lowMood)
                    ? com.linguauniversalis.core.behavior.MoodLowTemplateRegistry.displayName(lowMood)
                    : lowMood;
            specs.add(new Spec(Section.MOOD, "low_mood", Encyclopedia.Kind.BASIC_INFO,
                    "@codex.value.mood_low." + lowMood.replace("low_mood_", ""))
                    .withTooltip("@codex.value.low_mood_tip|" + (label == null ? lowMood : label)));
        }
        return List.copyOf(specs);
    }
}
