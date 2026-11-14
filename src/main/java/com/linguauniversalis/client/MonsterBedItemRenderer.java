package com.linguauniversalis.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoItemRenderer;
import com.linguauniversalis.item.MonsterBedItem;
import java.util.function.Consumer;

/** 中魔物床物品渲染器（GeckoLib）：位置/大小由 {@code models/item/monster_bed.json} 的 display 段决定。 */
public class MonsterBedItemRenderer extends GeoItemRenderer<MonsterBedItem> {
    public MonsterBedItemRenderer() {
        super(new MonsterBedItemGeoModel());
    }

    public static void create(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final MonsterBedItemRenderer renderer = new MonsterBedItemRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return this.renderer;
            }
        });
    }
}