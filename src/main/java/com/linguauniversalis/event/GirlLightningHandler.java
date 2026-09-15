package com.linguauniversalis.event;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;

/**
 * 命缕充能：掉落形态的命缕被闪电劈中后获得「充能」标记
 * （设计汇总 §9：闪电充能 → 才可在凋灵玫瑰上使用 / 参与复活）。
 */
public final class GirlLightningHandler {
    private GirlLightningHandler() {
    }

    public static void onStruck(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.getItem() == LURegistries.FATUM_FILUM.get()) {
            stack.set(LURegistries.FILUM_CHARGED.get(), true);
        }
    }
}
