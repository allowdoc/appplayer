package com.facebook.react.bridge;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 * Mirrors the abstract methods of RN 0.74's Promise interface. At runtime the
 * app's real Promise interface is used; implementations here cover all of them.
 */
public interface Promise {
    void resolve(Object value);
    void reject(String code, String message);
    void reject(String code, Throwable cause);
    void reject(String code, String message, Throwable cause);
    void reject(String code, String message, WritableMap throwableData);
    void reject(String code, Throwable cause, WritableMap throwableData);
    void reject(String message);
    void reject(Throwable cause);
    void reject(String message, WritableMap throwableData);
    void reject(Throwable cause, WritableMap throwableData);
}
