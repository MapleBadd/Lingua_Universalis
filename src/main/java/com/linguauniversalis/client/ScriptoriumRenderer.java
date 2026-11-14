package com.linguauniversalis.client;

import com.geckolib.renderer.GeoBlockRenderer;
import com.linguauniversalis.block.ScriptoriumBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 誊写台的方块渲染器（GeckoLib）。
 *
 * <p>只做一件事：把渲染转交给 GeckoLib 的方块渲染器。**台面上的书不再由这里单独绘制** ——
 * 书的骨骼已经并进合并模型 {@code geckolib/models/scriptorium_book.geo.json}
 * （挂在 {@code BookLocator → book_root} 下，含 -140° 的自转与枢轴重合），
 * 动画 {@code open → display} 由 {@link ScriptoriumBlockEntity} 的控制器驱动。
 *
 * <p>为什么组合而不是继承 {@code GeoBlockRenderer}：它的 {@code postRenderPass} 等钩子要求渲染状态
 * 实现 GeckoLib 的 {@code GeoRenderState}（只由 GeckoLib 的 mixin 注入），自定义子类泛型边界过不了。
 * 组合转发与 vanilla 完全对齐。
 *
 * <p>模型选择（有书 → 合并模型，没书 → 台子模型）在 {@link ScriptoriumGeoModel#getModelResource} 里；
 * 朝向由 {@code GeoBlockRenderer} 按 {@code FACING} 自动绕 Y 旋转。
 */
public class ScriptoriumRenderer implements BlockEntityRenderer<ScriptoriumBlockEntity, BlockEntityRenderState> {

    private final GeoBlockRenderer<ScriptoriumBlockEntity, BlockEntityRenderState> geo;

    public ScriptoriumRenderer(BlockEntityRendererProvider.Context context) {
        this.geo = new GeoBlockRenderer<>(context, new ScriptoriumGeoModel());
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return this.geo.createRenderState();
    }

    @Override
    public void extractRenderState(ScriptoriumBlockEntity blockEntity, BlockEntityRenderState renderState,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        this.geo.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
    }

    @Override
    public void submit(BlockEntityRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        this.geo.submit(renderState, poseStack, collector, cameraState);
    }
}