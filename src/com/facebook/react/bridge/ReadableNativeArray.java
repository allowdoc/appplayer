package com.facebook.react.bridge;

import java.util.ArrayList;
import java.util.List;

public class ReadableNativeArray implements ReadableArray {

    protected final List<Object> mList;

    public ReadableNativeArray() {
        this.mList = new ArrayList<>();
    }

    public ReadableNativeArray(List<Object> list) {
        this.mList = new ArrayList<>();
        if (list != null) {
            // Device-bridge semantics: nested values become bridge types
            // (WritableNativeMap.deepConvert), exactly like the C++ bridge
            // serialization layer produces on a real phone.
            for (Object v : list) {
                this.mList.add(WritableNativeMap.deepConvert(v));
            }
        }
    }

    @Override
    public int size() {
        return mList.size();
    }

    @Override
    public boolean isNull(int index) {
        return index < 0 || index >= mList.size() || mList.get(index) == null;
    }

    @Override
    public boolean getBoolean(int index) {
        Object val = mList.get(index);
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(String.valueOf(val));
    }

    @Override
    public double getDouble(int index) {
        Object val = mList.get(index);
        if (val instanceof Number) return ((Number) val).doubleValue();
        // Real Android throws for type mismatches; a silent 0.0 default
        // corrupted downstream JSON encoding and callback payloads.
        throw new java.lang.IllegalStateException(
                "Index " + index + " is not a number: " + (val == null ? "null" : val.getClass().getName()));
    }

    @Override
    public int getInt(int index) {
        Object val = mList.get(index);
        if (val instanceof Number) return ((Number) val).intValue();
        throw new java.lang.IllegalStateException(
                "Index " + index + " is not an int: " + (val == null ? "null" : val.getClass().getName()));
    }

    @Override
    public long getLong(int index) {
        Object val = mList.get(index);
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public String getString(int index) {
        Object val = mList.get(index);
        return (val != null) ? String.valueOf(val) : null;
    }

    @Override
    public ReadableNativeArray getArray(int index) {
        Object val = mList.get(index);
        if (val instanceof ReadableNativeArray) {
            return (ReadableNativeArray) val;
        }
        if (val instanceof List) {
            return new ReadableNativeArray((List<Object>) val);
        }
        return new ReadableNativeArray();
    }

    @Override
    public ReadableNativeMap getMap(int index) {
        Object val = mList.get(index);
        if (val instanceof ReadableNativeMap) {
            return (ReadableNativeMap) val;
        }
        if (val instanceof java.util.Map) {
            return new ReadableNativeMap((java.util.Map<String, Object>) val);
        }
        return new ReadableNativeMap();
    }

    @Override
    public Dynamic getDynamic(int index) {
        return new DynamicValue(index < mList.size() ? mList.get(index) : null);
    }

    @Override
    public ReadableType getType(int index) {
        if (index < 0 || index >= mList.size()) return ReadableType.Null;
        Object val = mList.get(index);
        if (val == null) return ReadableType.Null;
        if (val instanceof Boolean) return ReadableType.Boolean;
        if (val instanceof Number) return ReadableType.Number;
        if (val instanceof String) return ReadableType.String;
        if (val instanceof ReadableMap || val instanceof java.util.Map) return ReadableType.Map;
        if (val instanceof ReadableArray || val instanceof List) return ReadableType.Array;
        return ReadableType.String;
    }

    @Override
    public ArrayList<Object> toArrayList() {
        ArrayList<Object> copy = new ArrayList<>();
        for (Object item : mList) {
            if (item instanceof ReadableArray) {
                copy.add(((ReadableArray) item).toArrayList());
            } else if (item instanceof ReadableMap) {
                copy.add(((ReadableMap) item).toHashMap());
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    private static class DynamicValue implements Dynamic {
        private final Object val;

        public DynamicValue(Object val) {
            this.val = val;
        }

        @Override
        public boolean isNull() {
            return val == null;
        }

        @Override
        public boolean asBoolean() {
            if (val instanceof Boolean) return (Boolean) val;
            return Boolean.parseBoolean(String.valueOf(val));
        }

        @Override
        public double asDouble() {
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(String.valueOf(val)); } catch (Throwable t) { return 0.0; }
        }

        @Override
        public int asInt() {
            if (val instanceof Number) return ((Number) val).intValue();
            try { return Integer.parseInt(String.valueOf(val)); } catch (Throwable t) { return 0; }
        }

        @Override
        public String asString() {
            return val != null ? String.valueOf(val) : null;
        }

        @Override
        public ReadableArray asArray() {
            if (val instanceof ReadableArray) return (ReadableArray) val;
            if (val instanceof List) return new ReadableNativeArray((List<Object>) val);
            return new ReadableNativeArray();
        }

        @Override
        public ReadableMap asMap() {
            if (val instanceof ReadableMap) return (ReadableMap) val;
            if (val instanceof java.util.Map) return new ReadableNativeMap((java.util.Map<String, Object>) val);
            return new ReadableNativeMap();
        }

        @Override
        public ReadableType getType() {
            if (val == null) return ReadableType.Null;
            if (val instanceof Boolean) return ReadableType.Boolean;
            if (val instanceof Number) return ReadableType.Number;
            if (val instanceof String) return ReadableType.String;
            if (val instanceof ReadableMap || val instanceof java.util.Map) return ReadableType.Map;
            if (val instanceof ReadableArray || val instanceof List) return ReadableType.Array;
            return ReadableType.String;
        }

        @Override
        public void recycle() {
        }
    }
}
