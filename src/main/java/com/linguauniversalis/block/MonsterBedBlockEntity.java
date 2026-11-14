package com.linguauniversalis.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.linguauniversalis.registry.LURegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中魔物床的方块实体：**只为 GeckoLib 渲染服务**（床是静态模型，没有动画）。
 *
 * <p>为什么需要方块实体：GeckoLib 的方块渲染器按方块实体逐个渲染，而原版床的模型是
 * 通版贴图、无法承载自定义 geo，所以外观改由 GeckoLib 绘制（方块本体设为 INVISIBLE）。
 */
public class MonsterBedBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MonsterBedBlockEntity(BlockPos pos, BlockState state) {
        super(LURegistries.MONSTER_BED_BE.get(), pos, state);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：控制器恒 STOP，保持默认姿态
        controllers.add(new AnimationController<MonsterBedBlockEntity>("bed", 0, state -> PlayState.STOP));
    }
}