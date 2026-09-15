package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.item.FirstDraftItem;
import net.minecraft.resources.Identifier;

/**
 * 初稿的 GeckoLib 模型（书）。
 *
 * <p>资源约定与猫又一致（GeckoLib 只扫 {@code geckolib/models} 与 {@code geckolib/animations}，
 * 并把目录与后缀剥掉当缓存键）：
 * <ul>
 *   <li>{@code geckolib/models/first_draft.geo.json} → 键 {@code lingua_universalis:first_draft}</li>
 *   <li>{@code geckolib/animations/first_draft.animation.json} → 键 {@code lingua_universalis:first_draft}</li>
 *   <li>贴图 {@code textures/item/first_draft.png}（完整路径含后缀）</li>
 * </ul>
 */
public class FirstDraftGeoModel extends GeoModel<FirstDraftItem> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:first_draft");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:first_draft");
    private static final Identifier TEXTURE = Identifier.parse("lingua_universalis:textures/item/first_draft.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FirstDraftItem animatable) {
        return ANIMATION;
    }
}
