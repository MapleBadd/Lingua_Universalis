package com.linguauniversalis.client;

import com.linguauniversalis.entity.LuBoltEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/**
 * 能力弹道渲染器（占位）：直接复用原版箭的贴图与渲染管线。
 * 三种弹道（蛛网/蛛丝/暗影箭）共用；正式美术就绪后可替换贴图或换模型。
 */
public class LuBoltRenderer extends ArrowRenderer<LuBoltEntity, ArrowRenderState> {
    public LuBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }

    @Override
    protected Identifier getTextureLocation(ArrowRenderState state) {
        return LuBoltEntity.ARROW_TEXTURE;
    }
}
