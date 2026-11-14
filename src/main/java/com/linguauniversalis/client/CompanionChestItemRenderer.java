package com.linguauniversalis.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoItemRenderer;
import com.linguauniversalis.item.CompanionChestItem;
import java.util.function.Consumer;

/** 伙伴宝箱物品渲染器（GeckoLib）：位置/大小由 {@code models/item/chest_of_companions.json} 的 display 段决定。 */
public class CompanionChestItemRenderer extends GeoItemRenderer<CompanionChestItem> {
    public CompanionChestItemRenderer() {
        super(new CompanionChestItemGeoModel());
    }

    public static void create(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final CompanionChestItemRenderer renderer = new CompanionChestItemRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return this.renderer;
            }
        });
    }
}