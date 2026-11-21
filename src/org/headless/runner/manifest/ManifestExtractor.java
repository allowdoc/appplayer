package org.headless.runner.manifest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManifestExtractor {

    /**
     * Locate aapt2 universally: bundled copy first (tools/aapt2/&lt;os&gt;/), then
     * ANDROID_HOME build-tools, then PATH. No absolute developer-machine paths.
     */
    public static String resolveAapt2() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = isWindows ? "aapt2.exe" : "aapt2";
        String runnerDir = System.getProperty("headless.runner.dir",
                System.getProperty("user.dir", "."));

        File[] candidates = new File[] {
                new File(runnerDir, "tools/aapt2/" + (isWindows ? "win-x86_64" : "linux-x86_64") + "/" + exe),
                new File(runnerDir, "tools/aapt2/" + exe),
                new File(runnerDir, "tools/" + exe),
        };
        for (File f : candidates) {
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        // Android SDK build-tools (both OSes), newest first
        String sdk = System.getenv("ANDROID_HOME");
        if (sdk == null || sdk.isEmpty()) sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk != null) {
            File bt = new File(sdk, "build-tools");
            File[] vers = bt.listFiles();
            if (vers != null) {
                java.util.Arrays.sort(vers, (a, b) -> b.getName().compareTo(a.getName()));
                for (File v : vers) {
                    File aapt = new File(v, exe);
                    if (aapt.exists()) return aapt.getAbsolutePath();
                }
            }
        }

        // PATH fallback
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(isWindows ? ";" : ":")) {
                File aapt = new File(dir, exe);
                if (aapt.exists() && aapt.canExecute()) return aapt.getAbsolutePath();
            }
        }
        return exe; // last resort: hope it is on PATH
    }

    private static String aapt2Path() {
        String cached = System.getProperty("headless.runner.aapt2");
        if (cached != null) return cached;
        String resolved = resolveAapt2();
        System.setProperty("headless.runner.aapt2", resolved);
        return resolved;
    }

    private static class ElementEntry {
        final int indent;
        final String tag;
        ElementEntry(int indent, String tag) {
            this.indent = indent;
            this.tag = tag;
        }
    }

    public static ApkInfo extract(File apkFile) throws Exception {
        if (!apkFile.exists()) {
            throw new IllegalArgumentException("APK file not found: " + apkFile.getAbsolutePath());
        }

        String packageName = "";
        String applicationClass = "android.app.Application";
        String mainActivity = "";
        String versionName = "";
        int versionCode = 0;

        // Run aapt2 dump badging
        String aapt2 = aapt2Path();
        ProcessBuilder pb = new ProcessBuilder(aapt2, "dump", "badging", apkFile.getAbsolutePath());
        Process process = pb.start();

        Pattern pkgPattern = Pattern.compile("package:\\s+name='([^']+)'\\s+versionCode='([^']+)'\\s+versionName='([^']+)'");
        Pattern actPattern = Pattern.compile("launchable-activity:\\s+name='([^']+)'");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = pkgPattern.matcher(line);
                if (m.find()) {
                    packageName = m.group(1);
                    try { versionCode = Integer.parseInt(m.group(2)); } catch (Exception ignored) {}
                    versionName = m.group(3);
                }

                Matcher actMatcher = actPattern.matcher(line);
                if (actMatcher.find()) {
                    mainActivity = actMatcher.group(1);
                }
            }
        }
        process.waitFor();

        ApkInfo apkInfo = new ApkInfo(packageName, applicationClass, mainActivity, versionName, versionCode);

        // Run aapt2 dump xmltree to get application name, content providers, services, and meta-data
        ProcessBuilder xmlPb = new ProcessBuilder(aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apkFile.getAbsolutePath());
        Process xmlProcess = xmlPb.start();

        Pattern elementPattern = Pattern.compile("^(\\s*)E:\\s*([\\w-]+)");
        Pattern appClassPattern = Pattern.compile("android:name\\([^\\)]+\\)=\"([^\"]+)\"");
        Pattern authPattern = Pattern.compile("android:authorities\\([^\\)]+\\)=\"([^\"]+)\"");
        Pattern orderPattern = Pattern.compile("android:initOrder\\([^\\)]+\\)=(\\d+)");
        Pattern permPattern = Pattern.compile("android:permission\\([^\\)]+\\)=\"([^\"]+)\"");
        Pattern expPattern = Pattern.compile("android:exported\\([^\\)]+\\)=.*(true|0x1)");
        Pattern valPattern = Pattern.compile("android:value\\([^\\)]+\\)=(.*)");
        Pattern resPattern = Pattern.compile("android:resource\\([^\\)]+\\)=@?(0x[0-9a-fA-F]+|\\d+)");
        Pattern rawPattern = Pattern.compile("\\(Raw:\\s*\"([^\"]+)\"\\)");
        Pattern themePattern = Pattern.compile("android:theme\\([^\\)]+\\)=@?(0x[0-9a-fA-F]+)");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(xmlProcess.getInputStream()))) {
            String line;
            List<ElementEntry> stack = new ArrayList<>();
            String curComp = null;
            String curProvName = null;
            String curProvAuth = null;
            int curProvOrder = 0;
            String curSvcName = null;
            String curSvcPerm = null;
            boolean curSvcExp = false;

            String metaName = null;
            Object metaVal = null;

            String curThemeComp = null;
            int curTheme = 0;

            while ((line = reader.readLine()) != null) {
                Matcher elMatcher = elementPattern.matcher(line);
                if (elMatcher.find()) {
                    int indent = elMatcher.group(1).length();
                    String tag = elMatcher.group(2);

                    while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= indent) {
                        stack.remove(stack.size() - 1);
                    }
                    stack.add(new ElementEntry(indent, tag));

                    if ("provider".equals(tag)) {
                        if (curProvName != null) {
                            apkInfo.addProvider(curProvName, curProvAuth, curProvOrder);
                        }
                        curProvName = null;
                        curProvAuth = null;
                        curProvOrder = 0;
                        curComp = null;
                    } else if ("service".equals(tag)) {
                        if (curSvcName != null) {
                            apkInfo.addService(curSvcName, curSvcPerm, curSvcExp);
                        }
                        curSvcName = null;
                        curSvcPerm = null;
                        curSvcExp = false;
                        curComp = null;
                    } else if ("activity".equals(tag) || "receiver".equals(tag)) {
                        if (curThemeComp != null && curTheme != 0) {
                            apkInfo.addActivityTheme(curThemeComp, curTheme);
                        }
                        curThemeComp = null;
                        curTheme = 0;
                        curComp = null;
                    } else if ("meta-data".equals(tag)) {
                        metaName = null;
                        metaVal = null;
                    }
                }

                if (stack.isEmpty()) continue;
                String currentTag = stack.get(stack.size() - 1).tag;

                if ("application".equals(currentTag)) {
                    Matcher m = appClassPattern.matcher(line);
                    if (m.find() && "android.app.Application".equals(apkInfo.getApplicationClass())) {
                        apkInfo.setApplicationClass(m.group(1));
                    }
                    Matcher tm = themePattern.matcher(line);
                    if (tm.find()) {
                        // Long.parseLong(s, 16) rejects the "0x" prefix that the
                        // regex captures - aapt2 prints theme refs as @0x7f13000b.
                        // Use Long.decode so app/activity themes actually parse.
                        try {
                            apkInfo.setApplicationTheme((int) Long.decode(tm.group(1)).longValue());
                        } catch (Exception ignored) {}
                    }
                } else if ("provider".equals(currentTag)) {
                    Matcher nm = appClassPattern.matcher(line);
                    if (nm.find() && curProvName == null) {
                        curProvName = nm.group(1);
                        curComp = curProvName;
                    }
                    Matcher am = authPattern.matcher(line);
                    if (am.find()) curProvAuth = am.group(1);
                    Matcher om = orderPattern.matcher(line);
                    if (om.find()) {
                        try { curProvOrder = Integer.parseInt(om.group(1)); } catch (Exception ignored) {}
                    }
                } else if ("service".equals(currentTag)) {
                    Matcher nm = appClassPattern.matcher(line);
                    if (nm.find() && curSvcName == null) {
                        curSvcName = nm.group(1);
                        curComp = curSvcName;
                    }
                    Matcher pm = permPattern.matcher(line);
                    if (pm.find()) curSvcPerm = pm.group(1);
                    Matcher em = expPattern.matcher(line);
                    if (em.find()) curSvcExp = true;
                } else if ("activity".equals(currentTag) || "receiver".equals(currentTag)) {
                    Matcher tm = themePattern.matcher(line);
                    if (tm.find()) {
                        // Long.decode accepts the "0x" prefix (parseLong(16) does not).
                        try { curTheme = (int) Long.decode(tm.group(1)).longValue(); } catch (Exception ignored) {}
                    }
                    Matcher nm = appClassPattern.matcher(line);
                    if (nm.find()) {
                        if (curComp == null) {
                            curComp = nm.group(1);
                        }
                        if (curTheme != 0 && curComp != null) {
                            apkInfo.addActivityTheme(curComp, curTheme);
                        }
                    }
                } else if ("meta-data".equals(currentTag)) {
                    Matcher nm = appClassPattern.matcher(line);
                    if (nm.find()) {
                        metaName = nm.group(1);
                    }
                    Matcher vm = valPattern.matcher(line);
                    if (vm.find()) {
                        String vRaw = vm.group(1).trim();
                        if (vRaw.startsWith("\"")) {
                            Matcher rm = rawPattern.matcher(line);
                            if (rm.find()) {
                                metaVal = rm.group(1);
                            } else {
                                metaVal = vRaw.replace("\"", "");
                            }
                        } else if ("true".equalsIgnoreCase(vRaw) || "0x1".equalsIgnoreCase(vRaw)) {
                            metaVal = Boolean.TRUE;
                        } else if ("false".equalsIgnoreCase(vRaw) || "0x0".equalsIgnoreCase(vRaw)) {
                            metaVal = Boolean.FALSE;
                        } else if (vRaw.startsWith("@0x") || vRaw.startsWith("0x")) {
                            try {
                                String hex = vRaw.replace("@0x", "").replace("0x", "");
                                metaVal = (int) Long.parseLong(hex, 16);
                            } catch (Exception ignored) {
                                metaVal = vRaw;
                            }
                        } else {
                            try {
                                metaVal = Integer.parseInt(vRaw);
                            } catch (Exception ignored) {
                                metaVal = vRaw;
                            }
                        }
                    }
                    Matcher rm = resPattern.matcher(line);
                    if (rm.find() && metaVal == null) {
                        String rStr = rm.group(1);
                        try {
                            metaVal = rStr.startsWith("0x") ? (int) Long.parseLong(rStr.substring(2), 16) : Integer.parseInt(rStr);
                        } catch (Exception ignored) {
                            metaVal = rStr;
                        }
                    }

                    if (metaName != null && metaVal != null) {
                        String parentTag = null;
                        for (int i = stack.size() - 2; i >= 0; i--) {
                            String t = stack.get(i).tag;
                            if ("activity".equals(t) || "service".equals(t) || "provider".equals(t) || "receiver".equals(t) || "application".equals(t)) {
                                parentTag = t;
                                break;
                            }
                        }
                        if ("application".equals(parentTag)) {
                            apkInfo.addApplicationMetaData(metaName, metaVal);
                        } else if (curComp != null) {
                            apkInfo.addComponentMetaData(curComp, metaName, metaVal);
                        }
                        metaName = null;
                        metaVal = null;
                    }
                }
            }

            if (curProvName != null) {
                apkInfo.addProvider(curProvName, curProvAuth, curProvOrder);
            }
            if (curSvcName != null) {
                apkInfo.addService(curSvcName, curSvcPerm, curSvcExp);
            }
        }
        xmlProcess.waitFor();

        // Sort providers by initOrder descending (standard Android behavior)
        apkInfo.getProviders().sort((a, b) -> Integer.compare(b.initOrder, a.initOrder));

        return apkInfo;
    }
}
