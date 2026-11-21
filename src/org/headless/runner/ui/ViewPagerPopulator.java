package org.headless.runner.ui;

import android.app.Activity;
import android.os.Binder;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Generic engine helper that forces ViewPager (and subclasses) to populate
 * their children in headless mode.
 *
 * In a real Android device, ViewPager.populate() is triggered from onMeasure() /
 * onLayout() and requires getWindowToken() != null. In headless mode with no
 * display server, onMeasure/onLayout never fires and getWindowToken() can be null
 * unless explicitly attached.
 *
 * This implementation is 100% generic: walks the view tree, attaches any
 * ViewPager to the window AttachInfo, sets layout flags, invokes populate(),
 * falls back to instantiateItem() if needed, and flushes FragmentTransactions.
 */
public class ViewPagerPopulator {

    private static final String TAG = "[ViewPagerPopulator]";

    public static void populateAll(Activity activity) {
        if (activity == null) return;
        android.view.Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;

        walkAndPopulate(activity, decor, 0);
        drainAndFlush(activity);
    }

    private static void walkAndPopulate(Activity activity, View view, int depth) {
        if (view == null) return;

        if (isViewPager(view)) {
            populateViewPager(activity, view);
        }

        // NOTE: ReactRootViews are deliberately NOT populated here.
        // Real React views arrive from the app's own ViewManagers via the
        // universal Hermes bridge (UIManager.createView). Fabricating
        // synthetic UI was removed by design.

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                walkAndPopulate(activity, vg.getChildAt(i), depth + 1);
            }
        }
    }

    private static boolean isViewPager(View view) {
        Class<?> cls = view.getClass();
        while (cls != null && cls != Object.class) {
            String name = cls.getName();
            if ("androidx.viewpager.widget.ViewPager".equals(name)) return true;
            cls = cls.getSuperclass();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void populateViewPager(Activity activity, View pager) {
        String pagerClass = pager.getClass().getName();
        try {
            // 1. Check adapter
            Object adapter = getFieldValue(pager, "mAdapter");
            if (adapter == null) {
                System.out.println("[!] " + TAG + " " + pagerClass + " has no adapter set — skipping.");
                return;
            }

            int count = 0;
            try {
                Method getCount = adapter.getClass().getMethod("getCount");
                count = (int) getCount.invoke(adapter);
            } catch (Throwable t) {
                System.err.println("[-] " + TAG + " Could not call getCount(): " + t.getMessage());
            }
            System.out.println("[*] " + TAG + " Found " + pagerClass + " with adapter=" +
                    adapter.getClass().getName() + " count=" + count);

            if (count == 0) {
                System.out.println("[!] " + TAG + " Adapter reports 0 pages — nothing to populate.");
                return;
            }

            // 2. Ensure window attachment (ViewPager.populate line 74: if (getWindowToken() == null) return;)
            ensureWindowAttached(activity, pager);

            // 3. Simulate completed layout pass
            setFirstLayoutDone(pager);

            // 4. Force populate() via reflection
            boolean populated = false;
            Class<?> cls = pager.getClass();
            while (cls != null && !populated) {
                try {
                    Method populate = cls.getDeclaredMethod("populate");
                    populate.setAccessible(true);
                    System.out.println("[*] " + TAG + " Calling populate() on " + pagerClass + "...");
                    populate.invoke(pager);
                    populated = true;
                    int children = ((ViewGroup) pager).getChildCount();
                    System.out.println("[+] " + TAG + " populate() completed. Children now: " + children);
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                } catch (Throwable t) {
                    System.err.println("[-] " + TAG + " populate() threw: " +
                            t.getClass().getName() + ": " + t.getMessage());
                    break;
                }
            }

            // Flush pending fragment transactions scheduled during populate()
            drainAndFlush(activity);

            // 5. If child count is still 0, trigger manual instantiation
            int currentChildren = ((ViewGroup) pager).getChildCount();
            if (currentChildren == 0) {
                System.out.println("[*] " + TAG + " Pager still has 0 children — invoking manual instantiateItem...");
                manualInstantiatePages(activity, pager, adapter, count);
                drainAndFlush(activity);
                System.out.println("[+] " + TAG + " Post-manual children: " + ((ViewGroup) pager).getChildCount());
            }

            // 6. Measure and layout pager and all its newly instantiated children
            int pw = pager.getWidth() > 0 ? pager.getWidth() : 1082;
            int ph = pager.getHeight() > 0 ? pager.getHeight() : 1920;
            int wSpec = View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY);
            try {
                pager.measure(wSpec, hSpec);
                pager.layout(0, 0, pw, ph);
                ViewGroup vg = (ViewGroup) pager;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    child.measure(wSpec, hSpec);
                    child.layout(0, 0, pw, ph);
                }
                System.out.println("[+] " + TAG + " Laid out " + vg.getChildCount() + " child(ren) to [" + pw + "x" + ph + "]");
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " Unexpected error processing " + pagerClass + ": " +
                    t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void ensureWindowAttached(Activity activity, View pager) {
        try {
            if (pager.getWindowToken() != null) {
                System.out.println("[+] " + TAG + " Pager already has windowToken: " + pager.getWindowToken());
                return;
            }

            android.view.Window win = activity.getWindow();
            if (win == null) return;
            View decor = win.getDecorView();
            if (decor == null) return;

            Field attachInfoField = View.class.getDeclaredField("mAttachInfo");
            attachInfoField.setAccessible(true);
            Object attachInfo = attachInfoField.get(decor);
            if (attachInfo == null) {
                org.headless.runner.core.ActivityStack.attachWindowIfNeeded(activity);
                attachInfo = attachInfoField.get(decor);
            }

            if (attachInfo != null) {
                // Ensure mWindowToken is set on attachInfo
                try {
                    Field wtField = attachInfo.getClass().getDeclaredField("mWindowToken");
                    wtField.setAccessible(true);
                    if (wtField.get(attachInfo) == null) {
                        wtField.set(attachInfo, new Binder("window_token_fallback"));
                    }
                } catch (Throwable ignored) {}

                // Dispatch attached to window on the pager
                Method dispatch = View.class.getDeclaredMethod("dispatchAttachedToWindow", attachInfo.getClass(), int.class);
                dispatch.setAccessible(true);
                dispatch.invoke(pager, attachInfo, 0);
                System.out.println("[+] " + TAG + " dispatchAttachedToWindow successfully attached Pager! windowToken=" + pager.getWindowToken());
            }
        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " ensureWindowAttached failed: " + t.getMessage());
        }
    }

    private static void setFirstLayoutDone(View pager) {
        try {
            Field f = findField(pager.getClass(), "mFirstLayout");
            f.set(pager, false);
            System.out.println("[+] " + TAG + " mFirstLayout=false.");
        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " Could not set mFirstLayout: " + t.getMessage());
        }

        try {
            Field mPopPending = findField(pager.getClass(), "mPopulatePending");
            mPopPending.set(pager, false);
        } catch (Throwable ignored) {}

        int w = pager.getWidth();
        if (w == 0) {
            try {
                Field mRight = findField(View.class, "mRight");
                mRight.set(pager, 1082);
                Field mBottom = findField(View.class, "mBottom");
                mBottom.set(pager, 1920);
                System.out.println("[+] " + TAG + " Injected dimensions 1082x1920.");
            } catch (Throwable t) {
                System.err.println("[-] " + TAG + " Could not inject dimensions: " + t.getMessage());
            }
        }
    }

    private static void manualInstantiatePages(Activity activity, View pager, Object adapter, int count) {
        try {
            int currentItem = 0;
            try {
                Object ci = getFieldValue(pager, "mCurItem");
                if (ci instanceof Integer) currentItem = (int) ci;
            } catch (Throwable ignored) {}

            int offscreen = 1;
            try {
                Object limit = getFieldValue(pager, "mOffscreenPageLimit");
                if (limit instanceof Integer) offscreen = (int) limit;
            } catch (Throwable ignored) {}

            int start = Math.max(0, currentItem - offscreen);
            int end   = Math.min(count - 1, currentItem + offscreen);

            callMethod(adapter, "startUpdate", new Class[]{ViewGroup.class}, new Object[]{pager});

            for (int pos = start; pos <= end; pos++) {
                System.out.println("[*] " + TAG + " instantiateItem(container, " + pos + ")...");
                Object item = callMethodRet(adapter, "instantiateItem",
                        new Class[]{ViewGroup.class, int.class}, new Object[]{pager, pos});
                System.out.println("[+] " + TAG + " page " + pos + " instantiated: " + item);
            }

            callMethod(adapter, "finishUpdate", new Class[]{ViewGroup.class}, new Object[]{pager});

        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " manualInstantiatePages failed: " + t.getMessage());
        }
    }

    private static boolean sWarnedOffMain = false;

    private static void drainAndFlush(Activity activity) {
        try {
            if (android.os.Looper.getMainLooper() != null) {
                android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
            }
        } catch (Throwable ignored) {}

        if (activity instanceof androidx.fragment.app.FragmentActivity) {
            try {
                ((androidx.fragment.app.FragmentActivity) activity)
                        .getSupportFragmentManager().executePendingTransactions();
            } catch (Throwable t) {
                // Fragment transactions must run on the main thread. Callers
                // outside it (e.g. paint timers) are silently skipped - only
                // warn once instead of spamming at frame rate.
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                boolean offMain = msg.contains("main thread");
                if (!offMain || !sWarnedOffMain) {
                    System.err.println("[-] " + TAG + " executePendingTransactions skipped: " + msg);
                    if (offMain) sWarnedOffMain = true;
                }
            }
        }

        try {
            if (android.os.Looper.getMainLooper() != null) {
                android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
            }
        } catch (Throwable ignored) {}
    }

    private static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + obj.getClass().getName());
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + name + "' not found in " + cls.getName());
    }

    private static void callMethod(Object obj, String name, Class<?>[] params, Object[] args) {
        try {
            Class<?> cls = obj.getClass();
            while (cls != null) {
                try {
                    Method m = cls.getDeclaredMethod(name, params);
                    m.setAccessible(true);
                    m.invoke(obj, args);
                    return;
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            System.err.println("[-] " + TAG + " callMethod(" + name + ") failed: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace(System.err);
        }
    }

    private static Object callMethodRet(Object obj, String name, Class<?>[] params, Object[] args) {
        try {
            Class<?> cls = obj.getClass();
            while (cls != null) {
                try {
                    Method m = cls.getDeclaredMethod(name, params);
                    m.setAccessible(true);
                    return m.invoke(obj, args);
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            System.err.println("[-] " + TAG + " callMethodRet(" + name + ") failed: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace(System.err);
        }
        return null;
    }
}
