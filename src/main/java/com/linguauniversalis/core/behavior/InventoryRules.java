package com.linguauniversalis.core.behavior;

import com.linguauniversalis.core.species.SpeciesProfile;

/**
 * 魔物娘的「快捷栏 + 背包」槽位规则（纯规则，无 MC 依赖；可冒烟测试）。
 *
 * <p>设计 §6：快捷栏是**模型可见**的少数槽位（手里抓着/嘴里叼着），槽位构成由**物种档案**指定
 * （{@link SpeciesProfile#hotbarSlotNames()}）：阿拉克涅 = 主手/副手/附肢1/附肢2，猫又 = 主手/副手；
 * 背包是不可见的纯存储格数（{@link SpeciesProfile#backpackSize()}）。
 *
 * <p>存储模型：两者共用一个**连续容器**，索引 {@code 0 .. 快捷栏数-1} 是快捷栏，
 * 其后是背包 —— 这样菜单/存档只需一个容器，槽位含义由本类换算。
 */
public final class InventoryRules {
    private InventoryRules() {
    }

    /** 快捷栏槽位名：主手。 */
    public static final String MAIN_HAND = "main_hand";
    /** 快捷栏槽位名：副手。 */
    public static final String OFF_HAND = "off_hand";
    /** 背包最大格数（GUI 每行 9 格 × 3 行）。 */
    public static final int MAX_BACKPACK_SLOTS = 27;
    /** 设计 §6：捡起的物品先留在手上，5 秒（100 tick）后放进背包。 */
    public static final int LIFT_TO_BACKPACK_DELAY_TICKS = 100;

    /** 快捷栏槽位数（物种档案里声明的命名槽个数）。 */
    public static int hotbarSlots(SpeciesProfile profile) {
        return profile == null ? 0 : profile.hotbarSlotNames().size();
    }

    /** 背包格数（夹在 0..{@link #MAX_BACKPACK_SLOTS} 之间，防止档案写错把 GUI 撑爆）。 */
    public static int backpackSlots(SpeciesProfile profile) {
        if (profile == null) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_BACKPACK_SLOTS, profile.backpackSize()));
    }

    /** 容器总格数（快捷栏 + 背包）。 */
    public static int totalSlots(SpeciesProfile profile) {
        return hotbarSlots(profile) + backpackSlots(profile);
    }

    /** 是否快捷栏槽位。 */
    public static boolean isHotbarSlot(SpeciesProfile profile, int index) {
        return index >= 0 && index < hotbarSlots(profile);
    }

    /** 是否背包槽位。 */
    public static boolean isBackpackSlot(SpeciesProfile profile, int index) {
        return index >= hotbarSlots(profile) && index < totalSlots(profile);
    }

    /**
     * 槽位名：快捷栏用档案里的名字（main_hand / off_hand / appendage1…），
     * 背包统一返回 {@code "backpack"}（无命名，纯存储）。
     */
    public static String slotName(SpeciesProfile profile, int index) {
        if (isHotbarSlot(profile, index)) {
            return profile.hotbarSlotNames().get(index);
        }
        return isBackpackSlot(profile, index) ? "backpack" : "";
    }

    /** 按名字找快捷栏槽位索引；找不到返回 -1。 */
    public static int indexOfHotbarSlot(SpeciesProfile profile, String name) {
        if (profile == null || name == null) {
            return -1;
        }
        return profile.hotbarSlotNames().indexOf(name);
    }

    /** 主手槽索引（没有主手槽的档案返回 -1）。 */
    public static int mainHandIndex(SpeciesProfile profile) {
        return indexOfHotbarSlot(profile, MAIN_HAND);
    }

    /** 副手槽索引（没有副手槽的档案返回 -1）。 */
    public static int offHandIndex(SpeciesProfile profile) {
        return indexOfHotbarSlot(profile, OFF_HAND);
    }
}
