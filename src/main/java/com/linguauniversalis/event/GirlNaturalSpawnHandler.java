package com.linguauniversalis.event;

import com.linguauniversalis.core.behavior.SpawnRules;
import com.linguauniversalis.core.species.SpeciesProfile;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.registry.LUEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 自然生成接线（MC 侧，设计汇总 §12「生成」）。
 *
 * <p>规则：原版在「会刷猫的结构」（{@code #minecraft:cats_spawn_in}：村庄 / 沼泽小屋）中
 * <b>自然生成</b>一只猫时，按各物种的
 * {@link com.linguauniversalis.core.behavior.TemplateKeys#SPECIES_VILLAGE_CAT_CONVERSION}
 * 变量投掷（猫又 = 50%）——命中则取消这只猫的加入，并在下一 tick 原地生成对应的<b>野生</b>魔物娘。
 *
 * <p>实现要点：
 * <ul>
 *   <li>只在 {@link EntityJoinLevelEvent} 且 {@code loadedFromDisk() == false} 时判定，
 *       因此读档不会转换、刷怪蛋/指令/繁殖/刷怪笼都不会触发（那些 spawn reason 不是自然生成）；</li>
 *   <li>取消猫的加入后<b>延迟一 tick</b>再生成魔物娘，避免在实体入世界的路径里重入添加实体；</li>
 *   <li>生成走 {@link EventHooks#finalizeMobSpawn}，与其它模组一致地经过 FinalizeSpawnEvent；</li>
 *   <li>物种选择完全由物种变量驱动（注册顺序即匹配顺序），不硬编码物种 id。</li>
 * </ul>
 */
public final class GirlNaturalSpawnHandler {
    private GirlNaturalSpawnHandler() {
    }

    /** 参与转换的自然生成原因：世界自然刷新与区块生成（玩家行为/刷怪笼一律不算）。 */
    public static boolean isNaturalWorldSpawn(EntitySpawnReason reason) {
        return SpawnRules.isNaturalWorldSpawnFlag(
                reason == EntitySpawnReason.NATURAL,
                reason == EntitySpawnReason.CHUNK_GENERATION);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Cat cat)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        // 原版猫的 spawn type 由其 finalizeSpawn 写入；非自然生成直接跳过
        if (!isNaturalWorldSpawn(cat.getSpawnType())) {
            return;
        }

        BlockPos pos = cat.blockPosition();
        boolean inCatSpawnStructure = server.structureManager()
                .getStructureWithPieceAt(pos, StructureTags.CATS_SPAWN_IN)
                .isValid();
        if (!inCatSpawnStructure) {
            return;
        }

        // 按物种变量逐个判定（注册顺序即匹配顺序；未声明该变量的物种概率为 0 → 直接跳过）
        SpeciesProfile chosen = null;
        EntityType<MonsterGirlEntity> chosenType = null;
        for (SpeciesProfile profile : SpeciesRegistry.all()) {
            if (!SpawnRules.convertsFromCats(profile)) {
                continue;
            }
            if (!SpawnRules.convertsFromCat(profile, true, true, server.getRandom().nextDouble())) {
                continue;
            }
            EntityType<MonsterGirlEntity> type = LUEntities.typeForSpecies(profile.id());
            if (type == null) {
                continue;
            }
            chosen = profile;
            chosenType = type;
            break;
        }
        if (chosen == null) {
            return;
        }

        double x = cat.getX();
        double y = cat.getY();
        double z = cat.getZ();
        float yRot = cat.getYRot();
        EntityType<MonsterGirlEntity> girlType = chosenType;

        event.setCanceled(true); // 这只猫不再进入世界
        server.getServer().execute(() -> spawnGirl(server, girlType, x, y, z, yRot));
    }

    /** 在猫的位置生成一只野生魔物娘（下一 tick 执行）。 */
    private static void spawnGirl(ServerLevel server, EntityType<MonsterGirlEntity> type,
                                  double x, double y, double z, float yRot) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!server.isLoaded(pos)) {
            return; // 目标区块已卸载：放弃本次转换
        }
        MonsterGirlEntity girl = type.create(server, EntitySpawnReason.NATURAL);
        if (girl == null) {
            return;
        }
        girl.snapTo(x, y, z, yRot, 0.0F);
        EventHooks.finalizeMobSpawn(girl, server, server.getCurrentDifficultyAt(pos),
                EntitySpawnReason.NATURAL, null);
        if (!girl.isAlive()) {
            return; // 被 FinalizeSpawnEvent 取消
        }
        server.addFreshEntity(girl);
    }
}
