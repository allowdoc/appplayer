package org.headless.runner.core;

import java.io.File;

/**
 * Single source of truth for runner-relative paths (universal, no absolute
 * host paths anywhere in engine code).
 */
public final class RunnerPaths {
    private RunnerPaths() {}

    public static File runnerDir() {
        return new File(System.getProperty("headless.runner.dir",
                System.getProperty("user.dir", ".")));
    }

    public static File dataDir() {
        return new File(runnerDir(), "data");
    }

    public static String aapt2Path() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = isWindows ? "aapt2.exe" : "aapt2";
        File bundled = new File(runnerDir(),
                "tools/aapt2/" + (isWindows ? "win-x86_64" : "linux-x86_64") + "/" + exe);
        if (bundled.exists() && bundled.canExecute()) return bundled.getAbsolutePath();
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
        return exe;
    }
}
