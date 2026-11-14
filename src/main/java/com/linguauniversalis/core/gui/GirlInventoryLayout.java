package com.linguauniversalis.core.gui;

import com.linguauniversalis.core.behavior.InventoryRules;
import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 魔物娘 GUI（快捷栏 + 背包）的容器布局（纯计算，无 MC 依赖；可冒烟测试）。
 *
 * <p>坐标全部来自美术图 {@code textures/gui/girl_inventory.png}（176×220，逐像素量过）：
 * <pre>
 *   快捷栏     9 格：物品 y=35，格子左上 (7,34)，横向排布、**从左往右**依次是
 *              主手 / 副手 / 附肢1 / 附肢2 …（物种档案声明的槽位），其余格子显示"锁定"图标
 *   背包      3 行 × 9 格：物品 y=71 / 89 / 107
 *   玩家背包  3 行：物品 y=139 / 157 / 175
 *   玩家快捷栏 1 行：物品 y=197
 * </pre>
 *
 * <p>槽位间距 18（16 像素格 + 1 像素边框）；物品坐标 = 格子左上 + 1。
 */
public final class GirlInventoryLayout {
    private GirlInventoryLayout() {
    }

    /** 面板尺寸（与美术图一致）。 */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 220;
    /** 槽位间距。 */
    public static final int SLOT_SIZE = 18;
    /** 每行槽位数。 */
    public static final int SLOTS_PER_ROW = 9;
    /** 快捷栏最大格数（美术图第一行固定 9 格）。 */
    public static final int MAX_HOTBAR_SLOTS = 9;

    /** 格子左上角 x（第一列）。 */
    public static final int SLOT_CELL_X = 7;
    /** 快捷栏格子左上角 y。 */
    public static final int HOTBAR_CELL_Y = 34;
    /** 背包第一行格子左上角 y。 */
    public static final int BACKPACK_CELL_Y = 70;

    /** 物品位置相对格子的偏移（边框 1 像素）。 */
    private static final int ITEM_OFFSET = 1;

    /** 标题 y。 */
    public static final int TITLE_Y = 6;
    /** 玩家背包区左边距（原版口径）。 */
    public static final int PLAYER_INV_X = 8;
    /** 玩家背包首行物品 y。 */
    public static final int PLAYER_INV_Y = 139;
    /** 玩家背包标签 y。 */
    public static final int PLAYER_INV_LABEL_Y = 129;

    /** 「快捷栏」标签 y。 */
    public static final int HOTBAR_LABEL_Y = 20;
    /** 「背包」标签 y。 */
    public static final int BACKPACK_LABEL_Y = 56;
    /** 标签左边距。 */
    public static final int LABEL_X = 8;

    /** 背包行数（按实际格数向上取整，最多 3 行）。 */
    public static int backpackRows(int backpackSlots) {
        return Math.max(0, Math.min(3, (backpackSlots + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW));
    }

    /** 快捷栏格子左上角 x（第 index 格）。 */
    public static int hotbarCellX(int index) {
        return SLOT_CELL_X + index * SLOT_SIZE;
    }

    /** 快捷栏格子左上角 y。 */
    public static int hotbarCellY() {
        return HOTBAR_CELL_Y;
    }

    /** 背包格子左上角 x / y（index 相对背包首格）。 */
    public static int backpackCellX(int index) {
        return SLOT_CELL_X + (index % SLOTS_PER_ROW) * SLOT_SIZE;
    }

    public static int backpackCellY(int index) {
        return BACKPACK_CELL_Y + (index / SLOTS_PER_ROW) * SLOT_SIZE;
    }

    /** 快捷栏第 index 个**真实槽位**的物品 x / y（左侧对齐，从左往右排）。 */
    public static int hotbarSlotX(int index) {
        return hotbarCellX(index) + ITEM_OFFSET;
    }

    public static int hotbarSlotY(int index) {
        return hotbarCellY() + ITEM_OFFSET;
    }

    /** 背包第 index 个**真实槽位**的物品 x / y。 */
    public static int backpackSlotX(int index) {
        return backpackCellX(index) + ITEM_OFFSET;
    }

    public static int backpackSlotY(int index) {
        return backpackCellY(index) + ITEM_OFFSET;
    }

    /** 容器第 index 个槽位 x（快捷栏/背包自动分派）。 */
    public static int girlSlotX(SpeciesProfile profile, int index) {
        if (InventoryRules.isHotbarSlot(profile, index)) {
            return hotbarSlotX(index);
        }
        return backpackSlotX(index - InventoryRules.hotbarSlots(profile));
    }

    /** 容器第 index 个槽位 y（快捷栏/背包自动分派）。 */
    public static int girlSlotY(SpeciesProfile profile, int index) {
        if (InventoryRules.isHotbarSlot(profile, index)) {
            return hotbarSlotY(index);
        }
        return backpackSlotY(index - InventoryRules.hotbarSlots(profile));
    }

    /** 面板总高（固定，与美术图一致）。 */
    public static int imageHeight(int backpackSlots) {
        return PANEL_HEIGHT;
    }

    /** 玩家背包标签 y（固定）。 */
    public static int playerInventoryLabelY(int backpackSlots) {
        return PLAYER_INV_LABEL_Y;
    }

    /** 玩家背包首行物品 y（固定）。 */
    public static int playerInventoryY(int backpackSlots) {
        return PLAYER_INV_Y;
    }
}