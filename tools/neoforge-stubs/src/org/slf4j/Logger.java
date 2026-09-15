package org.slf4j;

/** 桩：仅覆盖 info 用法。 */
public interface Logger {
    void info(String format, Object... args);
}
