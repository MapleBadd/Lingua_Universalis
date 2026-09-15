package com.linguauniversalis.event;

import com.linguauniversalis.registry.LURegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;

/**
 * 命缕掉落强化：掉落形态的命缕免疫爆炸/火焰/仙人掌等伤害
 * （设计汇总 §9：不被摧毁；不自然消失与荧光后续实现）。
 */
public final class GirlFilumProtectionHandler {
    private GirlFilumProtectionHandler() {
    }

    public static void onInvulCheck(EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.getItem() == LURegistries.FATUM_FILUM.get()) {
            event.setInvulnerable(true);
        }
    }
}
