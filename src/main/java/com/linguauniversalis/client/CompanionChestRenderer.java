package com.linguauniversalis.client;

import com.geckolib.renderer.GeoBlockRenderer;
import com.linguauniversalis.block.CompanionChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 伙伴宝箱渲染器（GeckoLib）。
 *
 * <p>组合转发给 {@link GeoBlockRenderer}（不能直接继承：它要求渲染状态实现 GeckoLib 的
 * {@code GeoRenderState}，自定义子类过不了泛型边界 —— 与誊写台同一套写法）。
 * 朝向由 {@code FACING} 自动绕 Y 旋转；开合动画在 {@link CompanionChestBlockEntity} 的控制器里。
 */
public class CompanionChestRenderer
        implements BlockEntityRenderer<CompanionChestBlockEntity, BlockEntityRenderState> {

    private final GeoBlockRenderer<CompanionChestBlockEntity, BlockEntityRenderState> geo;

    public CompanionChestRenderer(BlockEntityRendererProvider.Context context) {
        this.geo = new GeoBlockRenderer<>(context, new CompanionChestGeoModel());
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return this.geo.createRenderState();
    }

    @Override
    public void extractRenderState(CompanionChestBlockEntity chest, BlockEntityRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        this.geo.extractRenderState(chest, state, partialTick, cameraPos, crumbling);
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        this.geo.submit(state, poseStack, collector, camera);
    }
}