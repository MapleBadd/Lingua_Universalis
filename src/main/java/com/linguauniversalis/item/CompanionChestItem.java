package com.linguauniversalis.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import java.util.function.Consumer;
import net.minecraft.world.item.BlockItem;

/**
 * 伙伴宝箱的物品形态：{@link BlockItem} + GeckoLib {@link GeoItem}。
 *
 * <p>物品栏里显示的就是宝箱自己的 geo 模型（与方块共用 {@code chest_of_companions}），
 * 不需要另做 2D 图标；外观声明见 {@code items/chest_of_companions.json} 与
 * {@code models/item/chest_of_companions.json} 的 display 段。
 */
public class CompanionChestItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = new GeoItem.ContextBasedAnimatableInstanceCache(this);

    public CompanionChestItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        // 静态物品：无控制器（开合动画只在方块形态播）
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        com.linguauniversalis.client.CompanionChestItemRenderer.create(consumer);
    }
}