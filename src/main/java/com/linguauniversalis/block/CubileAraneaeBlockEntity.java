package com.linguauniversalis.block;

import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LUEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 蜘蛛巢心方块实体：阿拉克涅的"固定生成一只"锚点。
 *
 * <p>行为：
 * <ol>
 *   <li>区块加载后周期性检查巢心附近（半径 {@link #OCCUPANT_SCAN_RADIUS}）是否已有阿拉克涅；</li>
 *   <li>已有 → 标记为已占用（不重复生成）；</li>
 *   <li>没有 → 在巢心上方生成一只阿拉克涅（生成原因 {@code STRUCTURE}），设其不可自然消失，
 *       并让它<b>认领该巢心方块</b>成为领地圆心；</li>
 *   <li>该标记随方块实体持久化：<b>一个巢心只生成一只</b>，她死亡后不再补生成
 *       （复活走命缕/绽放之刺链路）。</li>
 * </ol>
 */
public class CubileAraneaeBlockEntity extends BlockEntity {
    private static final String TAG_OCCUPIED = "OccupantSpawned";
    /** 检查间隔（tick）。 */
    private static final long CHECK_INTERVAL_TICKS = 40L;
    /** 判定"已有住户"的扫描半径（格）。 */
    private static final double OCCUPANT_SCAN_RADIUS = 24.0;

    private boolean occupied;

    public CubileAraneaeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isOccupied() {
        return occupied;
    }

    @Override
    public void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean(TAG_OCCUPIED, occupied);
    }

    @Override
    public void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        occupied = in.getBooleanOr(TAG_OCCUPIED, false);
    }

    /** 服务端 tick：确认/生成巢穴住户（阿拉克涅）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CubileAraneaeBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)) {
            return;
        }
        if (be.occupied || server.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        AABB area = new AABB(pos).inflate(OCCUPANT_SCAN_RADIUS);
        List<MonsterGirlEntity> residents = server.getEntitiesOfClass(MonsterGirlEntity.class, area,
                e -> e.isAlive() && "arakne".equals(e.speciesId()));
        if (!residents.isEmpty()) {
            // 已有阿拉克涅（含玩家此前手动放置的个体）：直接认领，避免重复生成
            for (MonsterGirlEntity resident : residents) {
                resident.claimNest(pos);
            }
            be.occupied = true;
            be.setChanged();
            return;
        }
        MonsterGirlEntity girl = LUEntities.ARAKNE.get().create(server, EntitySpawnReason.STRUCTURE);
        if (girl == null) {
            return;
        }
        BlockPos spawnPos = findSpawnSpot(server, pos);
        girl.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                server.getRandom().nextFloat() * 360.0f, 0.0f);
        girl.setPersistenceRequired(); // 固定生成、区块卸载不刷掉
        girl.claimNest(pos);           // 认领巢心 → 领地/威胁以该方块为圆心
        server.addFreshEntity(girl);
        be.occupied = true;
        be.setChanged();
    }

    /** 在巢心方块上方（必要时向外找空位）挑一个可站立的生成点。 */
    private static BlockPos findSpawnSpot(ServerLevel server, BlockPos nestPos) {
        BlockPos[] candidates = new BlockPos[] {
                nestPos.above(),
                nestPos.above(2),
                nestPos.above().north(),
                nestPos.above().south(),
                nestPos.above().east(),
                nestPos.above().west()
        };
        for (BlockPos candidate : candidates) {
            if (server.getBlockState(candidate).isAir() && server.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return nestPos.above();
    }
}
