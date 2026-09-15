package com.linguauniversalis.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/**
 * 魔物娘形态模型：把<b>原版玩家模型（人形）</b>与<b>原版猫模型烘焙层（伪装形态）</b>
 * 组装为同一模型的两个孩子，按渲染状态切换可见性。
 *
 * <p>为什么这样组合：26.2 的渲染状态类型是固定的（人形模型要求 HumanoidRenderState、
 * 猫模型要求 FelineRenderState），同一个实体无法在两个模型类之间切换；而
 * {@link ModelPart} 可组合、且渲染时会跳过 {@code visible=false} 的分支，
 * 因此"两个烘焙层 + 可见性切换"即可在单一实体上复用原版黑猫模型（设计 §12 猫又伪装）。
 */
public class MonsterGirlFormModel extends EntityModel<MonsterGirlRenderState> {
    private final HumanoidModel<MonsterGirlRenderState> humanoid;
    private final ModelPart catRoot;

    private final ModelPart catHead;
    private final ModelPart catTail1;
    private final ModelPart catTail2;
    private final ModelPart catLegFrontLeft;
    private final ModelPart catLegFrontRight;
    private final ModelPart catLegHindLeft;
    private final ModelPart catLegHindRight;

    /** 各部位烘焙默认姿态（动画在此基础上叠加，避免覆盖猫模型本身的姿势）。 */
    private final float headBaseXRot;
    private final float headBaseYRot;
    private final float tail1BaseZRot;
    private final float legFrontLeftBase;
    private final float legFrontRightBase;
    private final float legHindLeftBase;
    private final float legHindRightBase;

    public MonsterGirlFormModel(EntityRendererProvider.Context context) {
        this(new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), context.bakeLayer(ModelLayers.CAT));
    }

    private MonsterGirlFormModel(HumanoidModel<MonsterGirlRenderState> humanoid, ModelPart catRoot) {
        super(new ModelPart(List.of(), Map.of("humanoid", humanoid.root(), "cat", catRoot)));
        this.humanoid = humanoid;
        this.catRoot = catRoot;
        this.catHead = child(catRoot, "head");
        this.catTail1 = child(catRoot, "tail1");
        this.catTail2 = child(catRoot, "tail2");
        this.catLegFrontLeft = child(catRoot, "front_left_leg", "left_front_leg");
        this.catLegFrontRight = child(catRoot, "front_right_leg", "right_front_leg");
        this.catLegHindLeft = child(catRoot, "back_left_leg", "left_hind_leg");
        this.catLegHindRight = child(catRoot, "back_right_leg", "right_hind_leg");
        this.headBaseXRot = catHead == null ? 0f : catHead.xRot;
        this.headBaseYRot = catHead == null ? 0f : catHead.yRot;
        this.tail1BaseZRot = catTail1 == null ? 0f : catTail1.zRot;
        this.legFrontLeftBase = catLegFrontLeft == null ? 0f : catLegFrontLeft.xRot;
        this.legFrontRightBase = catLegFrontRight == null ? 0f : catLegFrontRight.xRot;
        this.legHindLeftBase = catLegHindLeft == null ? 0f : catLegHindLeft.xRot;
        this.legHindRightBase = catLegHindRight == null ? 0f : catLegHindRight.xRot;
    }

    /** 按候选名取子部位（不同版本命名可能不同，取到即用，取不到则该动画跳过）。 */
    private static ModelPart child(ModelPart root, String... names) {
        for (String name : names) {
            if (root.hasChild(name)) {
                return root.getChild(name);
            }
        }
        return null;
    }

    @Override
    public void setupAnim(MonsterGirlRenderState state) {
        boolean catForm = state.catForm;
        this.humanoid.root().visible = !catForm;
        this.catRoot.visible = catForm;
        if (catForm) {
            setupCatAnim(state);
        } else {
            this.humanoid.setupAnim(state);
        }
    }

    /** 伪装形态（猫）的基础动画：头部随视线转动 + 四足行走摆动 + 尾巴轻摆。 */
    private void setupCatAnim(MonsterGirlRenderState state) {
        if (catHead != null) {
            catHead.xRot = headBaseXRot + state.xRot * Mth.DEG_TO_RAD;
            catHead.yRot = headBaseYRot + state.yRot * Mth.DEG_TO_RAD;
        }
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        float swingOpposite = Mth.cos(state.walkAnimationPos * 0.6662f + (float) Math.PI) * 1.4f
                * state.walkAnimationSpeed;
        if (catLegFrontLeft != null) {
            catLegFrontLeft.xRot = legFrontLeftBase + swing;
        }
        if (catLegHindRight != null) {
            catLegHindRight.xRot = legHindRightBase + swing;
        }
        if (catLegFrontRight != null) {
            catLegFrontRight.xRot = legFrontRightBase + swingOpposite;
        }
        if (catLegHindLeft != null) {
            catLegHindLeft.xRot = legHindLeftBase + swingOpposite;
        }
        if (catTail1 != null) {
            catTail1.zRot = tail1BaseZRot + Mth.cos(state.ageInTicks * 0.1f) * 0.08f;
        }
        if (catTail2 != null) {
            catTail2.zRot = Mth.cos(state.ageInTicks * 0.1f + 0.6f) * 0.12f;
        }
    }
}
