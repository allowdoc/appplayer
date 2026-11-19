package com.facebook.react.bridge;

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
            mMap.putAll(source.toHashMap());
        }
    }

    @Override
    public WritableMap copy() {
        WritableNativeMap target = new WritableNativeMap();
        target.merge(this);
        return target;
    }
}
