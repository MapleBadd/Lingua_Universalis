package com.linguauniversalis.core.worldgen;

/**
 * 阿拉克涅巢穴（深色橡木巨树 + 蛛丝巢）的<b>形状数学</b>（纯规则，无 MC 依赖；可冒烟测试）。
 *
 * <p>几何规格来自设计：大树总高约 {@link #TOTAL_HEIGHT}（含树冠）、树干约 {@link #TRUNK_HEIGHT}；
 * 树干顶端分岔处有一个内部空心的不规则球型蜘蛛巢（水平半径 {@link #NEST_RADIUS_XZ}、
 * 垂直半径 {@link #NEST_RADIUS_Y}，用羊毛模拟厚实蛛丝）；球体一侧开口约占总面积
 * {@link #ENTRANCE_AREA_FRACTION}（出入口）；入口反方向靠近巢壁处放蜘蛛巢心；
 * 巢内蜘蛛网从巢心向外密度递减，但巢心朝入口方向留一条通路；树干外表面刻一圈螺旋楼梯。
 *
 * <p>本类只提供判定/取点；具体摆放方块与随机抖动由 {@code ArakneNestFeature} 执行。
 */
public final class NestShapeRules {
    private NestShapeRules() {
    }

    // ------------------------------------------------------------------ 规格常量
    /** 树干高度（格）。 */
    public static final int TRUNK_HEIGHT = 15;
    /** 结构总高（含树冠，格）。 */
    public static final int TOTAL_HEIGHT = 25;
    /** 树干底部半径（格，最粗处）。 */
    public static final double TRUNK_RADIUS_BOTTOM = 2.6;
    /** 树干顶部半径（格，最细处）。 */
    public static final double TRUNK_RADIUS_TOP = 1.1;
    /** 巢心（球心）相对树干顶端的高度偏移。 */
    public static final int NEST_CENTER_Y_OFFSET = 1;
    /** 巢穴球体水平半径（格）。 */
    public static final double NEST_RADIUS_XZ = 5.0;
    /** 巢穴球体垂直半径（格）。 */
    public static final double NEST_RADIUS_Y = 3.5;
    /** 出入口占球面面积比例（0.20 = 20%）。 */
    public static final double ENTRANCE_AREA_FRACTION = 0.20;
    /** 巢壁厚度（格，球壳内外半径差）。 */
    public static final double NEST_WALL_THICKNESS = 1.8;
    /** 巢心朝入口方向留出的通路半径（格，通路内不放蜘蛛网以便直接点到巢心）。 */
    public static final double CORRIDOR_RADIUS = 1.45;
    /** 巢心距球心的距离（格）：稍靠近巢壁。 */
    public static final double HEART_FROM_CENTER = NEST_RADIUS_XZ * 0.72;
    /** 巢内蜘蛛网最大铺设距离（距巢心，格）；超出则不放。 */
    public static final double COBWEB_MAX_DISTANCE = 8.0;
    /** 巢心附近的蜘蛛网最大密度（0..1）与最远处密度。 */
    public static final double COBWEB_DENSITY_NEAR = 0.85;
    public static final double COBWEB_DENSITY_FAR = 0.10;
    /** 螺旋楼梯：每转一圈上升的格数（越小越陡；10 = 每格水平推进约 1 格高）。 */
    public static final int SPIRAL_BLOCKS_PER_TURN = 10;
    /** 螺旋凹陷的角宽（弧度）与深度（格）。 */
    public static final double SPIRAL_GROOVE_ANGLE = 0.55;
    public static final double SPIRAL_GROOVE_DEPTH = 1.0;
    /** 树干顶端分岔的树枝数量。 */
    public static final int BRANCH_COUNT = 4;

    // ------------------------------------------------------------------ 树干
    /** 树干在给定高度（0 = 根部）的半径：从下到上由粗变细（巴别塔式收分）。 */
    public static double trunkRadius(int y) {
        if (y <= 0) {
            return TRUNK_RADIUS_BOTTOM;
        }
        if (y >= TRUNK_HEIGHT) {
            return TRUNK_RADIUS_TOP;
        }
        double t = (double) y / TRUNK_HEIGHT;
        double eased = t * t * (3.0 - 2.0 * t); // smoothstep：底部收得慢、上部收得快
        return TRUNK_RADIUS_BOTTOM + (TRUNK_RADIUS_TOP - TRUNK_RADIUS_BOTTOM) * eased;
    }

    /** 水平点是否落在该高度树干的实心截面内（含半径内）。 */
    public static boolean inTrunk(double dx, double dz, int y) {
        double r = trunkRadius(y);
        return dx * dx + dz * dz <= r * r;
    }

    /** 该水平点是否在树干<b>外表面</b>一层（用于雕刻螺旋凹陷）。 */
    public static boolean onTrunkSurface(double dx, double dz, int y) {
        double r = trunkRadius(y);
        double d2 = dx * dx + dz * dz;
        double inner = r - SPIRAL_GROOVE_DEPTH;
        return d2 <= r * r && d2 > inner * inner;
    }

    /** 螺旋楼梯在给定高度的方位角（弧度）。 */
    public static double spiralAngle(int y) {
        return 2.0 * Math.PI * y / SPIRAL_BLOCKS_PER_TURN;
    }

    /** 该水平点在该高度是否属于螺旋凹陷（角宽内 + 在外表面一层）。 */
    public static boolean onSpiralGroove(double dx, double dz, int y) {
        if (!onTrunkSurface(dx, dz, y)) {
            return false;
        }
        double angle = Math.atan2(dz, dx);
        double diff = angleDifference(angle, spiralAngle(y));
        return Math.abs(diff) <= SPIRAL_GROOVE_ANGLE;
    }

    /** 归一到 [-π, π] 的角差。 */
    public static double angleDifference(double a, double b) {
        double d = a - b;
        while (d > Math.PI) {
            d -= 2.0 * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2.0 * Math.PI;
        }
        return d;
    }

    // ------------------------------------------------------------------ 巢穴球体
    /** 由出入口面积比例求"出入口锥"的余弦阈值：球冠面积比例 = (1 - cosθ) / 2。 */
    public static double entranceCosThreshold() {
        return 1.0 - 2.0 * ENTRANCE_AREA_FRACTION;
    }

    /** 归一化椭球距离：1 = 球面，<1 内部，>1 外部。 */
    public static double normalizedRadius(double dx, double dy, double dz) {
        double nx = dx / NEST_RADIUS_XZ;
        double ny = dy / NEST_RADIUS_Y;
        double nz = dz / NEST_RADIUS_XZ;
        return Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    /**
     * 是否属于巢壁（球壳）实体部分。
     *
     * @param jitter 0..1 的不规则抖动（由生成器确定性采样），使巢壁厚薄不均
     */
    public static boolean isNestWall(double dx, double dy, double dz, double jitter) {
        double n = normalizedRadius(dx, dy, dz);
        double thickness = NEST_WALL_THICKNESS * (0.65 + 0.7 * jitter);
        double outer = 1.0 + 0.06 * (jitter - 0.5);
        double inner = 1.0 - thickness / NEST_RADIUS_XZ;
        return n <= outer && n >= inner;
    }

    /** 是否落在出入口开口内（球面朝 entranceDir 的球冠，面积占比 = ENTRANCE_AREA_FRACTION）。 */
    public static boolean inEntranceOpening(double dx, double dy, double dz, double dirX, double dirZ) {
        double nx = dx / NEST_RADIUS_XZ;
        double ny = dy / NEST_RADIUS_Y;
        double nz = dz / NEST_RADIUS_XZ;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6) {
            return false;
        }
        double dot = (nx * dirX + nz * dirZ) / len;
        return dot >= entranceCosThreshold();
    }

    /** 巢心（蜘蛛巢心方块）相对球心的偏移：入口反方向、稍靠近巢壁。 */
    public static int[] heartOffset(double dirX, double dirZ) {
        return new int[] {
                (int) Math.round(-dirX * HEART_FROM_CENTER),
                0,
                (int) Math.round(-dirZ * HEART_FROM_CENTER)
        };
    }

    // ------------------------------------------------------------------ 蜘蛛网
    /** 蜘蛛网密度（0..1）：离巢心越近越密，超出最大距离为 0。 */
    public static double cobwebDensity(double distanceFromHeart) {
        if (distanceFromHeart >= COBWEB_MAX_DISTANCE) {
            return 0.0;
        }
        double t = distanceFromHeart / COBWEB_MAX_DISTANCE;
        return COBWEB_DENSITY_NEAR + (COBWEB_DENSITY_FAR - COBWEB_DENSITY_NEAR) * t;
    }

    /** 点到线段（巢心 → 出入口）的距离。 */
    public static double distanceToSegment(double px, double py, double pz,
                                           double ax, double ay, double az,
                                           double bx, double by, double bz) {
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double wx = px - ax;
        double wy = py - ay;
        double wz = pz - az;
        double vv = vx * vx + vy * vy + vz * vz;
        double t = vv < 1.0E-9 ? 0.0 : (wx * vx + wy * vy + wz * vz) / vv;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + vx * t;
        double cy = ay + vy * t;
        double cz = az + vz * t;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 是否位于"巢心 → 出入口"的通路内（通路内不放蜘蛛网，保证巢心可被直接点击）。 */
    public static boolean inHeartCorridor(double px, double py, double pz,
                                          double heartX, double heartY, double heartZ,
                                          double exitX, double exitY, double exitZ) {
        return distanceToSegment(px, py, pz, heartX, heartY, heartZ, exitX, exitY, exitZ) <= CORRIDOR_RADIUS;
    }

    /** 树冠是否应跳过该点（巢穴球体（含外壳 1 格外扩）内不长树叶，避免把巢埋住）。 */
    public static boolean skipLeafAt(double dx, double dy, double dz) {
        return normalizedRadius(dx, dy, dz) <= 1.0 + 1.0 / NEST_RADIUS_XZ;
    }
}
