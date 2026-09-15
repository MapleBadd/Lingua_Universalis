package com.linguauniversalis;

import com.linguauniversalis.command.LUCommands;
import com.linguauniversalis.core.behavior.MoodLowTemplateRegistry;
import com.linguauniversalis.core.species.BuiltinSpecies;
import com.linguauniversalis.core.species.SpeciesRegistry;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.linguauniversalis.event.GirlDamageHandler;
import com.linguauniversalis.event.GirlDeathHandler;
import com.linguauniversalis.event.GirlFilumProtectionHandler;
import com.linguauniversalis.event.GirlLightningHandler;
import com.linguauniversalis.event.GirlNaturalSpawnHandler;
import com.linguauniversalis.registry.LUEntities;
import com.linguauniversalis.registry.LURegistries;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/**
 * 万象牧语（Lingua Universalis）主模组类。
 *
 * <p>阶段规划（对应《万象牧语-设计汇总.md》）：
 * <ul>
 *   <li>Phase 0/1/1.5：工程骨架、核心数据模型、纯规则层（已离线 100 项断言验证）；</li>
 *   <li>Phase 2/4-①（当前）：通用魔物娘实体骨架（注册/属性/状态持久化/占位渲染）；</li>
 *   <li>Phase 2：通用互动系统（摸头/投喂/送礼/命令，实体事件接入）；</li>
 *   <li>Phase 3：物品/方块自定义行为实现；</li>
 *   <li>Phase 4：物种专属 AI + GeckoLib 动画资源。</li>
 * </ul>
 */
@Mod(LinguaUniversalis.MODID)
public final class LinguaUniversalis {
    public static final String MODID = "lingua_universalis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LinguaUniversalis(IEventBus modEventBus) {
        LURegistries.register(modEventBus);
        LUEntities.register(modEventBus);

        // 纯数据注册表（不依赖 MC 注册事件，构造时即可安全初始化）
        com.linguauniversalis.core.taxonomy.Taxonomy.registerDefaults();
        com.linguauniversalis.core.behavior.TaxonTemplates.registerDefaults();
        com.linguauniversalis.core.behavior.OrdoTemplates.registerDefaults();
        BuiltinSpecies.registerAll();
        MoodLowTemplateRegistry.registerDefaults();

        // 本地可调设置（config/lingua_universalis.properties；不存在时生成一份带注释的默认文件）
        com.linguauniversalis.core.config.LuSettings.get().loadIfNeeded(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                        .resolve("lingua_universalis.properties"));

        // 实体属性注册（Mod 事件总线）
        modEventBus.addListener(LinguaUniversalis::onRegisterAttributes);

        // 游戏总线：命令注册（开发调试）
        NeoForge.EVENT_BUS.addListener(LinguaUniversalis::onRegisterCommands);
        // 游戏总线：魔物娘战败/受伤处理
        NeoForge.EVENT_BUS.addListener(GirlDamageHandler::onIncomingDamage);
        // 游戏总线：魔物娘死亡掉落命缕
        NeoForge.EVENT_BUS.addListener(GirlDeathHandler::onDeath);
        // 游戏总线：命缕被闪电充能
        NeoForge.EVENT_BUS.addListener(GirlLightningHandler::onStruck);
        // 游戏总线：命缕掉落物免疫爆炸/火焰等伤害
        NeoForge.EVENT_BUS.addListener(GirlFilumProtectionHandler::onInvulCheck);
        // 游戏总线：自然生成（原版自然刷猫 → 按物种变量转换为该物种的野生个体）
        NeoForge.EVENT_BUS.addListener(GirlNaturalSpawnHandler::onEntityJoinLevel);

        LOGGER.info("[Lingua Universalis] loaded. Species: {}", SpeciesRegistry.all().size());
    }

    private static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(LUEntities.MONSTER_GIRL.get(), MonsterGirlEntity.createAttributes().build());
        event.put(LUEntities.ARAKNE.get(), MonsterGirlEntity.createAttributes().build());
        event.put(LUEntities.NEKOMATA.get(), MonsterGirlEntity.createAttributes().build());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        LUCommands.register(event.getDispatcher());
    }
}
