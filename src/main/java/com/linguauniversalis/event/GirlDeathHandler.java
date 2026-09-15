package com.linguauniversalis.event;

import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LUEntities;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 死亡处理：
 * 1) 魔物娘死亡 → 必掉一个携带个体快照的「命缕」；
 * 2) 绽放之刺击杀 ≥20 血生物 → 读取花朵内快照并原地复活对应魔物娘（移除花朵）。
 */
public final class GirlDeathHandler {
    private static final int SEARCH_RADIUS = 2;

    private GirlDeathHandler() {
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel server)) {
            return;
        }
        if (victim instanceof MonsterGirlEntity girl) {
            dropGirlInventory(girl);
            dropFilum(server, girl);
            return;
        }
        // 低落·随机攻击模板：她击杀生物 → 记录击杀时刻（低落 tick 中把心情抬回 30）
        if (event.getSource().getEntity() instanceof MonsterGirlEntity killer && killer.girlState().isDepressed()) {
            killer.lowMoodOnKill();
        }
        tryReviveByFlower(server, victim);
    }

    /**
     * 她真死时把**快捷栏 + 背包**里的东西撒在原地（倒地不算死，不掉）。
     *
     * <p>用原版 {@code Entity#spawnAtLocation}（自带随机散布与归属信息），与普通生物掉落一致。
     */
    private static void dropGirlInventory(MonsterGirlEntity girl) {
        ServerLevel server = (ServerLevel) girl.level();
        for (ItemStack stack : girl.girlInventory().drainContents()) {
            girl.spawnAtLocation(server, stack);
        }
    }

    private static void dropFilum(ServerLevel server, MonsterGirlEntity girl) {
        ItemStack filum = new ItemStack(LURegistries.FATUM_FILUM.get());
        filum.set(LURegistries.GIRL_SNAPSHOT.get(), girl.girlSnapshot());
        ItemEntity entity = new ItemEntity(server, girl.getX(), girl.getY() + 0.2, girl.getZ(), filum);
        entity.setUnlimitedLifetime();
        entity.setGlowingTag(true);
        server.addFreshEntity(entity);
    }

    /** 绽放之刺献祭复活：被杀生物生命上限 ≥20 且附近花朵存有快照 → 原地复活。 */
    private static void tryReviveByFlower(ServerLevel server, LivingEntity victim) {
        if (victim.getMaxHealth() < 20f) {
            return;
        }
        BlockPos victimPos = victim.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = victimPos.offset(dx, dy, dz);
                    BlockEntity be = server.getBlockEntity(pos);
                    if (be instanceof SpinaFlorensBlockEntity flower && flower.hasSnapshot()) {
                        MonsterGirlEntity girl = new MonsterGirlEntity(LUEntities.MONSTER_GIRL.get(), server);
                        if (girl.applySnapshot(flower.snapshot())) {
                            girl.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                            if (girl.getAttribute(Attributes.MAX_HEALTH) != null) {
                                girl.getAttribute(Attributes.MAX_HEALTH).setBaseValue(girl.profile().maxHealth());
                                girl.setHealth(girl.getMaxHealth());
                            }
                            server.addFreshEntity(girl);
                        }
                        server.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        return; // 每朵花只生效一次
                    }
                }
            }
        }
    }
}
