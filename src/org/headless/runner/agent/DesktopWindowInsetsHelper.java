package org.headless.runner.agent;

/**
 * Static helpers used by the runner's window-attach code. Thin facade over
 * {@link DesktopWindowInsetsProvider} so core classes do not import the agent
 * package root class directly (keeps the agent premain dependency surface
 * minimal).
 */
public final class DesktopWindowInsetsHelper {

    private DesktopWindowInsetsHelper() {}

    public static void applyDisplayFrame(android.graphics.Rect outRect) {
        DesktopWindowInsetsProvider.applyDisplayFrame(outRect);
    }

    public static void applyVisibleInsets(android.graphics.Rect outRect) {
        DesktopWindowInsetsProvider.applyVisibleInsets(outRect);
    }
}
