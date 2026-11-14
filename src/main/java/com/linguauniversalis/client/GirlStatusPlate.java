package com.linguauniversalis.client;

import com.linguauniversalis.core.behavior.CombatModeRules;
import com.linguauniversalis.core.behavior.StatusPlateRules;
import com.linguauniversalis.entity.MonsterGirlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 头顶状态牌：替代命名牌，显示魔物娘的即时状态。
 *
 * <pre>
 *   主动 / 被动              常态（显示战斗模式）
 *   休眠                     休眠中
 *   战败 42 秒               倒地锁血中（剩余秒数）
 *   濒死                     倒地且锁血结束（可被急救箱救起，再被打即死）
 * </pre>
 *
 * <p>实现：不走原版 {@code state.nameTag}（那套位置由碰撞箱 {@code EntityAttachments} 决定，
 * 她的模型比碰撞箱高得多，名牌会卡在胸口），而是自己在 {@code submit} 里用
 * {@link SubmitNodeCollector#submitNameTag} 按模型高度绘制。
 */
public final class GirlStatusPlate {
    private GirlStatusPlate() {
    }

    /** 人形状态牌的离脚高度（格）——按模型实际高度给的常量，模型缩放会乘上去。 */
    public static final double HEIGHT_BLOCKS = 2.45;
    /** 猫形（变形形态）状态牌高度。 */
    public static final double FORM_HEIGHT_BLOCKS = 0.95;

    /** 状态牌文本（翻译键见 lang：gui.lingua_universalis.plate.*）。 */
    public static Component textFor(MonsterGirlEntity girl) {
        StatusPlateRules.Status status = StatusPlateRules.statusForDisplay(
                girl.isDormantVisual(), girl.isDownedVisual(), girl.downedSecondsVisual(),
                girl.combatModeVisual());
        return switch (status.kind()) {
            case DEFEATED -> Component.translatable("gui.lingua_universalis.plate.defeated", status.seconds());
            case DYING -> Component.translatable("gui.lingua_universalis.plate.dying");
            case DORMANT -> Component.translatable("gui.lingua_universalis.plate.dormant");
            case MODE -> Component.translatable(status.mode() == CombatModeRules.CombatMode.AGGRESSIVE
                    ? "gui.lingua_universalis.plate.aggressive"
                    : "gui.lingua_universalis.plate.passive");
        };
    }

    /** 状态牌离脚高度：人形按模型缩放，变形形态按形态高度。 */
    public static double heightFor(MonsterGirlEntity girl, boolean disguised) {
        if (disguised) {
            com.linguauniversalis.core.species.DisguiseForm form = girl.disguiseForm();
            return form == null ? FORM_HEIGHT_BLOCKS : Math.max(0.8, form.height() + 0.35);
        }
        double scale = com.linguauniversalis.core.config.LuSettings.get().modelRenderScale(girl.speciesId());
        return Math.max(1.2, HEIGHT_BLOCKS * scale);
    }

    /**
     * 状态牌只有「<b>伙伴档</b> + <b>她自己的主人</b>」才看得见。
     *
     * <p>理由：被动/主动、休眠、战败都是**伙伴才有的状态**，野生/友善个体压根没有这些状态；
     * 而且多人服务器上别人的伙伴状态属于别人的私事，不该被旁人看到。
     */
    public static boolean visibleToLocalPlayer(MonsterGirlEntity girl) {
        if (!girl.isCompanionVisual()) {
            return false;
        }
        String owner = girl.ownerUuidVisual();
        net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
        return local != null && owner != null && !owner.isEmpty() && owner.equals(local.getStringUUID());
    }

    /**
     * 提交状态牌（由渲染器在模型之后调用）。
     *
     * @param distanceToCameraSq 到相机的平方距离（决定是否显示）
     */
    public static void submit(MonsterGirlEntity girl, boolean disguised, PoseStack poseStack,
                              SubmitNodeCollector collector, CameraRenderState camera,
                              int packedLight, double distanceToCameraSq) {
        if (!visibleToLocalPlayer(girl)) {
            return;
        }
        if (!StatusPlateRules.shouldShow(distanceToCameraSq)) {
            return;
        }
        Component text = textFor(girl);
        poseStack.pushPose();
        collector.submitNameTag(poseStack, new Vec3(0.0, heightFor(girl, disguised), 0.0), 0, text,
                false, packedLight, camera);
        poseStack.popPose();
    }
}
