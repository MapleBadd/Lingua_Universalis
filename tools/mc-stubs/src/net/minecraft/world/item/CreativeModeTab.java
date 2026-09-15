package net.minecraft.world.item;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** 最小桩：仅供离线语法校验。 */
public class CreativeModeTab {
    private CreativeModeTab() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder title(Component title) {
            return this;
        }

        public Builder icon(Supplier<ItemStack> icon) {
            return this;
        }

        public Builder displayItems(DisplayItemsConsumer consumer) {
            return this;
        }

        public CreativeModeTab build() {
            return new CreativeModeTab();
        }
    }

    @FunctionalInterface
    public interface DisplayItemsConsumer {
        void accept(DisplayItemsParameters parameters, Output output);
    }

    public static final class DisplayItemsParameters {
    }

    public interface Output {
        void accept(ItemStack stack);

        void accept(Item item);
    }
}
