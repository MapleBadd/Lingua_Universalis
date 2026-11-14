package com.linguauniversalis.core.species;

import java.util.List;

/**
 * 数据包物种的**自然生成规则**（纯数据）。
 *
 * <p>为什么不用原版 spawn placement：原版那套在 mod 初始化时就要注册好，而数据包物种是运行期
 * 才出现的。所以这里改成"我们自己的刷怪器"（见 {@code event/SpeciesSpawnHandler}）：
 * 服务端 tick 时按这份规则挑位置生成，完全不依赖原版刷怪表。
 *
 * @param biomes   允许生成的群系 id 列表（支持 {@code #tag} 形式；空 = 任意群系）
 * @param weight   相对权重（越大越常见）
 * @param group    一次生成多少只（1 = 单只）
 * @param minY     最低 y
 * @param maxY     最高 y
 * @param lightMax 最大光照（0 = 只在全黑，15 = 不限）
 */
public record SpawnRule(List<String> biomes, int weight, int group, int minY, int maxY, int lightMax) {

    /** 默认规则：任意群系、权重 10、单只、地表高度、不限光照。 */
    public static SpawnRule defaults() {
        return new SpawnRule(List.of(), 10, 1, 50, 200, 15);
    }

    /** 该群系是否允许生成（id 精确匹配或 tag 命中由调用方处理 tag）。 */
    public boolean allowsBiome(String biomeId) {
        if (biomes.isEmpty()) {
            return true;
        }
        return biomes.contains(biomeId);
    }

    /** y 是否在范围内。 */
    public boolean allowsY(int y) {
        return y >= minY && y <= maxY;
    }

    /** 光照是否允许。 */
    public boolean allowsLight(int light) {
        return light <= lightMax;
    }
}
