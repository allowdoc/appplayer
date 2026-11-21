package org.headless.runner.ui;

import android.view.View;

/**
 * ReactRootView detection helper.
 *
 * HISTORICAL NOTE: this class used to fabricate a synthetic login dashboard
 * (LinearLayout + EditText + "GET OTP" button) whenever ReactRootView had no
 * children. That fabricated UI was removed permanently: the universal Hermes
 * bridge (org.headless.runner.rn.UniversalReactBridge) now executes the app's
 * REAL index.android.bundle, and the app's own ViewManagers create the real
 * React views. No engine code may ever synthesize app UI again.
 */
public final class ReactViewPopulator {

    private ReactViewPopulator() {}

    public static boolean isReactRootView(View view) {
        if (view == null) return false;
        Class<?> cls = view.getClass();
        while (cls != null && cls != Object.class) {
            if ("com.facebook.react.ReactRootView".equals(cls.getName())) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }
}
