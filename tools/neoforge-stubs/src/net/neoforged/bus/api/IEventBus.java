package net.neoforged.bus.api;

import java.util.function.Consumer;

/** 桩：仅覆盖工程使用的 addListener 用法。 */
public interface IEventBus {
    <T> void addListener(Consumer<T> listener);
}
