package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** 最小桩：仅供离线语法校验。 */
public final class Registries {
    public static final ResourceKey<Registry<net.minecraft.world.item.CreativeModeTab>> CREATIVE_MODE_TAB =
            new ResourceKey<>();
    public static final ResourceKey<Registry<net.minecraft.world.level.block.Block>> BLOCK =
            new ResourceKey<>();
    public static final ResourceKey<Registry<net.minecraft.world.item.Item>> ITEM =
            new ResourceKey<>();

    private Registries() {
    }
}
