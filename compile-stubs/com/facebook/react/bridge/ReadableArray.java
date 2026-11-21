package com.facebook.react.bridge;

import java.util.List;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 * Mirrors the RN ReadableArray members the engine calls. Runtime uses the
 * app's real interface.
 */
public interface ReadableArray {
    List<Object> toArrayList();
    int size();
    boolean isNull(int index);
    double getDouble(int index);
    int getInt(int index);
    String getString(int index);
    boolean getBoolean(int index);
    ReadableMap getMap(int index);
    ReadableArray getArray(int index);
    Dynamic getDynamic(int index);
    ReadableType getType(int index);
}
