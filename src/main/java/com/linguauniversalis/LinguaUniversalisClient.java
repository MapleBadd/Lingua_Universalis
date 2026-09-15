package com.linguauniversalis;

import com.linguauniversalis.client.CompanionChestRenderer;
import com.linguauniversalis.client.CompanionChestScreen;
import com.linguauniversalis.client.GirlInventoryScreen;
import com.linguauniversalis.client.LuBoltRenderer;
import com.linguauniversalis.client.MonsterGirlRenderer;
import com.linguauniversalis.client.NekomataRenderer;
import com.linguauniversalis.registry.LUEntities;
import com.linguauniversalis.registry.LURegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端专用入口：通过 {@link EntityRenderersEvent.RegisterRenderers}（Mod 总线）
 * 注册实体渲染器：猫又用 GeckoLib 模型/动画，其余物种暂用占位人形渲染器；
 * 并通过 {@link RegisterMenuScreensEvent} 注册魔物娘 GUI（快捷栏 + 背包）屏幕。
 */
@Mod(value = LinguaUniversalis.MODID, dist = Dist.CLIENT)
public final class LinguaUniversalisClient {
    public LinguaUniversalisClient(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterRenderers);
        modEventBus.addListener(this::onRegisterMenuScreens);
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(LURegistries.GIRL_INVENTORY_MENU.get(), GirlInventoryScreen::new);
        event.register(LURegistries.COMPANION_CHEST_MENU.get(), CompanionChestScreen::new);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 通用/阿拉克涅：占位人形渲染器（复用原版玩家模型 + 村民贴图；待各自美术资源就绪后替换）
        event.registerEntityRenderer(LUEntities.MONSTER_GIRL.get(), MonsterGirlRenderer::new);
        event.registerEntityRenderer(LUEntities.ARAKNE.get(), MonsterGirlRenderer::new);
        // 猫又：GeckoLib 模型 + 动画（geo/animations/textures 资源）
        event.registerEntityRenderer(LUEntities.NEKOMATA.get(), NekomataRenderer::new);
        // 能力弹道（蛛网/蛛丝拉拽/暗影箭）：占位复用原版箭贴图
        event.registerEntityRenderer(LUEntities.LU_BOLT.get(), LuBoltRenderer::new);
        // 伙伴宝箱：复用原版箱子渲染器，材质强制末影箱（占位外观）
        event.registerBlockEntityRenderer(LURegistries.COMPANION_CHEST_BE.get(), CompanionChestRenderer::new);
    }
}
