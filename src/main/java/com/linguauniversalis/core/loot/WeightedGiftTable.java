package com.linguauniversalis.core.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 带权重的礼物表（纯逻辑，可注入 {@link Random} 以便测试）。
 *
 * <p>口径：伙伴猫又睡醒礼物（设计汇总 §12）：
 * <ul>
 *   <li>基础表各 25%：腐肉 minecraft:rotten_flesh / 骨头 minecraft:bone /
 *       铁锭 minecraft:iron_ingot / 火药 minecraft:gunpowder；</li>
 *   <li>若当天偷到过村民绿宝石，则礼物必定为绿宝石（一次性；需再偷才能再送）。</li>
 * </ul>
 */
public final class WeightedGiftTable {
    public record GiftEntry(String itemId, double weight) {
    }

    private final List<GiftEntry> entries;
    private final double totalWeight;

    public WeightedGiftTable(List<GiftEntry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Gift table must not be empty");
        }
        double total = 0;
        for (GiftEntry e : entries) {
            if (e.weight() <= 0) {
                throw new IllegalArgumentException("Weight must be positive");
            }
            total += e.weight();
        }
        this.entries = List.copyOf(entries);
        this.totalWeight = total;
    }

    /** 投掷一次，返回命中的物品 id。 */
    public String roll(Random random) {
        double r = random.nextDouble() * totalWeight;
        for (GiftEntry e : entries) {
            r -= e.weight();
            if (r < 0) {
                return e.itemId();
            }
        }
        return entries.get(entries.size() - 1).itemId();
    }

    public List<GiftEntry> entries() {
        return entries;
    }

    /** 猫又基础礼物表（不含绿宝石；绿宝石走"偷村民"特例）。 */
    public static WeightedGiftTable nekomataGiftTable() {
        List<GiftEntry> list = new ArrayList<>();
        list.add(new GiftEntry("minecraft:rotten_flesh", 1));
        list.add(new GiftEntry("minecraft:bone", 1));
        list.add(new GiftEntry("minecraft:iron_ingot", 1));
        list.add(new GiftEntry("minecraft:gunpowder", 1));
        return new WeightedGiftTable(Collections.unmodifiableList(list));
    }

    /** 猫又绿宝石特例 id。 */
    public static final String EMERALD = "minecraft:emerald";
}
