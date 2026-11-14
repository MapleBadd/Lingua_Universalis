package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.item.CompanionChestItem;
import net.minecraft.resources.Identifier;

/** 伙伴宝箱物品形态的 GeckoLib 模型（与方块共用几何与贴图）。 */
public class CompanionChestItemGeoModel extends GeoModel<CompanionChestItem> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:chest_of_companions");
    private static final Identifier TEXTURE =
            Identifier.parse("lingua_universalis:textures/entity/chest_of_companions.png");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:chest_of_companions");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CompanionChestItem animatable) {
        return ANIMATION;
    }
}