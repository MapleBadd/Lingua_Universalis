package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.item.ScriptoriumItem;
import net.minecraft.resources.Identifier;

/** 誊写台在物品形态下的 GeckoLib 模型：与方块共用同一份 geo 与贴图。 */
public class ScriptoriumItemGeoModel extends GeoModel<ScriptoriumItem> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:scriptorium");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:scriptorium");
    private static final Identifier TEXTURE =
            Identifier.parse("lingua_universalis:textures/block/scriptorium.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ScriptoriumItem animatable) {
        return ANIMATION;
    }
}