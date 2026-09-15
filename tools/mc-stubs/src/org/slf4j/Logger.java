package org.slf4j;

/** 最小桩：仅供离线语法校验。 */
public interface Logger {
    void info(String format, Object... args);
}
