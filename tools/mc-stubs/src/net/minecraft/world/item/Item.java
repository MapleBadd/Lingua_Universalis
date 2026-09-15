package net.minecraft.world.item;

import java.util.function.Supplier;

/** 最小桩：仅供离线语法校验。 */
public class Item {
    public Item(Properties properties) {
    }

    public static class Properties {
        public Properties stacksTo(int maxStackSize) {
            return this;
        }

        public Properties food(Object foodProperties) {
            return this;
        }
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack();
    }
}
