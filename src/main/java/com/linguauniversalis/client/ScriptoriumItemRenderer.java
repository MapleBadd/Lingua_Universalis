package com.linguauniversalis.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoItemRenderer;
import com.linguauniversalis.item.ScriptoriumItem;
import java.util.function.Consumer;

/** 誊写台的物品渲染器（GeckoLib）：位置/大小由 {@code models/item/scriptorium.json} 的 display 段决定。 */
public class ScriptoriumItemRenderer extends GeoItemRenderer<ScriptoriumItem> {
    public ScriptoriumItemRenderer() {
        super(new ScriptoriumItemGeoModel());
    }

    public static void create(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final ScriptoriumItemRenderer renderer = new ScriptoriumItemRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return this.renderer;
            }
        });
    }
}