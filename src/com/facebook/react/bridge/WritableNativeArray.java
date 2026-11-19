package com.facebook.react.bridge;

import java.util.List;

public final class WritableNativeArray extends ReadableNativeArray implements WritableArray {

    public WritableNativeArray() {
        super();
        initHybrid();
    }

    public WritableNativeArray(List<Object> list) {
        super(list);
        initHybrid();
    }

    private void initHybrid() {
        // Pure-Java headless mode: no JNI / libreactnativejni.so required
    }

    @Override
    public void pushNull() {
        mList.add(null);
    }

    @Override
    public void pushBoolean(boolean value) {
        mList.add(value);
    }

    @Override
    public void pushDouble(double value) {
        mList.add(value);
    }

    @Override
    public void pushInt(int value) {
        mList.add(value);
    }

    @Override
    public void pushLong(long value) {
        mList.add(value);
    }

    @Override
    public void pushString(String value) {
        mList.add(value);
    }

    @Override
    public void pushArray(ReadableArray array) {
        mList.add(array);
    }

    @Override
    public void pushMap(ReadableMap map) {
        mList.add(map);
    }
}
