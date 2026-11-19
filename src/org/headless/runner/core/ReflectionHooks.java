package org.headless.runner.core;

import java.lang.reflect.Field;

public class ReflectionHooks {

    // Dummy field returned when Android libraries reflect on "accessFlags" (ART/Dalvik specific)
    public static int accessFlags = 0;

    public static Field getDeclaredField(Class<?> cls, String name) throws NoSuchFieldException {
        if ("accessFlags".equals(name)) {
            try {
                return ReflectionHooks.class.getField("accessFlags");
            } catch (Throwable ignored) {
            }
        }
        return cls.getDeclaredField(name);
    }
}
