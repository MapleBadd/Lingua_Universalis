package net.minecraft.network.chat;

/** 最小桩：仅供离线语法校验。 */
public class Component {
    private Component() {
    }

    public static Component translatable(String key) {
        return new Component();
    }
}
