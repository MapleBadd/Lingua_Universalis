package com.linguauniversalis.registry;

/**
 * 注册层常量（与 {@code LinguaUniversalis.MODID} 同值）。
 * 独立出来使 registry 包不依赖主类，便于 API 桩离线编译校验。
 */
public final class LUConstants {
    public static final String MODID = "lingua_universalis";

    private LUConstants() {
    }
}
