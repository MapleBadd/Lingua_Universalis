package com.linguauniversalis.client;

import com.geckolib.renderer.GeoBlockRenderer;
import com.linguauniversalis.block.SpinaFlorensBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * 绽放之刺的方块渲染器（GeckoLib）。
 *
 * <p>方块本身的 vanilla 模型是**空模型**（只有 particle 贴图），实际外观完全由这里绘制的
 * {@code spina_florens.geo.json} 提供 —— 这样美术的骨骼模型能直接用，以后也能加动画。
 * 物品栏图标是 2D 贴图（{@code textures/item/spina_florens.png}，目前是占位图）。
 */
public class SpinaFlorensRenderer
        extends GeoBlockRenderer<SpinaFlorensBlockEntity, BlockEntityRenderState> {

    public SpinaFlorensRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new SpinaFlorensModel());
    }
}
