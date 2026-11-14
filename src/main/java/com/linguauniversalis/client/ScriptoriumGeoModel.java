package com.linguauniversalis.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.linguauniversalis.block.ScriptoriumBlockEntity;
import net.minecraft.resources.Identifier;

/**
 * 誊写台的 GeckoLib 模型。
 *
 * <p>用的是**合并模型** {@code geckolib/models/scriptorium_book.geo.json}：台子本体 + 书骨骼。
 * 书骨骼挂在 {@code BookLocator → book_root} 下（{@code book_root} 负责"再绕 X 轴 -140°"，
 * 所有书骨骼枢轴已按"定位器枢轴 − 书枢轴"平移 ⇒ 与定位器**枢轴重合**）。
 *
 * <p>书的显示/隐藏靠**动画**（没书时循环 {@code empty}，把书骨骼缩放为 0），
 * 而不是"换模型"—— 渲染状态里的方块坐标不可靠，靠它判断有没有书会偶发"书不显示"。
 *
 * <p>物品形态仍用只有台子的 {@code lingua_universalis:scriptorium}（见 {@link ScriptoriumItemGeoModel}）。
 */
public class ScriptoriumGeoModel extends GeoModel<ScriptoriumBlockEntity> {
    private static final Identifier MODEL = Identifier.parse("lingua_universalis:scriptorium_book");
    private static final Identifier ANIMATION = Identifier.parse("lingua_universalis:scriptorium_book");
    /**
     * 合并贴图：左边是台子（`誊写台.png`），右边是初稿（`初稿.png`），由脚本拼成 256×128。
     *
     * <p>为什么必须合并：一个 geo 模型只有**一张**贴图。书骨骼沿用台子的贴图时，
     * 书的 UV 会落到台子贴图的透明区域 ⇒ **书整块不可见**（这就是"台子上的书不显示"的真正原因）。
     * 合并后书的面 UV 在 U 方向 +128，正好指向右半张图。
     */
    private static final Identifier TEXTURE =
            Identifier.parse("lingua_universalis:textures/block/scriptorium_book.png");

    private static boolean loggedModel;

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        if (!loggedModel) {
            loggedModel = true;
            com.linguauniversalis.LinguaUniversalis.LOGGER.info(
                    "[scriptorium] rendering with model {}", MODEL);
        }
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ScriptoriumBlockEntity animatable) {
        return ANIMATION;
    }
}