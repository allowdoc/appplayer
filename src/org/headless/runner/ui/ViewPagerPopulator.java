package org.headless.runner.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Generic engine helper that forces ViewPager (and ViewPager2) to populate
 * their children in headless mode.
 *
 * In a real Android device, ViewPager.populate() is called from onMeasure()/
 * onLayout() which only runs during a real drawing pass. In our headless
 * in-process runner there is no drawing pass, so we must trigger it manually
 * after the activity's layout has settled.
 *
 * This is 100% generic — it works for any app's ViewPager/CustomViewPager
 * subclass without hardcoding any app-specific class names.
 */
public class ViewPagerPopulator {

    private static final String TAG = "[ViewPagerPopulator]";

    /**
     * Walk the entire view tree of an activity, find every ViewPager subclass,
     * and forcibly trigger populate() so fragments/views get instantiated.
     *
     * Call this after all Looper messages have drained (i.e., after the
     * settle-poll loop in AppLauncher / ActivityStack.notifyStackChanged).
     */
    public static void populateAll(Activity activity) {
        if (activity == null) return;
        android.view.Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;

        walkAndPopulate(activity, decor, 0);

        // After populate() calls may have scheduled fragment transactions,
        // drain the looper again and then flush pending fragment transactions.
        drainAndFlush(activity);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void walkAndPopulate(Activity activity, View view, int depth) {
        if (view == null) return;

        if (isViewPager(view)) {
            populateViewPager(activity, view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                walkAndPopulate(activity, vg.getChildAt(i), depth + 1);
            }
        }
    }

    /** True for any androidx.viewpager.widget.ViewPager (and subclasses). */
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
            // 1. Check if adapter is set and non-null
            Object adapter = getFieldValue(pager, "mAdapter");
            if (adapter == null) {
                System.out.println("[!] " + TAG + " " + pagerClass + " has no adapter set — skipping.");
                return;
            }

            // 2. Check adapter count
            int count = 0;
            try {
                Method getCount = adapter.getClass().getMethod("getCount");
                count = (int) getCount.invoke(adapter);
            } catch (Throwable t) {
                System.err.println("[-] " + TAG + " Could not call getCount(): " + t.getMessage());
            }
            System.out.println("[*] " + TAG + " Found " + pagerClass + " with adapter=" +
                    adapter.getClass().getSimpleName() + " count=" + count);

            if (count == 0) {
                System.out.println("[!] " + TAG + " Adapter reports 0 pages — nothing to populate.");
                return;
            }

            // 3. Simulate that a layout pass already happened.
            //    ViewPager.populate() has this guard at the top:
            //      if (mFirstLayout) { mCurItem = item; requestLayout(); return; }
            //    In headless mode there's no drawing pass so mFirstLayout stays true
            //    and populate() bails immediately without instantiating any items.
            //    We set mFirstLayout=false and give the ViewPager fake dimensions so
            //    populate() proceeds to call instantiateItem().
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
                    t.printStackTrace(System.err);
                    break;
                }
            }

            if (!populated) {
                System.out.println("[*] " + TAG + " populate() not found — using manual instantiateItem fallback...");
                manualInstantiatePages(activity, pager, adapter, count);
            }

        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " Unexpected error processing " + pagerClass + ": " +
                    t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    /**
     * Simulate that a layout pass has already occurred on this ViewPager.
     * Sets mFirstLayout=false and injects a fake non-zero width so that
     * ViewPager.populate() proceeds past its early-exit guard.
     */
    private static void setFirstLayoutDone(View pager) {
        // Clear mFirstLayout (the main guard in populate())
        try {
            Field f = findField(pager.getClass(), "mFirstLayout");
            f.set(pager, false);
            System.out.println("[+] " + TAG + " mFirstLayout=false (simulate layout pass).");
        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " Could not set mFirstLayout: " + t.getMessage());
        }

        // Give the pager a fake width if mWidth/mLeft/mRight are still 0
        int w = pager.getWidth();
        if (w == 0) {
            try {
                // android.view.View.mRight and mLeft determine getWidth()
                Field mRight = findField(View.class, "mRight");
                mRight.set(pager, 1082);
                Field mBottom = findField(View.class, "mBottom");
                mBottom.set(pager, 1920);
                System.out.println("[+] " + TAG + " Injected fake dimensions: 1082x1920.");
            } catch (Throwable t) {
                System.err.println("[-] " + TAG + " Could not inject fake dimensions: " + t.getMessage());
            }
        }

        // Also set mScrollX=0 (should already be 0, but be explicit)
        try {
            Field scrollX = findField(View.class, "mScrollX");
            scrollX.set(pager, 0);
        } catch (Throwable ignored) {}
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

    /**
     * Manual fallback: call adapter.startUpdate, instantiateItem for the first
     * offscreen pages, then finishUpdate — mimics what ViewPager.populate() does
     * for the case where populate() cannot be found via reflection.
     */
    private static void manualInstantiatePages(Activity activity, View pager, Object adapter, int count) {
        try {
            // Determine offscreen page limit (default = 1 on each side => pages 0..2)
            int offscreen = 1;
            try {
                Object limit = getFieldValue(pager, "mOffscreenPageLimit");
                if (limit instanceof Integer) offscreen = (int) limit;
            } catch (Throwable ignored) {}

            int currentItem = 0;
            try {
                Object ci = getFieldValue(pager, "mCurItem");
                if (ci instanceof Integer) currentItem = (int) ci;
            } catch (Throwable ignored) {}

            int start = Math.max(0, currentItem - offscreen);
            int end   = Math.min(count - 1, currentItem + offscreen);

            // startUpdate(container)
            callMethod(adapter, "startUpdate", new Class[]{ViewGroup.class}, new Object[]{pager});

            for (int pos = start; pos <= end; pos++) {
                System.out.println("[*] " + TAG + " instantiateItem(container, " + pos + ")...");
                Object item = callMethodRet(adapter, "instantiateItem",
                        new Class[]{ViewGroup.class, int.class}, new Object[]{pager, pos});
                System.out.println("[+] " + TAG + " page " + pos + " instantiated: " + item);
            }

            // finishUpdate(container)
            callMethod(adapter, "finishUpdate", new Class[]{ViewGroup.class}, new Object[]{pager});

        } catch (Throwable t) {
            System.err.println("[-] " + TAG + " manualInstantiatePages failed: " + t.getMessage());
        }
    }

    private static void drainAndFlush(Activity activity) {
        // Drain looper
        try {
            if (android.os.Looper.getMainLooper() != null) {
                android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
            }
        } catch (Throwable ignored) {}

        // Execute pending fragment transactions (FragmentPagerAdapter schedules commits)
        if (activity instanceof androidx.fragment.app.FragmentActivity) {
            try {
                ((androidx.fragment.app.FragmentActivity) activity)
                        .getSupportFragmentManager().executePendingTransactions();
                System.out.println("[+] " + TAG + " executePendingTransactions() completed.");
            } catch (Throwable t) {
                System.err.println("[-] " + TAG + " executePendingTransactions failed: " + t.getMessage());
            }
        }

        // Drain looper again after FM commits
        try {
            if (android.os.Looper.getMainLooper() != null) {
                android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
            }
        } catch (Throwable ignored) {}
    }

    // -------------------------------------------------------------------------
    // Reflection utilities
    // -------------------------------------------------------------------------

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
            System.err.println("[-] " + TAG + " callMethod(" + name + ") failed: " + t.getMessage());
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
            System.err.println("[-] " + TAG + " callMethodRet(" + name + ") failed: " + t.getMessage());
        }
        return null;
    }
}
