package org.headless.runner.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Looper;
import org.headless.runner.manifest.ApkInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SyntheticContext extends MockContext {
    private final ApkInfo apkInfo;
    private final File baseDir;
    private final File filesDir;
    private final File cacheDir;
    private final File prefsDir;
    private final File dbDir;
    private final Map<String, SharedPreferencesImpl> sharedPrefsCache = new HashMap<>();
    private final Map<String, Object> services = new HashMap<>();
    private final ApplicationInfo appInfo;
    private final SyntheticPackageManager packageManager;
    private final SyntheticResources resources;
    private final SyntheticContentResolver contentResolver;
    private final Resources.Theme theme;
    private android.app.Application mApplication;

    public void setApplication(android.app.Application app) {
        this.mApplication = app;
    }

    public SyntheticContext(ApkInfo apkInfo, File storageRoot) {
        this(apkInfo, null, storageRoot);
    }

    public SyntheticContext(ApkInfo apkInfo, File apkFile, File storageRoot) {
        this.apkInfo = apkInfo;
        this.baseDir = new File(storageRoot, apkInfo.getPackageName());
        this.filesDir = new File(baseDir, "files");
        this.cacheDir = new File(baseDir, "cache");
        this.prefsDir = new File(baseDir, "shared_prefs");
        this.dbDir = new File(baseDir, "databases");
        File libDir = new File(baseDir, "lib");

        filesDir.mkdirs();
        cacheDir.mkdirs();
        prefsDir.mkdirs();
        dbDir.mkdirs();
        libDir.mkdirs();

        this.appInfo = new ApplicationInfo();
        this.appInfo.packageName = apkInfo.getPackageName();
        this.appInfo.dataDir = baseDir.getAbsolutePath();
        this.appInfo.sourceDir = apkFile.getAbsolutePath();
        this.appInfo.publicSourceDir = apkFile.getAbsolutePath();
        this.appInfo.nativeLibraryDir = libDir.getAbsolutePath();
        this.appInfo.flags = ApplicationInfo.FLAG_INSTALLED | ApplicationInfo.FLAG_ALLOW_BACKUP;
        this.appInfo.metaData = apkInfo.createApplicationMetaDataBundle();
        if (!this.appInfo.metaData.containsKey("com.google.android.gms.version")
                || this.appInfo.metaData.getInt("com.google.android.gms.version") > 0x7f000000) {
            this.appInfo.metaData.putInt("com.google.android.gms.version", 12451000);
        }

        this.packageManager = new SyntheticPackageManager(apkInfo, appInfo);
        this.resources = new SyntheticResources(apkInfo);
        this.resources.getAssets().addAssetPath(apkFile.getAbsolutePath());
        this.contentResolver = new SyntheticContentResolver(this);
        this.theme = resources.newTheme();

        // Universal resource runtime: parse resources.arsc from the APK and
        // resolve the app's default theme so framework/appx code sees real
        // theme attributes (windowActionBar, windowNoTitle, colors, dimensions).
        try {
            int themeRes = apkInfo.getThemeForActivity(apkInfo.getMainActivity());
            this.resources.loadArsc(apkFile, themeRes);
        } catch (Throwable t) {
            System.out.println("[-] Notice loading resources.arsc: " + t);
        }
        this.theme.applyStyle(apkInfo.getThemeForActivity(apkInfo.getMainActivity()), true);
    }

    @Override
    public Resources.Theme getTheme() {
        return theme;
    }

    @Override
    public android.content.ContentResolver getContentResolver() {
        return contentResolver;
    }

    @Override
    public int getUserId() {
        return 0;
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader() != null ? getClass().getClassLoader() : ClassLoader.getSystemClassLoader();
    }

    @Override
    public Context createConfigurationContext(android.content.res.Configuration overrideConfiguration) {
        return this;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        return this;
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        return false;
    }

    @Override
    public Context createDisplayContext(android.view.Display display) {
        return this;
    }

    @Override
    public android.view.Display getDisplay() {
        return new android.view.Display(0);
    }

    @Override
    public Context createCredentialProtectedStorageContext() {
        return this;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) {
        return this;
    }

    @Override
    public android.content.res.AssetManager getAssets() {
        return resources.getAssets();
    }

    @Override
    public PackageManager getPackageManager() {
        return packageManager;
    }

    @Override
    public String getPackageName() {
        return apkInfo.getPackageName();
    }

    @Override
    public String getOpPackageName() {
        return apkInfo.getPackageName();
    }

    @Override
    public String getBasePackageName() {
        return apkInfo.getPackageName();
    }

    @Override
    public String getPackageResourcePath() {
        return baseDir.getAbsolutePath();
    }

    @Override
    public String getPackageCodePath() {
        return baseDir.getAbsolutePath();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return appInfo;
    }

    @Override
    public Context getApplicationContext() {
        return mApplication != null ? mApplication : this;
    }

    @Override
    public void registerComponentCallbacks(android.content.ComponentCallbacks callback) {
        // Safe in-process register callback
    }

    @Override
    public void unregisterComponentCallbacks(android.content.ComponentCallbacks callback) {
        // Safe in-process unregister callback
    }

    @Override
    public Looper getMainLooper() {
        return Looper.getMainLooper();
    }

    @Override
    public File getFilesDir() {
        return filesDir;
    }

    @Override
    public File getNoBackupFilesDir() {
        File f = new File(baseDir, "no_backup");
        f.mkdirs();
        return f;
    }

    @Override
    public File getDataDir() {
        return baseDir;
    }

    @Override
    public File getCacheDir() {
        return cacheDir;
    }

    @Override
    public File getDatabasePath(String name) {
        if (name == null) return null;
        File f = new File(name);
        if (f.isAbsolute()) {
            return f;
        }
        return new File(dbDir, name);
    }

    @Override
    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
        if (name == null || name.trim().isEmpty()) {
            name = "default_preferences";
        }
        final String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sharedPrefsCache.computeIfAbsent(name, n -> {
            File prefFile = new File(prefsDir, safeName + ".prefs");
            return new SharedPreferencesImpl(prefFile);
        });
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException {
        File file = new File(filesDir, name);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return new FileOutputStream(file, (mode & 0x8000) != 0);
    }

    @Override
    public FileInputStream openFileInput(String name) throws java.io.FileNotFoundException {
        File file = new File(filesDir, name);
        return new FileInputStream(file);
    }

    @Override
    public Object getSystemService(String name) {
        Object s = services.get(name);
        if (s != null) return s;
        return SyntheticServiceManager.getService(this, name);
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        return SyntheticServiceManager.getServiceName(serviceClass);
    }

    public void registerSystemService(String name, Object instance) {
        services.put(name, instance);
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSelfPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void startActivity(android.content.Intent intent) {
        startActivity(intent, null);
    }

    @Override
    public void startActivity(android.content.Intent intent, android.os.Bundle options) {
        System.out.println("\n------------------------------------------------------------------");
        System.out.println("[*] [INTENT DISPATCHER] Context.startActivity intercepted!");
        if (intent != null && intent.getComponent() != null) {
            System.out.println("[+] Target Component : " + intent.getComponent().getClassName());
            System.out.println("[+] Package          : " + intent.getComponent().getPackageName());
        }
        if (intent != null && intent.getAction() != null) {
            System.out.println("[+] Action           : " + intent.getAction());
        }
        if (intent != null && intent.getExtras() != null) {
            System.out.println("[+] Extras           : " + intent.getExtras());
        }
        System.out.println("------------------------------------------------------------------\n");
        ActivityStack.getInstance().launchActivity(intent);
    }

    // =========================================================================
    // Background Service & Task Execution Pipeline
    // =========================================================================

    @Override
    public android.content.ComponentName startService(android.content.Intent service) {
        return HeadlessTaskDispatcher.getInstance().startService(this, service);
    }

    @Override
    public android.content.ComponentName startForegroundService(android.content.Intent service) {
        return HeadlessTaskDispatcher.getInstance().startService(this, service);
    }

    @Override
    public boolean stopService(android.content.Intent service) {
        return HeadlessTaskDispatcher.getInstance().stopService(this, service);
    }

    @Override
    public boolean bindService(android.content.Intent service, android.content.ServiceConnection conn, int flags) {
        return HeadlessTaskDispatcher.getInstance().bindService(this, service, conn, flags);
    }

    @Override
    public void unbindService(android.content.ServiceConnection conn) {
        HeadlessTaskDispatcher.getInstance().unbindService(this, conn);
    }

    @Override
    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
        return HeadlessTaskDispatcher.getInstance().registerReceiver(this, receiver, filter);
    }

    @Override
    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter, int flags) {
        return HeadlessTaskDispatcher.getInstance().registerReceiver(this, receiver, filter);
    }

    @Override
    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter, String broadcastPermission, android.os.Handler scheduler) {
        return HeadlessTaskDispatcher.getInstance().registerReceiver(this, receiver, filter);
    }

    @Override
    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter, String broadcastPermission, android.os.Handler scheduler, int flags) {
        return HeadlessTaskDispatcher.getInstance().registerReceiver(this, receiver, filter);
    }

    @Override
    public void unregisterReceiver(android.content.BroadcastReceiver receiver) {
        HeadlessTaskDispatcher.getInstance().unregisterReceiver(receiver);
    }

    @Override
    public void sendBroadcast(android.content.Intent intent) {
        HeadlessTaskDispatcher.getInstance().sendBroadcast(this, intent);
    }

    @Override
    public void sendBroadcast(android.content.Intent intent, String receiverPermission) {
        HeadlessTaskDispatcher.getInstance().sendBroadcast(this, intent);
    }

    @Override
    public void sendOrderedBroadcast(android.content.Intent intent, String receiverPermission) {
        HeadlessTaskDispatcher.getInstance().sendBroadcast(this, intent);
    }
}
