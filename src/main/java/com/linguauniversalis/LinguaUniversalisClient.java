package com.linguauniversalis;

import com.linguauniversalis.client.CompanionChestRenderer;
import com.linguauniversalis.client.CompanionChestScreen;
import com.linguauniversalis.client.GirlInventoryScreen;
import com.linguauniversalis.client.LuBoltRenderer;
import com.linguauniversalis.client.SpeciesRenderer;
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
        modEventBus.addListener(this::onAddClientReloadListeners);
    }

    /** 客户端资源重载：清掉渲染回退的"资源是否存在"缓存。 */
    private void onAddClientReloadListeners(
            net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        event.addListener(net.minecraft.resources.Identifier.parse("lingua_universalis:render_cache"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources ->
                        com.linguauniversalis.client.SpeciesGeoModel.clearResourceCache());
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(LURegistries.GIRL_INVENTORY_MENU.get(), GirlInventoryScreen::new);
        event.register(LURegistries.COMPANION_CHEST_MENU.get(), CompanionChestScreen::new);
        // 图鉴界面（P1 骨架）
        event.register(LURegistries.CODEX_MENU.get(),
                com.linguauniversalis.client.CodexScreen::new);
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 通用/阿拉克涅：占位人形渲染器（复用原版玩家模型 + 村民贴图；待各自美术资源就绪后替换）
        // 占位：草方块（原版人形占位太丑，已替换）
        event.registerEntityRenderer(LUEntities.MONSTER_GIRL.get(),
                com.linguauniversalis.client.PlaceholderGrassRenderer::new);
        event.registerEntityRenderer(LUEntities.ARAKNE.get(),
                com.linguauniversalis.client.PlaceholderGrassRenderer::new);
        // 有 GeckoLib 资源的物种：通用物种渲染器（资源按物种 id 推导，新物种无需写渲染类）
        event.registerEntityRenderer(LUEntities.NEKOMATA.get(),
                context -> new SpeciesRenderer(context, "nekomata"));
        // 能力弹道（蛛网/蛛丝拉拽/暗影箭）：占位复用原版箭贴图
        event.registerEntityRenderer(LUEntities.LU_BOLT.get(), LuBoltRenderer::new);
        // 伙伴宝箱：复用原版箱子渲染器，材质强制末影箱（占位外观）
        event.registerBlockEntityRenderer(LURegistries.COMPANION_CHEST_BE.get(), CompanionChestRenderer::new);
        // 绽放之刺：GeckoLib 模型（美术的 geo），方块本体用空模型
        event.registerBlockEntityRenderer(LURegistries.SPINA_FLORENS_BE.get(),
                com.linguauniversalis.client.SpinaFlorensRenderer::new);
        // 誊写台：GeckoLib 模型 + 台面上的初稿（BookLocator 定位）
        event.registerBlockEntityRenderer(LURegistries.MONSTER_BED_BE.get(),
                com.linguauniversalis.client.MonsterBedRenderer::new);
        event.registerBlockEntityRenderer(LURegistries.SCRIPTORIUM_BE.get(),
                com.linguauniversalis.client.ScriptoriumRenderer::new);
    }
}
