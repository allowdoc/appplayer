package org.headless.runner.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * NATIVE Android binary XML (AXML) parser - the runner's equivalent of
 * libandroidfw's ResXMLTree. Parses resources.arsc-compiled XML chunks
 * (layouts, drawables, colors, menus, xml, anim...) straight out of the APK,
 * with zero external processes and zero aapt2 dependency.
 *
 * This is exactly what a real device does: the app (however obfuscated) asks
 * Resources for an XML resource, the framework parses the compiled binary
 * chunk and hands back attribute values with their real typed data
 * (references, dimensions, colors, booleans...). No app knowledge involved.
 *
 * Supported chunks (ResourceTypes.h):
 *   RES_XML_TYPE 0x0003 container
 *   RES_STRING_POOL_TYPE 0x0001 (UTF-8 and UTF-16 encodings)
 *   RES_XML_RESOURCE_MAP_TYPE 0x0180 (attr index -> attr resource id)
 *   START/END_NAMESPACE 0x0100/0x0101, START/END_ELEMENT 0x0102/0x0103, CDATA 0x0104
 */
public final class AxmlTreeParser {

    private static final int RES_XML_TYPE = 0x0003;
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    private static final int RES_XML_CDATA_TYPE = 0x0104;
    private static final int RES_XML_START_NAMESPACE_TYPE = 0x0100;

    private AxmlTreeParser() {}

    /** Read a file entry out of the APK zip (handles the leading-slash normalization). */
    public static byte[] readFile(File apk, String path) {
        if (apk == null || path == null) return null;
        String p = path.startsWith("/") ? path.substring(1) : path;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry e = zip.getEntry(p);
            if (e == null) {
                // Obfuscated/shortened resource tables store paths relative to res/
                for (String cand : new String[]{ "res/" + p, "assets/" + p }) {
                    e = zip.getEntry(cand);
                    if (e != null) break;
                }
            }
            if (e == null) {
                // Last resort: suffix scan (small APKs only; exact match preferred)
                Enumeration<? extends ZipEntry> it = zip.entries();
                while (it.hasMoreElements()) {
                    ZipEntry ze = it.nextElement();
                    if (ze.getName().endsWith(p)) { e = ze; break; }
                }
            }
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1 << 15];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Parse an APK XML resource into a RealXmlResourceParser.Node tree. */
    public static RealXmlResourceParser.Node parse(byte[] xml) {
        if (xml == null || xml.length < 8) return null;
        int fileType = u16(xml, 0);
        if (fileType != RES_XML_TYPE) return null;
        int headerSize = u16(xml, 2);
        int fileSize = i32(xml, 4);
        if (fileSize <= 0 || fileSize > xml.length) fileSize = xml.length;

        String[] strings = null;
        int[] attrMap = null;
        RealXmlResourceParser.Node root = null;
        java.util.ArrayDeque<RealXmlResourceParser.Node> stack = new java.util.ArrayDeque<>();

        int off = headerSize > 0 ? headerSize : 8;
        while (off + 8 <= fileSize) {
            int type = u16(xml, off);
            int chunkHeaderSize = u16(xml, off + 2);
            int chunkSize = i32(xml, off + 4);
            if (chunkSize <= 0 || off + chunkSize > fileSize) chunkSize = fileSize - off;

            if (type == RES_STRING_POOL_TYPE) {
                strings = parseStringPool(xml, off, chunkHeaderSize, chunkSize);
            } else if (type == RES_XML_RESOURCE_MAP_TYPE) {
                int count = (chunkSize - chunkHeaderSize) / 4;
                attrMap = new int[count];
                for (int i = 0; i < count; i++) {
                    attrMap[i] = i32(xml, off + chunkHeaderSize + i * 4);
                }
            } else if (type == RES_XML_START_ELEMENT_TYPE) {
                int nodeOff = off + 16;              // skip ResXMLTree_node header
                int ns = i32(xml, nodeOff);
                int name = i32(xml, nodeOff + 4);
                int attributeStart = u16(xml, nodeOff + 8);
                int attributeSize = u16(xml, nodeOff + 10);
                int attributeCount = u16(xml, nodeOff + 12);
                String tag = str(strings, name);
                if (tag == null) tag = "";
                RealXmlResourceParser.Node node = new RealXmlResourceParser.Node();
                node.tag = tag;
                if (!stack.isEmpty()) {
                    RealXmlResourceParser.Node parent = stack.peek();
                    node.parent = parent;
                    parent.children.add(node);
                } else if (root == null) {
                    root = node;
                }
                int attrExtBase = nodeOff;
                int base = attrExtBase + attributeStart;
                for (int i = 0; i < attributeCount; i++) {
                    int a = base + i * (attributeSize > 0 ? attributeSize : 20);
                    if (a + 20 > fileSize) break;
                    int attrNameIdx = i32(xml, a + 4);
                    int rawValueIdx = i32(xml, a + 8);
                    int dataType = u8(xml, a + 15);
                    int data = i32(xml, a + 16);
                    String raw = str(strings, rawValueIdx);
                    String rendered = renderValue(dataType, data, raw, strings);

                    String attrName = str(strings, attrNameIdx);
                    if (attrName == null) continue;
                    // strip namespace prefix exactly like the aapt2 pipeline did
                    String clean = attrName;
                    int colon = clean.lastIndexOf(':');
                    if (colon >= 0) clean = clean.substring(colon + 1);

                    if (rendered == null) continue;   // NULL-typed value: skip like aapt2
                    node.attributes.put(clean, rendered);

                    // exact attr resource id via the resource-map chunk
                    int attrResId = (attrMap != null && i < attrMap.length) ? attrMap[i] : 0;
                    if (attrResId != 0) {
                        node.attrValues.put(attrResId, rendered);
                    }
                    // referenced resource ids (typed values of type reference)
                    if (dataType == 0x01 || dataType == 0x02) {
                        try {
                            node.resIds.put(clean, data);
                            if (attrResId != 0) node.attrResIds.put(attrResId, data);
                        } catch (Throwable ignored) {}
                    }
                }
                stack.push(node);
            } else if (type == RES_XML_END_ELEMENT_TYPE) {
                if (!stack.isEmpty()) stack.pop();
            } else if (type == RES_XML_CDATA_TYPE) {
                int dataIdx = i32(xml, off + 16);
                String text = str(strings, dataIdx);
                if (text != null && !text.trim().isEmpty() && !stack.isEmpty()) {
                    RealXmlResourceParser.Node node = stack.peek();
                    node.attributes.put("#text", text);
                }
            } else if (type == RES_XML_START_NAMESPACE_TYPE) {
                // namespaces are not needed for name-based lookups
            }
            off += chunkSize;
        }
        return root;
    }

    /** Parse an APK XML resource by file path. */
    public static RealXmlResourceParser.Node parseApkXml(File apk, String path) {
        byte[] data = readFile(apk, path);
        if (data == null) return null;
        return parse(data);
    }

    // ---------------- string pool ----------------

    private static String[] parseStringPool(byte[] b, int chunkOff, int headerSize, int chunkSize) {
        // ResStringPool_header: type(2) headerSize(2) size(4) stringCount(4)
        // styleCount(4) flags(4) stringsStart(4) stylesStart(4)
        int stringCount = i32(b, chunkOff + 8);
        int flags = i32(b, chunkOff + 16);
        int stringsStart = i32(b, chunkOff + 20);
        boolean utf8 = (flags & 0x100) != 0;
        if (stringCount < 0 || stringCount > 200000) return null;
        String[] out = new String[stringCount];
        int base = chunkOff + stringsStart;
        int indexBase = chunkOff + headerSize;
        for (int i = 0; i < stringCount; i++) {
            int strOff = i32(b, indexBase + i * 4);
            int p = base + strOff;
            if (p < 0 || p >= chunkOff + chunkSize) { out[i] = null; continue; }
            out[i] = utf8 ? decodeUtf8(b, p) : decodeUtf16(b, p);
        }
        return out;
    }

    private static String decodeUtf8(byte[] b, int p) {
        try {
            VarLen cl = varLen8(b, p);
            int charLen = cl.value;
            p = cl.next;
            VarLen bl = varLen8(b, p);
            int byteLen = bl.value;
            p = bl.next;
            if (p + byteLen > b.length) byteLen = b.length - p;
            return new String(b, p, byteLen, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String decodeUtf16(byte[] b, int p) {
        try {
            VarLen cl = varLen16(b, p);
            int charLen = cl.value;
            p = cl.next;
            if (p + charLen * 2 > b.length) charLen = (b.length - p) / 2;
            StringBuilder sb = new StringBuilder(charLen);
            for (int i = 0; i < charLen; i++) {
                int lo = b[p + i * 2] & 0xFF;
                int hi = b[p + i * 2 + 1] & 0xFF;
                sb.append((char) ((hi << 8) | lo));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class VarLen {
        final int value;
        final int next;
        VarLen(int v, int n) { value = v; next = n; }
    }

    private static VarLen varLen8(byte[] b, int p) {
        int v = b[p] & 0x7F;
        int n = 1;
        if ((b[p] & 0x80) != 0) {
            v = ((b[p] & 0x7F) << 8) | (b[p + 1] & 0xFF);
            n = 2;
        }
        return new VarLen(v, p + n);
    }

    private static VarLen varLen16(byte[] b, int p) {
        int v = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
        int n = 2;
        if ((v & 0x8000) != 0) {
            v = ((v & 0x7FFF) << 16) | ((b[p + 2] & 0xFF) | ((b[p + 3] & 0xFF) << 8));
            n = 4;
        }
        return new VarLen(v, p + n);
    }

    private static String str(String[] pool, int idx) {
        if (pool == null || idx < 0 || idx >= pool.length) return null;
        return pool[idx];
    }

    // ---------------- typed value rendering (Res_value -> display string) ----------------

    private static String renderValue(int dataType, int data, String raw, String[] strings) {
        if (raw != null && dataType == 0x03) return raw;   // STRING with raw text
        switch (dataType) {
            case 0x00: return null;                        // NULL - omit
            case 0x01:                                     // REFERENCE
            case 0x02:                                     // ATTRIBUTE
                return String.format(Locale.US, "@0x%08x", data);
            case 0x03:                                     // STRING (no raw)
                return str(strings, data);
            case 0x04:                                     // FLOAT
                return Float.toString(Float.intBitsToFloat(data));
            case 0x05: {                                   // DIMENSION
                float f = complexToFloat(data);
                String unit = complexUnitSuffix(data);
                return trimFloat(f) + unit;
            }
            case 0x06: {                                   // FRACTION
                float f = complexToFloat(data);
                int unit = (data >> 28) & 0x3;
                return trimFloat(f * 100f) + (unit == 1 ? "%p" : "%");
            }
            case 0x10:                                     // INT_DEC
                return Integer.toString(data);
            case 0x11:                                     // INT_HEX
                return String.format(Locale.US, "0x%08x", data);
            case 0x12:                                     // INT_BOOLEAN
                return data != 0 ? "true" : "false";
            case 0x1c: case 0x1d: case 0x1e: case 0x1f:    // COLOR_INT*
                return String.format(Locale.US, "#%08x", data);
            default:
                return Integer.toString(data);
        }
    }

    /** libandroidfw Res_value::complexToFloat equivalent. */
    static float complexToFloat(int data) {
        float mantissa = (data & 0x00FFFFFF);
        int radix = (data >> 24) & 0x3;
        float mult = (radix == 0) ? 1.0f
                : (radix == 1) ? 1.0f / 128f
                : (radix == 2) ? 1.0f / 32768f
                : 1.0f / 8388608f;
        return mantissa * mult;
    }

    private static String complexUnitSuffix(int data) {
        switch ((data >> 28) & 0xF) {
            case 0: return "px";
            case 1: return "dp";
            case 2: return "sp";
            case 3: return "pt";
            case 4: return "in";
            case 5: return "mm";
            default: return "px";
        }
    }

    private static String trimFloat(float f) {
        if (f == (long) f) return Long.toString((long) f);
        return Float.toString(f);
    }

    // ---------------- low-level readers ----------------

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int u8(byte[] b, int off) {
        return b[off] & 0xFF;
    }

    private static int i32(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }
}
