package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.block.MonsterBedBlockEntity;
import net.minecraft.resources.Identifier;

/** 中魔物床的 GeckoLib 模型（静态，无动画）。 */
public class MonsterBedGeoModel extends GeoModel<MonsterBedBlockEntity> {
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
    public Identifier getAnimationResource(MonsterBedBlockEntity animatable) {
        return ANIMATION;
    }
}