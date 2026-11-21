package com.facebook.soloader;

import android.content.Context;
import android.os.StrictMode;
import java.io.File;

/**
 * Desktop (JVM) native library loader for the Android Wine runner.
 *
 * Android APKs ship ARM ELF .so files which a desktop JVM cannot load, so the
 * runner ships cross-platform desktop builds of the required native engines
 * (currently Yoga) under native/&lt;os&gt;/ and loads them with System.load().
 * Everything else stays a soft no-op (feature libraries like libworklets.so
 * degrade gracefully inside their Java modules).
 */
public class SoLoader {
    public static final int SOLOADER_ALLOW_ASYNC_INIT = 1;
    public static final int SOLOADER_ENABLE_EXOPACKAGE = 2;
    public static final int SOLOADER_LOOK_IN_ZIP = 4;
    public static final int SOLOADER_ENABLE_DIRECT_SOSOURCE = 8;
    public static final int SOLOADER_DISABLE_BACKUP_SOSOURCE = 16;
    public static final int SOLOADER_EXPLICITLY_ENABLE_BACKUP_SOSOURCE = 32;
    public static final int SOLOADER_ENABLE_SYSTEMLOAD_WRAPPER_SOSOURCE = 64;
    public static final int SOLOADER_ENABLE_BASE_APK_SPLIT_SOURCE = 128;
    public static final int SOLOADER_DISABLE_FS_SYNC_JOB = 256;
    public static final int SOLOADER_DONT_TREAT_AS_SYSTEMAPP = 512;
    public static final int SOLOADER_SKIP_MERGED_JNI_ONLOAD = 1024;
    public static final int SOLOADER_IMPLICIT_DEPENDENCIES_TEST = 2048;
    public static final int SOLOADER_ENABLE_BACKUP_SOSOURCE_DSONOTFOUND_ERROR_RECOVERY = 4096;

    public static final String SO_STORE_NAME_MAIN = "lib-main";
    public static final String TAG = "SoLoader";
    public static final String VERSION = "0.10.5";

    private static volatile boolean sInitialized = true;
    private static final java.util.Set<String> sLoadedDesktop = new java.util.HashSet<>();

    public static void init(Context context, int flags) {
        System.out.println("[*] [SoLoader] Native library loader initialized (Desktop Bridge Mode).");
        sInitialized = true;
    }

    public static void init(Context context, boolean useBuiltIn) {
        init(context, 0);
    }

    public static void init(Context context, int flags, Object fileLoader) {
        init(context, flags);
    }

    public static void init(Context context, ExternalSoMapping mapping) {
        init(context, 0);
    }

    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * Load a native library. If a desktop build of the library exists in
     * native/&lt;os&gt;/ it is genuinely loaded into the JVM (e.g. Yoga layout
     * engine); otherwise a graceful soft-success is returned so Android
     * feature libraries (libworklets.so, libhermes.so ...) never hard-crash.
     */
    public static boolean loadLibrary(String shortName) {
        File desktop = locateDesktopLibrary(shortName);
        if (desktop != null) {
            if (sLoadedDesktop.contains(shortName)) return true;
            try {
                System.load(desktop.getAbsolutePath());
                sLoadedDesktop.add(shortName);
                System.out.println("[+] [SoLoader] Loaded DESKTOP native library: "
                        + desktop.getName() + " (" + desktop.getAbsolutePath() + ")");
                return true;
            } catch (Throwable t) {
                System.err.println("[-] [SoLoader] Desktop load failed for " + shortName
                        + ": " + t.getMessage());
                return false;
            }
        }
        System.out.println("[*] [SoLoader] Soft load (no desktop build): lib" + shortName + ".so");
        return true;
    }

    public static boolean loadLibrary(String shortName, int flags) {
        return loadLibrary(shortName);
    }

    private static File locateDesktopLibrary(String shortName) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String dir;
        String fileName;
        if (os.contains("win")) {
            dir = "native/win-x86_64";
            fileName = shortName + ".dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            dir = "native/osx-x86_64";
            fileName = "lib" + shortName + ".dylib";
        } else {
            dir = "native/linux-x86_64";
            fileName = "lib" + shortName + ".so";
        }

        File runnerDir = new File(System.getProperty("headless.runner.dir",
                System.getProperty("user.dir", ".")));
        File candidate = new File(runnerDir, dir + "/" + fileName);
        if (!candidate.exists()) {
            // Also accept "yoga" style names (yoga.dll / libyoga.so)
            File alt = new File(runnerDir, dir + "/" + (os.contains("win")
                    ? fileName : fileName.replace("lib" + shortName, shortName)));
            candidate = alt.exists() ? alt : candidate;
        }
        return candidate.exists() ? candidate : null;
    }

    public static void loadDependency(String mainSoName, int flags, StrictMode.ThreadPolicy threadPolicy) {}

    public static boolean areSoSourcesAbisSupported() {
        return true;
    }

    public static int getLoadedLibrariesCount() {
        return sLoadedDesktop.size();
    }

    public static String makeLdLibraryPath() {
        return "";
    }

    public static File getSoFile(String shortName) {
        return locateDesktopLibrary(shortName);
    }

    public static File unpackLibraryBySoName(String soName) {
        return locateDesktopLibrary(soName);
    }

    public static File unpackLibraryAndDependencies(String shortName) {
        return locateDesktopLibrary(shortName);
    }

    public static void deinitForTest() {}
    public static void setInTestMode() {}
}
