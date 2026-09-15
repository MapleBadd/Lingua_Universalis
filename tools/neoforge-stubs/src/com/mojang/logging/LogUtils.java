package com.mojang.logging;

/** 桩：getLogger。 */
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
