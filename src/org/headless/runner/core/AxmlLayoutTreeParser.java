package org.headless.runner.core;

import android.content.res.XmlResourceParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AxmlLayoutTreeParser {
    private static final String AAPT2_PATH = "C:\\Users\\Noval\\AppData\\Local\\Android\\Sdk\\build-tools\\35.0.0\\aapt2.exe";
    private static File sApkFile;
    private static final Map<Integer, String> LAYOUT_ID_TO_PATH = new ConcurrentHashMap<>();
    private static final Map<Integer, RealXmlResourceParser.Node> PARSED_CACHE = new ConcurrentHashMap<>();

    public static void initialize(File apkFile) {
        sApkFile = apkFile;
        if (apkFile == null || !apkFile.exists()) return;

        try {
            ProcessBuilder pb = new ProcessBuilder(AAPT2_PATH, "dump", "resources", apkFile.getAbsolutePath());
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // resource 0x7f0d047f layout/one_activity_home
                    int resIdx = line.indexOf("resource 0x7f0d");
                    if (resIdx >= 0) {
                        int layoutIdx = line.indexOf("layout/", resIdx);
                        if (layoutIdx > 0) {
                            String hex = line.substring(resIdx + 11, line.indexOf(' ', resIdx + 11)).trim();
                            String name = line.substring(layoutIdx + 7).trim();
                            if (name.contains(" ")) name = name.substring(0, name.indexOf(' '));
                            try {
                                int id = (int) Long.parseLong(hex, 16);
                                LAYOUT_ID_TO_PATH.put(id, "res/layout/" + name + ".xml");
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            p.waitFor();
            System.out.println("[+] [AxmlLayout] Mapped " + LAYOUT_ID_TO_PATH.size() + " XML layout resources from APK!");
        } catch (Throwable t) {
            System.err.println("[-] [AxmlLayout] Failed to map layout resources: " + t.getMessage());
        }
    }

    public static XmlResourceParser getParser(int resid) {
        return getParser(sApkFile, resid);
    }

    public static XmlResourceParser getParser(File apkFile, int resid) {
        if (apkFile == null) apkFile = sApkFile;
        if (apkFile == null || !apkFile.exists()) return null;

        String path = LAYOUT_ID_TO_PATH.get(resid);
        if (path == null) {
            // Lazy scan if not initialized
            if (LAYOUT_ID_TO_PATH.isEmpty()) {
                initialize(apkFile);
                path = LAYOUT_ID_TO_PATH.get(resid);
            }
            if (path == null) return null;
        }

        RealXmlResourceParser.Node root = PARSED_CACHE.get(resid);
        if (root == null) {
            root = parseXmlTreeFromApk(apkFile, path);
            if (root != null) {
                PARSED_CACHE.put(resid, root);
            }
        }

        if (root != null) {
            return new RealXmlResourceParser(root);
        }
        return null;
    }

    private static RealXmlResourceParser.Node parseXmlTreeFromApk(File apkFile, String layoutPath) {
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

                            // Extract resource ID if present (e.g. @0x7f0a05a2)
                            if (value.startsWith("@0x") || value.startsWith("@")) {
                                try {
                                    String hex = value.replace("@0x", "").replace("@", "");
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
