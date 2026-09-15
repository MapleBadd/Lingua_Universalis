package com.mojang.logging;

/** 最小桩：仅供离线语法校验。 */
public final class LogUtils {
    private LogUtils() {
    }

    public static org.slf4j.Logger getLogger() {
        return new org.slf4j.Logger() {
            @Override
            public void info(String format, Object... args) {
            }
        };
    }
}
