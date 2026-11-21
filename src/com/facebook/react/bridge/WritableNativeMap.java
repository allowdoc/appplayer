package com.facebook.react.bridge;

import java.util.List;
import java.util.Map;

public final class WritableNativeMap extends ReadableNativeMap implements WritableMap {

    public WritableNativeMap() {
        super();
        initHybrid();
    }

    public WritableNativeMap(Map<String, Object> map) {
        super(map);
        initHybrid();
    }

    /**
     * DEVICE BRIDGE SEMANTICS: on a real phone every nested value inside a
     * cross-boundary map is itself a bridge type (ReadableNativeMap /
     * ReadableNativeArray) produced by the C++ serialization layer. The
     * runner's stdio bridge materializes JSON as plain java.util structures,
     * so the map constructor performs the same deep conversion the C++
     * bridge does - apps (including obfuscated RCTView property setters)
     * can then cast values to ReadableMap / ReadableArray exactly like on a
     * device, with zero app-side knowledge or patching.
     */
    public static Object deepConvert(Object val) {
        if (val instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) val;
            return new WritableNativeMap(m);
        }
        if (val instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) val;
            return new WritableNativeArray(l);
        }
        return val;
    }

    @Override
    protected void adoptMap(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            mMap.put(e.getKey(), deepConvert(e.getValue()));
        }
    }

    private void initHybrid() {
        // Pure-Java headless mode: no JNI / libreactnativejni.so required
    }

    @Override
    public void putNull(String key) {
        mMap.put(key, null);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        mMap.put(key, value);
    }

    @Override
    public void putDouble(String key, double value) {
        mMap.put(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        mMap.put(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        mMap.put(key, value);
    }

    @Override
    public void putString(String key, String value) {
        mMap.put(key, value);
    }

    @Override
    public void putArray(String key, ReadableArray value) {
        mMap.put(key, value);
    }

    @Override
    public void putMap(String key, ReadableMap value) {
        mMap.put(key, value);
    }

    @Override
    public void merge(ReadableMap source) {
        if (source != null) {
            // Preserve bridge types: copy through the source's own map
            // (already deep-converted), not through toHashMap() which
            // materializes raw java.util structures.
            if (source instanceof ReadableNativeMap) {
                mMap.putAll(((ReadableNativeMap) source).mMap);
            } else {
                mMap.putAll(source.toHashMap());
            }
        }
    }

    @Override
    public WritableMap copy() {
        WritableNativeMap target = new WritableNativeMap();
        target.merge(this);
        return target;
    }
}
