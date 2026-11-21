package org.headless.runner.core;

import android.app.Activity;
import android.view.View;

/**
 * Headless traversal pump (desktop ViewRootImpl.performTraversals equivalent).
 *
 * A real device runs measure/layout/draw through ViewRootImpl per frame and
 * fires the ViewTreeObserver pre-draw listeners right before each draw pass.
 * Headless mode has no display driver, so onMeasure/onLayout/onPreDraw never
 * fire on their own. This pump runs the essential traversal contract on the
 * main looper:
 *
 *   attach (once) -> measure -> layout -> ViewTreeObserver.dispatchOnPreDraw()
 *
 * App views that compute first-frame data in an OnPreDrawListener - most
 * importantly react-native-safe-area-context's SafeAreaProvider, which emits
 * the onInsetsChange event the JS gate waits for - therefore behave exactly
 * like they do on a device, universally, without per-app code.
 */
public final class HeadlessChoreographer {

    private static final long FRAME_INTERVAL_MS = 400;
    private static volatile boolean sStarted;
    private static volatile boolean sPreDrawFiredThisSession;
    private static long sPassCount;

    private HeadlessChoreographer() {}

    public static void start() {
        if (sStarted) return;
        synchronized (HeadlessChoreographer.class) {
            if (sStarted) return;
            sStarted = true;
        }
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable frame = new Runnable() {
            @Override
            public void run() {
                try {
                    traverseTop();
                } catch (Throwable ignored) {
                }
                h.postDelayed(this, FRAME_INTERVAL_MS);
            }
        };
        h.postDelayed(frame, FRAME_INTERVAL_MS);
        System.out.println("[+] [HeadlessChoreographer] Traversal pump active ("
                + FRAME_INTERVAL_MS + "ms frame interval, pre-draw dispatch on main looper).");
    }

    /** Locate the app's SafeAreaProvider view and report its AttachInfo. */
    private static Object findSafeAreaProviderAttachInfo(View decor) {
        java.util.ArrayDeque<View> stack = new java.util.ArrayDeque<>();
        stack.push(decor);
        int scanned = 0;
        while (!stack.isEmpty() && scanned < 400) {
            View v = stack.pop();
            scanned++;
            if (v.getClass().getName().contains("SafeAreaProvider")) {
                try {
                    java.lang.reflect.Field ai = View.class.getDeclaredField("mAttachInfo");
                    ai.setAccessible(true);
                    Object attach = ai.get(v);
                    Object floating = null;
                    try {
                        java.lang.reflect.Field fo = View.class.getDeclaredField("mFloatingTreeObserver");
                        fo.setAccessible(true);
                        floating = fo.get(v);
                    } catch (Throwable ignored) {}
                    java.lang.reflect.Field ft = null;
                    Object shared = null;
                    if (attach != null) {
                        ft = attach.getClass().getDeclaredField("mTreeObserver");
                        ft.setAccessible(true);
                        shared = ft.get(attach);
                    }
                    return "attach=" + (attach != null ? Integer.toHexString(System.identityHashCode(attach)) : "null")
                            + " sharedObs=" + (shared != null ? Integer.toHexString(System.identityHashCode(shared)) : "null")
                            + " floatingObs=" + (floating != null ? Integer.toHexString(System.identityHashCode(floating)) : "null");
                } catch (Throwable t) {
                    return "probe-fail " + t;
                }
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) v;
                for (int i = vg.getChildCount() - 1; i >= 0; i--) stack.push(vg.getChildAt(i));
            }
        }
        return "no-provider (scanned " + scanned + ")";
    }

    /** One traversal pass over the top activity. Safe to call repeatedly. */
    public static void traverseTop() {
        Activity top = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
        if (top == null || top.isFinishing()) return;
        traverse(top);
    }

    public static void traverse(Activity activity) {
        if (activity == null) return;
        try {
            ActivityStack.attachWindowIfNeeded(activity);
            android.view.Window window = activity.getWindow();
            View decor = window != null ? window.getDecorView() : null;
            if (decor == null) return;

            int w = org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_WIDTH;
            int hgt = org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_HEIGHT;
            int wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(hgt, View.MeasureSpec.EXACTLY);
            decor.measure(wSpec, hSpec);
            decor.layout(0, 0, w, hgt);

            // Pre-draw listeners: the device fires these in ViewRootImpl right
            // before drawing. SafeAreaProvider (safe-area-context) registers one
            // and reports insets to JS from inside it.
            try {
                boolean canDraw = HeadlessTraversal.dispatchPreDraw(decor);
                sPassCount++;
                boolean introspect = (sPassCount % 25L) == 1L;
                if (introspect) {
                    System.out.println("[*] [HeadlessChoreographer] pass " + sPassCount
                            + " pre-draw on " + activity.getClass().getSimpleName()
                            + " (canDraw=" + canDraw + ")");
                }
            } catch (Throwable preDrawError) {
                if (!sPreDrawFiredThisSession) {
                    sPreDrawFiredThisSession = true;
                    System.out.println("[-] [HeadlessChoreographer] dispatchOnPreDraw failed: " + preDrawError);
                }
            }
        } catch (Throwable ignoredTraversal) {
        }
    }
}
