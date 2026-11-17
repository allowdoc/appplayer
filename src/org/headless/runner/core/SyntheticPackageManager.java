package org.headless.runner.core;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import org.headless.runner.manifest.ApkInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SyntheticPackageManager extends MockPackageManager {
    private final ApkInfo apkInfo;
    private final ApplicationInfo appInfo;
    private final PackageInfo packageInfo;

    public SyntheticPackageManager(ApkInfo apkInfo, ApplicationInfo appInfo) {
        this.apkInfo = apkInfo;
        this.appInfo = appInfo;
        if (this.appInfo != null) {
            if (this.appInfo.metaData == null) {
                this.appInfo.metaData = apkInfo.createApplicationMetaDataBundle();
            }
            if (!this.appInfo.metaData.containsKey("com.google.android.gms.version")
                    || this.appInfo.metaData.getInt("com.google.android.gms.version") > 0x7f000000) {
                this.appInfo.metaData.putInt("com.google.android.gms.version", 12451000);
            }
        }

        this.packageInfo = new PackageInfo();
        this.packageInfo.packageName = apkInfo.getPackageName();
        this.packageInfo.versionName = apkInfo.getVersionName();
        this.packageInfo.versionCode = (int) apkInfo.getVersionCode();
        this.packageInfo.applicationInfo = appInfo;
        this.packageInfo.firstInstallTime = System.currentTimeMillis();
        this.packageInfo.lastUpdateTime = System.currentTimeMillis();
        android.content.pm.Signature defaultSig = new android.content.pm.Signature("308201");
        this.packageInfo.signatures = new android.content.pm.Signature[]{defaultSig};

        if (apkInfo.getMainActivity() != null) {
            ActivityInfo act = new ActivityInfo();
            act.packageName = apkInfo.getPackageName();
            act.name = apkInfo.getMainActivity();
            act.applicationInfo = appInfo;
            act.metaData = apkInfo.createComponentMetaDataBundle(apkInfo.getMainActivity());
            this.packageInfo.activities = new ActivityInfo[]{act};
        }
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
        ActivityInfo ai = new ActivityInfo();
        ai.packageName = component.getPackageName();
        ai.name = component.getClassName();
        ai.applicationInfo = appInfo;
        ai.metaData = apkInfo.createComponentMetaDataBundle(component.getClassName());
        ai.parentActivityName = null;
        return ai;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
        ServiceInfo si = new ServiceInfo();
        si.packageName = component.getPackageName();
        si.name = component.getClassName();
        si.applicationInfo = appInfo;
        si.metaData = apkInfo.createComponentMetaDataBundle(component.getClassName());
        return si;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
        ProviderInfo pi = new ProviderInfo();
        pi.packageName = component.getPackageName();
        pi.name = component.getClassName();
        pi.applicationInfo = appInfo;
        pi.grantUriPermissions = true;
        pi.metaData = apkInfo.createComponentMetaDataBundle(component.getClassName());

        // AndroidX AppInitializer discovery
        if (component.getClassName() != null && component.getClassName().contains("InitializationProvider")) {
            pi.metaData.putString("androidx.work.WorkManagerInitializer", "androidx.startup");
            pi.metaData.putString("androidx.lifecycle.ProcessLifecycleInitializer", "androidx.startup");
            pi.metaData.putString("androidx.emoji2.text.EmojiCompatInitializer", "androidx.startup");
            pi.metaData.putString("androidx.profileinstaller.ProfileInstallerInitializer", "androidx.startup");
        }
        return pi;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if (packageName.equals(apkInfo.getPackageName())) {
            return packageInfo;
        }
        PackageInfo pi = new PackageInfo();
        pi.packageName = packageName;
        if ("com.google.android.gms".equals(packageName) || "com.android.vending".equals(packageName)) {
            pi.versionName = "24.0.0";
            pi.versionCode = 240000000;
        } else {
            pi.versionName = "1.0";
            pi.versionCode = 1;
        }
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = packageName;
        pi.applicationInfo.metaData = new android.os.Bundle();
        pi.applicationInfo.metaData.putInt("com.google.android.gms.version", 240000000);
        android.content.pm.Signature defaultSig = new android.content.pm.Signature("308201");
        pi.signatures = new android.content.pm.Signature[]{defaultSig};
        return pi;
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, int flags, int userId) throws NameNotFoundException {
        return getPackageInfo(packageName, flags);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if (packageName.equals(apkInfo.getPackageName())) {
            return appInfo;
        }
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        ai.metaData = new android.os.Bundle();
        ai.metaData.putInt("com.google.android.gms.version", 12451000);
        return ai;
    }

    @Override
    public ApplicationInfo getApplicationInfoAsUser(String packageName, int flags, int userId) throws NameNotFoundException {
        return getApplicationInfo(packageName, flags);
    }

    @Override
    public String getInstallerPackageName(String packageName) {
        return "com.android.vending"; // Google Play Store
    }

    @Override
    public boolean hasSystemFeature(String name) {
        return true;
    }

    @Override
    public boolean hasSystemFeature(String name, int version) {
        return true;
    }

    @Override
    public int checkPermission(String permName, String pkgName) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSignatures(String pkg1, String pkg2) {
        return PackageManager.SIGNATURE_MATCH;
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        return new String[]{apkInfo.getPackageName()};
    }

    @Override
    public String getNameForUid(int uid) {
        return apkInfo.getPackageName();
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        List<PackageInfo> list = new ArrayList<>();
        list.add(packageInfo);
        return list;
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) {
        List<ApplicationInfo> list = new ArrayList<>();
        list.add(appInfo);
        return list;
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags) {
        ResolveInfo ri = new ResolveInfo();
        if (packageInfo.activities != null && packageInfo.activities.length > 0) {
            ri.activityInfo = packageInfo.activities[0];
        }
        return ri;
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        ResolveInfo ri = resolveActivity(intent, flags);
        if (ri != null && ri.activityInfo != null) {
            List<ResolveInfo> list = new ArrayList<>();
            list.add(ri);
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        List<ResolveInfo> list = new ArrayList<>();
        if (intent != null && intent.getComponent() != null) {
            ResolveInfo ri = new ResolveInfo();
            ri.serviceInfo = new ServiceInfo();
            ri.serviceInfo.packageName = intent.getComponent().getPackageName();
            ri.serviceInfo.name = intent.getComponent().getClassName();
            ri.serviceInfo.applicationInfo = appInfo;
            list.add(ri);
            return list;
        }
        for (ApkInfo.ServiceEntry se : apkInfo.getServices()) {
            ResolveInfo ri = new ResolveInfo();
            ri.serviceInfo = new ServiceInfo();
            ri.serviceInfo.packageName = apkInfo.getPackageName();
            ri.serviceInfo.name = se.className;
            ri.serviceInfo.permission = se.permission;
            ri.serviceInfo.exported = se.exported;
            ri.serviceInfo.applicationInfo = appInfo;
            list.add(ri);
        }
        return list;
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags) {
        List<ResolveInfo> list = queryIntentServices(intent, flags);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        return Collections.emptyList();
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        if (packageName.equals(apkInfo.getPackageName()) && apkInfo.getMainActivity() != null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(packageName, apkInfo.getMainActivity()));
            return intent;
        }
        return null;
    }

    @Override
    public CharSequence getApplicationLabel(ApplicationInfo info) {
        return apkInfo.getPackageName();
    }
}
