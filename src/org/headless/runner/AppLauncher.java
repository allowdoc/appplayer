package org.headless.runner;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.os.SystemProperties;
import org.headless.runner.core.SyntheticContext;
import org.headless.runner.manifest.ApkInfo;
import org.headless.runner.manifest.ManifestExtractor;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class AppLauncher {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        System.out.println("==================================================================");
        System.out.println("          ONE-CLICK IN-PROCESS ANDROID APP LAUNCHER               ");
        System.out.println("    Zero VM | Zero QEMU | Zero Daemons | Pure Native Execution   ");
        System.out.println("==================================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n==================== SHUTDOWN HOOK ====================");
            Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : all.entrySet()) {
                System.out.println("Thread [" + entry.getKey().getName() + "] (State: " + entry.getKey().getState() + "):");
                for (StackTraceElement ste : entry.getValue()) {
                    System.out.println("    at " + ste);
                }
            }
            System.out.println("=======================================================\n");
        }));

        if (args.length < 1) {
            System.out.println("Usage: launch.bat <path-to-apk>");
            return;
        }

        File apkFile = new File(args[0]);
        if (!apkFile.exists()) {
            System.err.println("[-] Error: APK not found: " + apkFile.getAbsolutePath());
            return;
        }
        android.content.res.AssetManager.setDefaultApkPath(apkFile.getAbsolutePath());

        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("[-] UNCAUGHT EXCEPTION on thread [" + thread.getName() + "]: " + throwable);
                throwable.printStackTrace(System.err);
            });
            System.setProperty("java.vm.version", "2.1.0"); // Declare ART multi-dex capable VM

            for (String arg : args) {
                if ("--ui".equalsIgnoreCase(arg) || "-ui".equalsIgnoreCase(arg)) {
                    System.setProperty("headless.runner.ui", "true");
                    break;
                }
            }

            // 1. Initialize Event Loop
            System.out.println("[*] [Step 1/5] Booting Android Main Event Loop (Looper)...");
            Looper.prepareMainLooper();
            System.out.println("[+] Main Looper is active: " + Looper.getMainLooper());
            org.headless.runner.core.AxmlLayoutTreeParser.initialize(apkFile);

            try {
                androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(new androidx.arch.core.executor.TaskExecutor() {
                    @Override
                    public void executeOnDiskIO(Runnable runnable) {
                        runnable.run();
                    }
                    @Override
                    public void postToMainThread(Runnable runnable) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
                    }
                    @Override
                    public boolean isMainThread() {
                        return true;
                    }
                });
                System.out.println("[+] [ArchTaskExecutor] Global MainThread delegate initialized (isMainThread=true).");
            } catch (Throwable t) {
                System.err.println("[-] Notice initializing ArchTaskExecutor delegate: " + t);
            }

            Thread looperPump = new Thread(() -> {
                org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                while (true) {
                    try {
                        org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                        Looper.getMainLooper().getQueue().drainPendingMessages();
                        Thread.sleep(50);
                    } catch (Throwable ignored) {}
                }
            }, "MainLooperPump");
            looperPump.setDaemon(true);
            looperPump.start();

            // 2. Inject In-Process Service Registry
            System.out.println("[*] [Step 2/5] Initializing In-Process Service Registry (Zero IPC)...");
            org.headless.runner.core.SyntheticServiceManager.initializeGlobalServices();
            System.out.println("[+] Registered core services into in-memory sCache.");

            // 3. Parse APK Manifest
            System.out.println("[*] [Step 3/5] Parsing APK manifest and package identity...");
            ApkInfo info = ManifestExtractor.extract(apkFile);
            System.out.println("[+] App Package       : " + info.getPackageName());
            System.out.println("[+] Entry Application : " + info.getApplicationClass());
            System.out.println("[+] Main Activity     : " + info.getMainActivity());
            System.out.println("[+] Version           : " + info.getVersionName() + " (Code: " + info.getVersionCode() + ")");

            // 4. Initialize Storage & Run Generic Annotation Recovery Pipeline
            File runnerDir = new File(System.getProperty("headless.runner.dir", System.getProperty("user.dir", ".")));
            File storageDir = new File(runnerDir, "data");
            File inputJar = new File(runnerDir, "app.jar");
            File patchedJar = new File(runnerDir, "app-patched.jar");
            org.headless.runner.tool.annotation.AnnotationPipeline.ensureAnnotationsInjected(apkFile, inputJar, patchedJar, storageDir, info);

            SyntheticContext context = new SyntheticContext(info, apkFile, storageDir);
            org.headless.runner.core.SyntheticServiceManager.setContext(context);
            System.out.println("[*] [Step 4/5] Binding SyntheticContext at " + context.getFilesDir().getAbsolutePath());

            // 5. Instantiate and Execute Application Lifecycle
            System.out.println("[*] [Step 5/6] Instantiating Application instance: " + info.getApplicationClass());
            Class<?> appClass = Class.forName(info.getApplicationClass());
            Class<?> superCls = appClass.getSuperclass();
            if (superCls != null && 
                superCls != android.app.Application.class && 
                !superCls.getName().startsWith("android.") &&
                !superCls.getName().startsWith("androidx.") &&
                android.app.Application.class.isAssignableFrom(superCls)) {
                System.out.println("[+] Generic Wrapper Detected: " + appClass.getName() + " -> Unwrapping to target Application: " + superCls.getName());
                appClass = superCls;
            }
            Object appInstance = appClass.getDeclaredConstructor().newInstance();
            System.out.println("[+] Application instance live: " + appInstance);
            if (appInstance instanceof android.app.Application) {
                context.setApplication((android.app.Application) appInstance);
            }

            // 6. Attach Base Context
            System.out.println("[*] [Step 6/6] Attaching SyntheticContext to Application...");
            Method attachBaseContextMethod = null;
            Class<?> cur = appClass;
            while (cur != null && attachBaseContextMethod == null) {
                try {
                    attachBaseContextMethod = cur.getDeclaredMethod("attachBaseContext", Context.class);
                } catch (NoSuchMethodException ignored) {
                    cur = cur.getSuperclass();
                }
            }
            if (attachBaseContextMethod != null) {
                attachBaseContextMethod.setAccessible(true);
                attachBaseContextMethod.invoke(appInstance, context);
                System.out.println("[+] Application.attachBaseContext() executed successfully!");
            }

            // Bind ActivityThread and mBoundApplication early so ContentProviders & WorkManager see processName
            android.app.ActivityThread at = null;
            try {
                Constructor<android.app.ActivityThread> atCtor = android.app.ActivityThread.class.getDeclaredConstructor();
                atCtor.setAccessible(true);
                at = atCtor.newInstance();

                Field sCurrentField = android.app.ActivityThread.class.getDeclaredField("sCurrentActivityThread");
                sCurrentField.setAccessible(true);
                sCurrentField.set(null, at);

                Field initialAppField = android.app.ActivityThread.class.getDeclaredField("mInitialApplication");
                initialAppField.setAccessible(true);
                initialAppField.set(at, appInstance);

                Class<?> bindDataCls = Class.forName("android.app.ActivityThread$AppBindData");
                Constructor<?> bindCtor = bindDataCls.getDeclaredConstructor();
                bindCtor.setAccessible(true);
                Object bindData = bindCtor.newInstance();
                Field procNameField = bindDataCls.getDeclaredField("processName");
                procNameField.setAccessible(true);
                procNameField.set(bindData, info.getPackageName());

                Field appInfoField = bindDataCls.getDeclaredField("appInfo");
                appInfoField.setAccessible(true);
                appInfoField.set(bindData, context.getApplicationInfo());

                Field boundAppField = android.app.ActivityThread.class.getDeclaredField("mBoundApplication");
                boundAppField.setAccessible(true);
                boundAppField.set(at, bindData);
                System.out.println("[+] [ActivityThread] Bound process: " + android.app.Application.getProcessName());
            } catch (Throwable t) {
                System.err.println("[-] Notice setting early mBoundApplication: " + t.getMessage());
            }

            // 6b. Install Manifest ContentProviders (Firebase, AndroidX Startup, etc.)
            System.out.println("[*] Installing " + info.getProviders().size() + " Manifest ContentProvider(s)...");
            for (org.headless.runner.manifest.ApkInfo.ProviderEntry pe : info.getProviders()) {
                try {
                    Class<?> pCls = Class.forName(pe.className);
                    if (android.content.ContentProvider.class.isAssignableFrom(pCls)) {
                        android.content.ContentProvider cp = (android.content.ContentProvider) pCls.getDeclaredConstructor().newInstance();
                        android.content.pm.ProviderInfo pInfo = new android.content.pm.ProviderInfo();
                        pInfo.name = pe.className;
                        pInfo.authority = pe.authority != null ? pe.authority : info.getPackageName() + "." + pCls.getSimpleName();
                        pInfo.packageName = info.getPackageName();
                        pInfo.grantUriPermissions = true;
                        cp.attachInfo(context, pInfo);
                        if (context.getContentResolver() instanceof org.headless.runner.core.SyntheticContentResolver) {
                            ((org.headless.runner.core.SyntheticContentResolver) context.getContentResolver()).registerProvider(pInfo.authority, cp);
                        }
                        System.out.println("[+] [ContentProvider] Active: " + pe.className + " (" + pInfo.authority + ")");
                    }
                } catch (Throwable t) {
                    System.out.println("[-] Notice initializing provider " + pe.className + ": " + t.getMessage());
                }
            }

            // 7. Invoke onCreate
            System.out.println("[*] Triggering Application.onCreate()...");
            Method onCreateMethod = appClass.getMethod("onCreate");
            onCreateMethod.invoke(appInstance);
            System.out.println("[+] Application.onCreate() executed successfully!");

            // 7b. Initialize Universal Headless Background Task & Service Pipeline
            System.out.println("[*] [HeadlessTaskPipeline] Initializing Background Services (" + info.getServices().size() + " declared in manifest)...");
            for (org.headless.runner.manifest.ApkInfo.ServiceEntry se : info.getServices()) {
                if (se.permission != null && se.permission.contains("BIND_JOB_SERVICE")) {
                    System.out.println("[+] [HeadlessTaskPipeline] Discovered JobService: " + se.className);
                }
            }

            // Provision synthetic Firebase/FCM device token so push receivers & backend sync succeed
            try {
                android.content.SharedPreferences fcmPrefs = context.getSharedPreferences("com.google.android.gms.appid", 0);
                if (!fcmPrefs.contains("token")) {
                    fcmPrefs.edit().putString("token", "synthetic_fcm_token_pixel5_" + System.currentTimeMillis()).apply();
                    System.out.println("[+] [HeadlessTaskPipeline] Provisioned synthetic FCM token for Firebase background sync.");
                }
            } catch (Throwable ignored) {}

            // 8. Launch Main Activity (Headless Lifecycle)
            String mainActivityName = info.getMainActivity();
            if (mainActivityName != null && !mainActivityName.isEmpty()) {
                System.out.println("[*] [Step 7/7] Launching Main Activity: " + mainActivityName);

                try {
                    Class<?> delegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate");
                    Method setNight = delegateClass.getMethod("setDefaultNightMode", int.class);
                    setNight.invoke(null, 1); // MODE_NIGHT_NO = 1
                    System.out.println("[+] Set AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)");
                } catch (Throwable ignored) {}

                try {
                    Class<?> rmiClass = Class.forName("androidx.appcompat.widget.ResourceManagerInternal");
                    Object instance = rmiClass.getMethod("get").invoke(null);
                    Field field = rmiClass.getDeclaredField("mHasCheckedVectorDrawableSetup");
                    field.setAccessible(true);
                    field.setBoolean(instance, true);
                    System.out.println("[+] Pre-configured ResourceManagerInternal (mHasCheckedVectorDrawableSetup = true)");
                } catch (Throwable t) {
                    System.out.println("[-] Pre-configuring ResourceManagerInternal skipped: " + t.getMessage());
                }

                Class<?> actClass = Class.forName(mainActivityName);
                android.app.Activity activity = (android.app.Activity) actClass.getDeclaredConstructor().newInstance();
                System.out.println("[+] Activity instance created: " + activity);

                // Pre-initialize AndroidX WorkManager
                try {
                    Class<?> bldCls = Class.forName("androidx.work.Configuration$Builder");
                    Object bld = bldCls.getDeclaredConstructor().newInstance();
                    Object cfg = bldCls.getMethod("build").invoke(bld);
                    Class<?> wmCls = Class.forName("androidx.work.WorkManager");
                    wmCls.getMethod("initialize", Context.class, Class.forName("androidx.work.Configuration"))
                         .invoke(null, context, cfg);
                    System.out.println("[+] [WorkManager] Pre-initialized WorkManager successfully.");
                } catch (Throwable t) {
                    System.err.println("[-] Notice pre-initializing WorkManager: " + t.getMessage());
                }

                // Pre-populate NetworkManager retrofitBuilderTemplates to eliminate baseUrl NPE
                try {
                    Class<?> rpClass = Class.forName("com.network.RetrofitProfile");
                    Object[] profiles = (Object[]) rpClass.getMethod("values").invoke(null);
                    Field tmplField = Class.forName("com.network.NetworkManager").getDeclaredField("retrofitBuilderTemplates");
                    tmplField.setAccessible(true);
                    java.util.Map map = (java.util.Map) tmplField.get(null);
                    if (map != null) {
                        Class<?> rBuilderCls = Class.forName("retrofit2.Retrofit$Builder");
                        Class<?> scFactoryCls = Class.forName("retrofit2.converter.scalars.ScalarsConverterFactory");
                        Class<?> gsFactoryCls = Class.forName("retrofit2.converter.gson.GsonConverterFactory");
                        Object scFactory = scFactoryCls.getMethod("create").invoke(null);
                        Object gsFactory = gsFactoryCls.getMethod("create").invoke(null);
                        Method addConverterFactory = rBuilderCls.getMethod("addConverterFactory", Class.forName("retrofit2.Converter$Factory"));

                        for (Object p : profiles) {
                            if (!map.containsKey(p)) {
                                Object b = rBuilderCls.getDeclaredConstructor().newInstance();
                                addConverterFactory.invoke(b, scFactory);
                                addConverterFactory.invoke(b, gsFactory);
                                map.put(p, b);
                            }
                        }
                        System.out.println("[+] [NetworkManager] Pre-populated " + map.size() + " RetrofitProfile templates.");
                    }
                } catch (Throwable ignored) {}

                org.headless.runner.core.SyntheticInstrumentation instrumentation = new org.headless.runner.core.SyntheticInstrumentation();
                try {
                    Field mInstrField = android.app.ActivityThread.class.getDeclaredField("mInstrumentation");
                    mInstrField.setAccessible(true);
                    mInstrField.set(at, instrumentation);
                } catch (Throwable ignored) {}

                org.headless.runner.core.ActivityStack.getInstance().initialize(
                    context, at, (android.app.Application) appInstance, instrumentation
                );

                // Attach Activity
                Method attachMethod = android.app.Activity.class.getDeclaredMethod(
                    "attach",
                    Context.class,
                    android.app.ActivityThread.class,
                    android.app.Instrumentation.class,
                    IBinder.class,
                    int.class,
                    android.app.Application.class,
                    android.content.Intent.class,
                    android.content.pm.ActivityInfo.class,
                    CharSequence.class,
                    android.app.Activity.class,
                    String.class,
                    Class.forName("android.app.Activity$NonConfigurationInstances"),
                    android.content.res.Configuration.class,
                    String.class,
                    Class.forName("com.android.internal.app.IVoiceInteractor"),
                    android.view.Window.class,
                    Class.forName("android.view.ViewRootImpl$ActivityConfigCallback"),
                    IBinder.class
                );
                attachMethod.setAccessible(true);

                android.content.Intent launchIntent = new android.content.Intent();
                launchIntent.setClassName(info.getPackageName(), mainActivityName);

                android.content.pm.ActivityInfo actInfo = new android.content.pm.ActivityInfo();
                actInfo.packageName = info.getPackageName();
                actInfo.name = mainActivityName;
                actInfo.applicationInfo = context.getApplicationInfo();

                android.content.res.Configuration config = new android.content.res.Configuration();
                config.setToDefaults();
                config.setLocale(java.util.Locale.ENGLISH);
                config.uiMode = android.content.res.Configuration.UI_MODE_TYPE_NORMAL | android.content.res.Configuration.UI_MODE_NIGHT_NO;

                attachMethod.invoke(
                    activity,
                    context,                                     // Context
                    at,                                          // ActivityThread
                    instrumentation,                             // Instrumentation
                    new Binder("activity_token"),                // token
                    0,                                           // ident
                    appInstance,                                 // Application
                    launchIntent,                                // Intent
                    actInfo,                                     // ActivityInfo
                    mainActivityName,                            // title
                    null,                                        // parent
                    "id",                                        // id
                    null,                                        // lastNonConfigurationInstances
                    config,                                      // config
                    "referrer",                                  // referrer
                    null,                                        // voiceInteractor
                    null,                                        // window (null -> PhoneWindow created)
                    null,                                        // activityConfigCallback
                    new Binder("assist_token")                   // assistToken
                );
                org.headless.runner.core.ActivityStack.getInstance().pushInitialActivity(activity);
                System.out.println("[+] Activity.attach() successfully attached!");

                // Trigger Activity.onCreate(null)
                Method actOnCreate = null;
                Class<?> curAct = actClass;
                while (curAct != null && actOnCreate == null) {
                    try {
                        actOnCreate = curAct.getDeclaredMethod("onCreate", android.os.Bundle.class);
                    } catch (NoSuchMethodException ignored) {
                        curAct = curAct.getSuperclass();
                    }
                }
                if (actOnCreate != null) {
                    actOnCreate.setAccessible(true);
                    System.out.println("[*] Triggering Activity.onCreate(null)...");
                    actOnCreate.invoke(activity, (android.os.Bundle) null);
                    System.out.println("[+] Activity.onCreate() executed successfully!");
                }

                // Trigger Activity.onStart()
                if (!activity.isFinishing()) {
                    try {
                        Method performStart = android.app.Activity.class.getDeclaredMethod("performStart", String.class);
                        performStart.setAccessible(true);
                        System.out.println("[*] Triggering Activity.onStart()...");
                        performStart.invoke(activity, "launch");
                        System.out.println("[+] Activity.onStart() executed successfully!");
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        System.err.println("[-] Notice during onStart: " + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace();
                    }
                }

                // Trigger Activity.onResume()
                if (!activity.isFinishing()) {
                    try {
                        Method performResume = android.app.Activity.class.getDeclaredMethod("performResume", boolean.class, String.class);
                        performResume.setAccessible(true);
                        System.out.println("[*] Triggering Activity.onResume()...");
                        performResume.invoke(activity, false, "launch");
                        System.out.println("[+] Activity.onResume() executed successfully!");
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        System.err.println("[-] Notice during onResume: " + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace();
                    }
                }

                // Drain Looper messages
                System.out.println("[*] Draining and executing pending Looper event queue...");
                int dispatched = Looper.getMainLooper().getQueue().drainPendingMessages();
                System.out.println("[+] Processed " + dispatched + " pending Looper task(s) on main event loop.");

                // Headless UI Tree Dump & Frame Capture
                try {
                    System.out.println("[*] Dumping UI hierarchy...");
                    org.headless.runner.ui.HeadlessUiTree.dump(activity);
                    File screenFile = new File(context.getFilesDir().getParentFile(), "screen.png");
                    System.out.println("[*] Capturing screen frame to: " + screenFile.getAbsolutePath());
                    org.headless.runner.ui.HeadlessScreenCapture.capture(activity, screenFile);
                    System.out.println("[+] Headless screen captured: " + screenFile.getAbsolutePath());
                } catch (Throwable t) {
                    System.err.println("[-] Error during UI dump / capture: " + t);
                    t.printStackTrace();
                }
                System.out.flush();

                // Drain Looper messages and allow initial activity lifecycle tasks to execute
                try {
                    boolean runTest = false;
                    for (String arg : args) {
                        if ("--test".equalsIgnoreCase(arg) || "-test".equalsIgnoreCase(arg)) {
                            runTest = true;
                            break;
                        }
                    }
                    if (runTest) {
                        scratch.TestNav.test(activity);
                    }

                    int postDispatched = Looper.getMainLooper().getQueue().drainPendingMessages();
                    if (postDispatched > 0) {
                        System.out.println("[+] Processed " + postDispatched + " Looper task(s).");
                    }

                    android.app.Activity topAct = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
                    if (topAct != null && topAct != activity) {
                        System.out.println("[*] New Top Activity Detected: " + topAct.getClass().getName());
                        // Allow new activity's event loop and view inflation to settle
                        for (int settle = 0; settle < 15; settle++) {
                            Thread.sleep(100);
                            Looper.getMainLooper().getQueue().drainPendingMessages();
                        }
                        System.out.println("[*] Dumping settled UI hierarchy for " + topAct.getClass().getSimpleName() + "...");
                        org.headless.runner.ui.HeadlessUiTree.dump(topAct);
                        File nextScreenFile = new File(context.getFilesDir().getParentFile(), "screen_home.png");
                        org.headless.runner.ui.HeadlessScreenCapture.capture(topAct, nextScreenFile);
                        System.out.println("[+] Captured next activity frame to: " + nextScreenFile.getAbsolutePath());
                    }
                } catch (Throwable t) {
                    System.err.println("[-] Notice during activity transition check: " + t);
                }

                // Optional interactive viewer if --ui requested
                boolean enableUi = false;
                for (String arg : args) {
                    if ("--ui".equalsIgnoreCase(arg) || "-ui".equalsIgnoreCase(arg)) {
                        enableUi = true;
                        System.setProperty("headless.runner.ui", "true");
                        break;
                    }
                }
                if (enableUi) {
                    org.headless.runner.ui.HeadlessViewerWindow.show(activity);
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

            System.out.println("\n==================================================================");
            System.out.println("[+] APP INSTANTIATED AND READY FOR LIFECYCLE!");
            System.out.println("[*] Total Boot Time             : " + totalTime + " ms");
            System.out.println("[*] Total Process RAM Footprint : " + usedMemory + " MB");
            System.out.println("[*] Status                      : RUNNING (Zero VM / Pure Native)");
            System.out.println("==================================================================");

            // 9. Continuous Headless Keep-Alive Loop & Event Loop
            long timeoutSec = -1;
            for (int i = 0; i < args.length; i++) {
                if ("--timeout".equalsIgnoreCase(args[i]) || "--exit-after".equalsIgnoreCase(args[i])) {
                    if (i + 1 < args.length) {
                        try {
                            timeoutSec = Long.parseLong(args[i + 1]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (timeoutSec > 0) {
                final long exitMs = timeoutSec * 1000L;
                Thread timeoutThread = new Thread(() -> {
                    try {
                        Thread.sleep(exitMs);
                        long curRam = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                        android.app.Activity finalTop = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
                        if (finalTop != null) {
                            System.out.println("\n[*] Final UI hierarchy before exit (" + finalTop.getClass().getSimpleName() + "):");
                            org.headless.runner.ui.HeadlessUiTree.dump(finalTop);
                            File finalScreenFile = new File(context.getFilesDir().getParentFile(), "screen_final.png");
                            org.headless.runner.ui.HeadlessScreenCapture.capture(finalTop, finalScreenFile);
                        }
                        System.out.println("\n==================================================================");
                        System.out.println("[+] [Headless Runner] Completed --timeout " + (exitMs / 1000) + "s execution window.");
                        System.out.println("[*] Final RAM Footprint: " + curRam + " MB");
                        System.out.println("==================================================================");
                        System.exit(0);
                    } catch (InterruptedException ignored) {}
                }, "HeadlessTimeoutExit");
                timeoutThread.setDaemon(true);
                timeoutThread.start();
            }

            Thread monitorThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(10000);
                        long curRam = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                        android.app.Activity curTop = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
                        String actName = curTop != null ? curTop.getClass().getSimpleName() : "None";
                        int activeSvcs = org.headless.runner.core.HeadlessTaskDispatcher.getInstance().getActiveServiceCount();
                        int tasksRun = org.headless.runner.core.HeadlessTaskDispatcher.getInstance().getExecutedTaskCount();
                        System.out.println("[Heartbeat] App Active | Top: " + actName + " | Services: " + activeSvcs + " | Tasks Executed: " + tasksRun + " | Memory: " + curRam + " MB");
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }, "HeadlessKeepAliveMonitor");
            monitorThread.setDaemon(true);
            monitorThread.start();

            System.out.println("[+] [Headless Runner] Entering Android Main Looper event loop...");
            System.out.println("[+] [Headless Runner] Continuous Keep-Alive active. Host process will stay alive indefinitely.");
            System.out.println("[*] Press Ctrl+C to terminate headless runner.\n");

            while (true) {
                try {
                    Looper.loop();
                } catch (Throwable t) {
                    System.err.println("[-] Main Looper recovered from: " + t.getMessage());
                }
            }

        } catch (Throwable e) {
            System.err.println("[-] App Launch Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
