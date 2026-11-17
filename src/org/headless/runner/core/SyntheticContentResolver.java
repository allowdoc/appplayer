package org.headless.runner.core;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.os.Bundle;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal In-Process ContentResolver.
 * Dynamically resolves manifest ContentProviders (FileProvider, MoEngage, Sentry, Room, etc.)
 * and falls back to synthetic system settings provider for framework queries.
 */
public class SyntheticContentResolver extends ContentResolver {
    private final IContentProvider mockSettingsProvider;
    private final Map<String, ContentProvider> mProviders = new ConcurrentHashMap<>();

    public SyntheticContentResolver(Context context) {
        super(context);
        this.mockSettingsProvider = (IContentProvider) Proxy.newProxyInstance(
            IContentProvider.class.getClassLoader(),
            new Class<?>[]{IContentProvider.class},
            (proxy, method, args) -> {
                if ("call".equals(method.getName())) {
                    String settingName = "";
                    if (args != null) {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof String) {
                                String s = (String) args[i];
                                if (!s.startsWith("GET_") && !s.startsWith("PUT_") && !"settings".equals(s) && !s.contains(".")) {
                                    settingName = s;
                                    break;
                                }
                            }
                        }
                    }
                    Bundle b = new Bundle();
                    if ("font_scale".equals(settingName)) {
                        b.putString("value", "1.0");
                        return b;
                    } else if ("screen_off_timeout".equals(settingName)) {
                        b.putString("value", "60000");
                        return b;
                    } else if ("android_id".equals(settingName)) {
                        b.putString("value", "a1b2c3d4e5f60718");
                        return b;
                    } else if ("development_settings_enabled".equals(settingName) || "adb_enabled".equals(settingName) || "force_resizable_activities".equals(settingName) || "show_angle_in_use_dialog_box".equals(settingName)) {
                        b.putString("value", "0");
                        return b;
                    } else if ("install_non_market_apps".equals(settingName)) {
                        b.putString("value", "1");
                        return b;
                    }
                    b.putString("value", null);
                    return b;
                }
                return null;
            }
        );
    }

    public void registerProvider(String authority, ContentProvider cp) {
        if (authority != null && cp != null) {
            for (String auth : authority.split(";")) {
                mProviders.put(auth.trim(), cp);
            }
        }
    }

    @Override
    protected IContentProvider acquireProvider(Context c, String name) {
        if (name != null) {
            ContentProvider cp = mProviders.get(name);
            if (cp != null) {
                return cp.getIContentProvider();
            }
        }
        return mockSettingsProvider;
    }

    @Override
    public boolean releaseProvider(IContentProvider icp) {
        return true;
    }

    @Override
    protected IContentProvider acquireUnstableProvider(Context c, String name) {
        return acquireProvider(c, name);
    }

    @Override
    public boolean releaseUnstableProvider(IContentProvider icp) {
        return true;
    }

    @Override
    public void unstableProviderDied(IContentProvider icp) {}
}
