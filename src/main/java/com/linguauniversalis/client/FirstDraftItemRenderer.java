package com.linguauniversalis.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoItemRenderer;
import com.linguauniversalis.item.FirstDraftItem;
import java.util.function.Consumer;

/**
 * 初稿的物品渲染器（GeckoLib 的 {@link GeoItemRenderer}）。
 *
 * <p>当前**不做任何自定义**：不加动画阶段、不加渲染旋转，模型一律按默认（合着）姿态渲染，
 * 位置与大小完全由 {@code models/item/first_draft.json} 的 {@code display} 段决定
 * （手持两手的数值 = 原版普通物品的数值）。
 *
 * <p>保留这个类是因为 GeckoLib 的物品外观要靠它提供渲染器；以后要接动画时，
 * 在这里往渲染状态注入动画阶段即可（参考 {@code NekomataRenderer} 的做法）。
 */
public class FirstDraftItemRenderer extends GeoItemRenderer<FirstDraftItem> {
    public FirstDraftItemRenderer() {
        super(new FirstDraftGeoModel());
    }

    /** GeckoLib 取渲染器的入口（由 {@code FirstDraftItem#createGeoRenderer} 调用，仅客户端）。 */
    public static void create(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final FirstDraftItemRenderer renderer = new FirstDraftItemRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return this.renderer;
            }
        });
    }
}
