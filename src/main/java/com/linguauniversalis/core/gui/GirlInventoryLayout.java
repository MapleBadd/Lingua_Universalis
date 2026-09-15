package com.linguauniversalis.core.gui;

import com.linguauniversalis.core.behavior.InventoryRules;
import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 魔物娘 GUI（快捷栏 + 背包）的容器布局（纯计算，无 MC 依赖；可冒烟测试）。
 *
 * <p>面板宽固定 176（与原版容器一致），高随背包行数增长：
 * <pre>
 *   ┌ 标题
 *   ├ 快捷栏 标签 + 一格（槽位居中：2 格 = 主手/副手，4 格 = 阿拉克涅主副手+附肢1/2）
 *   ├ 背包 标签 + 每行最多 9 格
 *   ├ 玩家背包 标签 + 3 行 + 快捷栏行（原版 addStandardInventorySlots 的口径）
 *   └ 底边
 * </pre>
 *
 * <p>槽位坐标是**物品位置**；槽位底图（原版 18×18 槽位贴图）画在 {@code (x-1, y-1)}。
 */
public final class GirlInventoryLayout {
    private GirlInventoryLayout() {
    }

    /** 面板宽度（原版容器口径）。 */
    public static final int PANEL_WIDTH = 176;
    /** 槽位间距 / 槽位底图尺寸。 */
    public static final int SLOT_SIZE = 18;
    /** 每行最多槽位数（背包）。 */
    public static final int SLOTS_PER_ROW = 9;

    /** 标签左边距。 */
    public static final int LABEL_X = 8;
    /** 标题 y（原版默认 6）。 */
    public static final int TITLE_Y = 6;
    /** 「快捷栏」标签 y。 */
    public static final int HOTBAR_LABEL_Y = 18;
    /** 快捷栏那一行的槽位 y。 */
    public static final int HOTBAR_Y = 30;
    /** 「背包」标签 y。 */
    public static final int BACKPACK_LABEL_Y = 52;
    /** 背包第一行槽位 y。 */
    public static final int BACKPACK_Y = 64;
    /** 背包最后一行与「玩家背包」标签之间的间距。 */
    public static final int PLAYER_LABEL_GAP = 14;
    /** 标签与首行槽位的间距（原版 12）。 */
    public static final int LABEL_TO_SLOT = 12;
    /** 玩家背包区左边距（原版口径）。 */
    public static final int PLAYER_INV_X = 8;
    /** 玩家背包区高度：3 行 + 4 间隔 + 快捷栏行（原版 addStandardInventorySlots 口径）。 */
    public static final int PLAYER_INV_BLOCK_HEIGHT = 3 * SLOT_SIZE + 4 + SLOT_SIZE;
    /** 面板底部留白。 */
    public static final int BOTTOM_MARGIN = 8;

    /** 面板顶部原尺寸片段高度（含标题区与圆角上边框）。 */
    public static final int PANEL_TOP_HEIGHT = 18;
    /** 面板底部原尺寸片段高度（含下边框与投影）。 */
    public static final int PANEL_BOTTOM_HEIGHT = 7;

    /** 背包行数（0 格 = 0 行）。 */
    public static int backpackRows(int backpackSlots) {
        if (backpackSlots <= 0) {
            return 0;
        }
        return (backpackSlots + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
    }

    /** 背包某一行的格数（最后一行可能不满）。 */
    public static int countInRow(int backpackSlots, int row) {
        return Math.max(0, Math.min(SLOTS_PER_ROW, backpackSlots - row * SLOTS_PER_ROW));
    }

    /** 一行 countInRow 个槽位居中时的起始 x。 */
    private static int rowStartX(int countInRow) {
        return (PANEL_WIDTH - countInRow * SLOT_SIZE) / 2;
    }

    /** 快捷栏第 index 个槽位 x（整行居中）。 */
    public static int hotbarSlotX(int hotbarSlots, int index) {
        return rowStartX(hotbarSlots) + index * SLOT_SIZE;
    }

    /** 快捷栏第 index 个槽位 y。 */
    public static int hotbarSlotY(int index) {
        return HOTBAR_Y;
    }

    /** 背包第 index 个槽位所在的背包行（0 起）。 */
    public static int backpackRow(int index) {
        return index / SLOTS_PER_ROW;
    }

    /** 背包第 index 个槽位 x（按所在行居中）。 */
    public static int backpackSlotX(int backpackSlots, int index) {
        int row = backpackRow(index);
        return rowStartX(countInRow(backpackSlots, row)) + (index % SLOTS_PER_ROW) * SLOT_SIZE;
    }

    /** 背包第 index 个槽位 y。 */
    public static int backpackSlotY(int index) {
        return BACKPACK_Y + backpackRow(index) * SLOT_SIZE;
    }

    // ------------------------------------------------------------ 按档案的便捷重载
    /** 容器第 index 个槽位 x（快捷栏/背包自动分派）。 */
    public static int girlSlotX(SpeciesProfile profile, int index) {
        if (InventoryRules.isHotbarSlot(profile, index)) {
            return hotbarSlotX(InventoryRules.hotbarSlots(profile), index);
        }
        return backpackSlotX(InventoryRules.backpackSlots(profile),
                index - InventoryRules.hotbarSlots(profile));
    }

    /** 容器第 index 个槽位 y（快捷栏/背包自动分派）。 */
    public static int girlSlotY(SpeciesProfile profile, int index) {
        if (InventoryRules.isHotbarSlot(profile, index)) {
            return hotbarSlotY(index);
        }
        return backpackSlotY(index - InventoryRules.hotbarSlots(profile));
    }

    /** 「玩家背包」标签 y。 */
    public static int playerInventoryLabelY(int backpackSlots) {
        return BACKPACK_Y + backpackRows(backpackSlots) * SLOT_SIZE + PLAYER_LABEL_GAP;
    }

    /** 玩家背包首行槽位 y。 */
    public static int playerInventoryY(int backpackSlots) {
        return playerInventoryLabelY(backpackSlots) + LABEL_TO_SLOT;
    }

    /** 面板总高。 */
    public static int imageHeight(int backpackSlots) {
        return playerInventoryY(backpackSlots) + PLAYER_INV_BLOCK_HEIGHT + BOTTOM_MARGIN;
    }

    /** 面板中段（纵向拉伸填充）的高度：总高 - 顶部片段 - 底部片段。 */
    public static int panelMiddleHeight(int imageHeight) {
        return Math.max(0, imageHeight - PANEL_TOP_HEIGHT - PANEL_BOTTOM_HEIGHT);
    }
}
