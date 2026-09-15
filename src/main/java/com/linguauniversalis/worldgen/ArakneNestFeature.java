package com.linguauniversalis.worldgen;

import com.linguauniversalis.core.worldgen.NestShapeRules;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * 阿拉克涅巢穴结构（程序化生成，不使用 NBT 结构文件；设计汇总 §11 生成）。
 *
 * <p>按设计描述建造：一棵<b>深色橡木巨树</b>（总高 {@link NestShapeRules#TOTAL_HEIGHT} 格、
 * 树干 {@link NestShapeRules#TRUNK_HEIGHT} 格、自下而上由粗变细），树干顶端分岔出数根树枝；
 * 分岔处有一个<b>内部空心的不规则球型蜘蛛巢</b>（羊毛模拟厚实蛛丝，水平半径 5 / 垂直半径 3.5），
 * 一侧留出占球面约 20% 的<b>出入口</b>；入口反方向靠近巢壁处安置<b>蜘蛛巢心方块</b>；
 * 巢内零散蜘蛛网、密度自巢心向外递减，但巢心朝入口方向留一条通路（可直接点击巢心）；
 * 树干外表面刻出<b>螺旋形凹陷</b>（绕树上升的螺旋楼梯，通到树顶巢穴）。
 *
 * <p>阿拉克涅本体不在生成时创建，而由<b>巢心方块实体</b>在区块加载后生成一只
 * （见 {@code CubileAraneaeBlockEntity}）：这样"固定生成一只、不被刷新"更稳，
 * 且不会在世界生成阶段创建实体。
 */
public class ArakneNestFeature extends Feature<NoneFeatureConfiguration> {

    /** 大树原木/木/树叶与巢材（羊毛模拟蛛丝）。 */
    private final BlockState logState = Blocks.DARK_OAK_LOG.defaultBlockState();
    private final BlockState woodState = Blocks.DARK_OAK_WOOD.defaultBlockState();
    private final BlockState leafState = Blocks.DARK_OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, true);
    private final BlockState silkState = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)
            .defaultBlockState();
    private final BlockState cobwebState = Blocks.COBWEB.defaultBlockState();

    public ArakneNestFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!level.ensureCanWrite(origin)) {
            return false;
        }

        // 出入口方位：与螺旋楼梯终点对齐（楼梯爬上去正好到开口）
        double entranceAngle = NestShapeRules.spiralAngle(NestShapeRules.TRUNK_HEIGHT);
        double dirX = Math.cos(entranceAngle);
        double dirZ = Math.sin(entranceAngle);

        buildTrunk(level, origin, random);
        buildBranches(level, origin, random);
        buildCanopy(level, origin, random);
        buildNest(level, origin, dirX, dirZ, random);
        return true;
    }

    // ------------------------------------------------------------------ 树干 + 螺旋楼梯
    private void buildTrunk(WorldGenLevel level, BlockPos origin, RandomSource random) {
        for (int y = -1; y < NestShapeRules.TRUNK_HEIGHT; y++) {
            double radius = NestShapeRules.trunkRadius(y);
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 > radius * radius) {
                        continue;
                    }
                    // 螺旋凹陷：外表面一层沿螺旋角挖空，形成绕树上升的楼梯
                    if (y >= 0 && NestShapeRules.onSpiralGroove(dx, dz, y)) {
                        setBlock(level, origin.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    // 底部一圈混入深色橡木原木/木头，看起来更自然
                    BlockState state = (y < 0 || (random.nextFloat() < 0.12f && d2 > (radius - 0.7) * (radius - 0.7)))
                            ? woodState : logState;
                    setBlock(level, origin.offset(dx, y, dz), state);
                }
            }
        }
        // 根部隆起：让巨树看起来扎进地里
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 <= 9.0 && d2 >= 4.0 && random.nextFloat() < 0.75f) {
                    setBlock(level, origin.offset(dx, -1, dz), woodState);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 树顶分岔
    private void buildBranches(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int branches = NestShapeRules.BRANCH_COUNT;
        double baseAngle = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < branches; i++) {
            double angle = baseAngle + (2.0 * Math.PI / branches) * i + (random.nextDouble() - 0.5) * 0.4;
            int length = 3 + random.nextInt(3);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            for (int step = 1; step <= length; step++) {
                int y = NestShapeRules.TRUNK_HEIGHT + (int) Math.round(step * 0.55);
                BlockPos pos = origin.offset((int) Math.round(dirX * step), y, (int) Math.round(dirZ * step));
                Direction.Axis axis = Math.abs(dirX) >= Math.abs(dirZ)
                        ? Direction.Axis.X : Direction.Axis.Z;
                setBlock(level, pos, logState.setValue(RotatedPillarBlock.AXIS, axis));
                int rx = (int) Math.round(dirX * (step + 1));
                int rz = (int) Math.round(dirZ * (step + 1));
                if (step == length) {
                    leafBlob(level, origin.offset(rx, y + 1, rz), 2 + random.nextInt(2), random);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 树冠
    private void buildCanopy(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int baseY = NestShapeRules.TRUNK_HEIGHT + 2;
        for (int dy = 0; dy <= NestShapeRules.TOTAL_HEIGHT - NestShapeRules.TRUNK_HEIGHT - 2; dy++) {
            double t = (double) dy / Math.max(1, NestShapeRules.TOTAL_HEIGHT - NestShapeRules.TRUNK_HEIGHT);
            double radius = 5.5 - 3.0 * t;
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 > radius * radius) {
                        continue;
                    }
                    // 巢穴所在处不长树叶（避免把巢埋住）
                    if (NestShapeRules.skipLeafAt(dx, dy + baseY - (NestShapeRules.TRUNK_HEIGHT
                            + NestShapeRules.NEST_CENTER_Y_OFFSET), dz)) {
                        continue;
                    }
                    if (d2 > (radius - 1.0) * (radius - 1.0) && random.nextFloat() < 0.35f) {
                        continue; // 边缘随机留空，轮廓更自然
                    }
                    setBlock(level, origin.offset(dx, baseY + dy, dz), leafState);
                }
            }
        }
    }

    private void leafBlob(WorldGenLevel level, BlockPos center, int radius, RandomSource random) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    if (random.nextFloat() < 0.25f) {
                        continue;
                    }
                    setBlock(level, center.offset(dx, dy, dz), leafState);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 蛛丝巢
    private void buildNest(WorldGenLevel level, BlockPos origin, double dirX, double dirZ, RandomSource random) {
        int centerY = NestShapeRules.TRUNK_HEIGHT + NestShapeRules.NEST_CENTER_Y_OFFSET;
        int rxz = (int) Math.ceil(NestShapeRules.NEST_RADIUS_XZ);
        int ry = (int) Math.ceil(NestShapeRules.NEST_RADIUS_Y);
        int[] heartOffset = NestShapeRules.heartOffset(dirX, dirZ);
        int heartX = heartOffset[0];
        int heartY = centerY + heartOffset[1];
        int heartZ = heartOffset[2];
        // 出入口外沿参考点（用于"巢心→出入口"通路）
        double exitX = dirX * NestShapeRules.NEST_RADIUS_XZ;
        double exitY = centerY;
        double exitZ = dirZ * NestShapeRules.NEST_RADIUS_XZ;

        for (int dy = -ry - 1; dy <= ry + 1; dy++) {
            for (int dx = -rxz - 1; dx <= rxz + 1; dx++) {
                for (int dz = -rxz - 1; dz <= rxz + 1; dz++) {
                    double jitter = hash01(dx, dy, dz);
                    double n = NestShapeRules.normalizedRadius(dx, dy, dz);
                    if (n > 1.15) {
                        continue;
                    }
                    boolean inOpening = NestShapeRules.inEntranceOpening(dx, dy, dz, dirX, dirZ);
                    BlockPos pos = origin.offset(dx, centerY + dy, dz);
                    if (NestShapeRules.isNestWall(dx, dy, dz, jitter)) {
                        if (inOpening) {
                            // 出入口：挖穿巢壁，形成开口
                            setBlock(level, pos, Blocks.AIR.defaultBlockState());
                        } else {
                            setBlock(level, pos, silkState);
                        }
                        continue;
                    }
                    // 巢穴内部：空气 + 蜘蛛网（密度自巢心向外递减，巢心朝入口留通路）
                    if (n >= 1.0) {
                        continue;
                    }
                    double distFromHeart = Math.sqrt(
                            (dx - heartX) * (dx - heartX)
                                    + (dy - (heartY - centerY)) * (dy - (heartY - centerY))
                                    + (dz - heartZ) * (dz - heartZ));
                    if (NestShapeRules.inHeartCorridor(dx, centerY + dy, dz,
                            heartX, heartY, heartZ, exitX, exitY, exitZ)) {
                        setBlock(level, pos, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    double density = NestShapeRules.cobwebDensity(distFromHeart);
                    if (density > 0.0 && random.nextDouble() < density) {
                        setBlock(level, pos, cobwebState);
                    } else {
                        setBlock(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // 巢心方块 + 其周围清空（保证能直接点到）
        BlockPos heartPos = origin.offset(heartX, heartY, heartZ);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setBlock(level, heartPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        setBlock(level, heartPos, LURegistries.CUBILE_ARANEAE.get().defaultBlockState());
        // 巢心上方的丝绸顶盖，避免从上方看穿（不遮挡入口通路）
        setBlock(level, heartPos.above(), silkState);
    }

    /** 位置哈希 → [0,1)：确定性"伪随机"，让巢壁厚薄不均（不消耗世界生成随机序列）。 */
    private static double hash01(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    private void setBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!level.ensureCanWrite(pos)) {
            return;
        }
        // UPDATE_CLIENTS：世界生成阶段只写方块，不触发连锁邻接更新
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
