package com.linguauniversalis.core.behavior;

/**
 * 游荡范围锚点（设计汇总 §2 游荡命令）。
 *
 * <p>口径：以切换为"游荡"那一瞬间所在位置（中心区块）为圆心，范围 = 中心区块及其
 * 周围 8 个区块，即 9×9 区块、共 144×144 格。被带离范围后应主动寻路返回（AI 层实现）。
 */
public final class WanderAnchor {
    /** 覆盖半宽：中心区块外各 4 个区块。 */
    public static final int HALF_CHUNKS = 4;

    private final int centerBlockX;
    private final int centerBlockZ;

    public WanderAnchor(int centerBlockX, int centerBlockZ) {
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
    }

    public int centerBlockX() {
        return centerBlockX;
    }

    public int centerBlockZ() {
        return centerBlockZ;
    }

    /** 目标 (x, z)（方块坐标）是否在游荡范围内。 */
    public boolean inWanderArea(int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        int baseCx = centerBlockX >> 4;
        int baseCz = centerBlockZ >> 4;
        return Math.abs(cx - baseCx) <= HALF_CHUNKS && Math.abs(cz - baseCz) <= HALF_CHUNKS;
    }
}
