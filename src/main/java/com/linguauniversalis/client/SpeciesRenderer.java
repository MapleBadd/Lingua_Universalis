package com.linguauniversalis.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.linguauniversalis.core.anim.HeadLookRules;
import com.linguauniversalis.core.anim.LocatorRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 通用物种渲染器：<b>按形态分派</b>两套渲染（一套代码服务所有 GeckoLib 魔物娘）。
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
public class SpeciesRenderer extends GeoEntityRenderer<MonsterGirlEntity, LivingEntityRenderState> {
    /**
     * 变形形态的渲染数据在渲染状态数据表里的存放位（本体形态时显式写 null，避免复用的状态残留旧值）。
     *
     * <p>不同形态的渲染状态类型不同，因此用 {@link DisguiseRenderData} 把"渲染器 + 状态对象"一起装。
     */
    private static final DataTicket<DisguiseRenderData> FORM_RENDER =
            DataTicket.create("lingua_universalis:form_render", DisguiseRenderData.class);
    /** 当前变形形态 id（给 GeoModel 换资源用；空串 = 本体形态）。 */
    public static final DataTicket<String> FORM_ID =
            DataTicket.create("lingua_universalis:form_id", String.class);

    /** 变形形态的一帧渲染数据。 */
    private record DisguiseRenderData(com.linguauniversalis.core.species.DisguiseForm form,
                                      DisguiseFormRenderers.FormRenderer renderer, Object state) {
    }

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

    /** 手上物品的渲染输入（extract 阶段解析一次，骨骼阶段取用）。 */
    private static final DataTicket<ItemStackRenderState> HELD_MAIN =
            DataTicket.create("lingua_universalis:held_main", ItemStackRenderState.class);
    private static final DataTicket<ItemStackRenderState> HELD_OFF =
            DataTicket.create("lingua_universalis:held_off", ItemStackRenderState.class);


    /** 物种 id（资源按约定从它推导，见 {@link SpeciesGeoModel}）。 */
    private final String speciesId;

    public SpeciesRenderer(EntityRendererProvider.Context context, String speciesId) {
        super(context, new SpeciesGeoModel(speciesId));
        this.speciesId = speciesId;
        // 内置变形形态渲染器（vanilla_cat → 原版猫；GeckoLib 换形走主渲染器，不需要这里登记）
        DisguiseFormRenderers.registerDefaults(context);
        // 模型渲染缩放（config/lingua_universalis.properties 的 model.renderScale；1.0 = 原尺寸）
        this.withScale((float) com.linguauniversalis.core.config.LuSettings.get().modelRenderScale(speciesId));
    }

    @Override
    public void extractRenderState(MonsterGirlEntity girl, LivingEntityRenderState state, float partialTick) {
        // GeckoLib 人形路径：控制器/骨骼/贴图数据（切回人形时过渡不中断）
        super.extractRenderState(girl, state, partialTick);
        if (state instanceof GeoRenderState geoState) {
            // 形态：按种登记的形态定义分派（vanilla_cat 走专用渲染器；gecko 走主渲染器换资源）
            String formId = girl.disguiseFormId();
            geoState.addGeckolibData(FORM_ID, formId);
            com.linguauniversalis.core.species.DisguiseForm form =
                    com.linguauniversalis.core.species.DisguiseRegistry.get(formId);
            DisguiseRenderData formRender = null;
            if (form != null) {
                DisguiseFormRenderers.FormRenderer renderer = DisguiseFormRenderers.of(form.renderKind());
                if (renderer != null) {
                    Object formState = renderer.extractState(form, girl, partialTick);
                    if (formState != null) {
                        formRender = new DisguiseRenderData(form, renderer, formState);
                    }
                }
            }
            geoState.addGeckolibData(FORM_RENDER, formRender);
            geoState.addGeckolibData(GIRL, girl);
            // 头部朝向来源：**直接用实体同步的头朝向/俯仰**（yHeadRot / xRot）。
            // 她"看谁"由原版 LookAtPlayerGoal / RandomLookAroundGoal 决定（见 GirlLookGoals），
            // 那两个目标驱动 LookControl 时会同时更新偏航与俯仰，所以这里不需要再自己算目标方向。
            com.linguauniversalis.core.config.LuSettings settings =
                    com.linguauniversalis.core.config.LuSettings.get();
            String species = girl.speciesId();
            double headYaw = HeadLookRules.lerpDegrees(girl.yHeadRotO, girl.yHeadRot, partialTick);
            double pitch = Mth.lerp(partialTick, girl.xRotO, girl.getXRot());
            // 她的眼高 2.1548 远高于玩家眼睛（≈1.62），原版瞄"玩家眼睛"会让她低头 15°~25°；
            // 把低头分量压扁，避免看起来在盯着玩家的脚（抬头分量不动，蹲下时仍会看你）。
            pitch = HeadLookRules.softenDownPitch(pitch, HeadLookRules.DOWN_PITCH_SCALE);
            double bodyYaw = HeadLookRules.lerpDegrees(girl.yBodyRotO, girl.yBodyRot, partialTick);
            double yawDeg = HeadLookRules.applySign(
                            HeadLookRules.netHeadYawDeg(headYaw, bodyYaw, settings.headLookMaxYawDeg(species)),
                            settings.headLookYawSign(species));
            double pitchDeg = HeadLookRules.applySign(
                            HeadLookRules.headPitchDeg(pitch, settings.headLookMaxPitchDeg(species)),
                            settings.headLookPitchSign(species));
            geoState.addGeckolibData(HEAD_YAW_DEG, (float) yawDeg);
            geoState.addGeckolibData(HEAD_PITCH_DEG, (float) pitchDeg);
            // 手上物品：定位组渲染输入（她手上拿的东西不属于原版装备槽位，由实体同步过来）
            geoState.addGeckolibData(HELD_MAIN,
                    buildHeldItemState(girl.mainHandVisual(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, girl));
            geoState.addGeckolibData(HELD_OFF,
                    buildHeldItemState(girl.offHandVisual(), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, girl));
        }
    }

    /** 把手上物品解析成"可提交的渲染状态"（空手时得到一个 isEmpty 的状态，骨骼阶段直接跳过）。 */
    private static ItemStackRenderState buildHeldItemState(ItemStack stack, ItemDisplayContext context,
                                                           MonsterGirlEntity girl) {
        ItemStackRenderState state = new ItemStackRenderState();
        if (stack == null || stack.isEmpty()) {
            return state;
        }
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack, context, girl);
        return state;
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

        // ---- 2) 手上物品：画在爪部定位组上（与"是否开头部跟随"无关，必须在下面 return 之前）
        if (girl != null) {
            applyHeldItems(girl, settings, renderPassInfo);
        }

        // ---- 3) 头部跟随视角
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
        boolean followPitch = settings.headLookFollowPitch(species);
        if (settings.headLookCompensatePose(species)) {
            // 世界轴补偿：把 yaw 绕"世界竖轴"、pitch 绕"世界横轴"施加，再换算回骨骼本地欧拉角
            applyWorldSpaceHeadLook(renderPassInfo.model(), snapshots, yawRad, followPitch ? pitchRad : 0.0F);
        } else {
            snapshots.ifPresent(HEAD_BONE, bone -> {
                addRotation(bone, settings.headLookYawAxis(species), yawRad);
                if (followPitch) {
                    addRotation(bone, settings.headLookPitchAxis(species), pitchRad);
                }
            });
        }
    }

    /**
     * 在<b>世界（模型）空间</b>给头部叠加转头，并把结果换算回骨骼本地欧拉角。
     *
     * <p>为什么需要：GeckoLib 的骨骼旋转是"父骨骼矩阵 × 本地旋转"，我们在 {@code AllHead} 上叠加的是
     * <b>本地轴</b>旋转。坐姿（{@code main_sit}）里 {@code All} / {@code TopBody} 乃至 {@code AllHead}
     * 本身都被转起来（实测 {@code All}=15°、{@code TopBody}=20°、{@code AllHead}=52.5°），
     * 此时绕本地 Y 转 = 绕一根已经歪掉的轴转 ⇒ 视觉上"转头同时歪头"。
     *
     * <p>做法：设父链累计旋转为 {@code P}、头骨骼本地动画旋转为 {@code L}，
     * 希望世界空间里最终是 {@code D · P · L}（{@code D} = 绕世界竖轴的 yaw × 绕世界横轴的 pitch），
     * 于是本地旋转取 {@code P⁻¹ · D · P · L}，再用与 GeckoLib 相同的欧拉分解
     * （{@code rotationZYX} ↔ {@code getEulerAnglesZYX}）写回快照。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyWorldSpaceHeadLook(com.geckolib.cache.model.BakedGeoModel model,
                                                BoneSnapshots snapshots, float yawRad, float pitchRad) {
        com.geckolib.cache.model.GeoBone head = model.getBone(HEAD_BONE).orElse(null);
        if (head == null) {
            return;
        }
        // 父链累计旋转 P（从根到头的父，矩阵顺序与 GeckoLib 渲染一致：父 × 子）
        org.joml.Quaternionf parent = new org.joml.Quaternionf();
        java.util.List<com.geckolib.cache.model.GeoBone> chain = new java.util.ArrayList<>();
        for (com.geckolib.cache.model.GeoBone bone = head.parent(); bone != null; bone = bone.parent()) {
            chain.add(0, bone);
        }
        for (com.geckolib.cache.model.GeoBone bone : chain) {
            String name = bone.name();
            snapshots.ifPresent(name, snapshot -> parent.mul(new org.joml.Quaternionf().rotationZYX(
                    snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX())));
        }
        snapshots.ifPresent(HEAD_BONE, snapshot -> {
            // 头骨骼本地动画旋转 L
            org.joml.Quaternionf local = new org.joml.Quaternionf()
                    .rotationZYX(snapshot.getRotZ(), snapshot.getRotY(), snapshot.getRotX());
            // 想要的世界空间增量 D（绕世界竖轴的 yaw、绕世界横轴的 pitch）
            org.joml.Quaternionf world = new org.joml.Quaternionf().rotationY(yawRad)
                    .mul(new org.joml.Quaternionf().rotationX(pitchRad));
            // 本地 = P⁻¹ · D · P · L
            org.joml.Quaternionf result = new org.joml.Quaternionf(parent).invert()
                    .mul(world).mul(parent).mul(local);
            org.joml.Vector3f euler = result.getEulerAnglesZYX(new org.joml.Vector3f());
            snapshot.setRotation(euler.x, euler.y, euler.z);
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

    /**
     * 手上物品：按 {@link LocatorRules} 的约定把主手/副手物品画到爪部定位组上。
     *
     * <p>定位组是模型里的空骨骼（只有枢轴），GeckoLib 会先把姿态摆到该骨骼再回调，
     * 所以物品默认就"长在美术给的落点上"；位置/朝向/大小的微调走配置 {@code heldItem.*}，
     * 不用改代码也能在游戏里试出来。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyHeldItems(MonsterGirlEntity girl, com.linguauniversalis.core.config.LuSettings settings,
                                RenderPassInfo renderPassInfo) {
        String species = girl.speciesId();
        attachHeldItem(renderPassInfo, species, settings, LocatorRules.PawSide.MAIN, HELD_MAIN);
        attachHeldItem(renderPassInfo, species, settings, LocatorRules.PawSide.OFF, HELD_OFF);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void attachHeldItem(RenderPassInfo renderPassInfo, String species,
                                       com.linguauniversalis.core.config.LuSettings settings,
                                       LocatorRules.PawSide side, DataTicket<ItemStackRenderState> ticket) {
        ItemStackRenderState state =
                (ItemStackRenderState) renderPassInfo.getOrDefaultGeckolibData(ticket, (Object) null);
        if (state == null || state.isEmpty()) {
            return;
        }
        GeoBone bone = HeldItemLocators.find(renderPassInfo.model(), side);
        if (bone == null) {
            return; // 这只手没有定位组：不画，也不猜位置
        }
        PerBoneRender<GeoRenderState> task = new HeldItemTask(state,
                (float) settings.heldItemScale(species),
                (float) settings.heldItemOffsetX(species),
                (float) settings.heldItemOffsetY(species),
                (float) settings.heldItemOffsetZ(species),
                (float) settings.heldItemRotXDeg(species),
                (float) settings.heldItemRotYDeg(species),
                (float) settings.heldItemRotZDeg(species));
        renderPassInfo.addPerBoneRender(bone, task);
    }

    /**
     * 单件手持物品的提交任务：定位组已经把姿态摆好，这里只做配置给的微调后提交物品模型。
     *
     * <p>为什么不需要再乘 1/16：GeckoLib 摆姿态时把枢轴按"模型单位 ÷ 16"平移到格空间
     * （{@code GeoBone#translateToPivotPoint}），所以定位组处的坐标系已经是"1 单位 = 1 格"，
     * 物品提交进来就是原尺寸——太大就调 {@code heldItem.scale}。
     */
    private static final class HeldItemTask implements PerBoneRender<GeoRenderState> {
        private final ItemStackRenderState state;
        private final float scale;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;
        private final float rotXDeg;
        private final float rotYDeg;
        private final float rotZDeg;

        private HeldItemTask(ItemStackRenderState state, float scale, float offsetX, float offsetY, float offsetZ,
                             float rotXDeg, float rotYDeg, float rotZDeg) {
            this.state = state;
            this.scale = scale <= 0.0F ? 1.0F : scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.rotXDeg = rotXDeg;
            this.rotYDeg = rotYDeg;
            this.rotZDeg = rotZDeg;
        }

        @Override
        public void submitRenderTask(RenderPassInfo<GeoRenderState> info, GeoBone bone,
                                     SubmitNodeCollector collector) {
            PoseStack poseStack = info.poseStack();
            poseStack.pushPose();
            if (offsetX != 0.0F || offsetY != 0.0F || offsetZ != 0.0F) {
                poseStack.translate(offsetX, offsetY, offsetZ);
            }
            if (rotXDeg != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotXDeg));
            }
            if (rotYDeg != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotYDeg));
            }
            if (rotZDeg != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotZDeg));
            }
            poseStack.scale(scale, scale, scale);
            state.submit(poseStack, collector, info.packedLight(), info.packedOverlay(), 0);
            poseStack.popPose();
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
        if (state instanceof com.geckolib.renderer.base.GeoRenderState geoState) {
            MonsterGirlEntity girl = geoState.getOrDefaultGeckolibData(GIRL, (MonsterGirlEntity) null);
            DisguiseRenderData formRender =
                    geoState.getOrDefaultGeckolibData(FORM_RENDER, (DisguiseRenderData) null);
            if (formRender != null) {
                // 专用形态渲染器（如原版猫）接管这一帧
                formRender.renderer().submit(formRender.form(), formRender.state(), poseStack,
                        submitNodeCollector, camera);
                if (girl != null) {
                    GirlStatusPlate.submit(girl, true, poseStack, submitNodeCollector, camera,
                            state.lightCoords, state.distanceToCameraSq);
                }
                return;
            }
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
        // 本体（GeckoLib）形态：模型之后再画头顶状态牌
        if (state instanceof com.geckolib.renderer.base.GeoRenderState geoState) {
            MonsterGirlEntity girl = geoState.getOrDefaultGeckolibData(GIRL, (MonsterGirlEntity) null);
            if (girl != null) {
                GirlStatusPlate.submit(girl, false, poseStack, submitNodeCollector, camera,
                        state.lightCoords, state.distanceToCameraSq);
            }
        }
    }

    /**
     * 屏蔽原版名牌：她的头顶文字由 {@link GirlStatusPlate} 全权接管
     * （原版名牌位置由碰撞箱决定，她的模型比碰撞箱高得多，会卡在胸口）。
     */
    @Override
    protected net.minecraft.network.chat.Component getNameTag(MonsterGirlEntity girl) {
        return null;
    }
}
