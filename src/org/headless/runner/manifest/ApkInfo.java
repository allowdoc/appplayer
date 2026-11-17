package org.headless.runner.manifest;

import java.util.ArrayList;
import java.util.List;

public class ApkInfo {
    public static class ProviderEntry {
        public final String className;
        public final String authority;
        public final int initOrder;

        public ProviderEntry(String className, String authority, int initOrder) {
            this.className = className;
            this.authority = authority;
            this.initOrder = initOrder;
        }
    }

    public static class ServiceEntry {
        public final String className;
        public final String permission;
        public final boolean exported;

        public ServiceEntry(String className, String permission, boolean exported) {
            this.className = className;
            this.permission = permission;
            this.exported = exported;
        }
    }

    private final String packageName;
    private String applicationClass;
    private final String mainActivity;
    private final String versionName;
    private final int versionCode;
    private final List<ProviderEntry> providers = new ArrayList<>();
    private final List<ServiceEntry> services = new ArrayList<>();
    private final java.util.Map<String, Object> applicationMetaData = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Map<String, Object>> componentMetaData = new java.util.HashMap<>();

    public ApkInfo(String packageName, String applicationClass, String mainActivity, String versionName, int versionCode) {
        this.packageName = packageName;
        this.applicationClass = applicationClass;
        this.mainActivity = mainActivity;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    public String getPackageName() { return packageName; }
    public String getApplicationClass() { return applicationClass; }
    public void setApplicationClass(String applicationClass) { this.applicationClass = applicationClass; }
    public String getMainActivity() { return mainActivity; }
    public String getVersionName() { return versionName; }
    public int getVersionCode() { return versionCode; }
    public List<ProviderEntry> getProviders() { return providers; }
    public List<ServiceEntry> getServices() { return services; }

    public void addProvider(String className, String authority, int initOrder) {
        if (className != null && !className.isEmpty()) {
            providers.add(new ProviderEntry(className, authority, initOrder));
        }
    }

    public void addService(String className, String permission, boolean exported) {
        if (className != null && !className.isEmpty()) {
            services.add(new ServiceEntry(className, permission, exported));
        }
    }

    public java.util.Map<String, Object> getApplicationMetaData() { return applicationMetaData; }
    public java.util.Map<String, java.util.Map<String, Object>> getComponentMetaData() { return componentMetaData; }

    public void addApplicationMetaData(String key, Object val) {
        if (key != null) applicationMetaData.put(key, val);
    }

    public void addComponentMetaData(String comp, String key, Object val) {
        if (comp != null && key != null) {
            componentMetaData.computeIfAbsent(comp, k -> new java.util.HashMap<>()).put(key, val);
        }
    }

    public android.os.Bundle createApplicationMetaDataBundle() {
        android.os.Bundle b = new android.os.Bundle();
        for (java.util.Map.Entry<String, Object> e : applicationMetaData.entrySet()) {
            putToBundle(b, e.getKey(), e.getValue());
        }
        return b;
    }

    public android.os.Bundle createComponentMetaDataBundle(String comp) {
        android.os.Bundle b = new android.os.Bundle();
        java.util.Map<String, Object> map = componentMetaData.get(comp);
        if (map != null) {
            for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
                putToBundle(b, e.getKey(), e.getValue());
            }
        }
        return b;
    }

    private static void putToBundle(android.os.Bundle b, String k, Object v) {
        if (v instanceof String) b.putString(k, (String) v);
        else if (v instanceof Integer) b.putInt(k, (Integer) v);
        else if (v instanceof Boolean) b.putBoolean(k, (Boolean) v);
        else if (v instanceof Long) b.putLong(k, (Long) v);
        else if (v instanceof Float) b.putFloat(k, (Float) v);
    }

    @Override
    public String toString() {
        return "ApkInfo{" +
                "package='" + packageName + '\'' +
                ", appClass='" + applicationClass + '\'' +
                ", mainActivity='" + mainActivity + '\'' +
                ", version=" + versionName + " (" + versionCode + ")" +
                ", providers=" + providers.size() +
                '}';
    }
}
