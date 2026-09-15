package com.linguauniversalis.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * 魔物娘渲染状态（26.2 状态式渲染管线）：承载每帧从实体提取的渲染数据。
 * 占位阶段仅含形态标记；后续接入模型/动画状态时在此扩展。
 */
public class MonsterGirlRenderState extends HumanoidRenderState {
    /** 是否伪装形态（猫形）——决定模型与贴图。 */
    public boolean catForm;
}
