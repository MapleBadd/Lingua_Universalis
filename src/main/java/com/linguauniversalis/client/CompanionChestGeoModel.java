package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.block.CompanionChestBlockEntity;
import net.minecraft.resources.Identifier;

/**
 * 伙伴宝箱的 GeckoLib 模型：模型/贴图/动画都按固定 id 推导（美术新资源直接替换即可）。
 *
 * <p>动画只有一个 {@code open}（lid 从 0° 转到 -77.5°，0.1667 秒，停在最后一帧），
 * 开箱正向播、关箱**反向播**（由方块实体的控制器控制时间与速度）。
 */
public class CompanionChestGeoModel extends GeoModel<CompanionChestBlockEntity> {
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
    public Identifier getAnimationResource(CompanionChestBlockEntity animatable) {
        return ANIMATION;
    }
}