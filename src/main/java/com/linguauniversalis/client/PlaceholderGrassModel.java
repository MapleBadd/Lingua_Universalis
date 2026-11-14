package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.resources.Identifier;

/**
 * 占位模型：一个**草方块**（16×16×16 立方体 + 草顶/泥土侧面贴图）。
 *
 * <p>用途：还没有专属美术资源的物种先用它占位（之前用的是原版人形，太丑）。
 * 资源是 GeckoLib 格式，所以以后换成真正的物种模型时只要替换
 * {@code geckolib/models/<species>.geo.json} 即可，渲染器不用动。
 */
public class PlaceholderGrassModel extends GeoModel<MonsterGirlEntity> {
    private static final Identifier MODEL =
            Identifier.parse("lingua_universalis:placeholder_grass");
    private static final Identifier TEXTURE =
            Identifier.parse("minecraft:textures/block/grass_block_side.png");
    /** 空动画文件：声明了控制器会请求的动画名（无骨骼动作），避免 GeckoLib 查不到时 NPE。 */
    private static final Identifier ANIMATION =
            Identifier.parse("lingua_universalis:placeholder_grass");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MonsterGirlEntity animatable) {
        // 不能返回 null：GeckoLib 会拿它去查烘焙动画表，null 键会直接 NPE。
        return ANIMATION;
    }
}
