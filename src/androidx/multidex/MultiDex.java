package androidx.multidex;

import android.content.Context;

public final class MultiDex {
    public static void install(Context context) {
        // In-process headless runtime: all multi-dex classes are unified in classpath
    }

    public static void installInstrumentation(Context context, Context targetContext) {
    }

    public static boolean isVMMultidexCapable(String versionString) {
        return true;
    }
}
