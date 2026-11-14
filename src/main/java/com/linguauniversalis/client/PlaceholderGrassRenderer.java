package com.linguauniversalis.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.linguauniversalis.entity.MonsterGirlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * 占位渲染器：把还没有专属模型的物种渲染成**一个草方块**。
 *
 * <p>为什么用 GeckoLib 而不是原版方块渲染：GeckoLib 的 {@code GeoEntityRenderer} 直接吃
 * 我们的 {@code .geo.json}，换美术资源时零代码改动；原版方块渲染在 26.2 是四边面输出式的接口，
 * 往实体渲染器里塞很别扭。
 */
public class PlaceholderGrassRenderer
        extends GeoEntityRenderer<MonsterGirlEntity, LivingEntityRenderState> {

    public PlaceholderGrassRenderer(EntityRendererProvider.Context context) {
        super(context, new PlaceholderGrassModel());
        // 方块比人形矮，影子缩小一点
        this.shadowRadius = 0.4F;
    }
}
