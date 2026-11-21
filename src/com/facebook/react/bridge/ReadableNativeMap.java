package com.facebook.react.bridge;

import java.util.*;

public class ReadableNativeMap extends NativeMap implements ReadableMap {

    protected final Map<String, Object> mMap;

    public ReadableNativeMap() {
        this.mMap = new LinkedHashMap<>();
    }

    public ReadableNativeMap(Map<String, Object> map) {
        this.mMap = new LinkedHashMap<>();
        adoptMap(map);
    }

    /**
     * DEVICE BRIDGE SEMANTICS (androidfw-equivalent for the JS bridge): on a
     * real phone every nested value inside a cross-boundary map is itself a
     * bridge type (ReadableNativeMap / ReadableNativeArray) produced by the
     * C++ serialization layer. The runner's stdio bridge materializes JSON
     * as plain java.util structures, so adoption performs the same deep
     * conversion the C++ bridge does - apps (including obfuscated RCTView
     * property setters) can then cast values to ReadableMap / ReadableArray
     * exactly like on a device, with zero app-side knowledge or patching.
     */
    @Override
    protected void adoptMap(Map<String, Object> map) {
        if (map == null) return;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            mMap.put(e.getKey(), WritableNativeMap.deepConvert(e.getValue()));
        }
    }

    @Override
    public boolean hasKey(String name) {
        return mMap.containsKey(name);
    }

    @Override
    public boolean isNull(String name) {
        return !mMap.containsKey(name) || mMap.get(name) == null;
    }

    @Override
    public boolean getBoolean(String name) {
        Object val = mMap.get(name);
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(String.valueOf(val));
    }

    @Override
    public double getDouble(String name) {
        Object val = mMap.get(name);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (Throwable t) {
            return 0.0;
        }
    }

    @Override
    public int getInt(String name) {
        Object val = mMap.get(name);
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public long getLong(String name) {
        Object val = mMap.get(name);
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Throwable t) {
            return 0L;
        }
    }

    @Override
    public String getString(String name) {
        Object val = mMap.get(name);
        return (val != null) ? String.valueOf(val) : null;
    }

    @Override
    public ReadableNativeArray getArray(String name) {
        Object val = mMap.get(name);
        if (val instanceof ReadableNativeArray) {
            return (ReadableNativeArray) val;
        }
        if (val instanceof List) {
            return new ReadableNativeArray((List<Object>) val);
        }
        return new ReadableNativeArray();
    }

    @Override
    public ReadableNativeMap getMap(String name) {
        Object val = mMap.get(name);
        if (val instanceof ReadableNativeMap) {
            return (ReadableNativeMap) val;
        }
        if (val instanceof Map) {
            return new ReadableNativeMap((Map<String, Object>) val);
        }
        return new ReadableNativeMap();
    }

    @Override
    public Dynamic getDynamic(String name) {
        return new DynamicMapValue(mMap.get(name));
    }

    @Override
    public ReadableType getType(String name) {
        if (!mMap.containsKey(name)) return ReadableType.Null;
        Object val = mMap.get(name);
        if (val == null) return ReadableType.Null;
        if (val instanceof Boolean) return ReadableType.Boolean;
        if (val instanceof Number) return ReadableType.Number;
        if (val instanceof String) return ReadableType.String;
        if (val instanceof ReadableMap || val instanceof Map) return ReadableType.Map;
        if (val instanceof ReadableArray || val instanceof List) return ReadableType.Array;
        return ReadableType.String;
    }

    @Override
    public ReadableMapKeySetIterator keySetIterator() {
        final Iterator<String> iterator = mMap.keySet().iterator();
        return new ReadableMapKeySetIterator() {
            @Override
            public boolean hasNextKey() {
                return iterator.hasNext();
            }

            @Override
            public String nextKey() {
                return iterator.next();
            }
        };
    }

    @Override
    public Iterator<Map.Entry<String, Object>> getEntryIterator() {
        return mMap.entrySet().iterator();
    }

    @Override
    public HashMap<String, Object> toHashMap() {
        HashMap<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : mMap.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof ReadableArray) {
                copy.put(entry.getKey(), ((ReadableArray) val).toArrayList());
            } else if (val instanceof ReadableMap) {
                copy.put(entry.getKey(), ((ReadableMap) val).toHashMap());
            } else {
                copy.put(entry.getKey(), val);
            }
        }
        return copy;
    }

    private static class DynamicMapValue implements Dynamic {
        private final Object val;

        public DynamicMapValue(Object val) {
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
            if (val instanceof Map) return new ReadableNativeMap((Map<String, Object>) val);
            return new ReadableNativeMap();
        }

        @Override
        public ReadableType getType() {
            if (val == null) return ReadableType.Null;
            if (val instanceof Boolean) return ReadableType.Boolean;
            if (val instanceof Number) return ReadableType.Number;
            if (val instanceof String) return ReadableType.String;
            if (val instanceof ReadableMap || val instanceof Map) return ReadableType.Map;
            if (val instanceof ReadableArray || val instanceof List) return ReadableType.Array;
            return ReadableType.String;
        }

        @Override
        public void recycle() {
        }
    }
}
