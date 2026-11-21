package com.facebook.react.bridge;

import java.util.HashMap;

/**
 * COMPILE-TIME ONLY stub. Runtime uses the app's real interface.
 */
public interface ReadableMap {
    HashMap<String, Object> toHashMap();
    boolean hasKey(String name);
    boolean isNull(String name);
    double getDouble(String name);
    int getInt(String name);
    String getString(String name);
    boolean getBoolean(String name);
    ReadableMap getMap(String name);
    ReadableArray getArray(String name);
    Dynamic getDynamic(String name);
    ReadableMapKeySetIterator keySetIterator();
    ReadableType getType(String name);
}
