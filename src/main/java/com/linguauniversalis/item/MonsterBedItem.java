package com.linguauniversalis.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import java.util.function.Consumer;
import net.minecraft.world.item.BedItem;

/**
 * 中魔物床的物品形态：{@link BedItem} + GeckoLib {@link GeoItem}。
 *
 * <p>物品栏里显示的是床自己的 geo 模型（与方块共用 {@code monster_bed}），
 * 姿态沿用原版床的物品模型 display 段（见 {@code models/item/monster_bed.json}）。
 */
public class MonsterBedItem extends BedItem implements GeoItem {
    private final AnimatableInstanceCache cache = new GeoItem.ContextBasedAnimatableInstanceCache(this);

    public MonsterBedItem(net.minecraft.world.level.block.Block block, Properties properties) {
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
        com.linguauniversalis.client.MonsterBedItemRenderer.create(consumer);
    }
}