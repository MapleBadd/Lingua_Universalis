package net.neoforged.neoforge.registries;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;

/**
 * 最小桩：仅供离线语法校验（Lingua Universalis MC 层）。
 * 签名对齐 NeoForge 26.2 官方 DeferredRegister。
 */
public class DeferredRegister<T> {

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modId) {
        return new DeferredRegister<>();
    }

    public static Items createItems(String modId) {
        return new Items();
    }

    public static Blocks createBlocks(String modId) {
        return new Blocks();
    }

    public void register(IEventBus eventBus) {
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        return new DeferredHolder<>();
    }

    public static class Items extends DeferredRegister<Item> {
        public DeferredItem<Item> registerSimpleItem(String name) {
            return new DeferredItem<>();
        }

        public DeferredItem<Item> registerSimpleItem(String name,
                                                     UnaryOperator<Item.Properties> properties) {
            return new DeferredItem<>();
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name,
                                                               Supplier<? extends Block> block) {
            return new DeferredItem<>();
        }
    }

    public static class Blocks extends DeferredRegister<Block> {
        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return new DeferredBlock<>();
        }

        public DeferredBlock<Block> registerSimpleBlock(String name,
                                                        UnaryOperator<BlockBehaviour.Properties> properties) {
            return new DeferredBlock<>();
        }
    }
}
