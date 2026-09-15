package com.linguauniversalis.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 绽放之刺方块实体：承载被吸收命缕的个体快照；周期性对重叠生物造成接触伤害。
 */
public class SpinaFlorensBlockEntity extends BlockEntity {
    private static final String TAG_SNAPSHOT = "Snapshot";
    private static final long DAMAGE_INTERVAL_TICKS = 10L;

    private String snapshot = "";

    public SpinaFlorensBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasSnapshot() {
        return snapshot != null && !snapshot.isEmpty();
    }

    public String snapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot == null ? "" : snapshot;
    }

    @Override
    public void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString(TAG_SNAPSHOT, snapshot);
    }

    @Override
    public void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        snapshot = in.getStringOr(TAG_SNAPSHOT, "");
    }

    /** 服务端 tick：每 10 tick 对与该植株碰撞的生物造成 10 点虚空（magic 真实伤害占位）伤害。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SpinaFlorensBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel server)) {
            return;
        }
        if (level.getGameTime() % DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }
        AABB area = new AABB(pos.getX() + 0.1, pos.getY(), pos.getZ() + 0.1,
                pos.getX() + 0.9, pos.getY() + 1.0, pos.getZ() + 0.9);
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive());
        for (LivingEntity victim : victims) {
            victim.hurtServer(server, level.damageSources().magic(), SpinaFlorensBlock.CONTACT_DAMAGE);
        }
    }
}
