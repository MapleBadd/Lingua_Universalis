package com.linguauniversalis.registry;

import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体注册门面（Phase 2/4）。
 *
 * <p>注册三只实体：
 * <ul>
 *   <li>{@code monster_girl} —— 通用魔物娘（开发者用，默认挂载物种档案后即通用承载个体状态）；</li>
 *   <li>{@code arakne} —— 阿拉克涅（固定档案物种）；</li>
 *   <li>{@code nekomata} —— 猫又（固定档案物种）。</li>
 * </ul>
 * 所有实体共用 {@link MonsterGirlEntity} 类，物种差异由个体内的物种档案（SpeciesRegistry）
 * 决定；专用实体类型让刷怪蛋/自然生成能直接产出对应物种。
 */
public final class LUEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES =
            DeferredRegister.createEntities(LUConstants.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MonsterGirlEntity>> MONSTER_GIRL =
            ENTITY_TYPES.registerEntityType(LUIds.ENTITY_MONSTER_GIRL,
                    MonsterGirlEntity::new,
                    MobCategory.CREATURE,
                    b -> b.sized(0.6f, 1.8f).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<MonsterGirlEntity>> ARAKNE =
            ENTITY_TYPES.registerEntityType(LUIds.ENTITY_ARAKNE,
                    (type, level) -> MonsterGirlEntity.ofSpecies(type, level, "arakne"),
                    MobCategory.CREATURE,
                    b -> b.sized(0.6f, 1.8f).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<MonsterGirlEntity>> NEKOMATA =
            ENTITY_TYPES.registerEntityType(LUIds.ENTITY_NEKOMATA,
                    (type, level) -> MonsterGirlEntity.ofSpecies(type, level, "nekomata"),
                    MobCategory.CREATURE,
                    // 爬行姿态：0.6 宽 × 1.5 高（实际尺寸由 MonsterGirlEntity#getDefaultDimensions 按物种变量给出）
                    b -> b.sized(0.6f, 1.5f).clientTrackingRange(10));

    /** 能力弹道实体（蛛网/蛛丝拉拽/暗影箭；无重力直线飞行，视觉复用原版箭）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<com.linguauniversalis.entity.LuBoltEntity>> LU_BOLT =
            ENTITY_TYPES.registerEntityType(LUIds.ENTITY_LU_BOLT,
                    com.linguauniversalis.entity.LuBoltEntity::new,
                    MobCategory.MISC,
                    b -> b.sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20));

    private LUEntities() {
    }

    /**
     * 物种 id → 专用实体类型（自然生成/图鉴等按物种取类型时使用）。
     *
     * <p>新增物种时在此登记一行即可；未登记的物种返回 {@code null}（调用方自行决定回退，
     * 例如改用通用 {@link #MONSTER_GIRL}）。
     */
    public static EntityType<MonsterGirlEntity> typeForSpecies(String speciesId) {
        if (speciesId == null) {
            return null;
        }
        return switch (speciesId) {
            case "arakne" -> ARAKNE.get();
            case "nekomata" -> NEKOMATA.get();
            default -> null;
        };
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
