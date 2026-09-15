package com.linguauniversalis.registry;

/**
 * 注册 id 常量表 —— 所有原版注册 id（物品/方块/实体等）集中于此，防止命名漂移。
 *
 * <p>id 规则：取《万象牧语-设计汇总.md》§16 英文名小写 + 下划线。
 */
public final class LUIds {
    private LUIds() {
    }

    // ------------------------------------------------------------------ 物品
    /** 初稿（图鉴入口书）。 */
    public static final String ITEM_FIRST_DRAFT = "first_draft";
    /** 命缕（死亡掉落、记录全档案、复活媒介）。 */
    public static final String ITEM_FATUM_FILUM = "fatum_filum";
    /** 命帛（收纳命缕的魔法布料）。 */
    public static final String ITEM_FATUM_PANNUS = "fatum_pannus";
    /** 百晓镜（观测魔物娘、录入图鉴）。 */
    public static final String ITEM_SPECULUM_SCIENTIAE = "speculum_scientiae";
    /** 礼物盒（送礼载体，+2 喜爱物好感）。 */
    public static final String ITEM_PRESENT_CASE = "present_case";
    /** 急救箱（战败后 10% 生命 + 6 饱食）。 */
    public static final String ITEM_FIRST_AID_KIT = "first_aid_kit";
    /** 誓约协议书（伙伴 + 好感 200 → 誓约）。 */
    public static final String ITEM_PAPER_OF_VOW = "paper_of_vow";
    /** 调试：魔物娘移除工具（创造模式右键瞬间移除）。 */
    public static final String ITEM_DEBUG_REMOVER = "debug_girl_remover";
    /** 调试：好感提升道具（右键 +10 好感，不设每日上限）。 */
    public static final String ITEM_DEBUG_AFFECTION = "debug_affection";
    /** 调试：好感提升道具小号（右键 +1 好感，不设每日上限）。 */
    public static final String ITEM_DEBUG_AFFECTION_1 = "debug_affection_1";
    /** 刷怪蛋：阿拉克涅。 */
    public static final String ITEM_ARAKNE_SPAWN_EGG = "arakne_spawn_egg";
    /** 刷怪蛋：猫又。 */
    public static final String ITEM_NEKOMATA_SPAWN_EGG = "nekomata_spawn_egg";

    // ------------------------------------------------------------------ 数据组件
    /** 命缕内嵌的个体快照（String：species|obey|Base64(GirlState)）。 */
    public static final String COMPONENT_GIRL_SNAPSHOT = "girl_snapshot";
    /** 命缕是否已被闪电充能（Boolean；未充能不可用于复活仪式）。 */
    public static final String COMPONENT_FILUM_CHARGED = "fatum_charged";
    /** 礼物盒内封装的内容物物品 id（String）。 */
    public static final String COMPONENT_PRESENT_CONTENT = "present_content";

    // ------------------------------------------------------------------ 方块实体
    public static final String BLOCK_ENTITY_SPINA_FLORENS = "spina_florens";
    /** 蜘蛛巢心方块实体（锚定生成阿拉克涅，保证"固定生成一只、不被刷新"）。 */
    public static final String BLOCK_ENTITY_CUBILE_ARANEAE = "cubile_araneae";
    /** 伙伴宝箱方块实体（54 格共享收纳）。 */
    public static final String BLOCK_ENTITY_COMPANION_CHEST = "chest_of_companions";

    // ------------------------------------------------------------------ 世界生成
    /** 阿拉克涅巢穴结构（程序化生成的深色橡木巨树 + 蛛丝巢，见 ArakneNestFeature）。 */
    public static final String FEATURE_ARAKNE_NEST = "arakne_nest";

    // ------------------------------------------------------------------ 方块（Phase 3/4 实现）    /** 誊写台：放置初稿并打开图鉴 UI。 */
    public static final String BLOCK_SCRIPTORIUM = "scriptorium";
    /** 伙伴宝箱：54 格共享收纳。 */
    public static final String BLOCK_CHEST_OF_COMPANIONS = "chest_of_companions";
    /** 绽放之刺：复活仪式植物。 */
    public static final String BLOCK_SPINA_FLORENS = "spina_florens";
    /** 蜘蛛巢心（阿拉克涅巢穴方块）。 */
    public static final String BLOCK_CUBILE_ARANEAE = "cubile_araneae";

    // ------------------------------------------------------------------ 实体（Phase 4 实现）
    public static final String ENTITY_MONSTER_GIRL = "monster_girl";
    public static final String ENTITY_ARAKNE = "arakne";
    public static final String ENTITY_NEKOMATA = "nekomata";
    /** 魔物娘能力弹道（蛛网/蛛丝拉拽/暗影箭；视觉复用原版箭贴图）。 */
    public static final String ENTITY_LU_BOLT = "lu_bolt";

    // ------------------------------------------------------------------ 容器菜单（GUI）
    /** 魔物娘 GUI：快捷栏 + 背包（手持初稿右键伙伴打开）。 */
    public static final String MENU_GIRL_INVENTORY = "girl_inventory";
    /** 伙伴宝箱界面（54 格共享收纳）。 */
    public static final String MENU_COMPANION_CHEST = "companion_chest";
}
