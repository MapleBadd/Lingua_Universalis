package com.linguauniversalis.client;

import com.linguauniversalis.core.anim.CatFormRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.feline.AbstractFelineModel;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 猫形伪装渲染：<b>原版猫模型 + 原版全黑猫贴图 + 原版猫动画</b>（设计 §12「形态」）。
 *
 * <p>做法：复用原版 {@link AdultCatModel}（原版动画逻辑在模型内），并把实体的同步状态折算成
 * 原版 {@code CatRenderState} 的猫专属字段——
 * 通用部分（移动摆动/头部/受伤/缩放）由 {@link LivingEntityRenderer#extractRenderState} 从本体直接填充，
 * 猫专属部分（坐/趴/放松/贴图/项圈）由本类填充：
 * <ul>
 *   <li>坐姿：命令"待机"时坐下（{@link CatFormRules#useSittingPose}）；</li>
 *   <li>趴姿：休眠 / 以猫形陪玩家睡觉时趴下（{@link CatFormRules#useLiePose}），
 *       过渡步长沿用原版猫的数值（每 tick 推进，按 {@code tickCount} 节流，避免高帧率下加速）；</li>
 *   <li>贴图：原版全黑猫（{@code cat_all_black}），无项圈（非驯服猫外观）。</li>
 * </ul>
 *
 * <p>趴姿的旋转位移是原版 {@code CatRenderer#setupRotations} 的逻辑复刻（这里用 {@link CatRenderState}
 * 但不是原版 {@code CatRenderer}，需要自己套用）。
 */
public class CatFormRenderer extends LivingEntityRenderer<MonsterGirlEntity, CatRenderState,
        AbstractFelineModel<CatRenderState>> {
    private static final Identifier CAT_TEXTURE =
            Identifier.parse(CatFormRules.CAT_FORM_TEXTURE);

    /** 每只个体一份姿态进度（仅客户端；弱引用避免实体卸载后泄漏）。 */
    private final Map<MonsterGirlEntity, CatPose> poses = new WeakHashMap<>();

    public CatFormRenderer(EntityRendererProvider.Context context) {
        super(context, new AdultCatModel(context.bakeLayer(ModelLayers.CAT)),
                CatFormRules.CAT_FORM_SHADOW_RADIUS);
    }

    @Override
    public CatRenderState createRenderState() {
        return new CatRenderState();
    }

    @Override
    public Identifier getTextureLocation(CatRenderState state) {
        return CAT_TEXTURE;
    }

    @Override
    public void extractRenderState(MonsterGirlEntity girl, CatRenderState state, float partialTick) {
        // 通用部分（移动摆动、身体/头部朝向、受伤、缩放、隐身等）直接取本体
        super.extractRenderState(girl, state, partialTick);

        boolean dormant = girl.isDormantVisual();
        boolean sleepingWithOwner = girl.isSleepingWithOwnerVisual();
        boolean lying = CatFormRules.useLiePose(dormant, sleepingWithOwner);
        boolean sitting = CatFormRules.useSittingPose(dormant, girl.isStandingByVisual());

        CatPose pose = poses.computeIfAbsent(girl, key -> new CatPose());
        // 姿态过渡按游戏 tick 推进（渲染帧率无关）
        if (pose.lastTick != girl.tickCount) {
            pose.lastTick = girl.tickCount;
            pose.step(lying);
        }

        state.texture = CAT_TEXTURE;
        state.isCrouching = girl.isCrouching();
        state.isSprinting = girl.isSprinting();
        state.isSitting = sitting;
        state.lieDownAmount = pose.lie;
        state.lieDownAmountTail = pose.lieTail;
        state.relaxStateOneAmount = pose.relax;
        state.isLyingOnTopOfSleepingPlayer = sleepingWithOwner;
        state.collarColor = null; // 非驯服猫：无项圈
    }

    @Override
    protected void setupRotations(CatRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        // 复刻原版 CatRenderer#setupRotations：趴下时侧翻 90°
        float lieDownAmount = state.lieDownAmount;
        if (lieDownAmount > 0.0F) {
            poseStack.translate(0.4F * lieDownAmount, 0.15F * lieDownAmount, 0.1F * lieDownAmount);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(lieDownAmount, 0.0F, 90.0F)));
            if (state.isLyingOnTopOfSleepingPlayer) {
                poseStack.translate(0.15F * lieDownAmount, 0.0F, 0.0F);
            }
        }
    }

    /** 每只个体的猫形姿态进度（趴下/尾巴/放松）。 */
    private static final class CatPose {
        private float lie;
        private float lieTail;
        private float relax;
        private int lastTick = Integer.MIN_VALUE;

        private void step(boolean lying) {
            lie = CatFormRules.stepLieAmount(lie, lying);
            lieTail = CatFormRules.stepLieTailAmount(lieTail, lying);
            relax = CatFormRules.stepRelaxAmount(relax, lying);
        }
    }
}
