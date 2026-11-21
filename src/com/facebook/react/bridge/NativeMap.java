package com.facebook.react.bridge;

import com.facebook.jni.HybridClassBase;

public abstract class NativeMap extends HybridClassBase {
    public NativeMap() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
