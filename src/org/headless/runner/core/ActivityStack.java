package org.headless.runner.core;

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
        try {
            if (android.os.Looper.getMainLooper() != null) {
                java.lang.reflect.Field tlField = android.os.Looper.class.getDeclaredField("sThreadLocal");
                tlField.setAccessible(true);
                ThreadLocal<android.os.Looper> tl = (ThreadLocal<android.os.Looper>) tlField.get(null);
                if (tl != null) {
                    tl.set(android.os.Looper.getMainLooper());
                }
                try {
                    java.lang.reflect.Field mThreadField = android.os.Looper.class.getDeclaredField("mThread");
                    mThreadField.setAccessible(true);
                    mThreadField.set(android.os.Looper.getMainLooper(), Thread.currentThread());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
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

    public Activity getActivityByToken(IBinder token) {
        if (token == null) return null;
        return mTokenMap.get(token);
    }

    public Activity getTopActivity() {
        return mStack.peek();
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

            Class<?> attachInfoCls = Class.forName("android.view.View$AttachInfo");
            java.lang.reflect.Constructor<?>[] ctors = attachInfoCls.getDeclaredConstructors();
            Object attachInfo = null;
            for (java.lang.reflect.Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] pTypes = c.getParameterTypes();
                Object[] args = new Object[pTypes.length];
                for (int i = 0; i < pTypes.length; i++) {
                    if (pTypes[i] == android.os.Handler.class) {
                        args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                    } else if (pTypes[i] == Context.class) {
                        args[i] = activity;
                    } else if (pTypes[i] == android.view.Display.class) {
                        args[i] = activity.getWindowManager() != null ? activity.getWindowManager().getDefaultDisplay() : null;
                    } else {
                        args[i] = null;
                    }
                }
                try {
                    attachInfo = c.newInstance(args);
                    break;
                } catch (Throwable ignored) {}
            }

            if (attachInfo != null) {
                try {
                    java.lang.reflect.Field toField = attachInfoCls.getDeclaredField("mTreeObserver");
                    toField.setAccessible(true);
                    toField.set(attachInfo, decor.getViewTreeObserver());
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field rvField = attachInfoCls.getDeclaredField("mRootView");
                    rvField.setAccessible(true);
                    rvField.set(attachInfo, decor);
                } catch (Throwable ignored) {}

                java.lang.reflect.Method dispatchAttached = android.view.View.class.getDeclaredMethod("dispatchAttachedToWindow", attachInfoCls, int.class);
                dispatchAttached.setAccessible(true);
                dispatchAttached.invoke(decor, attachInfo, 0);
                System.out.println("[+] [ActivityStack] dispatchAttachedToWindow successfully attached View hierarchy to window!");
            }
        } catch (Throwable t) {
            System.err.println("[-] Notice attaching window: " + t.getMessage());
        }
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

            // 2. Execute pending Fragment transactions if FragmentActivity
            if (top instanceof androidx.fragment.app.FragmentActivity) {
                try {
                    ((androidx.fragment.app.FragmentActivity) top).getSupportFragmentManager().executePendingTransactions();
                } catch (Throwable ignored) {}
            }

            // 3. Force ViewPager populate() — no drawing pass in headless mode means
            //    onMeasure/onLayout never fires, so pager children stay at zero.
            //    ViewPagerPopulator is generic: walks the full view tree and calls
            //    populate() via reflection on every ViewPager subclass it finds.
            try {
                org.headless.runner.ui.ViewPagerPopulator.populateAll(top);
            } catch (Throwable vpe) {
                System.err.println("[-] [ViewPagerPopulator] Error: " + vpe.getMessage());
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
