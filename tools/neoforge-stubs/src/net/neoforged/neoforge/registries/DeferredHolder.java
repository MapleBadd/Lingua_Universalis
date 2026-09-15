package net.neoforged.neoforge.registries;

import java.util.function.Supplier;

/** 桩。 */
public class DeferredHolder<A, B> implements Supplier<B> {
    @Override
    public B get() {
        return null;
    }
}
