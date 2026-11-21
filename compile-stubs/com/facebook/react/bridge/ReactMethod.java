package com.facebook.react.bridge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 * Mirrors RN's @ReactMethod annotation (the engine reads it via reflection;
 * at runtime the annotation resolved is the app's own real class).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReactMethod {
    boolean isBlockingSynchronousMethod() default false;
}
