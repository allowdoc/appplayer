package org.headless.runner.core;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * MAIN LOADER: universal React Native boot driver (the "auto-attach module
 * loader"). Completely app-agnostic - it only ever talks to classes the APK
 * itself ships (com.facebook.react.* comes from the app's dex, not from us):
 *
 *   1. If a ReactRootView is already mounted by the app's own onCreate flow,
 *      force it to the device size (1080x1920) so JS-driven UIManager commits
 *      produce real views.
 *   2. If not (exotic delegates, Expo wrappers, deferred mounts), find the
 *      app's ReactNativeHost -> ReactInstanceManager, create a ReactRootView
 *      OURSELVES, startReactApplication(mainComponentName) and attach it -
 *      exactly what ReactActivity would have done, driven by the app's own
 *      RN classes at runtime. No per-app patching, no decompiled watching.
 *
 * The lazy hooks this relies on are the same entry points real Android uses:
 * ClassLoader loads classes when first referenced, CatalystInstanceImpl.runJSBundle
 * boots the Hermes bridge, and RN native modules self-register when the app's
 * packages are reflected.
 */
public class MainLoader {

    private static boolean sMounted = false;

    public static void ensureReactMounted(Activity activity) {
        if (activity == null) return;
        try {
            if (Boolean.getBoolean("headless.rnloader.disabled")) return;

            // 1. Existing ReactRootView? (normal RN flow already ran)
            View existing = findReactRoot(activity);
            if (existing != null) {
                mountExisting(activity, existing);
                return;
            }

            // 2. No root mounted - is this actually a React Native app?
            android.app.Application app = activity.getApplication();
            if (app == null) return;

            Object host = findReactNativeHost(app);
            if (host == null) {
                System.out.println("[*] [MainLoader] Not a React Native app (no ReactNativeHost found) - skipping.");
                return;
            }
            System.out.println("[+] [MainLoader] React Native app detected. Host: " + host.getClass().getName());

            // 3. Classic bridge: ReactInstanceManager. (Bridgeless ReactHost: not yet.)
            Object rim = callFirst(host, "getReactInstanceManager");
            if (rim == null) {
                System.out.println("[!] [MainLoader] No ReactInstanceManager on host "
                        + "(bridgeless RN / ReactHost not yet supported). Skipping auto-mount.");
                return;
            }

            // 4. Main component name from the app's own classes.
            String componentName = findMainComponentName(activity, app);
            if (componentName == null || componentName.isEmpty()) {
                System.out.println("[!] [MainLoader] Could not resolve mainComponentName from the app "
                        + "(no getMainComponentName) - skipping auto-mount.");
                return;
            }
            System.out.println("[+] [MainLoader] Main component: " + componentName);

            // 5. Create + start ReactRootView with the app's own RN classes.
            Class<?> rrvCls = findClass("com.facebook.react.ReactRootView",
                    "com.facebook.react.views.rootview.ReactRootView");
            if (rrvCls == null) {
                System.out.println("[!] [MainLoader] ReactRootView not found in APK - skipping auto-mount.");
                return;
            }
            Object rootView = rrvCls.getDeclaredConstructor(android.content.Context.class)
                    .newInstance(activity);
            if (!startReactApplication(rrvCls, rootView, rim, componentName)) {
                return;
            }

            // 6. Attach to window and size to the device frame so Yoga layout runs.
            try {
                Method setContent = Activity.class.getMethod("setContentView", View.class);
                setContent.invoke(activity, rootView);
            } catch (Throwable t) {
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                decor.addView((View) rootView);
            }
            System.out.println("[+] [MainLoader] ReactRootView attached to window.");

            // 7. onHostResume so the instance mounts the component.
            try {
                Class<?> rimCls = rim.getClass();
                Method resume = null;
                for (Method m : rimCls.getMethods()) {
                    if ("onHostResume".equals(m.getName())) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length >= 1 && pt[0] == Activity.class) { resume = m; break; }
                    }
                }
                if (resume != null) {
                    if (resume.getParameterTypes().length >= 2) {
                        resume.invoke(rim, activity, null);
                    } else {
                        resume.invoke(rim, activity);
                    }
                }
            } catch (Throwable t) {
                System.out.println("[-] Notice onHostResume: " + t.getMessage());
            }

            sMounted = true;
            System.out.println("[+] [MainLoader] React application mounted. Waiting for first JS commit...");

            // 8. Wait (bounded) for the catalyst instance + first layout pass.
            waitForFirstCommit((View) rootView, 15000);
            mountExisting(activity, (View) rootView);
        } catch (Throwable t) {
            System.err.println("[-] [MainLoader] RN auto-mount failed (app may not be RN): " + t);
            t.printStackTrace();
        }
    }

    private static void mountExisting(Activity activity, View reactRoot) {
        try {
            int wSpec = View.MeasureSpec.makeMeasureSpec(
                    org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_WIDTH, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(
                    org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_HEIGHT, View.MeasureSpec.EXACTLY);
            reactRoot.measure(wSpec, hSpec);
            reactRoot.layout(0, 0,
                    org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_WIDTH,
                    org.headless.runner.ui.HeadlessScreenCapture.DEFAULT_HEIGHT);
            int views = countViews(reactRoot, 0);
            System.out.println("[+] [MainLoader] React root laid out at 1080x1920; "
                    + views + " real view(s) in hierarchy.");
            sMounted = true;
        } catch (Throwable t) {
            System.out.println("[-] Notice laying out ReactRootView: " + t.getMessage());
        }
    }

    private static void waitForFirstCommit(View root, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (System.currentTimeMillis() < deadline) {
                Looper.getMainLooper().getQueue().drainPendingMessages();
                int views = countViews(root, 0);
                if (views > 1) {
                    System.out.println("[+] [MainLoader] First JS commit observed: " + views + " view(s).");
                    return;
                }
                Thread.sleep(200);
            }
            System.out.println("[!] [MainLoader] No JS-driven views after "
                    + timeoutMs + "ms (bundle may still be executing).");
        } catch (InterruptedException ignored) {
        } catch (Throwable t) {
            System.out.println("[-] Notice waiting for first commit: " + t.getMessage());
        }
    }

    private static int countViews(View v, int depth) {
        if (v == null || depth > 32) return 0;
        int n = 1;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                n += countViews(vg.getChildAt(i), depth + 1);
            }
        }
        return n;
    }

    private static View findReactRoot(Activity activity) {
        try {
            Class<?> rrv = findClass("com.facebook.react.ReactRootView",
                    "com.facebook.react.views.rootview.ReactRootView");
            if (rrv == null) return null;
            return findInstanceOf(rrv, activity.getWindow() != null
                    ? activity.getWindow().getDecorView() : null, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static View findInstanceOf(Class<?> target, View node, int depth) {
        if (node == null || depth > 32) return null;
        if (target.isAssignableFrom(node.getClass())) return node;
        if (node instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) node;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findInstanceOf(target, vg.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object findReactNativeHost(android.app.Application app) {
        try {
            Class<?> reactApp = findClass("com.facebook.react.ReactApplication");
            if (reactApp != null && reactApp.isAssignableFrom(app.getClass())) {
                return app.getClass().getMethod("getReactNativeHost").invoke(app);
            }
            // Expo/wrapped applications often expose getReactNativeHost anyway.
            try {
                return app.getClass().getMethod("getReactNativeHost").invoke(app);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable t) {
            System.out.println("[-] Notice finding ReactNativeHost: " + t.getMessage());
        }
        return null;
    }

    private static String findMainComponentName(Activity activity, android.app.Application app) {
        try {
            Method m = activity.getClass().getMethod("getMainComponentName");
            Object v = m.invoke(activity);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {}
        try {
            Method m = app.getClass().getMethod("getMainComponentName");
            Object v = m.invoke(app);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {}
        // ReactNativeHost has no name; try common helpers the app may expose.
        for (String helper : new String[]{"getComponentName", "getModuleName"}) {
            try {
                Method m = app.getClass().getMethod(helper);
                Object v = m.invoke(app);
                if (v instanceof String) return (String) v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean startReactApplication(Class<?> rrvCls, Object rootView, Object rim, String name) {
        Class<?> rimCls = findClass("com.facebook.react.ReactInstanceManager");
        if (rimCls == null) return false;
        try {
            // Newer signature: startReactApplication(ReactInstanceManager, String, Bundle)
            Method m = rrvCls.getMethod("startReactApplication", rimCls, String.class, android.os.Bundle.class);
            m.invoke(rootView, rim, name, null);
            System.out.println("[+] [MainLoader] startReactApplication(" + name + ") dispatched.");
            return true;
        } catch (NoSuchMethodException e) {
            try {
                Method m = rrvCls.getMethod("startReactApplication", rimCls, String.class);
                m.invoke(rootView, rim, name);
                System.out.println("[+] [MainLoader] startReactApplication(" + name + ") dispatched (2-arg).");
                return true;
            } catch (Throwable t2) {
                System.out.println("[-] [MainLoader] startReactApplication failed: " + t2);
                return false;
            }
        } catch (Throwable t) {
            System.out.println("[-] [MainLoader] startReactApplication failed: " + t);
            return false;
        }
    }

    private static Object callFirst(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> findClass(String... names) {
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean isMounted() {
        return sMounted;
    }
}
