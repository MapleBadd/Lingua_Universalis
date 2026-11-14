package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.item.MonsterBedItem;
import net.minecraft.resources.Identifier;

/** 中魔物床物品形态的 GeckoLib 模型（与方块共用几何与贴图）。 */
public class MonsterBedItemGeoModel extends GeoModel<MonsterBedItem> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:monster_bed");
    private static final Identifier TEXTURE =
            Identifier.parse("lingua_universalis:textures/entity/monster_bed.png");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:monster_bed");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MonsterBedItem animatable) {
        return ANIMATION;
    }
}
