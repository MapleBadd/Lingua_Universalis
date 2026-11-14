package com.linguauniversalis.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/**
 * 初稿（图鉴书）：用 GeckoLib **模型**表达（不是 2D 贴图）。
 *
 * <p><b>当前只做最朴素的展示</b>：模型与贴图放
 * {@code geckolib/models/first_draft.geo.json} 与 {@code textures/item/first_draft.png}，
 * 各显示场景的姿态写在 {@code models/item/first_draft.json} 的 {@code display} 段
 * （手持两个手位都用**原版普通物品**的数值；物品栏/掉落物/展示框用美术调好的数值）。
 *
 * <p><b>动画与右键交互暂时都不接</b>：模型保持默认（合着）姿态，右键没有任何初稿自身的行为。
 * 美术资源（含 {@code 初稿动画.json} 的 display/open/flip）先留在资源目录里备用 ——
 * 物品模型已声明为 {@code minecraft:special} + {@code geckolib:geckolib}，
 * 以后要接动画时只需加控制器与阶段机，不用改资源声明。
 */
public class FirstDraftItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = new GeoItem.ContextBasedAnimatableInstanceCache(this);

    public FirstDraftItem(Properties properties) {
        super(properties);
    }

    /**
     * 初稿的功能是否在该手生效。
     *
     * <p>规则：**只有主手**（副手只显示模型，不参与任何初稿功能）；
     * 以后新增初稿功能都从这里过一遍，避免漏掉副手。
     */
    public static boolean isEnabledInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * 动画控制器：**物品自身不播动画**（手持 / 物品栏 / 掉落一律默认合着姿态）。
     *
     * <p>书的"翻开 → 摊开展示"动画只在**誊写台上**播：那份动画烘进了合并模型
     * {@code scriptorium_book.geo.json}（台子 + 书骨骼），由
     * {@code ScriptoriumBlockEntity} 的控制器驱动（{@code open} 播一遍 → 循环 {@code display}）。
     */
    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        // 有意留空：物品形态不播动画
    }

    /** 客户端渲染器入口（GeckoLib 约定；物品外观由 {@code items/first_draft.json} 声明为 special 模型）。 */
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        com.linguauniversalis.client.FirstDraftItemRenderer.create(consumer);
    }
}
