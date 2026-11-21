package com.facebook.react.bridge;

import com.facebook.jni.HybridClassBase;

/**
 * Pure-Java NativeArray shim. Extends ReadableNativeArray so every value
 * carried across the universal stdio bridge implements the full ReadableArray
 * contract (getters + toArrayList) expected by the app's real native modules.
 */
public abstract class NativeArray extends ReadableNativeArray implements NativeArrayInterface {
    public NativeArray() {
        super();
    }

    public NativeArray(java.util.List<Object> list) {
        super(list);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + toArrayList();
    }
}
