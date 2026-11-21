package com.facebook.react.module.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Compile-time stub only (never emitted into bin/). At runtime the APK's real
 * annotation class (same FQN, recovered from the app jar) is used, whose
 * {@code name()} holds the canonical JS module name, e.g. "PlatformConstants".
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ReactModule {
    String name();
    boolean canOverrideExistingModule() default false;
    boolean needsEagerInit() default false;
    boolean isCxxModule() default false;
    boolean isTurboModule() default false;
    boolean hasConstants() default true;
}
