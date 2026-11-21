package org.headless.render;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner-side font registry bridging expo/RN font loading to the desktop
 * Skia text stack. The app's own ReactFontManager stores android.graphics
 * Typeface objects (framework-stubbed on the desktop); this registry keeps
 * the REAL font file paths so the Skia renderer can shape text with the
 * exact family the app asked for. Pure environment state - no per-app code.
 */
public final class FontStore {

    private static final Map<String, List<String>> FAMILY_FILES = new ConcurrentHashMap<>();

    private FontStore() {}

    public static void register(String family, File fontFile) {
        if (family == null || fontFile == null) return;
        FAMILY_FILES.computeIfAbsent(family, k -> new ArrayList<>()).add(fontFile.getAbsolutePath());
        System.out.println("[+] [FontStore] registered font family '" + family + "' -> "
                + fontFile.getName());
    }

    public static List<String> getFiles(String family) {
        List<String> files = FAMILY_FILES.get(family);
        return files != null ? files : new ArrayList<>();
    }

    public static boolean hasFamily(String family) {
        return family != null && FAMILY_FILES.containsKey(family);
    }
}
