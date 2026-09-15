package com.linguauniversalis.client;

import com.linguauniversalis.block.CompanionChestBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * 伙伴宝箱的渲染器：复用原版箱子渲染器（{@link ChestRenderer}），**外观强制成末影箱**。
 *
 * <p>原版是按方块实体类型选材质的（{@code be instanceof EnderChestBlockEntity} → 末影箱），
 * 我们的箱子是自定义方块实体，所以在抓取渲染状态后把材质字段改成 {@code ENDER_CHEST} ——
 * 这就是"暂时拿末影箱当模型"的实现方式，等美术给正式模型时把本类删掉、换成普通方块模型即可。
 *
 * <p>盖子不播开合动画（{@code getOpenNess} 恒 0），所以不需要方块事件同步。
 */
public class CompanionChestRenderer extends ChestRenderer<CompanionChestBlockEntity> {
    public CompanionChestRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(CompanionChestBlockEntity chest, ChestRenderState state, float partialTick,
            Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        super.extractRenderState(chest, state, partialTick, cameraPos, crumbling);
        state.material = ChestRenderState.ChestMaterialType.ENDER_CHEST;
    }
}
