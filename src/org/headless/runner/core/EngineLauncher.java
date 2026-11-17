package org.headless.runner.core;

import org.headless.runner.loader.ApkDexScanner;
import org.headless.runner.manifest.ApkInfo;
import org.headless.runner.manifest.ManifestExtractor;

import java.io.File;
import java.util.List;

public class EngineLauncher {
    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("   ZERO-DAEMON HEADLESS ANDROID RUNNER ENGINE (v0.2)     ");
        System.out.println("   Memory Target: < 40 MB | Pure In-Process Execution    ");
        System.out.println("=========================================================");

        if (args.length < 1) {
            System.out.println("Usage: java org.headless.runner.core.EngineLauncher <path-to-apk>");
            return;
        }

        File apkFile = new File(args[0]);
        if (!apkFile.exists()) {
            System.err.println("[-] Error: APK file does not exist at " + apkFile.getAbsolutePath());
            return;
        }

        try {
            System.out.println("[*] [Phase 1] Parsing APK manifest and signatures...");
            ApkInfo info = ManifestExtractor.extract(apkFile);

            System.out.println("[+] Target Package    : " + info.getPackageName());
            System.out.println("[+] Application Class : " + info.getApplicationClass());
            System.out.println("[+] Launch Activity   : " + info.getMainActivity());
            System.out.println("[+] Version           : " + info.getVersionName() + " (" + info.getVersionCode() + ")");

            File storageDir = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data");
            File dexCacheDir = new File(storageDir, info.getPackageName() + "/dex_cache");
            dexCacheDir.mkdirs();

            System.out.println("\n[*] [Phase 2] Scanning & Indexing DEX Bytecode modules...");
            List<ApkDexScanner.DexSummary> dexModules = ApkDexScanner.scanApk(apkFile, dexCacheDir);
            int totalClasses = 0;
            int totalMethods = 0;
            for (ApkDexScanner.DexSummary dex : dexModules) {
                System.out.printf("    - %-14s : %6d classes | %7d methods | %8.2f KB\n",
                        dex.name, dex.classes, dex.methods, dex.size / 1024.0);
                totalClasses += dex.classes;
                totalMethods += dex.methods;
            }
            System.out.printf("[+] Total App Bytecode: %d DEX files | %d classes | %d methods\n",
                    dexModules.size(), totalClasses, totalMethods);

            System.out.println("\n[*] [Phase 3] Initializing in-memory SyntheticContext at " + storageDir.getAbsolutePath() + "...");
            SyntheticContext context = new SyntheticContext(info, storageDir);

            System.out.println("[+] Private Storage   : " + context.getFilesDir().getAbsolutePath());
            System.out.println("[+] SharedPreferences : " + new File(storageDir, info.getPackageName() + "/shared_prefs").getAbsolutePath());

            // Initialize sample engine prefs
            android.content.SharedPreferences enginePrefs = context.getSharedPreferences("engine_runtime_config", 0);
            enginePrefs.edit()
                    .putString("runner_mode", "headless_in_process")
                    .putString("boot_timestamp", String.valueOf(System.currentTimeMillis()))
                    .apply();
            System.out.println("[+] Runtime State     : Saved configuration to isolated storage.");

            long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
            System.out.println("\n---------------------------------------------------------");
            System.out.println("[*] Current Engine Process RAM Footprint: " + usedMemory + " MB");
            System.out.println("[+] Core Engine Initialization & Bytecode Indexing SUCCESSFUL!");
            System.out.println("---------------------------------------------------------");

        } catch (Exception e) {
            System.err.println("[-] Engine Launch Failure: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
