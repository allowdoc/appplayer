package com.facebook.react.bridge;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 *
 * The engine sources reference standard React Native interface names so they can
 * be compiled WITHOUT any app jar present. At runtime the real classes from the
 * app's own jar (standard, non-obfuscated names in every RN app) are used.
 * Signatures mirror RN 0.74, the version the bridge was designed against.
 */
public interface Callback {
    void invoke(Object... args);
}
