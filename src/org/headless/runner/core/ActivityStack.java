package org.headless.runner.core;

import org.headless.runner.agent.DesktopWindowInsetsHelper;

import android.app.Activity;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

public class ActivityStack {
    private static ActivityStack sInstance;
    private final Deque<Activity> mStack = new ArrayDeque<>();
    private final java.util.Map<IBinder, Activity> mTokenMap = new java.util.concurrent.ConcurrentHashMap<>();
    private Context mBaseContext;
    private ActivityThread mActivityThread;
    private Application mApplication;
    private Instrumentation mInstrumentation;

    public static synchronized ActivityStack getInstance() {
        if (sInstance == null) {
            sInstance = new ActivityStack();
        }
        return sInstance;
    }

    @SuppressWarnings("unchecked")
    public static void ensureMainLooperOnThread() {
        // RETIRED. This used to rewrite Looper.sThreadLocal and the MAIN
        // looper's mThread field to the CALLING thread (pump / AWT-EDT / task
        // dispatcher) so androidx main-thread assertions would pass on host
        // threads draining the main queue. The rewrite is destructive: the
        // main looper's identity moves off the real main thread, so the same
        // assertions then FAIL on the actual main thread
        // ("Method addObserver must be called on the main thread" inside every
        // R8-obfuscated app whose androidx ArchTaskExecutor delegate never
        // installs - e.g. me.tsukanov.counter).
        //
        // The Looper shadow (src/android/os/Looper.java) plus the
        // MessageQueue dispatch bracket (enterMainDispatch/exitMainDispatch)
        // now give every main-queue dispatch TRUE main-thread identity,
        // exactly like a device - race-free, no reflection into final fields.
        // Kept as a no-op so existing call sites stay source-compatible.
    }

    public void initialize(Context baseContext, ActivityThread at, Application app, Instrumentation instr) {
        this.mBaseContext = baseContext;
        this.mActivityThread = at;
        this.mApplication = app;
        this.mInstrumentation = instr;
    }

    public void pushInitialActivity(Activity activity) {
        mStack.push(activity);
        try {
            java.lang.reflect.Field tokenField = Activity.class.getDeclaredField("mToken");
            tokenField.setAccessible(true);
            IBinder token = (IBinder) tokenField.get(activity);
            if (token != null) {
                mTokenMap.put(token, activity);
            }
        } catch (Throwable ignored) {}
    }

    private Activity mLastFinishedActivity;

    public Activity getActivityByToken(IBinder token) {
        if (token == null) return null;
        return mTokenMap.get(token);
    }

    public Activity getTopActivity() {
        Activity top = mStack.peek();
        return (top != null) ? top : mLastFinishedActivity;
    }

    public Deque<Activity> getAllActivities() {
        return mStack;
    }

    public Activity launchActivity(Intent intent) {
        if (intent == null) return null;
        ComponentName comp = intent.getComponent();
        String className = comp != null ? comp.getClassName() : intent.getAction();
        if (className == null) return null;
        if (className.contains("action.") || className.startsWith("android.content.pm.action") || className.startsWith("android.settings.")) {
            System.out.println("[*] [ActivityStack] System action ignored (non-Activity): " + className);
            return null;
        }

        System.out.println("\n==================================================================");
        System.out.println("[*] [ActivityStack] Navigating to Activity: " + className);
        System.out.println("==================================================================");

        ensureMainLooperOnThread();

        try {
            Class<?> actClass = Class.forName(className);
            if (!Activity.class.isAssignableFrom(actClass)) {
                System.err.println("[-] Target class is not an Activity: " + className);
                return null;
            }

            // 1. Pause top activity if any
            Activity current = mStack.peek();
            if (current != null) {
                try {
                    System.out.println("[*] [ActivityStack] Pausing current Activity: " + current.getClass().getName());
                    Method performPause = Activity.class.getDeclaredMethod("performPause");
                    performPause.setAccessible(true);
                    performPause.invoke(current);
                } catch (Throwable t) {
                    System.err.println("[-] Notice during performPause: " + t.getMessage());
                }
            }

            // 2. Instantiate new activity
            Activity newAct = (Activity) actClass.getDeclaredConstructor().newInstance();

            // 3. Attach
            attachActivity(newAct, intent, className);

            // 4. Push to stack immediately so newAct is current top during lifecycle
            mStack.push(newAct);
            System.out.println("[+] [ActivityStack] Activity live on top of stack. (Stack depth: " + mStack.size() + ")\n");

            // 5. Lifecycle: onCreate -> onStart -> onResume
            Method actOnCreate = null;
            Class<?> cur = actClass;
            while (cur != null && actOnCreate == null) {
                try {
                    actOnCreate = cur.getDeclaredMethod("onCreate", Bundle.class);
                } catch (NoSuchMethodException ignored) {
                    cur = cur.getSuperclass();
                }
            }
            if (actOnCreate != null) {
                actOnCreate.setAccessible(true);
                System.out.println("[*] [ActivityStack] Triggering " + className + ".onCreate()...");
                try {
                    actOnCreate.invoke(newAct, (Bundle) null);
                    System.out.println("[+] [ActivityStack] " + className + ".onCreate() executed successfully!");
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    System.err.println("[-] Notice in " + className + ".onCreate(): " + cause);
                    cause.printStackTrace();
                }
            }

            if (!newAct.isFinishing()) {
                try {
                    Method performStart = Activity.class.getDeclaredMethod("performStart", String.class);
                    performStart.setAccessible(true);
                    System.out.println("[*] [ActivityStack] Triggering " + className + ".onStart()...");
                    performStart.invoke(newAct, "launch");
                    System.out.println("[+] [ActivityStack] " + className + ".onStart() executed successfully!");
                } catch (Throwable t) {
                    System.err.println("[-] Notice during performStart: " + t.getMessage());
                }
            }

            if (!newAct.isFinishing()) {
                try {
                    Method performResume = Activity.class.getDeclaredMethod("performResume", boolean.class, String.class);
                    performResume.setAccessible(true);
                    System.out.println("[*] [ActivityStack] Triggering " + className + ".onResume()...");
                    performResume.invoke(newAct, false, "launch");
                    System.out.println("[+] [ActivityStack] " + className + ".onResume() executed successfully!");
                    notifyStackChanged(newAct);
                } catch (Throwable t) {
                    System.err.println("[-] Notice during performResume: " + t.getMessage());
                }
            }

            return newAct;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("[-] [ActivityStack] Failed to launch Activity " + className + ": " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace();
            return null;
        }
    }

    public void finishActivity(Activity activity) {
        if (activity == null) return;
        System.out.println("[*] [ActivityStack] Finishing Activity: " + activity.getClass().getName());
        try {
            Method performPause = Activity.class.getDeclaredMethod("performPause");
            performPause.setAccessible(true);
            performPause.invoke(activity);

            Method performStop = Activity.class.getDeclaredMethod("performStop", boolean.class, String.class);
            performStop.setAccessible(true);
            performStop.invoke(activity, true, "finish");

            Method performDestroy = Activity.class.getDeclaredMethod("performDestroy");
            performDestroy.setAccessible(true);
            performDestroy.invoke(activity);
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            System.err.println("[-] Notice during finishActivity: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace();
        }

        boolean wasTop = (mStack.peek() == activity);
        if (wasTop) {
            mLastFinishedActivity = activity;
        }
        mStack.remove(activity);
        mTokenMap.values().removeIf(a -> a == activity);

        if (wasTop) {
            // Resume previous activity
            Activity top = mStack.peek();
            if (top != null) {
                System.out.println("[*] [ActivityStack] Resuming previous Activity: " + top.getClass().getName());
                try {
                    Method performResume = Activity.class.getDeclaredMethod("performResume", boolean.class, String.class);
                    performResume.setAccessible(true);
                    performResume.invoke(top, false, "resumeAfterFinish");
                    notifyStackChanged(top);
                } catch (Throwable t) {
                    System.err.println("[-] Notice resuming previous activity: " + t.getMessage());
                }
            }
        }
    }

    public static void attachWindowIfNeeded(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        android.view.View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        try {
            java.lang.reflect.Field attachInfoField = android.view.View.class.getDeclaredField("mAttachInfo");
            attachInfoField.setAccessible(true);
            if (attachInfoField.get(decor) != null) {
                return; // Already attached
            }
            System.out.println("[*] [ActivityStack] attaching window for " + activity.getClass().getSimpleName());

            Class<?> attachInfoCls = Class.forName("android.view.View$AttachInfo");
            java.lang.reflect.Constructor<?>[] ctors = attachInfoCls.getDeclaredConstructors();
            Object attachInfo = null;
            // PLATFORM-REAL: build the window's view ancestor FIRST (a real
            // device constructs ViewRootImpl before AttachInfo; AttachInfo's
            // ctor wires it in). With a genuine ancestor attached,
            // View.getViewRootImpl() and AttachInfo.mViewRootImpl behave like
            // on a device - DrawerLayout insets, View.invalidate channel and
            // WindowInsetsCompat all resolve non-null.
            android.view.ViewRootImpl viewRoot = null;
            try {
                viewRoot = new android.view.ViewRootImpl(activity,
                        activity.getWindowManager() != null
                                ? activity.getWindowManager().getDefaultDisplay() : null);
                viewRoot.setView(decor, (android.view.WindowManager.LayoutParams)
                        activity.getWindow().getAttributes(), null);
            } catch (Throwable tVr) {
                System.out.println("[*] [ActivityStack] ViewRootImpl creation notice: " + tVr);
            }
            for (java.lang.reflect.Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] pTypes = c.getParameterTypes();
                Object[] args = new Object[pTypes.length];
                for (int i = 0; i < pTypes.length; i++) {
                    Class<?> pt = pTypes[i];
                    if (pt == android.os.Handler.class) {
                        args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                    } else if (Context.class.isAssignableFrom(pt)) {
                        args[i] = activity;
                    } else if (pt == android.view.Display.class) {
                        args[i] = activity.getWindowManager() != null
                                ? activity.getWindowManager().getDefaultDisplay() : null;
                    } else if (pt == android.view.IWindowSession.class) {
                        args[i] = createWindowSessionProxy();
                    } else if (pt == android.view.IWindow.class) {
                        // The constructor calls window.asBinder() for the
                        // window token - null breaks the whole attach.
                        args[i] = createWindowProxy();
                    } else if (pt == android.view.ViewRootImpl.class) {
                        args[i] = viewRoot;
                    } else {
                        args[i] = null;
                    }
                }
                try {
                    attachInfo = c.newInstance(args);
                    break;
                } catch (Throwable tCtor) {
                    Throwable cause = (tCtor instanceof java.lang.reflect.InvocationTargetException
                            && ((java.lang.reflect.InvocationTargetException) tCtor).getCause() != null)
                            ? ((java.lang.reflect.InvocationTargetException) tCtor).getCause() : tCtor;
                    java.io.StringWriter sw = new java.io.StringWriter();
                    cause.printStackTrace(new java.io.PrintWriter(sw));
                    System.out.println("[*] [ActivityStack] AttachInfo ctor failed:\n"
                            + sw.toString().substring(0, Math.min(1200, sw.getBuffer().length())));
                }
            }

            if (attachInfo != null) {
                try {
                    // IMPORTANT: the decor (still unattached here) hands out its
                    // FLOATING ViewTreeObserver. dispatchAttachedToWindow then
                    // merges that floating observer into attachInfo.mTreeObserver
                    // and merge() KILLS its argument - seeding the field with the
                    // floating instance would make the shared observer kill
                    // itself, and every later add*Listener call would fail with
                    // "This ViewTreeObserver is not alive". Seed a fresh one.
                    java.lang.reflect.Field toField = attachInfoCls.getDeclaredField("mTreeObserver");
                    toField.setAccessible(true);
                    java.lang.reflect.Constructor<?> vtoCtor =
                            android.view.ViewTreeObserver.class.getDeclaredConstructor(android.content.Context.class);
                    vtoCtor.setAccessible(true);
                    Object freshObserver = vtoCtor.newInstance(activity);
                    toField.set(attachInfo, freshObserver);
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field rvField = attachInfoCls.getDeclaredField("mRootView");
                    rvField.setAccessible(true);
                    rvField.set(attachInfo, decor);
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field wtField = attachInfoCls.getDeclaredField("mWindowToken");
                    wtField.setAccessible(true);
                    wtField.set(attachInfo, new Binder("window_token_" + activity.getClass().getSimpleName()));
                } catch (Throwable ignored) {}

                // Desktop window environment on the synthetic AttachInfo:
                //  - mSession proxy: View.getWindowVisibleDisplayFrame() calls
                //    IWindowSession.getDisplayFrame(...) through it.
                //  - mVisibleInsets: the visible system-window insets rect.
                //  - View.getRootWindowInsets() is rewritten by the engine
                //    agent to the desktop insets provider (no ViewRootImpl).
                try {
                    java.lang.reflect.Field sessionField = attachInfoCls.getDeclaredField("mSession");
                    sessionField.setAccessible(true);
                    Class<?> sessionIf = sessionField.getType();
                    Object sessionProxy = java.lang.reflect.Proxy.newProxyInstance(
                            sessionIf.getClassLoader(), new Class<?>[]{sessionIf},
                            (proxy, method, margs) -> {
                                String mn = method.getName();
                                if ("getDisplayFrame".equals(mn)) {
                                    DesktopWindowInsetsHelper.applyDisplayFrame((android.graphics.Rect) margs[1]);
                                    return null;
                                }
                                if ("getVisibleInsetsFrame".equals(mn) || "getInsets".equals(mn)) {
                                    DesktopWindowInsetsHelper.applyVisibleInsets((android.graphics.Rect) margs[1]);
                                    return null;
                                }
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return false;
                                if (rt == int.class) return 0;
                                if (rt == long.class) return 0L;
                                if (rt == float.class) return 0f;
                                if (rt == double.class) return 0d;
                                return null;
                            });
                    sessionField.set(attachInfo, sessionProxy);
                } catch (Throwable ignoredSession) {}
                try {
                    java.lang.reflect.Field viField = attachInfoCls.getDeclaredField("mVisibleInsets");
                    viField.setAccessible(true);
                    Object vi = viField.getType().getDeclaredConstructor().newInstance();
                    DesktopWindowInsetsHelper.applyVisibleInsets((android.graphics.Rect) vi);
                    viField.set(attachInfo, vi);
                } catch (Throwable ignoredVi) {}

                // Guarantee the ancestor reference even when the ctor shape
                // does not carry ViewRootImpl (framework-version dependent).
                if (viewRoot != null) {
                    try {
                        java.lang.reflect.Field vrField = attachInfoCls.getDeclaredField("mViewRootImpl");
                        vrField.setAccessible(true);
                        if (vrField.get(attachInfo) == null) {
                            vrField.set(attachInfo, viewRoot);
                        }
                    } catch (Throwable ignoredVr) {}
                    // Device contract (bidirectional): AttachInfo.mViewRootImpl
                    // AND ViewRootImpl.mAttachInfo both point at each other.
                    // androidx WindowInsetsCompat reads visible insets through
                    // ViewRootImpl.mAttachInfo.mVisibleInsets by reflection.
                    try {
                        java.lang.reflect.Field backField = viewRoot.getClass()
                                .getDeclaredField("mAttachInfo");
                        backField.setAccessible(true);
                        backField.set(viewRoot, attachInfo);
                    } catch (Throwable ignoredBack) {}
                }

                java.lang.reflect.Method dispatchAttached = android.view.View.class.getDeclaredMethod("dispatchAttachedToWindow", attachInfoCls, int.class);
                dispatchAttached.setAccessible(true);
                dispatchAttached.invoke(decor, attachInfo, 0);
                System.out.println("[+] [ActivityStack] dispatchAttachedToWindow successfully attached View hierarchy to window!");
            }
        } catch (Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            System.err.println("[-] Notice attaching window:\n"
                    + sw.toString().substring(0, Math.min(1500, sw.getBuffer().length())));
        }
    }

    /** IWindowSession proxy: display frame / visible insets queries. */
    private static Object createWindowSessionProxy() {
        Class<?> ifc = android.view.IWindowSession.class;
        return java.lang.reflect.Proxy.newProxyInstance(ifc.getClassLoader(), new Class<?>[]{ifc},
                (proxy, method, margs) -> {
                    String mn = method.getName();
                    for (Object a : margs) {
                        if (a instanceof android.graphics.Rect) {
                            if (mn.contains("DisplayFrame")) {
                                org.headless.runner.agent.DesktopWindowInsetsHelper.applyDisplayFrame((android.graphics.Rect) a);
                            } else {
                                org.headless.runner.agent.DesktopWindowInsetsHelper.applyVisibleInsets((android.graphics.Rect) a);
                            }
                            return null;
                        }
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    if (rt == long.class) return 0L;
                    if (rt == float.class) return 0f;
                    if (rt == double.class) return 0d;
                    return null;
                });
    }

    /** IWindow proxy: asBinder provides the window token; queries inert. */
    private static Object createWindowProxy() {
        Class<?> ifc = android.view.IWindow.class;
        android.os.IBinder token = new Binder("desktop_window_token");
        return java.lang.reflect.Proxy.newProxyInstance(ifc.getClassLoader(), new Class<?>[]{ifc},
                (proxy, method, margs) -> {
                    if ("asBinder".equals(method.getName())) return token;
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    if (rt == long.class) return 0L;
                    if (rt == float.class) return 0f;
                    if (rt == double.class) return 0d;
                    return null;
                });
    }

    private void notifyStackChanged(Activity top) {
        if (top == null || top.isFinishing()) return;
        try {
            attachWindowIfNeeded(top);

            // 1. Drain pending looper messages so any posted UI updates / handlers run
            try {
                if (android.os.Looper.getMainLooper() != null && android.os.Looper.getMainLooper().getQueue() != null) {
                    android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
                }
            } catch (Throwable ignored) {}

            // 2. Force ViewPager populate() — no drawing pass in headless mode means
            //    onMeasure/onLayout never fires, so pager children stay at zero.
            //    ViewPagerPopulator is generic: walks the full view tree and calls
            //    populate() via reflection on every ViewPager subclass it finds.
            //    populate()/instantiateItem() queues FragmentManager transactions
            //    as a side effect, so this MUST run before the transactions below
            //    are flushed, or the queued work from populate() never runs.
            try {
                org.headless.runner.ui.ViewPagerPopulator.populateAll(top);
            } catch (Throwable vpe) {
                System.err.println("[-] [ViewPagerPopulator] Error: " + vpe.getMessage());
            }

            // 3. Execute pending Fragment transactions if FragmentActivity — run
            //    AFTER populate() above so transactions it just queued are
            //    actually flushed before the measure/layout/draw pass below.
            if (top instanceof androidx.fragment.app.FragmentActivity) {
                try {
                    ((androidx.fragment.app.FragmentActivity) top).getSupportFragmentManager().executePendingTransactions();
                } catch (Throwable ignored) {}
            }

            if (mBaseContext != null) {
                java.io.File storageDir = mBaseContext.getFilesDir().getParentFile();
                java.io.File screenFile = new java.io.File(storageDir, "screen.png");
                org.headless.runner.ui.HeadlessScreenCapture.capture(top, screenFile);
            }
        } catch (Throwable ignored) {}
    }

    private void attachActivity(Activity activity, Intent intent, String className) throws Exception {
        Method attachMethod = Activity.class.getDeclaredMethod(
            "attach",
            Context.class,
            ActivityThread.class,
            Instrumentation.class,
            IBinder.class,
            int.class,
            Application.class,
            Intent.class,
            ActivityInfo.class,
            CharSequence.class,
            Activity.class,
            String.class,
            Class.forName("android.app.Activity$NonConfigurationInstances"),
            Configuration.class,
            String.class,
            Class.forName("com.android.internal.app.IVoiceInteractor"),
            android.view.Window.class,
            Class.forName("android.view.ViewRootImpl$ActivityConfigCallback"),
            IBinder.class
        );
        attachMethod.setAccessible(true);

        ActivityInfo actInfo = new ActivityInfo();
        actInfo.packageName = mBaseContext.getPackageName();
        actInfo.name = className;
        actInfo.applicationInfo = mBaseContext.getApplicationInfo();

        Configuration config = new Configuration();
        config.setToDefaults();
        config.setLocale(java.util.Locale.ENGLISH);
        config.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;

        Binder token = new Binder("activity_token_" + className);
        mTokenMap.put(token, activity);

        attachMethod.invoke(
            activity,
            mBaseContext,
            mActivityThread,
            mInstrumentation,
            token,
            mStack.size(),
            mApplication,
            intent,
            actInfo,
            className,
            null,
            "id_" + className,
            null,
            config,
            "referrer",
            null,
            null,
            null,
            new Binder("assist_token_" + className)
        );
    }
}
