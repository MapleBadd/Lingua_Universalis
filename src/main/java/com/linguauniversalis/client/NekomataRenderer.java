package com.linguauniversalis.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linguauniversalis.core.anim.HeadLookRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;

/**
 * 猫又渲染器：<b>按形态分派</b>两套渲染。
 *
 * <ul>
 *   <li><b>人形</b>：GeckoLib（{@link NekomataGeoModel} + 实体侧注册的动画控制器，规则见
 *       {@code core/anim/AnimationRules}）；</li>
 *   <li><b>猫形（伪装）</b>：原版猫模型 + 原版全黑猫贴图 + 原版动画（{@link CatFormRenderer}）。</li>
 * </ul>
 *
 * <p>实现方式：GeckoLib 的渲染状态类型（{@code R}）由它自己 final 提供，因此这里不替换状态类型，
 * 而是把"猫形渲染状态"塞进 GeckoLib 渲染状态自带的<b>数据表</b>（{@link GeoRenderState}）：
 * 在 {@code extractRenderState} 阶段为猫形个体生成原版猫状态，在 {@code submit} 阶段取出并
 * 转交 {@link CatFormRenderer} 提交——两套渲染器各自完整走原版渲染管线（含阴影与名牌）。
 */
public class NekomataRenderer extends GeoEntityRenderer<MonsterGirlEntity, LivingEntityRenderState> {
    /** 猫形渲染状态在渲染状态数据表里的存放位（人形时显式写 null，避免复用的状态残留旧值）。 */
    private static final DataTicket<CatRenderState> CAT_FORM_STATE =
            DataTicket.create("lingua_universalis:cat_form_state", CatRenderState.class);

    /**
     * 头部骨骼名：给它叠加"原版转头"的旋转（只动旋转，不动位置/缩放）。
     *
     * <p>模型里 {@code AllHead → Head} 是整颗头的父级，因此转 {@code AllHead} 即可带动全头。
     */
    public static final String HEAD_BONE = "AllHead";

    /** 需要"去掉自动过渡"的脚掌骨骼（过渡期间钉在原姿态，避免绕远路反向旋转）。 */
    public static final String[] TRANSITION_FREE_BONES = {"LeftPaw", "RightPaw"};

    /** 渲染状态里带上实体（过渡钉帧需要按实体保存姿态，渲染回调里拿不到实体）。 */
    private static final DataTicket<MonsterGirlEntity> GIRL =
            DataTicket.create("lingua_universalis:girl", MonsterGirlEntity.class);

    /** 脚掌过渡钉帧缓存：每只个体 [6] = LeftPaw(x,y,z) + RightPaw(x,y,z)；以及是否已捕获。 */
    private final java.util.Map<MonsterGirlEntity, float[]> frozenPaws = new java.util.WeakHashMap<>();
    private final java.util.Map<MonsterGirlEntity, Boolean> frozenCaptured = new java.util.WeakHashMap<>();

    /** 头部跟随：偏航（度）与俯仰（度）——在 extract 阶段算好（含部分 tick 插值）塞进渲染状态。 */
    private static final DataTicket<Float> HEAD_YAW_DEG = DataTicket.create("lingua_universalis:head_yaw", Float.class);
    private static final DataTicket<Float> HEAD_PITCH_DEG =
            DataTicket.create("lingua_universalis:head_pitch", Float.class);

    private final CatFormRenderer catFormRenderer;

    public NekomataRenderer(EntityRendererProvider.Context context) {
        super(context, new NekomataGeoModel());
        this.catFormRenderer = new CatFormRenderer(context);
        // 模型渲染缩放（config/lingua_universalis.properties 的 model.renderScale；1.0 = 原尺寸）
        this.withScale((float) com.linguauniversalis.core.config.LuSettings.get().modelRenderScale("nekomata"));
    }

    @Override
    public void extractRenderState(MonsterGirlEntity girl, LivingEntityRenderState state, float partialTick) {
        // GeckoLib 人形路径：控制器/骨骼/贴图数据（切回人形时过渡不中断）
        super.extractRenderState(girl, state, partialTick);
        if (state instanceof GeoRenderState geoState) {
            geoState.addGeckolibData(CAT_FORM_STATE,
                    girl.isCatForm() ? catFormRenderer.createRenderState(girl, partialTick) : null);
            geoState.addGeckolibData(GIRL, girl);
            // 头部朝向来源：优先"目视目标"（服务端同步的注视实体 id）→ 客户端直接算朝向目标的
            // 偏航与俯仰；没有目标时才退回实体同步的头朝向/俯仰。设置按物种查（可单独覆盖）。
            com.linguauniversalis.core.config.LuSettings settings =
                    com.linguauniversalis.core.config.LuSettings.get();
            String species = girl.speciesId();
            double headYaw;
            double pitch;
            net.minecraft.world.entity.Entity lookTarget = girl.level().getEntity(girl.lookTargetIdVisual());
            if (lookTarget != null && lookTarget.isAlive()) {
                double tx = Mth.lerp(partialTick, lookTarget.xo, lookTarget.getX());
                double ty = Mth.lerp(partialTick, lookTarget.yo, lookTarget.getY())
                        + lookTarget.getEyeHeight();
                double tz = Mth.lerp(partialTick, lookTarget.zo, lookTarget.getZ());
                double dx = tx - girl.getX();
                double dy = ty - girl.getEyeY();
                double dz = tz - girl.getZ();
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                // MC 约定：偏航 0 = +Z，逆时针为正；俯仰正 = 低头
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                headYaw = targetYaw;
                pitch = -Math.toDegrees(Math.atan2(dy, horizontal));
            } else {
                headYaw = HeadLookRules.lerpDegrees(girl.yHeadRotO, girl.yHeadRot, partialTick);
                pitch = Mth.lerp(partialTick, girl.xRotO, girl.getXRot());
            }
            double bodyYaw = HeadLookRules.lerpDegrees(girl.yBodyRotO, girl.yBodyRot, partialTick);
            double yawDeg = HeadLookRules.applySign(
                            HeadLookRules.netHeadYawDeg(headYaw, bodyYaw, settings.headLookMaxYawDeg(species)),
                            settings.headLookYawSign(species));
            double pitchDeg = HeadLookRules.applySign(
                            HeadLookRules.headPitchDeg(pitch, settings.headLookMaxPitchDeg(species)),
                            settings.headLookPitchSign(species));
            geoState.addGeckolibData(HEAD_YAW_DEG, (float) yawDeg);
            geoState.addGeckolibData(HEAD_PITCH_DEG, (float) pitchDeg);
        }
    }

    /**
     * 头部跟随视角（设计追加项）：把原版转头叠加到 {@link #HEAD_BONE} 的<b>旋转</b>上。
     *
     * <p>只改旋转，位置与缩放保持动画给出的值；本回调在动画控制器写入骨骼快照之后执行，
     * 因此是"动画旋转 + 视线旋转"的叠加。
     *
     * <p>注：这里用<b>裸类型</b> {@code RenderPassInfo} 覆写——GeckoLib 的 {@code RenderPassInfo<R>}
     * 要求 {@code R extends GeoRenderState}，而原版渲染状态只在运行时由 GeckoLib 的 mixin
     * 注入该接口，编译期写不出带泛型参数的签名（写 {@code RenderPassInfo<LivingEntityRenderState>}
     * 会因边界不满足而编译失败）。裸类型签名与接口方法的擦除一致，仍会被正常回调。
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        com.linguauniversalis.core.config.LuSettings settings =
                com.linguauniversalis.core.config.LuSettings.get();
        MonsterGirlEntity girl = (MonsterGirlEntity) renderPassInfo.getOrDefaultGeckolibData(GIRL, (Object) null);
        String species = girl == null ? null : girl.speciesId();

        // ---- 1) 脚掌：过渡期间钉在原姿态（= 这两根骨骼不参与自动过渡）
        if (settings.freezePawTransition(species) && girl != null) {
            applyPawTransitionFreeze(girl, renderPassInfo, snapshots);
        }

        // ---- 2) 头部跟随视角
        if (!settings.headLookEnabled(species)) {
            return;
        }
        Float yaw = (Float) renderPassInfo.getOrDefaultGeckolibData(HEAD_YAW_DEG, (Float) null);
        Float pitch = (Float) renderPassInfo.getOrDefaultGeckolibData(HEAD_PITCH_DEG, (Float) null);
        if (yaw == null || pitch == null) {
            return;
        }
        float yawRad = HeadLookRules.toRadians(yaw);
        float pitchRad = HeadLookRules.toRadians(pitch);
        snapshots.ifPresent(HEAD_BONE, bone -> {
            addRotation(bone, settings.headLookYawAxis(species), yawRad);
            if (settings.headLookFollowPitch(species)) {
                addRotation(bone, settings.headLookPitchAxis(species), pitchRad);
            }
        });
    }

    /**
     * 脚掌过渡钉帧：只要主控制器正在做自动过渡，就把 {@link #TRANSITION_FREE_BONES} 钉在过渡开始那一刻
     * 的姿态上（过渡结束立刻恢复由动画驱动）。
     *
     * <p>为什么需要：GeckoLib 的过渡是把关键帧数值<b>直接线性插值</b>（不做 360° 取最短弧），
     * 资源里 LeftPaw/RightPaw 的 X 在不同动画间是 178.69 ↔ -67.5 / -52.08 这种写法，
     * 插值就会"绕远路"反向转 180°+。钉帧相当于把这两根骨骼的过渡去掉。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyPawTransitionFreeze(MonsterGirlEntity girl, RenderPassInfo renderPassInfo,
                                          BoneSnapshots snapshots) {
        boolean transitioning = isTransitioning(renderPassInfo);
        if (!transitioning) {
            frozenPaws.remove(girl);       // 过渡结束：交还给动画（下一帧直接落到新动画的值）
            frozenCaptured.remove(girl);
            return;
        }
        float[] held = frozenPaws.get(girl);
        if (held == null || !Boolean.TRUE.equals(frozenCaptured.get(girl))) {
            held = new float[TRANSITION_FREE_BONES.length * 3];
            for (int i = 0; i < TRANSITION_FREE_BONES.length; i++) {
                float[] target = held;
                int base = i * 3;
                snapshots.ifPresent(TRANSITION_FREE_BONES[i], bone -> {
                    target[base] = bone.getRotX();
                    target[base + 1] = bone.getRotY();
                    target[base + 2] = bone.getRotZ();
                });
            }
            frozenPaws.put(girl, held);
            frozenCaptured.put(girl, Boolean.TRUE);
        }
        for (int i = 0; i < TRANSITION_FREE_BONES.length; i++) {
            float[] target = held;
            int base = i * 3;
            snapshots.ifPresent(TRANSITION_FREE_BONES[i],
                    bone -> bone.setRotation(target[base], target[base + 1], target[base + 2]));
        }
    }

    /** 是否有控制器正在做自动过渡（GeckoLib 的 ControllerState）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isTransitioning(RenderPassInfo renderPassInfo) {
        Object states = renderPassInfo.getOrDefaultGeckolibData(
                com.geckolib.constant.DataTickets.ANIMATION_CONTROLLER_STATES, (Object) null);
        if (!(states instanceof com.geckolib.animation.state.ControllerState[] array)) {
            return false;
        }
        for (com.geckolib.animation.state.ControllerState state : array) {
            if (state.transitionTicks() > 0 && state.transitionTime() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 把角度加到指定轴上（x / y / z，其它值按 y 处理）。 */
    private static void addRotation(com.geckolib.animation.state.BoneSnapshot bone, String axis, float radians) {
        switch (axis == null ? "y" : axis.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "x" -> bone.setRotX(bone.getRotX() + radians);
            case "z" -> bone.setRotZ(bone.getRotZ() + radians);
            default -> bone.setRotY(bone.getRotY() + radians);
        }
    }

    @Override
    public void submit(LivingEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state instanceof GeoRenderState geoState) {
            CatRenderState catState = geoState.getOrDefaultGeckolibData(CAT_FORM_STATE, (CatRenderState) null);
            if (catState != null) {
                catFormRenderer.submit(catState, poseStack, submitNodeCollector, camera);
                return;
            }
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
