package com.linguauniversalis.registry;

import com.linguauniversalis.block.SpinaFlorensBlock;
import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import com.linguauniversalis.item.FilumFilumItem;
import com.linguauniversalis.item.PresentCaseItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 注册表门面：集中管理本模组的所有原版注册。
 *
 * <p>当前：创意标签 + 物品/方块占位（Phase 0/3 的地基；后续以自定义子类替换各注册项，
 * 方块/实体在对应阶段接入）。id 一律取 {@link LUIds} 常量，避免拼写漂移。
 */
public final class LURegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LUConstants.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LUConstants.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LUConstants.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LUConstants.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LUConstants.MODID);

    // ------------------------------------------------------------------ 数据组件
    /** 命缕内嵌个体快照（String）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> GIRL_SNAPSHOT =
            DATA_COMPONENTS.registerComponentType(LUIds.COMPONENT_GIRL_SNAPSHOT,
                    b -> b.persistent(Codec.STRING));
    /** 命缕充能标记（Boolean）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FILUM_CHARGED =
            DATA_COMPONENTS.registerComponentType(LUIds.COMPONENT_FILUM_CHARGED,
                    b -> b.persistent(Codec.BOOL));
    /** 礼物盒封装内容物品 id（String）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PRESENT_CONTENT =
            DATA_COMPONENTS.registerComponentType(LUIds.COMPONENT_PRESENT_CONTENT,
                    b -> b.persistent(Codec.STRING));

    // ------------------------------------------------------------------ 物品
    /**
     * 初稿（图鉴书；GeckoLib 模型 + 翻开/翻页/合书动画）。
     *
     * <p><b>必须走 {@code registerItem(name, factory, UnaryOperator&lt;Properties&gt;)}</b>：
     * 这个重载会先把注册 id 写进 Properties 再交给工厂；直接 {@code new Item.Properties()} 传进去
     * 会在启动注册阶段抛 {@code NullPointerException: Item id not set}（Item 构造时要靠 id 生成
     * 翻译键/描述 id，此时物品还没进注册表）。
     */
    public static final DeferredItem<com.linguauniversalis.item.FirstDraftItem> FIRST_DRAFT =
            ITEMS.registerItem(LUIds.ITEM_FIRST_DRAFT,
                    com.linguauniversalis.item.FirstDraftItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<FilumFilumItem> FATUM_FILUM =
            ITEMS.registerItem(LUIds.ITEM_FATUM_FILUM, FilumFilumItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> FATUM_PANNUS =
            ITEMS.registerSimpleItem(LUIds.ITEM_FATUM_PANNUS, p -> p.stacksTo(1));
    public static final DeferredItem<Item> SPECULUM_SCIENTIAE =
            ITEMS.registerSimpleItem(LUIds.ITEM_SPECULUM_SCIENTIAE, p -> p.stacksTo(1));
    public static final DeferredItem<PresentCaseItem> PRESENT_CASE =
            ITEMS.registerItem(LUIds.ITEM_PRESENT_CASE, PresentCaseItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> FIRST_AID_KIT =
            ITEMS.registerSimpleItem(LUIds.ITEM_FIRST_AID_KIT, p -> p.stacksTo(1));
    public static final DeferredItem<Item> PAPER_OF_VOW =
            ITEMS.registerSimpleItem(LUIds.ITEM_PAPER_OF_VOW, p -> p.stacksTo(1));
    public static final DeferredItem<Item> DEBUG_GIRL_REMOVER =
            ITEMS.registerSimpleItem(LUIds.ITEM_DEBUG_REMOVER, p -> p.stacksTo(1));
    public static final DeferredItem<Item> DEBUG_AFFECTION =
            ITEMS.registerSimpleItem(LUIds.ITEM_DEBUG_AFFECTION, p -> p.stacksTo(64));
    /** 调试：好感 +1 道具（细粒度调整用）。 */
    public static final DeferredItem<Item> DEBUG_AFFECTION_1 =
            ITEMS.registerSimpleItem(LUIds.ITEM_DEBUG_AFFECTION_1, p -> p.stacksTo(64));

    // ------------------------------------------------------------------ 刷怪蛋（生物）
    public static final DeferredItem<SpawnEggItem> ARAKNE_SPAWN_EGG =
            ITEMS.registerItem(LUIds.ITEM_ARAKNE_SPAWN_EGG, SpawnEggItem::new,
                    p -> p.spawnEgg(LUEntities.ARAKNE.get()));
    public static final DeferredItem<SpawnEggItem> NEKOMATA_SPAWN_EGG =
            ITEMS.registerItem(LUIds.ITEM_NEKOMATA_SPAWN_EGG, SpawnEggItem::new,
                    p -> p.spawnEgg(LUEntities.NEKOMATA.get()));

    // ------------------------------------------------------------------ 方块（占位）
    public static final DeferredBlock<Block> SCRIPTORIUM =
            BLOCKS.registerSimpleBlock(LUIds.BLOCK_SCRIPTORIUM);
    public static final DeferredItem<BlockItem> SCRIPTORIUM_ITEM =
            ITEMS.registerSimpleBlockItem(LUIds.BLOCK_SCRIPTORIUM, SCRIPTORIUM);

    /**
     * 伙伴宝箱（54 格共享收纳）：自定义方块 + 方块实体 + 容器界面。
     *
     * <p>注意与物品/方块注册同一个坑：必须用 `registerBlock(id, 工厂, Properties 操作符)` 这个重载
     * （它先写入注册 id 再交给工厂），自己 new Properties 会在启动注册阶段抛 `Block id not set`。
     * <b>模型暂时用原版末影箱</b>（资源里 parent 到 `minecraft:block/ender_chest`）。
     */
    public static final DeferredBlock<com.linguauniversalis.block.CompanionChestBlock> CHEST_OF_COMPANIONS =
            BLOCKS.registerBlock(LUIds.BLOCK_CHEST_OF_COMPANIONS,
                    com.linguauniversalis.block.CompanionChestBlock::new,
                    p -> p.strength(2.5F).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> CHEST_OF_COMPANIONS_ITEM =
            ITEMS.registerSimpleBlockItem(LUIds.BLOCK_CHEST_OF_COMPANIONS, CHEST_OF_COMPANIONS);

    /** 伙伴宝箱的方块实体（54 格容器）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<
            com.linguauniversalis.block.CompanionChestBlockEntity>> COMPANION_CHEST_BE =
            BLOCK_ENTITY_TYPES.register(LUIds.BLOCK_ENTITY_COMPANION_CHEST,
                    () -> new BlockEntityType<>(
                            (pos, state) -> new com.linguauniversalis.block.CompanionChestBlockEntity(pos, state),
                            java.util.Set.of(CHEST_OF_COMPANIONS.get())));

    public static final DeferredBlock<SpinaFlorensBlock> SPINA_FLORENS =
            BLOCKS.registerBlock(LUIds.BLOCK_SPINA_FLORENS, SpinaFlorensBlock::new,
                    java.util.function.UnaryOperator.identity());
    public static final DeferredItem<BlockItem> SPINA_FLORENS_ITEM =
            ITEMS.registerSimpleBlockItem(LUIds.BLOCK_SPINA_FLORENS, SPINA_FLORENS);

    /** 绽放之刺方块实体（承载仪式快照 + 接触伤害 tick）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpinaFlorensBlockEntity>> SPINA_FLORENS_BE =
            BLOCK_ENTITY_TYPES.register(LUIds.BLOCK_ENTITY_SPINA_FLORENS, LURegistries::createSpinaType);

    private static BlockEntityType<SpinaFlorensBlockEntity> createSpinaType() {
        return new BlockEntityType<>(
                (pos, state) -> new SpinaFlorensBlockEntity(SPINA_FLORENS_BE.get(), pos, state),
                java.util.Set.of(SPINA_FLORENS.get()));
    }

    public static final DeferredBlock<Block> CUBILE_ARANEAE =
            BLOCKS.registerBlock(LUIds.BLOCK_CUBILE_ARANEAE,
                    com.linguauniversalis.block.CubileAraneaeBlock::new,
                    java.util.function.UnaryOperator.identity());
    public static final DeferredItem<BlockItem> CUBILE_ARANEAE_ITEM =
            ITEMS.registerSimpleBlockItem(LUIds.BLOCK_CUBILE_ARANEAE, CUBILE_ARANEAE);

    /** 蜘蛛巢心方块实体（阿拉克涅"固定生成一只"的锚点）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<
            com.linguauniversalis.block.CubileAraneaeBlockEntity>> CUBILE_ARANEAE_BE =
            BLOCK_ENTITY_TYPES.register(LUIds.BLOCK_ENTITY_CUBILE_ARANEAE, LURegistries::createCubileType);

    private static BlockEntityType<com.linguauniversalis.block.CubileAraneaeBlockEntity> createCubileType() {
        return new BlockEntityType<>(
                (pos, state) -> new com.linguauniversalis.block.CubileAraneaeBlockEntity(
                        CUBILE_ARANEAE_BE.get(), pos, state),
                java.util.Set.of(CUBILE_ARANEAE.get()));
    }

    // ------------------------------------------------------------------ 世界生成（结构特性）
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.FEATURE, LUConstants.MODID);

    /** 阿拉克涅巢穴（程序化生成的深色橡木巨树 + 蛛丝巢；无 NBT 结构文件）。 */
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>,
            com.linguauniversalis.worldgen.ArakneNestFeature> ARAKNE_NEST =
            FEATURES.register(LUIds.FEATURE_ARAKNE_NEST,
                    () -> new com.linguauniversalis.worldgen.ArakneNestFeature(
                            net.minecraft.world.level.levelgen.feature.configurations
                                    .NoneFeatureConfiguration.CODEC));

    // ------------------------------------------------------------------ 容器菜单（GUI）
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENU_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, LUConstants.MODID);

    /**
     * 魔物娘 GUI（快捷栏 + 背包）：用 NeoForge 的 {@code IMenuTypeExtension} 携带
     * 「实体 id + 物种 id」——物种 id 决定槽位布局，客户端必须按同一个物种重建菜单。
     */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.linguauniversalis.menu.GirlInventoryMenu>>
            GIRL_INVENTORY_MENU = MENU_TYPES.register(LUIds.MENU_GIRL_INVENTORY,
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            com.linguauniversalis.menu.GirlInventoryMenu::fromNetwork));

    /** 伙伴宝箱界面菜单（携带方块坐标，客户端据此取本地方块实体）。 */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.linguauniversalis.menu.CompanionChestMenu>>
            COMPANION_CHEST_MENU = MENU_TYPES.register(LUIds.MENU_COMPANION_CHEST,
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            com.linguauniversalis.menu.CompanionChestMenu::fromNetwork));

    // ------------------------------------------------------------------ 创意标签
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lingua_universalis"))
                    .icon(() -> FIRST_DRAFT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(FIRST_DRAFT.get().getDefaultInstance());
                        output.accept(FATUM_FILUM.get().getDefaultInstance());
                        output.accept(FATUM_PANNUS.get().getDefaultInstance());
                        output.accept(SPECULUM_SCIENTIAE.get().getDefaultInstance());
                        output.accept(PRESENT_CASE.get().getDefaultInstance());
                        output.accept(FIRST_AID_KIT.get().getDefaultInstance());
                        output.accept(PAPER_OF_VOW.get().getDefaultInstance());
                        output.accept(DEBUG_GIRL_REMOVER.get().getDefaultInstance());
                        output.accept(DEBUG_AFFECTION.get().getDefaultInstance());
                        output.accept(DEBUG_AFFECTION_1.get().getDefaultInstance());
                        output.accept(ARAKNE_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(NEKOMATA_SPAWN_EGG.get().getDefaultInstance());
                        output.accept(SCRIPTORIUM_ITEM.get().getDefaultInstance());
                        output.accept(CHEST_OF_COMPANIONS_ITEM.get().getDefaultInstance());
                        output.accept(SPINA_FLORENS_ITEM.get().getDefaultInstance());
                        output.accept(CUBILE_ARANEAE_ITEM.get().getDefaultInstance());
                    })
                    .build());

    private LURegistries() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        FEATURES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
    }
}
