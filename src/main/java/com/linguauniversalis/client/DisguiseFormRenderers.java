package com.linguauniversalis.client;

import com.linguauniversalis.core.species.DisguiseForm;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * 变形形态的<b>客户端渲染注册表</b> —— 把"变形玩法"和"怎么画"解耦。
 *
 * <p>核心层（{@link com.linguauniversalis.core.species.DisguiseRegistry}）只记录形态<b>是什么</b>
 * （id、资源名、碰撞箱），这里登记"这种 {@code renderKind} 怎么画"：
 * <ul>
 *   <li>{@link DisguiseForm#KIND_VANILLA_CAT}：套原版猫渲染（猫又的双尾黑猫伪装）；</li>
 *   <li>{@link DisguiseForm#KIND_GECKO}：不经过这里 —— 由主 GeckoLib 渲染器直接改用形态自带的
 *       模型/贴图/动画资源（见 {@code NekomataGeoModel#getModelResource}），也就是"丢资源即可变形"。</li>
 * </ul>
 *
 * <p>以后要加新的"整体换渲染器"的形态（比如某个物种要变成原版蜘蛛/狐狸外观），
 * 实现一个 {@link FormRenderer} 并 {@code register("spider", ...)} 即可，主渲染器不用改。
 */
public final class DisguiseFormRenderers {
    private DisguiseFormRenderers() {
    }

    /**
     * 一种"整体换渲染管线"的形态渲染器。
     *
     * <p>用 {@code Object} 承载渲染状态：不同形态的渲染状态类型不同（猫是 {@code CatRenderState}…），
     * 这里只做类型擦除的传递，具体类型由实现类自己保证。
     */
    public interface FormRenderer {
        /** extract 阶段：产出这一帧的渲染状态（可返回 null 表示本帧不画）。形态自带资源，所以要把 form 传进来。 */
        Object extractState(DisguiseForm form, MonsterGirlEntity girl, float partialTick);

        /** submit 阶段：把渲染状态画出来。 */
        void submit(DisguiseForm form, Object state, PoseStack poseStack, SubmitNodeCollector collector,
                    CameraRenderState camera);
    }

    private static final Map<String, FormRenderer> BY_KIND = new HashMap<>();
    private static boolean defaultsRegistered;

    /** 登记/替换某个 renderKind 的渲染器。 */
    public static void register(String renderKind, FormRenderer renderer) {
        if (renderKind != null && renderer != null) {
            BY_KIND.put(renderKind.trim().toLowerCase(java.util.Locale.ROOT), renderer);
        }
    }

    /** 取某个 renderKind 的渲染器；未登记返回 null（= 该形态走主 GeckoLib 渲染器）。 */
    public static FormRenderer of(String renderKind) {
        if (renderKind == null) {
            return null;
        }
        return BY_KIND.get(renderKind.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** 登记内置形态渲染器（重复调用无副作用）。 */
    public static void registerDefaults(EntityRendererProvider.Context context) {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;
        register(DisguiseForm.KIND_VANILLA_CAT, new VanillaCatFormRenderer(context));
    }

    /**
     * 原版猫形态（猫又的猫形伪装）：复用 {@link CatFormRenderer}。
     *
     * <p>每个<b>形态</b>一个渲染器实例（贴图来自形态定义），按形态 id 缓存。
     */
    private static final class VanillaCatFormRenderer implements FormRenderer {
        private final EntityRendererProvider.Context context;
        private final Map<String, CatFormRenderer> byForm = new HashMap<>();

        private VanillaCatFormRenderer(EntityRendererProvider.Context context) {
            this.context = context;
        }

        private CatFormRenderer rendererFor(DisguiseForm form) {
            return byForm.computeIfAbsent(form == null ? "" : form.id(), key -> new CatFormRenderer(context,
                    form == null || form.texturePath().isEmpty()
                            ? null
                            : net.minecraft.resources.Identifier.parse(form.texturePath())));
        }

        @Override
        public Object extractState(DisguiseForm form, MonsterGirlEntity girl, float partialTick) {
            CatFormRenderer renderer = rendererFor(form);
            CatRenderState state = renderer.createRenderState();
            renderer.extractRenderState(girl, state, partialTick);
            return state;
        }

        @Override
        public void submit(DisguiseForm form, Object state, PoseStack poseStack, SubmitNodeCollector collector,
                           CameraRenderState camera) {
            if (state instanceof CatRenderState catState) {
                rendererFor(form).submit(catState, poseStack, collector, camera);
            }
        }
    }
}
