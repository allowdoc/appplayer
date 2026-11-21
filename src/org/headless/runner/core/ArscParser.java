package org.headless.runner.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Universal binary resources.arsc parser ("Android Wine" resource runtime).
 *
 * Parses the compiled resource table shipped inside ANY APK:
 *   - global value string pool
 *   - packages with their type/key string pools
 *   - TYPE chunks (per config) with entries: simple values AND bags (styles/attrs)
 *
 * Everything the framework resource manager needs at runtime:
 *   id -> (type, name, value/bag), name -> id, style parent chains.
 *
 * Format references: AOSP frameworks/base/libs/androidfw/ResourceTypes.h
 * Validated against real aapt2-produced tables (agp 8.x).
 */
public final class ArscParser {

    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_TABLE_TYPE = 0x0002;
    private static final int RES_TABLE_PACKAGE_TYPE = 0x0200;
    private static final int RES_TABLE_TYPE_TYPE = 0x0201;
    private static final int RES_TABLE_TYPE_SPEC_TYPE = 0x0202;

    // Res_value data types (mirror android.util.TypedValue constants)
    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_ATTRIBUTE = 0x02;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_DIMENSION = 0x05;
    public static final int TYPE_FRACTION = 0x06;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;
    public static final int TYPE_FIRST_COLOR_INT = 0x1c;
    public static final int TYPE_LAST_COLOR_INT = 0x1f;

    public static final class ResVal {
        public final int type;
        public final int data;
        public final String str;

        public ResVal(int type, int data, String str) {
            this.type = type;
            this.data = data;
            this.str = str;
        }

        public boolean isString() { return type == TYPE_STRING && str != null; }
        public boolean isReference() { return type == TYPE_REFERENCE || type == TYPE_ATTRIBUTE; }
        public boolean isBoolean() { return type == TYPE_INT_BOOLEAN; }
        public boolean isColor() { return type >= TYPE_FIRST_COLOR_INT && type <= TYPE_LAST_COLOR_INT; }

        public boolean asBoolean(boolean def) {
            if (type == TYPE_INT_BOOLEAN) return data != 0;
            return def;
        }

        public int asInt(int def) {
            if (type == TYPE_INT_DEC || type == TYPE_INT_HEX || isColor()
                    || type == TYPE_INT_BOOLEAN || type == TYPE_REFERENCE || type == TYPE_ATTRIBUTE) {
                return data;
            }
            return def;
        }

        public float asFloat(float def) {
            if (type == TYPE_FLOAT) return Float.intBitsToFloat(data);
            return def;
        }

        public float asDimension(DisplayMetricsHolder dm, float def) {
            if (type == TYPE_DIMENSION) {
                // complex: MANTISSA multiplies; unit in top 4 bits
                float f = android.util.TypedValue.complexToFloat(data);
                int unit = (data >> android.util.TypedValue.COMPLEX_UNIT_SHIFT)
                        & android.util.TypedValue.COMPLEX_UNIT_MASK;
                float metricsScale = 1.0f;
                if (dm != null) {
                    switch (unit) {
                        case android.util.TypedValue.COMPLEX_UNIT_DIP:
                            metricsScale = dm.density; break;
                        case android.util.TypedValue.COMPLEX_UNIT_SP:
                            metricsScale = dm.scaledDensity > 0 ? dm.scaledDensity : dm.density; break;
                        case android.util.TypedValue.COMPLEX_UNIT_PT:
                            metricsScale = dm.xdpi / 72.0f; break;
                        case android.util.TypedValue.COMPLEX_UNIT_IN:
                            metricsScale = dm.xdpi; break;
                        case android.util.TypedValue.COMPLEX_UNIT_MM:
                            metricsScale = dm.xdpi / 25.4f; break;
                        default:
                            metricsScale = 1.0f; // px
                    }
                }
                return f * metricsScale;
            }
            if (type == TYPE_FRACTION) {
                float f = android.util.TypedValue.complexToFloat(data);
                int unit = (data >> android.util.TypedValue.COMPLEX_UNIT_SHIFT)
                        & android.util.TypedValue.COMPLEX_UNIT_MASK;
                return unit == android.util.TypedValue.COMPLEX_UNIT_FRACTION_PARENT ? f * 100.0f : f;
            }
            return def;
        }

        public String asString() {
            if (str != null) return str;
            if (type == TYPE_INT_DEC) return String.valueOf(data);
            if (type == TYPE_INT_HEX) return "0x" + Integer.toHexString(data);
            if (type == TYPE_INT_BOOLEAN) return data != 0 ? "true" : "false";
            return null;
        }

        @Override
        public String toString() {
            return "ResVal{t=" + Integer.toHexString(type) + ",d=" + Integer.toHexString(data)
                    + (str != null ? ",s=" + str : "") + "}";
        }
    }

    /** Density holder so the parser stays decoupled from android.* imports. */
    public static final class DisplayMetricsHolder {
        public float density = 2.625f;
        public float scaledDensity = 2.625f;
        public float xdpi = 420.0f;
    }

    public static final class Bag {
        public final int parentResId;
        public final int[] attrNames;
        public final ResVal[] values;

        Bag(int parentResId, int[] attrNames, ResVal[] values) {
            this.parentResId = parentResId;
            this.attrNames = attrNames;
            this.values = values;
        }

        public ResVal get(int attrId) {
            for (int i = 0; i < attrNames.length; i++) {
                if (attrNames[i] == attrId) return values[i];
            }
            return null;
        }
    }

    public static final class Entry {
        public final int resId;
        public final String type;
        public String name;
        public ResVal value;   // non-bag entry
        public Bag bag;        // style/attr/plurals containers

        Entry(int resId, String type, String name) {
            this.resId = resId;
            this.type = type;
            this.name = name;
        }

        public String fullName() { return type + "/" + name; }
    }

    private final Map<Integer, Entry> entries = new HashMap<>();
    private final Map<String, Integer> nameToId = new HashMap<>();
    private final Map<Integer, String> idToFullName = new HashMap<>();
    private final DisplayMetricsHolder metrics = new DisplayMetricsHolder();
    public final int packageId;
    private boolean parsed = false;

    private static volatile ArscParser sShared;

    /** Parse once per process and reuse (fast: a 600KB table). */
    public static ArscParser getShared(File apkFile) {
        ArscParser p = sShared;
        if (p != null) return p;
        synchronized (ArscParser.class) {
            if (sShared == null) sShared = fromApk(apkFile);
            return sShared;
        }
    }

    public java.util.Collection<Entry> allEntries() { return entries.values(); }

    private ArscParser(int packageId) {
        this.packageId = packageId;
    }

    public static ArscParser fromApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) return null;
        try {
            byte[] data = readArsc(apkFile);
            if (data == null) return null;
            return parse(data);
        } catch (Throwable t) {
            System.out.println("[-] [ArscParser] Failed to parse resources.arsc: " + t);
            return null;
        }
    }

    private static byte[] readArsc(File apkFile) throws Exception {
        try (ZipFile zf = new ZipFile(apkFile)) {
            ZipEntry e = zf.getEntry("resources.arsc");
            if (e == null) return null;
            try (InputStream is = zf.getInputStream(e);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        }
    }

    private static ArscParser parse(byte[] data) {
        int tblType = u16(data, 0);
        if (tblType != RES_TABLE_TYPE) {
            throw new IllegalStateException("Not a RES_TABLE chunk: " + Integer.toHexString(tblType));
        }
        int tableHeaderSize = u16(data, 2);
        int tableSize = u32(data, 4);
        int packageCount = u32(data, 8);

        // Global value string pool sits directly after the table header.
        String[] valueStrings = parseStringPool(data, tableHeaderSize);

        ArscParser out = new ArscParser(0x7f);
        int off = tableHeaderSize + u32(data, tableHeaderSize + 4); // skip global pool chunk
        int end = Math.min(tableHeaderSize + tableSize, data.length);
        int parsedPkgs = 0;
        while (off + 8 <= end && parsedPkgs < packageCount) {
            int chunkType = u16(data, off);
            int chunkSize = u32(data, off + 4);
            if (chunkSize <= 0) break;
            if (chunkType == RES_TABLE_PACKAGE_TYPE) {
                try {
                    out.parsePackage(data, off, chunkSize, valueStrings);
                } catch (Throwable t) {
                    System.out.println("[-] [ArscParser] Package parse warning: " + t);
                }
                parsedPkgs++;
            }
            off += chunkSize;
        }
        out.parsed = true;
        return out;
    }

    private void parsePackage(byte[] data, int pkgOff, int pkgSize, String[] valueStrings) {
        int pkgId = u32(data, pkgOff + 8);
        int typeStringsOff = u32(data, pkgOff + 268);
        int keyStringsOff = u32(data, pkgOff + 276);

        String[] typeNames = typeStringsOff > 0 ? parseStringPool(data, pkgOff + typeStringsOff) : new String[0];
        String[] keyNames = keyStringsOff > 0 ? parseStringPool(data, pkgOff + keyStringsOff) : new String[0];

        int co = pkgOff + u16(data, pkgOff + 2); // after package header
        int end = pkgOff + pkgSize;
        while (co + 8 <= end) {
            int chunkType = u16(data, co);
            int chunkSize = u32(data, co + 4);
            if (chunkSize <= 0) break;
            if (chunkType == RES_TABLE_TYPE_TYPE) {
                try {
                    parseTypeChunk(data, co, chunkSize, pkgId, typeNames, keyNames, valueStrings);
                } catch (Throwable t) {
                    // A malformed config must not kill the whole table.
                }
            }
            co += chunkSize;
        }
    }

    private void parseTypeChunk(byte[] data, int co, int chunkSize, int pkgId,
                                String[] typeNames, String[] keyNames, String[] valueStrings) {
        int typeId = data[co + 8] & 0xFF;
        if (typeId == 0 || typeId > typeNames.length) return;
        String typeName = typeNames[typeId - 1];
        int entryCount = u32(data, co + 12);
        int entriesStart = u32(data, co + 16);
        if (entryCount <= 0 || entryCount > 0x100000) return;

        int offsetsPos = co + u16(data, co + 2); // header size
        int entriesBase = co + entriesStart;

        for (int i = 0; i < entryCount; i++) {
            int eo = u32(data, offsetsPos + i * 4);
            if (eo == 0xFFFFFFFF) continue;
            int ap = entriesBase + eo;
            if (ap + 8 > data.length) continue;
            int entrySize = u16(data, ap);
            int entryFlags = u16(data, ap + 2);
            int keyIndex = u32(data, ap + 4);

            int resId = (pkgId << 24) | (typeId << 16) | i;
            String name = (keyIndex >= 0 && keyIndex < keyNames.length) ? keyNames[keyIndex] : null;
            if (name == null) name = "entry_" + Integer.toHexString(resId);

            Entry e = entries.get(resId);
            if (e == null) {
                e = new Entry(resId, typeName, name);
                entries.put(resId, e);
            } else {
                e.name = name; // keep the most recently seen (config variants share id)
            }

            if ((entryFlags & 0x0001) != 0) {
                // ResTable_map_entry: parent + count + map items
                if (ap + 16 > data.length) continue;
                int parent = u32(data, ap + 8);
                int count = u32(data, ap + 12);
                int[] attrNames = new int[count];
                ResVal[] vals = new ResVal[count];
                int mp = ap + 16;
                // Each bag item is a full ResTable_map: name(4) + Res_value(8)
                // = 12 bytes (sizeof(ResTable_map) in ResourceTypes.h). The
                // old stride of 8 desynced every pair after the first and
                // corrupted every theme/style attribute lookup.
                for (int k = 0; k < count; k++) {
                    if (mp + 12 > data.length) { count = k; attrNames = trim(attrNames, k); vals = trimV(vals, k); break; }
                    attrNames[k] = u32(data, mp);
                    vals[k] = readValue(data, mp + 4, valueStrings);
                    mp += 12;
                }
                e.bag = new Bag(parent, attrNames, vals);
            } else {
                // simple entry: Res_value right after the 8-byte header
                if (ap + 16 > data.length) continue;
                e.value = readValue(data, ap + 8, valueStrings);
            }

            String full = typeName + "/" + name;
            if (!nameToId.containsKey(full)) nameToId.put(full, resId);
            idToFullName.put(resId, full);
        }
    }

    private static int[] trim(int[] a, int n) {
        int[] r = new int[n];
        System.arraycopy(a, 0, r, 0, n);
        return r;
    }

    private static ResVal[] trimV(ResVal[] a, int n) {
        ResVal[] r = new ResVal[n];
        System.arraycopy(a, 0, r, 0, n);
        return r;
    }

    private static ResVal readValue(byte[] data, int pos, String[] valueStrings) {
        int dataType = data[pos + 3] & 0xFF;
        int vdata = u32(data, pos + 4);
        String s = null;
        if (dataType == TYPE_STRING && (vdata & 0x80000000) == 0
                && vdata >= 0 && vdata < valueStrings.length) {
            s = valueStrings[vdata];
        }
        return new ResVal(dataType, vdata, s);
    }

    /** Parse a RES_STRING_POOL_TYPE chunk (UTF-8 and UTF-16 variants). */
    private static String[] parseStringPool(byte[] data, int base) {
        int headerSize = u16(data, base + 2);
        int stringCount = u32(data, base + 8);
        int flags = u32(data, base + 16);
        int stringsStart = u32(data, base + 20);
        boolean utf8 = (flags & 0x100) != 0;
        if (stringCount < 0 || stringCount > 0x200000) return new String[0];

        String[] out = new String[stringCount];
        int offsetsPos = base + headerSize;
        int poolStart = base + stringsStart;
        for (int i = 0; i < stringCount; i++) {
            try {
                int o = poolStart + u32(data, offsetsPos + i * 4);
                if (utf8) {
                    // u16len (varint, len in UTF-16 units), then u8len, then bytes
                    int len16 = readVarint(data, o);
                    o += varintSize(data, o);
                    int len8 = readVarint(data, o);
                    o += varintSize(data, o);
                    out[i] = new String(data, o, Math.min(len8, data.length - o), StandardCharsets.UTF_8);
                } else {
                    int len = u16(data, o);
                    int hdr = 2;
                    if ((len & 0x8000) != 0) {
                        int hi = u16(data, o + 2);
                        len = ((len & 0x7FFF) << 16) | hi;
                        hdr = 4;
                    }
                    out[i] = new String(data, o + hdr, Math.min(len * 2, data.length - o - hdr),
                            StandardCharsets.UTF_16LE);
                }
            } catch (Throwable t) {
                out[i] = "";
            }
        }
        return out;
    }

    private static int readVarint(byte[] data, int pos) {
        int v = 0, shift = 0;
        while (pos < data.length) {
            int b = data[pos] & 0xFF;
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            pos++;
        }
        return v;
    }

    private static int varintSize(byte[] data, int pos) {
        int n = 0;
        while (pos + n < data.length) {
            int b = data[pos + n] & 0xFF;
            n++;
            if ((b & 0x80) == 0) break;
        }
        return n;
    }

    private static int u16(byte[] d, int off) { return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8); }

    private static int u32(byte[] d, int off) {
        return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8) | ((d[off + 2] & 0xFF) << 16) | ((d[off + 3] & 0xFF) << 24);
    }

    // =====================================================================
    //  Public query API
    // =====================================================================

    public boolean isParsed() { return parsed; }

    public Entry getEntry(int resId) { return entries.get(resId); }

    public int getIdentifier(String type, String name) {
        Integer id = nameToId.get(type + "/" + name);
        return id != null ? id : 0;
    }

    public String getEntryName(int resId) {
        Entry e = entries.get(resId);
        return e != null ? e.name : null;
    }

    public String getEntryType(int resId) {
        Entry e = entries.get(resId);
        return e != null ? e.type : null;
    }

    public String getFullName(int resId) { return idToFullName.get(resId); }

    /** Resolve a simple value (following one level of references). */
    public ResVal getValue(int resId) {
        return getValue(resId, 0);
    }

    private ResVal getValue(int resId, int depth) {
        Entry e = entries.get(resId);
        if (e == null || e.value == null) return null;
        ResVal v = e.value;
        if (v.isReference() && depth < 8) {
            ResVal inner = getValue(v.data, depth + 1);
            if (inner != null) return inner;
        }
        return v;
    }

    public String getString(int resId) {
        ResVal v = getValue(resId);
        return v != null ? v.asString() : null;
    }

    public boolean getBoolean(int resId, boolean def) {
        ResVal v = getValue(resId);
        return v != null ? v.asBoolean(def) : def;
    }

    public int getInt(int resId, int def) {
        ResVal v = getValue(resId);
        return v != null ? v.asInt(def) : def;
    }

    public int getColor(int resId, int def) {
        ResVal v = getValue(resId);
        return v != null ? v.asInt(def) : def;
    }

    public float getDimension(int resId, float def) {
        ResVal v = getValue(resId);
        return v != null ? v.asDimension(metrics, def) : def;
    }

    /**
     * Resolve a theme: walk the style's parent chain collecting attr -> value.
     * Child values override parent values. Values are kept in their compiled
     * form (references stay references, androidfw semantics) so TypedArray
     * accessors can dereference them through Resources like on a device.
     * Attribute chains (?attr/x -> ?attr/y -> value) are resolved exactly
     * like ResTable::Theme::getAttribute does on-device.
     */
    public LinkedHashMap<Integer, ResVal> resolveTheme(int themeResId) {
        LinkedHashMap<Integer, ResVal> map = new LinkedHashMap<>();
        collectTheme(themeResId, map, 0);
        // Post-pass: resolve TYPE_ATTRIBUTE chains through the collected map
        // (androidfw loops while value.type == TYPE_ATTRIBUTE).
        Integer[] attrs = map.keySet().toArray(new Integer[0]);
        for (Integer attr : attrs) {
            ResVal v = map.get(attr);
            int hops = 0;
            while (v != null && v.type == TYPE_ATTRIBUTE && hops < 8) {
                ResVal next = map.get(v.data);
                if (next == null || next == v) break;   // unresolvable: keep as-is
                v = next;
                hops++;
            }
            if (v != null) map.put(attr, v);
        }
        return map;
    }

    private void collectTheme(int styleResId, LinkedHashMap<Integer, ResVal> map, int depth) {
        if (depth > 40 || styleResId == 0) return;
        Entry e = entries.get(styleResId);
        if (e == null || e.bag == null) return;
        // parent first so child overrides win
        collectTheme(e.bag.parentResId, map, depth + 1);
        for (int i = 0; i < e.bag.attrNames.length; i++) {
            ResVal v = e.bag.values[i];
            if (v == null) continue;
            // Keep the compiled value: references/attributes are resolved
            // lazily by TypedArray through Resources (device behavior).
            map.put(e.bag.attrNames[i], v);
        }
    }
}
