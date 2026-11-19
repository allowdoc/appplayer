package com.facebook.react.bridge;

import com.facebook.jni.HybridClassBase;

public abstract class NativeArray extends HybridClassBase implements NativeArrayInterface {
    public NativeArray() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
