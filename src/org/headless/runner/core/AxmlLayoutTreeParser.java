package org.headless.runner.core;

import android.content.res.XmlResourceParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AxmlLayoutTreeParser {
    private static final String AAPT2_PATH = org.headless.runner.core.RunnerPaths.aapt2Path();
    private static File sApkFile;
    private static final Map<Integer, String> LAYOUT_ID_TO_PATH = new ConcurrentHashMap<>();
    /** Same map, semantically renamed: holds EVERY XML-valued resource id. */
    private static final Map<Integer, String> XML_ID_TO_PATH = LAYOUT_ID_TO_PATH;
    private static final Map<Integer, RealXmlResourceParser.Node> PARSED_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> XML_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "layout", "drawable", "xml", "color", "anim", "animator", "menu",
            "transition", "interpolator", "font", "navigation"));

    public static void initialize(File apkFile) {
        sApkFile = apkFile;
        if (apkFile == null || !apkFile.exists()) return;

        try {
            // Universal mapping via the compiled resource table: every XML-valued
            // entry already carries its real res path as the string value
            // (e.g. "res/layout/abc_screen_simple.xml" or an obfuscated
            // "res/-8.xml"). No hardcoded type ids, no aapt2 needed. XML-valued
            // types include layout, drawable (vector/shape), xml, color, anim,
            // animator, menu, transition, interpolator - appcompat's
            // VectorDrawableCompat self-test and Material's theme checks parse
            // drawable/color XML through Resources.getXml, so ALL of them must
            // resolve to the real APK binary XML, not to a dummy parser.
            ArscParser arsc = ArscParser.getShared(apkFile);
            if (arsc != null) {
                for (ArscParser.Entry e : arsc.allEntries()) {
                    if (e.value != null && e.value.str != null
                            && e.value.str.endsWith(".xml")
                            && XML_TYPES.contains(e.type)) {
                        XML_ID_TO_PATH.put(e.resId, e.value.str);
                    }
                }
            }
            if (XML_ID_TO_PATH.isEmpty()) {
                mapLayoutsViaAapt2(apkFile); // legacy fallback
            }
            System.out.println("[+] [AxmlLayout] Mapped " + XML_ID_TO_PATH.size() + " XML resources from APK!");
        } catch (Throwable t) {
            System.err.println("[-] [AxmlLayout] Failed to map XML resources: " + t.getMessage());
        }
    }

    /** Legacy aapt2-based mapping (kept for APKs where arsc parsing failed). */
    private static void mapLayoutsViaAapt2(File apkFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(AAPT2_PATH, "dump", "resources", apkFile.getAbsolutePath());
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int resIdx = line.indexOf("resource 0x");
                    if (resIdx >= 0) {
                        int slashIdx = line.indexOf('/', resIdx);
                        if (slashIdx > 0) {
                            String type = line.substring(slashIdx + 1);
                            int spaceIdx = type.indexOf(' ');
                            if (spaceIdx > 0) type = type.substring(0, spaceIdx);
                            if (!"layout".equals(type)) continue;
                            String hex = line.substring(resIdx + 9, resIdx + 11 + 8);
                            int sp = hex.indexOf(' ');
                            if (sp > 0) hex = hex.substring(0, sp);
                            String name = line.substring(slashIdx + 1);
                            if (name.contains(" ")) name = name.substring(0, name.indexOf(' '));
                            try {
                                int id = (int) Long.parseLong(hex.replace("0x", ""), 16);
                                LAYOUT_ID_TO_PATH.put(id, "res/layout/" + name + ".xml");
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            p.waitFor();
        } catch (Throwable ignored) {}
    }

    public static XmlResourceParser getParser(int resid) {
        return getParser(sApkFile, resid);
    }

    public static XmlResourceParser getParser(File apkFile, int resid) {
        if (apkFile == null) apkFile = sApkFile;
        if (apkFile == null || !apkFile.exists()) return null;

        RealXmlResourceParser.Node root = PARSED_CACHE.get(resid);
        if (root == null) {
            String path = resolvePath(resid, 0);
            if (path != null) {
                root = parseXmlTreeFromApk(apkFile, path);
            } else if ((resid >>> 24) == 0x01) {
                // System resource package (android:interpolator, android:anim,
                // framework drawables...) - framework-res equivalent.
                root = FrameworkRes.getNode(resid);
            }
            if (root != null) {
                PARSED_CACHE.put(resid, root);
            }
        }

        if (root != null) {
            return new RealXmlResourceParser(root);
        }
        return null;
    }

    /**
     * Resolve a resource id to its XML file path. Direct string-path entries
     * come from the resource table. Reference-typed entries (@color/x pointing
     * at a color-list file, theme attributes, style parents...) are followed
     * through the compiled table exactly like androidfw resolves references.
     */
    private static String resolvePath(int resid, int depth) {
        if (depth > 8) return null;
        String path = LAYOUT_ID_TO_PATH.get(resid);
        if (path != null) return path;
        ArscParser arsc = ArscParser.getShared(sApkFile);
        if (arsc != null && resid != 0) {
            try {
                ArscParser.ResVal v = arsc.getValue(resid);
                if (v != null) {
                    if (v.isString() && v.str != null && v.str.endsWith(".xml")) {
                        LAYOUT_ID_TO_PATH.put(resid, v.str);   // memoize
                        return v.str;
                    }
                    if (v.isReference()) {
                        return resolvePath(v.data, depth + 1);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * NATIVE binary-XML parse through the runner's own ResXMLTree equivalent
     * (AxmlTreeParser) - no external process, matches what libandroidfw does
     * on a device. The legacy aapt2 dump remains as a last-resort fallback.
     */
    private static RealXmlResourceParser.Node parseXmlTreeFromApk(File apkFile, String layoutPath) {
        try {
            RealXmlResourceParser.Node nativeNode = AxmlTreeParser.parseApkXml(apkFile, layoutPath);
            if (nativeNode != null) return nativeNode;
        } catch (Throwable ignored) {}
        return parseXmlTreeViaAapt2(apkFile, layoutPath);
    }

    private static RealXmlResourceParser.Node parseXmlTreeViaAapt2(File apkFile, String layoutPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(AAPT2_PATH, "dump", "xmltree", "--file", layoutPath, apkFile.getAbsolutePath());
            Process p = pb.start();

            RealXmlResourceParser.Node root = null;
            Deque<RealXmlResourceParser.Node> stack = new ArrayDeque<>();
            Deque<Integer> indentStack = new ArrayDeque<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("E: ")) {
                        int eIdx = line.indexOf("E: ");
                        int endTag = line.indexOf(" (line=", eIdx);
                        String tag = (endTag > 0) ? line.substring(eIdx + 3, endTag).trim() : line.substring(eIdx + 3).trim();

                        RealXmlResourceParser.Node node = new RealXmlResourceParser.Node();
                        node.tag = tag;

                        while (!indentStack.isEmpty() && indentStack.peek() >= eIdx) {
                            indentStack.pop();
                            stack.pop();
                        }

                        if (stack.isEmpty()) {
                            root = node;
                        } else {
                            RealXmlResourceParser.Node parent = stack.peek();
                            node.parent = parent;
                            parent.children.add(node);
                        }

                        stack.push(node);
                        indentStack.push(eIdx);
                    } else if (line.contains("A: ") && !stack.isEmpty()) {
                        int aIdx = line.indexOf("A: ");
                        int eqIdx = line.indexOf('=', aIdx);
                        if (eqIdx > 0) {
                            String rawName = line.substring(aIdx + 3, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            int rawIdx = value.indexOf(" (Raw: ");
                            if (rawIdx > 0) {
                                value = value.substring(0, rawIdx).trim();
                            }
                            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                                value = value.substring(1, value.length() - 1);
                            }

                            String cleanName = rawName;
                            int colon = cleanName.lastIndexOf(':');
                            if (colon >= 0) cleanName = cleanName.substring(colon + 1);
                            int paren = cleanName.indexOf('(');
                            if (paren >= 0) cleanName = cleanName.substring(0, paren);

                            RealXmlResourceParser.Node cur = stack.peek();
                            cur.attributes.put(cleanName, value);

                            int attrResId = 0;
                            int p1 = rawName.indexOf("(0x");
                            int p2 = rawName.indexOf(")", p1);
                            if (p1 >= 0 && p2 > p1) {
                                try {
                                    attrResId = (int) Long.parseLong(rawName.substring(p1 + 3, p2), 16);
                                } catch (Throwable ignored) {}
                            }
                            if (attrResId == 0) {
                                if ("id".equals(cleanName)) attrResId = 0x010100d0;
                                else if ("layout_width".equals(cleanName)) attrResId = 0x010100f4;
                                else if ("layout_height".equals(cleanName)) attrResId = 0x010100f5;
                                else if ("visibility".equals(cleanName)) attrResId = 0x010100dc;
                                else if ("orientation".equals(cleanName)) attrResId = 0x010100c4;
                                else if ("text".equals(cleanName)) attrResId = 0x0101014f;
                                else if ("textSize".equals(cleanName)) attrResId = 0x01010095;
                                else if ("textColor".equals(cleanName)) attrResId = 0x01010098;
                                else if ("background".equals(cleanName)) attrResId = 0x010100d4;
                                else if ("src".equals(cleanName)) attrResId = 0x01010119;
                            }

                            if (attrResId != 0) {
                                cur.attrValues.put(attrResId, value);
                            }

                            // Extract resource ID if present (e.g. @0x7f0a05a2, @ref/0x7f0a05a2)
                            if (value.startsWith("@0x") || value.startsWith("@ref/0x") || value.startsWith("@")) {
                                try {
                                    String hex = value.replace("@0x", "").replace("@ref/0x", "").replace("@", "");
                                    int resId = (int) Long.parseLong(hex, 16);
                                    cur.resIds.put(cleanName, resId);
                                    if (attrResId != 0) {
                                        cur.attrResIds.put(attrResId, resId);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
            p.waitFor();
            return root;
        } catch (Throwable t) {
            System.err.println("[-] [AxmlLayout] Failed to parse " + layoutPath + ": " + t.getMessage());
            return null;
        }
    }
}
