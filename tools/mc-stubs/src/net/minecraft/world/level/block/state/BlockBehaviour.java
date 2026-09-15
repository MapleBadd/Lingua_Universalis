package net.minecraft.world.level.block.state;

import java.util.function.UnaryOperator;

/** 最小桩：仅供离线语法校验。 */
public class BlockBehaviour {
    protected BlockBehaviour() {
    }

    public static class Properties {
        public static Properties of() {
            return new Properties();
        }

        public Properties mapColor(Object mapColor) {
            return this;
        }
    }
}
