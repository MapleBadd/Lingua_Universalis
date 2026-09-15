package net.neoforged.neoforge.common;

import net.neoforged.bus.api.IEventBus;

/** 桩：游戏事件总线。 */
public final class NeoForge {
    public static final IEventBus EVENT_BUS = new IEventBus() {
        @Override
        public <T> void addListener(java.util.function.Consumer<T> listener) {
        }
    };

    private NeoForge() {
    }
}
