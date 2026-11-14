package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import net.minecraft.resources.Identifier;

/**
 * 绽放之刺的 GeckoLib 模型（美术资源 {@code 绽放之刺.geo.json}）。
 *
 * <p>资源约定：模型/动画的 Identifier 是"剥掉目录+后缀的裸名字" {@code lingua_universalis:spina_florens}，
 * 贴图走常规路径 {@code textures/block/spina_florens.png}。
 * 目前没有动画文件（静态摆件）：{@link #getAnimationResource} 返回模型名占位，等有动画文件再指过去。
 */
public class SpinaFlorensModel extends GeoModel<SpinaFlorensBlockEntity> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:spina_florens");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:spina_florens");
    private static final Identifier TEXTURE =
            Identifier.parse("lingua_universalis:textures/block/spina_florens.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(SpinaFlorensBlockEntity animatable) {
        return ANIMATION;
    }
}
