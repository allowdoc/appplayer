package org.headless.runner.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkDexScanner {
    public static class DexSummary {
        public final String name;
        public final int classes;
        public final int methods;
        public final int strings;
        public final long size;

        public DexSummary(String name, int classes, int methods, int strings, long size) {
            this.name = name;
            this.classes = classes;
            this.methods = methods;
            this.strings = strings;
            this.size = size;
        }
    }

    public static List<DexSummary> scanApk(File apkFile, File cacheDir) throws Exception {
        List<DexSummary> results = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".dex") && !name.contains("/")) {
                    File tempDex = new File(cacheDir, name);
                    if (!tempDex.exists() || tempDex.length() != entry.getSize()) {
                        try (InputStream in = zip.getInputStream(entry);
                             FileOutputStream out = new FileOutputStream(tempDex)) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = in.read(buf)) != -1) {
                                out.write(buf, 0, r);
                            }
                        }
                    }

                    DexReader reader = new DexReader(tempDex);
                    reader.parseHeader();
                    results.add(new DexSummary(
                            name,
                            reader.getClassCount(),
                            reader.getMethodCount(),
                            reader.getStringCount(),
                            entry.getSize()
                    ));
                }
            }
        }
        return results;
    }
}
