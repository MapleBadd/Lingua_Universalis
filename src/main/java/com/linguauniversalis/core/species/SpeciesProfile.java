package com.linguauniversalis.core.species;

import com.linguauniversalis.core.behavior.MoodLowTemplateRegistry;
import com.linguauniversalis.core.taxonomy.Taxonomy;

/**
 * 物种档案模板 —— 新增魔物娘 = 填一份档案数据 + 模型动画资源，不改核心逻辑。
 *
 * <p>字段口径对应《万象牧语-设计汇总.md》§6/§11/§12 及分类学模板（§13）。
 * 模板特性是"默认值"，物种档案未覆盖则默认采用模板效果。
 */
public final class SpeciesProfile {
    /** 生成方式：固定刷新（结构绑定、不刷掉）或自然刷新（可能自然消失）。 */
    public enum SpawnType {
        FIXED,
        NATURAL
    }

    /** 移动速度档（相对玩家）。 */
    public enum SpeedTier {
        EXTREME("极快速"),
        FAST("快速"),
        NORMAL("中速"),
        SLOW("慢速");

        public final String zh;

        SpeedTier(String zh) {
            this.zh = zh;
        }
    }

    private final String id;
    private final String zhName;
    private final String latinName;

    /** "无巢穴"哨兵：物种档案显式声明"设计阶段即无巢穴"（如猫又）。 */
    public static final String NEST_NONE = "none";

    private final Taxonomy.Taxon domain;
    private final Taxonomy.Taxon kingdom;
    private final Taxonomy.Taxon magicClassis;
    private final Taxonomy.Taxon socialOrdo;
    private final Taxonomy.Taxon familia;
    private final Taxonomy.Taxon sectio;

    private final SpawnType spawnType;
    /**
     * 【物种变量】巢穴方块 id（如 阿拉克涅：cubile_araneae）。
     * 无巢穴的物种用 {@link #NEST_NONE} 显式声明"无巢穴"；读取一律走 {@link #hasNest()}。
     */
    private final String nestBlockId;

    private final java.util.List<String> hotbarSlotNames;
    private final int backpackSize;

    private final float maxHealth;
    private final SpeedTier speedTier;
    private final int armorValue;
    private final int armorToughness;

    private final String moodLowTemplate;
    private final java.util.List<String> likedFoods;
    private final float meleeDamage;
    private final float rangedDamage;
    private final float undeadDamageMultiplier;
    private final float undeadDamageReduction;

    /** 模板参数（模板默认值的物种级覆盖；键见 core/behavior/TemplateKeys）。 */
    private final java.util.Map<String, Double> templateParams;
    /** 模板开关（同上，布尔覆盖）。 */
    private final java.util.Map<String, Boolean> templateFlags;

    private SpeciesProfile(Builder b) {
        this.id = b.id;
        this.zhName = b.zhName;
        this.latinName = b.latinName;
        this.domain = b.domain;
        this.kingdom = b.kingdom;
        this.magicClassis = b.magicClassis;
        this.socialOrdo = b.socialOrdo;
        this.familia = b.familia;
        this.sectio = b.sectio;
        this.spawnType = b.spawnType;
        this.nestBlockId = b.nestBlockId;
        this.hotbarSlotNames = java.util.List.copyOf(b.hotbarSlotNames);
        this.backpackSize = b.backpackSize;
        this.maxHealth = b.maxHealth;
        this.speedTier = b.speedTier;
        this.armorValue = b.armorValue;
        this.armorToughness = b.armorToughness;
        this.moodLowTemplate = b.moodLowTemplate;
        this.likedFoods = java.util.List.copyOf(b.likedFoods);
        this.meleeDamage = b.meleeDamage;
        this.rangedDamage = b.rangedDamage;
        this.undeadDamageMultiplier = b.undeadDamageMultiplier;
        this.undeadDamageReduction = b.undeadDamageReduction;
        this.templateParams = java.util.Map.copyOf(b.templateParams);
        this.templateFlags = java.util.Map.copyOf(b.templateFlags);
    }

    // ------------------------------------------------------------- getters
    public String id() {
        return id;
    }

    public String zhName() {
        return zhName;
    }

    public String latinName() {
        return latinName;
    }

    public Taxonomy.Taxon domain() {
        return domain;
    }

    public Taxonomy.Taxon kingdom() {
        return kingdom;
    }

    public Taxonomy.Taxon magicClassis() {
        return magicClassis;
    }

    public Taxonomy.Taxon socialOrdo() {
        return socialOrdo;
    }

    public Taxonomy.Taxon familia() {
        return familia;
    }

    /** 读取物种级模板参数（未设置返回 default）。 */
    public double templateParam(String key, double defaultValue) {
        Double v = templateParams.get(key);
        return v == null ? defaultValue : v;
    }

    /** 读取物种级模板开关（未设置返回 default）。 */
    public boolean templateFlag(String key, boolean defaultValue) {
        Boolean v = templateFlags.get(key);
        return v == null ? defaultValue : v;
    }

    /** 按"物种覆盖 > 分类学默认 > fallback"解析数值特性（键见 core/behavior/TemplateKeys）。 */
    public double featureNumber(String key, double fallback) {
        Double override = templateParams.get(key);
        if (override != null) {
            return override;
        }
        for (Taxonomy.Taxon t : taxonomyOrder()) {
            if (com.linguauniversalis.core.behavior.TaxonTemplates.isRegistered(t)) {
                double def = com.linguauniversalis.core.behavior.TaxonTemplates.numberDefault(t, key, Double.NaN);
                if (!Double.isNaN(def)) {
                    return def;
                }
            }
        }
        return fallback;
    }

    /** 按"物种覆盖 > 分类学默认 > fallback"解析布尔特性。 */
    public boolean featureFlag(String key, boolean fallback) {
        Boolean override = templateFlags.get(key);
        if (override != null) {
            return override;
        }
        for (Taxonomy.Taxon t : taxonomyOrder()) {
            if (com.linguauniversalis.core.behavior.TaxonTemplates.isRegistered(t)
                    && com.linguauniversalis.core.behavior.TaxonTemplates.of(t).hasFlag(key)) {
                return com.linguauniversalis.core.behavior.TaxonTemplates.flagDefault(t, key, fallback);
            }
        }
        return fallback;
    }

    /** 六阶元遍历顺序（从大阶元到小阶元；特异性逐级变高，故后查者优先）。 */
    private Taxonomy.Taxon[] taxonomyOrder() {
        return new Taxonomy.Taxon[]{domain, kingdom, magicClassis, socialOrdo, familia, sectio};
    }

    public Taxonomy.Taxon sectio() {
        return sectio;
    }

    public SpawnType spawnType() {
        return spawnType;
    }

    /**
     * 巢穴方块 id（物种变量）：该物种的巢穴方块（如阿拉克涅 = {@code cubile_araneae}）。
     * 设计阶段即无巢穴的物种应显式声明 {@link #nestBlockNone()}，取值 {@link #NEST_NONE}。
     */
    public String nestBlockId() {
        return nestBlockId;
    }

    /**
     * 该物种是否有巢穴方块（巢穴相关行为的总开关：认领/守巢/领地返回/无巢狂暴/巢穴增益）。
     *
     * <p>未声明（null/空）或显式声明"无巢穴"（{@link #NEST_NONE}）均返回 false ——
     * 因此没有巢穴的物种（如猫又）不会误触发任何巢穴逻辑与依赖巢穴的模板效果
     * （例如外生息门的"巢穴附近增益"）。
     */
    public boolean hasNest() {
        return nestBlockId != null && !nestBlockId.isEmpty() && !NEST_NONE.equals(nestBlockId);
    }

    public java.util.List<String> hotbarSlotNames() {
        return hotbarSlotNames;
    }

    public int backpackSize() {
        return backpackSize;
    }

    public float maxHealth() {
        return maxHealth;
    }

    public SpeedTier speedTier() {
        return speedTier;
    }

    public int armorValue() {
        return armorValue;
    }

    public int armorToughness() {
        return armorToughness;
    }

    public String moodLowTemplate() {
        return moodLowTemplate;
    }

    public java.util.List<String> likedFoods() {
        return likedFoods;
    }

    /** 该物品是否为物种喜好食物（按物品 id 精确匹配；未来支持标签/分类）。 */
    public boolean isLikedFood(String itemId) {
        return likedFoods.contains(itemId);
    }

    /** 基础近战面板伤害（0 = 未配置，后续按科模板/档案补充）。 */
    public float meleeDamage() {
        return meleeDamage;
    }

    /** 基础远程面板伤害（0 = 无远程数值型攻击，如阿拉克涅使用特殊技能）。 */
    public float rangedDamage() {
        return rangedDamage;
    }

    /** 对亡灵生物的伤害倍率（默认 1）。 */
    public float undeadDamageMultiplier() {
        return undeadDamageMultiplier;
    }

    /** 受到亡灵生物伤害的减免比例（0~1，默认 0）。 */
    public float undeadDamageReduction() {
        return undeadDamageReduction;
    }

    // ------------------------------------------------------------- builder
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String zhName = "";
        private String latinName = "";
        private Taxonomy.Taxon domain;
        private Taxonomy.Taxon kingdom;
        private Taxonomy.Taxon magicClassis;
        private Taxonomy.Taxon socialOrdo;
        private Taxonomy.Taxon familia;
        private Taxonomy.Taxon sectio;
        private SpawnType spawnType = SpawnType.NATURAL;
        private String nestBlockId;
        private final java.util.List<String> hotbarSlotNames = new java.util.ArrayList<>();
        private int backpackSize;
        private float maxHealth = 20f;
        private SpeedTier speedTier = SpeedTier.NORMAL;
        private int armorValue;
        private int armorToughness;
        private String moodLowTemplate = MoodLowTemplateRegistry.LOW_MOOD_RANDOM_ATTACK;
        private final java.util.List<String> likedFoods = new java.util.ArrayList<>();
        private float meleeDamage;
        private float rangedDamage;
        private float undeadDamageMultiplier = 1f;
        private float undeadDamageReduction;
        private final java.util.Map<String, Double> templateParams = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> templateFlags = new java.util.HashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String zh, String latin) {
            this.zhName = zh;
            this.latinName = latin;
            return this;
        }

        public Builder taxonomy(Taxonomy.Taxon domain, Taxonomy.Taxon kingdom,
                                Taxonomy.Taxon magicClassis, Taxonomy.Taxon socialOrdo,
                                Taxonomy.Taxon familia, Taxonomy.Taxon sectio) {
            this.domain = domain;
            this.kingdom = kingdom;
            this.magicClassis = magicClassis;
            this.socialOrdo = socialOrdo;
            this.familia = familia;
            this.sectio = sectio;
            return this;
        }

        /** 设置物种级模板参数（纲模板默认值的覆盖）。 */
        public Builder templateParam(String key, double value) {
            this.templateParams.put(key, value);
            return this;
        }

        /** 设置物种级模板开关（模板默认值的覆盖）。 */
        public Builder templateFlag(String key, boolean value) {
            this.templateFlags.put(key, value);
            return this;
        }

        public Builder spawn(SpawnType spawnType) {
            this.spawnType = spawnType;
            return this;
        }

        /**
         * 声明该物种的巢穴方块（同时视为"固定刷新型"物种）。
         * 传 null/空/{@link NEST_NONE} 等价于 {@link #nestBlockNone()}。
         */
        public Builder nestBlock(String nestBlockId) {
            if (nestBlockId == null || nestBlockId.isEmpty() || NEST_NONE.equals(nestBlockId)) {
                return nestBlockNone();
            }
            this.nestBlockId = nestBlockId;
            this.spawnType = SpawnType.FIXED;
            return this;
        }

        /**
         * 【物种变量】显式声明"该物种没有巢穴方块"（设计阶段即无巢，如猫又）。
         * 不改变刷新型；所有巢穴相关行为（认领/守巢/领地返回/无巢狂暴/巢穴增益）都会被关闭。
         */
        public Builder nestBlockNone() {
            this.nestBlockId = NEST_NONE;
            return this;
        }

        public Builder hotbar(String... slotNames) {
            this.hotbarSlotNames.addAll(java.util.Arrays.asList(slotNames));
            return this;
        }

        public Builder backpack(int size) {
            this.backpackSize = size;
            return this;
        }

        public Builder stats(float maxHealth, SpeedTier speedTier) {
            this.maxHealth = maxHealth;
            this.speedTier = speedTier;
            return this;
        }

        public Builder armor(int value, int toughness) {
            this.armorValue = value;
            this.armorToughness = toughness;
            return this;
        }

        public Builder moodLow(String templateId) {
            this.moodLowTemplate = templateId;
            return this;
        }

        public Builder likeFood(String... itemIds) {
            this.likedFoods.addAll(java.util.Arrays.asList(itemIds));
            return this;
        }

        /** 面板战斗数值（远程/亡灵倍率仅对数值型攻击生效；阿拉克涅的技能在 AI 层按档案实现）。 */
        public Builder combat(float meleeDamage, float rangedDamage,
                              float undeadDamageMultiplier, float undeadDamageReduction) {
            this.meleeDamage = meleeDamage;
            this.rangedDamage = rangedDamage;
            this.undeadDamageMultiplier = undeadDamageMultiplier;
            this.undeadDamageReduction = undeadDamageReduction;
            return this;
        }

        public SpeciesProfile build() {
            if (domain == null || kingdom == null || magicClassis == null
                    || socialOrdo == null || familia == null || sectio == null) {
                throw new IllegalStateException("SpeciesProfile '" + id + "' requires full taxonomy.");
            }
            return new SpeciesProfile(this);
        }
    }
}
