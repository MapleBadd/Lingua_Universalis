package net.neoforged.neoforge.registries;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;

/**
 * 桩：签名对齐 NeoForge 26.2 官方 DeferredRegister（源码核实）。
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

    public static Entities createEntities(String modId) {
        return new Entities();
    }

    public static DataComponents createDataComponents(
            ResourceKey<? extends Registry<DataComponentType<?>>> key, String modId) {
        return new DataComponents();
    }

    public void register(IEventBus bus) {
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        return new DeferredHolder<>();
    }

    public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(
                String name, UnaryOperator<DataComponentType.Builder<D>> builder) {
            return new DeferredHolder<>();
        }
    }

    public static class Items extends DeferredRegister<Item> {
        public DeferredItem<Item> registerSimpleItem(String name) {
            return new DeferredItem<>();
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return new DeferredItem<>();
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return new DeferredItem<>();
        }

        public <I extends Item> DeferredItem<I> registerItem(
                String name,
                java.util.function.Function<Item.Properties, ? extends I> factory,
                UnaryOperator<Item.Properties> properties) {
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

        public <B extends Block> DeferredBlock<B> registerBlock(
                String name,
                java.util.function.Function<BlockBehaviour.Properties, ? extends B> factory,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            return new DeferredBlock<>();
        }
    }

    public static class Entities extends DeferredRegister<EntityType<?>> {
        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(
                String name, EntityType.EntityFactory<E> factory, MobCategory category,
                UnaryOperator<EntityType.Builder<E>> builder) {
            return new DeferredHolder<>();
        }
    }
}
