package com.linguauniversalis.client;

import com.geckolib.renderer.GeoBlockRenderer;
import com.linguauniversalis.block.MonsterBedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 中魔物床的渲染器（GeckoLib）。
 *
 * <p>床占两格（foot / head），而 geo 是**整张床**的模型：这里只在 **foot** 那一半绘制，
 * 并按朝向把模型朝 head 方向推半格，让它正好跨在两格上；head 那一半不画，避免出现两张床。
 */
public class MonsterBedRenderer
        implements BlockEntityRenderer<MonsterBedBlockEntity, BlockEntityRenderState> {

    /** 模型从 foot 向 head 推开半格用的偏移量（格）。 */
    /**
     * 模型沿"foot → head"方向的额外位移（格）。
     *
     * <p>当前值 0：180° 旋转之后模型本身已经正好跨在两格上，
     * 不需要再加位移；正数 = 往床头偏，负数 = 往床尾偏。
     */
    private static final float HALF_BED_OFFSET = 0.0F;

    private final GeoBlockRenderer<MonsterBedBlockEntity, BlockEntityRenderState> geo;

    /** 每个渲染状态对应的处置：null = 不画；[dx,dz] = 绘制前的位移。 */
    private final Map<BlockEntityRenderState, float[]> placement = new WeakHashMap<>();

    public MonsterBedRenderer(BlockEntityRendererProvider.Context context) {
        this.geo = new GeoBlockRenderer<>(context, new MonsterBedGeoModel());
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return this.geo.createRenderState();
    }

    @Override
    public void extractRenderState(MonsterBedBlockEntity bed, BlockEntityRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        this.geo.extractRenderState(bed, state, partialTick, cameraPos, crumbling);

        // 方块状态在这里才拿得到（render state 的字段不可见），把处置结果记下来给 submit 用
        var blockState = bed.getBlockState();
        BedPart part = blockState.getOptionalValue(BedBlock.PART).orElse(null);
        if (part == BedPart.HEAD) {
            this.placement.put(state, null);          // head 半格不画
        } else {
            Direction toHead = BedBlock.getConnectedDirection(blockState);
            this.placement.put(state, new float[]{
                    toHead.getStepX() * HALF_BED_OFFSET,
                    toHead.getStepZ() * HALF_BED_OFFSET});
        }
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!this.placement.containsKey(state)) {
            return;
        }
        float[] offset = this.placement.get(state);
        if (offset == null) {
            return;                                    // head 半格：跳过
        }
        poseStack.pushPose();
        // 模型前后反了：先绕方块中心 Y 轴转 180°，再朝床头方向平移
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        poseStack.translate(offset[0], 0.0D, offset[1]);
        this.geo.submit(state, poseStack, collector, camera);
        poseStack.popPose();
    }
}