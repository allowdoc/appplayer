package android.view;

import android.content.Context;
import android.graphics.Rect;

/**
 * Pure Java fallback for ThreadedRenderer.
 * Disables hardware accelerated rendering in headless/zero-VM OpenJDK environment,
 * preventing any UnsatisfiedLinkError on native render engine methods.
 */
public class ThreadedRenderer {
    public static boolean sRendererDisabled = true;

    public static void setFPSDivisor(int divisor) {
        // Pure-JVM no-op: prevents UnsatisfiedLinkError on nHackySetRTAnimationsEnabled
    }

    public static boolean isAvailable() {
        return false;
    }

    public static void disable(boolean val) {
        sRendererDisabled = true;
    }

    public static void enableForegroundTrimming() {
    }

    public static ThreadedRenderer create(Context context, boolean translucent, String name) {
        return null;
    }

    public void destroy() {}
    public boolean isEnabled() { return false; }
    public void setEnabled(boolean enabled) {}
    public boolean isRequested() { return false; }
    public void setRequested(boolean requested) {}
    public void invalidateRoot() {}
}
