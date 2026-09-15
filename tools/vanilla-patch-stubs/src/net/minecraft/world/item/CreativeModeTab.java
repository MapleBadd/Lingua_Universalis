package net.minecraft.world.item;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * 补丁面桩：NeoForge 在 26.2 补丁中提供 {@code CreativeModeTab.builder()} 无参工厂
 * （官方 MDK 用法，已由真实 NeoForge 构建验证）。此处仅用于"对原生 vanilla jar"的
 * 离线全量编译校验，编译时以源码优先于 classpath，不与 jar 内类冲突。
 */
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
    }
}
