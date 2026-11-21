package org.headless.runner.core;

import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import org.headless.runner.manifest.ApkInfo;

import java.util.Locale;

public class SyntheticResources extends Resources {
    private final ApkInfo apkInfo;

    public SyntheticResources(ApkInfo apkInfo) {
        super(new AssetManager(), createDisplayMetrics(), createConfiguration());
        this.apkInfo = apkInfo;
        if (apkInfo != null && apkInfo.getPackageName() != null) {
            setPackageName(apkInfo.getPackageName());
        }
    }

    private static DisplayMetrics createDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.widthPixels = 1080;
        dm.heightPixels = 1920;
        dm.density = 2.625f;
        dm.densityDpi = 420;
        dm.scaledDensity = 2.625f;
        dm.xdpi = 420.0f;
        dm.ydpi = 420.0f;
        return dm;
    }

    private static Configuration createConfiguration() {
        Configuration cfg = new Configuration();
        cfg.setToDefaults();
        cfg.setLocale(Locale.ENGLISH);
        cfg.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        return cfg;
    }

    @Override
    public String getResourcePackageName(int resid) {
        return apkInfo != null ? apkInfo.getPackageName() : "android";
    }
}
