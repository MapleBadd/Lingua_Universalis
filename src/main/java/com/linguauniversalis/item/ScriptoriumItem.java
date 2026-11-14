package com.linguauniversalis.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import java.util.function.Consumer;
import net.minecraft.world.item.BlockItem;

/**
 * 誊写台的物品形态：{@link BlockItem} + GeckoLib {@link GeoItem}。
 *
 * <p>这样**物品栏里显示的就是誊写台自己的 geo 模型**（{@code geckolib/models/scriptorium.geo.json}），
 * 不需要另做 2D 图标；外观声明见 {@code items/scriptorium.json}（{@code minecraft:special} + {@code geckolib:geckolib}）
 * 与 {@code models/item/scriptorium.json} 的 {@code display} 段。
 */
public class ScriptoriumItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = new GeoItem.ContextBasedAnimatableInstanceCache(this);

    public ScriptoriumItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        // 静态物品：无控制器
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        com.linguauniversalis.client.ScriptoriumItemRenderer.create(consumer);
    }
}