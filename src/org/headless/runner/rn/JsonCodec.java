package org.headless.runner.rn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON codec for the React Native stdio bridge.
 * Handles the exact value domain the RN bridge permits:
 * null / boolean / number / string / arrays / maps.
 * Reads both com.facebook.react.bridge.Readable* shims and plain Java collections.
 */
public final class JsonCodec {

    private JsonCodec() {}

    // ---------------- Encoding ----------------

    public static String encode(Object value) {
        StringBuilder sb = new StringBuilder(256);
        encodeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void encodeValue(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof String) {
            encodeString((String) v, sb);
            return;
        }
        if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
            return;
        }
        if (v instanceof Number) {
            Number n = (Number) v;
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    sb.append("null");
                } else if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1.0E15) {
                    sb.append((long) d);
                } else {
                    sb.append(d);
                }
            } else {
                sb.append(n.longValue());
            }
            return;
        }
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) v).entrySet()) {
                if (e.getKey() == null) continue;
                if (!first) sb.append(',');
                first = false;
                encodeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                encodeValue(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<Object>) v) {
                if (!first) sb.append(',');
                first = false;
                encodeValue(item, sb);
            }
            sb.append(']');
            return;
        }
        if (v instanceof Object[]) {
            sb.append('[');
            Object[] arr = (Object[]) v;
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                encodeValue(arr[i], sb);
            }
            sb.append(']');
            return;
        }
        if (v instanceof com.facebook.react.bridge.ReadableArray) {
            // The value may be the app's own ReadableArray whose toArrayList()
            // is not reachable here (R8-renamed / different interface shape);
            // fall back to the plain ReadableArray contract (size + get*).
            try {
                encodeValue(((com.facebook.react.bridge.ReadableArray) v).toArrayList(), sb);
            } catch (Throwable fallback) {
                com.facebook.react.bridge.ReadableArray ra =
                        (com.facebook.react.bridge.ReadableArray) v;
                sb.append('[');
                for (int i = 0; i < ra.size(); i++) {
                    if (i > 0) sb.append(',');
                    encodeValue(readableValueAt(ra, i), sb);
                }
                sb.append(']');
            }
            return;
        }
        if (v instanceof com.facebook.react.bridge.ReadableMap) {
            try {
                encodeValue(((com.facebook.react.bridge.ReadableMap) v).toHashMap(), sb);
            } catch (Throwable fallback) {
                com.facebook.react.bridge.ReadableMap rm =
                        (com.facebook.react.bridge.ReadableMap) v;
                sb.append('{');
                com.facebook.react.bridge.ReadableMapKeySetIterator it = rm.keySetIterator();
                boolean first = true;
                while (it.hasNextKey()) {
                    String key = it.nextKey();
                    if (!first) sb.append(',');
                    first = false;
                    encodeString(key, sb);
                    sb.append(':');
                    encodeValue(readableValueAt(rm, key), sb);
                }
                sb.append('}');
            }
            return;
        }
        if (v instanceof Iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Iterable<Object>) v) list.add(item);
            encodeValue(list, sb);
            return;
        }
        // Fallback: string-ify unknown objects (DTOs etc.)
        encodeString(String.valueOf(v), sb);
    }


    /** Value of index i from any ReadableArray implementation, boxed for JSON. */
    private static Object readableValueAt(com.facebook.react.bridge.ReadableArray ra, int i) {
        // Type-driven read: calling getDouble() first on a non-number element
        // returned a shim-default 0.0 (real Android throws), which destroyed
        // every callback payload (["key",null] pairs arrived as [0] and the
        // app's storage hydration silently died).
        try {
            switch (ra.getType(i)) {
                case Null: return null;
                case Boolean: return ra.getBoolean(i);
                case Number: return ra.getDouble(i);
                case String: return ra.getString(i);
                case Map: return ra.getMap(i);
                case Array: return ra.getArray(i);
                default: return null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Value of key from any ReadableMap implementation, boxed for JSON. */
    private static Object readableValueAt(com.facebook.react.bridge.ReadableMap rm, String key) {
        // Type-driven read (see the ReadableArray variant for the rationale).
        try {
            switch (rm.getType(key)) {
                case Null: return null;
                case Boolean: return rm.getBoolean(key);
                case Number: return rm.getDouble(key);
                case String: return rm.getString(key);
                case Map: return rm.getMap(key);
                case Array: return rm.getArray(key);
                default: return null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void encodeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------- Decoding ----------------

    public static Object decode(String json) {
        Parser p = new Parser(json);
        p.skipWs();
        return p.parseValue();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> decodeList(String json) {
        Object v = decode(json);
        if (v instanceof List) return (List<Object>) v;
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeMap(String json) {
        Object v = decode(json);
        if (v instanceof Map) return (Map<String, Object>) v;
        return new LinkedHashMap<>();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseMap();
                case '[': return parseList();
                case '"': return parseString();
                case 't': i += 4; return Boolean.TRUE;
                case 'f': i += 5; return Boolean.FALSE;
                case 'n': i += 4; return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') { i++; return map; }
            while (i < s.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                i++; // :
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; break; }
            }
            return map;
        }

        List<Object> parseList() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') { i++; return list; }
            while (i < s.length()) {
                Object val = parseValue();
                list.add(val);
                skipWs();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; break; }
            }
            return list;
        }

        String parseString() {
            StringBuilder sb = new StringBuilder();
            i++; // "
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '"') { i++; break; }
                if (c == '\\') {
                    i++;
                    char e = s.charAt(i);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return Double.parseDouble(num);
            }
            // JS numbers are IEEE doubles; the real C++ bridge delivers every
            // numeric prop as a Double. Long values broke RN's FloatPropSetter
            // (ClassCastException Long->Double on 'flex') and every int prop.
            try {
                return Double.parseDouble(num);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }
}
