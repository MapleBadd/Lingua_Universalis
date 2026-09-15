package net.neoforged.neoforge.registries;

import java.util.function.Supplier;

/** 最小桩：仅供离线语法校验。 */
public class DeferredHolder<A, B> implements Supplier<B> {
    @Override
    public B get() {
        return null;
    }
}
