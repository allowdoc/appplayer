package com.facebook.soloader;

import android.content.Context;
import android.os.StrictMode;
import java.io.File;

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

    public static void init(Context context, int flags) {
        System.out.println("[*] [SoLoader] Native library loader initialized (Stub Mode).");
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

    public static boolean loadLibrary(String shortName) {
        System.out.println("[+] [SoLoader] Simulated native library load: lib" + shortName + ".so -> SUCCESS");
        return true;
    }

    public static boolean loadLibrary(String shortName, int flags) {
        return loadLibrary(shortName);
    }

    public static void loadDependency(String mainSoName, int flags, StrictMode.ThreadPolicy threadPolicy) {}

    public static boolean areSoSourcesAbisSupported() {
        return true;
    }

    public static int getLoadedLibrariesCount() {
        return 0;
    }

    public static String makeLdLibraryPath() {
        return "";
    }

    public static File getSoFile(String shortName) {
        return null;
    }

    public static File unpackLibraryBySoName(String soName) {
        return null;
    }

    public static File unpackLibraryAndDependencies(String shortName) {
        return null;
    }

    public static void deinitForTest() {}
    public static void setInTestMode() {}
}
