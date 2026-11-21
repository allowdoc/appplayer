package org.headless.runner.tool.rn;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.nio.file.Files;

/**
 * Universal resource table generator.
 *
 * Replaces the previous approach of shipping per-APK .tsv caches with the
 * repository. For ANY apk, `aapt2 dump resources` is parsed into
 * data/&lt;pkg&gt;-strings.tsv and data/&lt;pkg&gt;-ids.tsv on first boot, so
 * resource lookups work without any app-specific artifacts in the repo.
 */
public final class ResourceTableGenerator {

    private ResourceTableGenerator() {}

    public static void ensureTables(File apkFile, File dataDir, String aapt2) {
        if (apkFile == null || !apkFile.exists() || aapt2 == null) return;
        try {
            File idsFile = new File(dataDir, "ids.tsv");
            File stringsFile = new File(dataDir, "strings.tsv");
            if (idsFile.exists() && stringsFile.exists()
                    && idsFile.lastModified() >= apkFile.lastModified()) {
                return; // up-to-date
            }
            System.out.println("[*] [ResourceTables] Generating resource tables from "
                    + apkFile.getName() + "...");
            ProcessBuilder pb = new ProcessBuilder(
                    aapt2, "dump", "resources", apkFile.getAbsolutePath());
            Process p = pb.start();

            StringBuilder ids = new StringBuilder();
            StringBuilder strings = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                // resource 0x7f0e0001 string/app_name "App Name"
                java.util.regex.Pattern resP = java.util.regex.Pattern.compile(
                        "^\\s*resource\\s+0x([0-9a-fA-F]{8})\\s+(\\w+)/([^\\s\"]+)\\s+\"(.*)\"\\s*$");
                // Also handle no-value entries: resource 0x7f0e0001 string/app_name
                java.util.regex.Pattern idOnlyP = java.util.regex.Pattern.compile(
                        "^\\s*resource\\s+0x([0-9a-fA-F]{8})\\s+(\\w+)/([^\\s\"]+)\\s*$");
                while ((line = r.readLine()) != null) {
                    java.util.regex.Matcher m = resP.matcher(line);
                    if (m.find()) {
                        long id = Long.parseLong(m.group(1), 16);
                        String type = m.group(2);
                        String name = m.group(3);
                        String value = m.group(4);
                        ids.append(id).append('\t').append(name).append('\n');
                        if ("string".equals(type) && value != null && !value.isEmpty()) {
                            strings.append(id).append('\t').append(name).append('\t')
                                    .append(value.replace("\n", "\\n")).append('\n');
                        }
                        continue;
                    }
                    java.util.regex.Matcher m2 = idOnlyP.matcher(line);
                    if (m2.find()) {
                        long id = Long.parseLong(m2.group(1), 16);
                        ids.append(id).append('\t').append(m2.group(3)).append('\n');
                    }
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                System.err.println("[-] [ResourceTables] aapt2 exit " + exit);
            }
            if (!dataDir.exists()) dataDir.mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(idsFile), StandardCharsets.UTF_8)) {
                w.write(ids.toString());
            }
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(stringsFile), StandardCharsets.UTF_8)) {
                w.write(strings.toString());
            }
            System.out.println("[+] [ResourceTables] Wrote " + idsFile.getName()
                    + " (" + ids.length() + " bytes) and " + stringsFile.getName()
                    + " (" + strings.length() + " bytes).");
        } catch (Throwable t) {
            System.err.println("[-] [ResourceTables] Generation failed: " + t.getMessage());
        }
    }
}
