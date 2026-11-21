package org.headless.runner.agent;

import android.graphics.Insets;
import android.view.WindowInsets;

/**
 * Desktop root-window insets provider.
 *
 * On a device View.getRootWindowInsets() reads ViewRootImpl.getWindowInsets()
 * (needs the WindowManager session and a display driver - none exist in this
 * single-process runner). The RuntimeNeutralizerAgent rewrites that one method
 * to delegate here, so framework-internal and app-internal insets consumers
 * (react-native-safe-area-context SafeAreaProvider, WindowInsetsCompat, ...)
 * observe genuine system-bar insets built from the device profile.
 */
public final class DesktopWindowInsetsProvider {

    // Pixel-5 class device profile: 420 dpi, 1080x1920, gesture navigation.
    private static volatile WindowInsets sInsets;

    private DesktopWindowInsetsProvider() {}

    public static WindowInsets getRootWindowInsets() {
        WindowInsets wi = sInsets;
        if (wi == null) {
            synchronized (DesktopWindowInsetsProvider.class) {
                wi = sInsets;
                if (wi == null) {
                    try {
                        wi = new WindowInsets.Builder()
                                .setSystemWindowInsets(Insets.of(0, 63, 0, 63))
                                .build();
                    } catch (Throwable t) {
                        // Framework jar without WindowInsets.Builder: leave the
                        // insets absent (null) rather than crash - callers must
                        // handle a device without system-bar data anyway.
                        return null;
                    }
                    sInsets = wi;
                }
            }
        }
        return wi;
    }

    /** Window visible display frame for the desktop device profile. */
    public static void applyDisplayFrame(android.graphics.Rect outRect) {
        if (outRect == null) return;
        outRect.set(0, 0, 1080, 1920 - 63);
    }

    /** System window (visible) insets rect for the desktop device profile. */
    public static void applyVisibleInsets(android.graphics.Rect outRect) {
        if (outRect == null) return;
        outRect.set(0, 63, 0, 63);
    }
}
