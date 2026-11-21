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

    /** Marker format: <absolute-apk-path>|<size-in-bytes> written by build/launch scripts. */
    private static void warnIfStaleAppJar(File apkFile, File markerFile) {
        if (!markerFile.isFile()) return;
        try {
            String recorded = new String(java.nio.file.Files.readAllBytes(markerFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            String current = apkFile.getAbsolutePath() + "|" + apkFile.length();
            boolean match = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? recorded.equalsIgnoreCase(current)
                    : recorded.equals(current);
            if (!match) {
                System.out.println("[!] ================================================================");
                System.out.println("[!] WARNING: app.jar was decompiled from a DIFFERENT APK.");
                System.out.println("[!]   launched : " + current);
                System.out.println("[!]   recorded : " + recorded);
                System.out.println("[!]   Run: build.bat " + apkFile.getName() + "   (or ./build.sh) to rebuild,");
                System.out.println("[!]   otherwise the PREVIOUS app may keep launching.");
                System.out.println("[!] ================================================================");
            }
        } catch (Throwable ignored) {}
    }

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
            System.out.println("       launch.bat <path-to.jar|.class|dir> [--main fully.qualified.Main]   (plain Java app)");
            return;
        }

        // UNIVERSAL ENTRY DISPATCH: plain Java applications (jar / class /
        // directory) run right here on the host JVM with the runner's framework
        // shims on the classpath. Android APKs (AndroidManifest.xml inside)
        // continue through the Android boot path below. One launcher, any app.
        try {
            if (org.headless.runner.entry.UniversalEntryLoader.tryLaunch(args)) {
                return;   // a Java app ran (and exited) - nothing else to do
            }
        } catch (Throwable tEntry) {
            Throwable c = tEntry;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            System.err.println("[-] [UniversalEntry] failed: " + c);
            c.printStackTrace();
            System.exit(1);
        }

        File apkFile = new File(args[0]);
        if (!apkFile.exists()) {
            System.err.println("[-] Error: APK not found: " + apkFile.getAbsolutePath());
            return;
        }
        android.content.res.AssetManager.setDefaultApkPath(apkFile.getAbsolutePath());
        // System properties consumed by the universal RN bridge / SoLoader
        System.setProperty("headless.runner.apk", apkFile.getAbsolutePath());
        try {
            File codeSrc = new File(AppLauncher.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            // codeSrc is <runner>/bin when run from the compiled tree; <runner>/bin is a dir
            File runnerRoot = codeSrc.getParentFile() != null ? codeSrc.getParentFile() : codeSrc;
            if (!new File(runnerRoot, "tools").exists() && codeSrc.isFile()) {
                // running from a jar: <runner>/bin/runner.jar -> parent is <runner>/bin
                runnerRoot = codeSrc.getParentFile().getParentFile();
            }
            System.setProperty("headless.runner.dir", runnerRoot.getAbsolutePath());
            System.out.println("[*] [Runner] Root directory: " + runnerRoot.getAbsolutePath());
        } catch (Throwable t) {
            System.setProperty("headless.runner.dir", System.getProperty("user.dir", "."));
            System.out.println("[*] [Runner] Root directory (fallback cwd): "
                    + System.getProperty("headless.runner.dir"));
        }

        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("[-] UNCAUGHT EXCEPTION on thread [" + thread.getName() + "]: " + throwable);
                throwable.printStackTrace(System.err);
            });
            System.setProperty("java.vm.version", "2.1.0"); // Declare ART multi-dex capable VM

            for (String arg : args) {
                if ("--ui".equalsIgnoreCase(arg) || "-ui".equalsIgnoreCase(arg)) {
                    System.setProperty("headless.runner.ui", "true");
                    // --ui needs a real AWT window: force headful BEFORE any AWT
                    // class initializes (GraphicsEnvironment caches the headless
                    // state at class-init from this property + DISPLAY).
                    System.setProperty("java.awt.headless", "false");
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
                // Boot-time drain assistant: while the main thread is still busy
                // with the synchronous lifecycle setup, drain the main queue so
                // posted work (RN latches, Choreographer, coroutine dispatch)
                // executes with MAIN-thread identity (MessageQueue brackets every
                // dispatch). Once the host main thread enters Looper.loop(),
                // this pump RETIRES - the main loop is then the single consumer,
                // exactly like a device.
                while (!android.os.Looper.isMainLoopActive()) {
                    try {
                        org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                        Looper.getMainLooper().getQueue().drainPendingMessages();
                        Thread.sleep(50);
                    } catch (Throwable ignored) {}
                }
                System.out.println("[+] [MainLooperPump] retired - main thread owns the loop now.");
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
            warnIfStaleAppJar(apkFile, new File(storageDir, ".appjar-src.txt"));
            org.headless.runner.tool.annotation.AnnotationPipeline.ensureAnnotationsInjected(apkFile, inputJar, patchedJar, storageDir, info);

            // Universal per-APK resource tables (replaces repo-shipped .tsv caches)
            org.headless.runner.tool.rn.ResourceTableGenerator.ensureTables(
                    apkFile, storageDir, org.headless.runner.core.RunnerPaths.aapt2Path());

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

            // 6b. Universal Google Services fixer: pre-initialize Firebase with
            // synthetic options so FirebaseInitProvider / FCM never abort boot
            // when the APK was built without google-services.json. Apps that DO
            // ship real values are untouched (we skip when Firebase already has
            // apps registered). Framework-layer satisfaction only, no app data.
            try {
                Class<?> fbAppCls = Class.forName("com.google.firebase.FirebaseApp");
                Object existing = fbAppCls.getMethod("getApps", android.content.Context.class)
                        .invoke(null, context);
                boolean noneYet = !(existing instanceof java.util.List) || ((java.util.List<?>) existing).isEmpty();
                if (noneYet) {
                    Class<?> bldCls = Class.forName("com.google.firebase.FirebaseOptions$Builder");
                    Object bld = bldCls.getDeclaredConstructor().newInstance();
                    String pkg = info.getPackageName() == null ? "app" : info.getPackageName();
                    bldCls.getMethod("setApplicationId", String.class).invoke(bld,
                            "1:1234567890:android:" + Integer.toHexString(pkg.hashCode()) + "appplayer");
                    bldCls.getMethod("setApiKey", String.class).invoke(bld, "AIzaSyntheticGenericKey000000000000");
                    bldCls.getMethod("setProjectId", String.class).invoke(bld, "generic-app-prod");
                    bldCls.getMethod("setGcmSenderId", String.class).invoke(bld, "1234567890");
                    bldCls.getMethod("setDatabaseUrl", String.class).invoke(bld, "https://generic-app.firebaseio.com");
                    bldCls.getMethod("setStorageBucket", String.class).invoke(bld, "generic-app-prod.appspot.com");
                    Object opts = bldCls.getMethod("build").invoke(bld);
                    fbAppCls.getMethod("initializeApp", android.content.Context.class, Class.forName("com.google.firebase.FirebaseOptions"))
                            .invoke(null, context, opts);
                    System.out.println("[+] [GoogleServicesFixer] Firebase default app pre-initialized (synthetic options).");
                }
            } catch (ClassNotFoundException cnfe) {
                // App does not use Firebase - nothing to fix.
            } catch (Throwable t) {
                System.out.println("[-] Notice pre-initializing Firebase: " + t);
            }

            // 6c. Install Manifest ContentProviders (Firebase, AndroidX Startup, etc.)
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

            // NOTE (fresh-install guarantee): no synthetic tokens, no fake
            // session keys, no prefilled app state. The app boots exactly as
            // it would on a pristine device. Google/Firebase clients are
            // satisfied at the framework layer (SyntheticGmsBroker /
            // GooglePlayServicesUtil shims), never by writing into app data.

            // 8. Launch Main Activity (Headless Lifecycle)
            String mainActivityName = info.getMainActivity();
            for (int i = 0; i < args.length; i++) {
                if ("--activity".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    mainActivityName = args[i + 1];
                    break;
                }
            }
            if (mainActivityName != null && !mainActivityName.isEmpty()) {
                System.out.println("[*] [Step 7/7] Launching Main Activity: " + mainActivityName);

                try {
                    Class<?> delegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate");
                    Method setNight = delegateClass.getMethod("setDefaultNightMode", int.class);
                    setNight.invoke(null, 1); // MODE_NIGHT_NO = 1
                    System.out.println("[+] Set AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)");
                } catch (Throwable ignored) {}

                // PLATFORM-REAL: no runner-side forcing of AppCompat internals.
                // appcompat's ResourceManagerInternal self-test calls
                // Resources.getDrawable(R.drawable.abc_vector_test) and checks
                // `instanceof VectorDrawable` - exactly like on a device. The
                // runner's framework Resources now inflate the APK's real
                // vector XML into a genuine android.graphics.drawable
                // .VectorDrawable instance, so the check passes NATURALLY and
                // no app-visible state is fabricated.

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

                // (Universal policy: no app-specific pre-population. The
                // previous com.network.RetrofitProfile hack only worked for one
                // APK and violated the zero-hardcoding invariant.)

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

                // Apply the manifest theme BEFORE onCreate so AppCompat/PhoneWindow
                // see the real theme attributes (windowActionBar/windowNoTitle etc.)
                try {
                    int actTheme = info.getThemeForActivity(mainActivityName);
                    if (actTheme != 0) {
                        activity.setTheme(actTheme);
                        System.out.println("[+] [Theme] Activity theme applied: 0x" + Integer.toHexString(actTheme));
                        // Publish the theme bag as Resources-level defaults so
                        // window-wrapper/inflater/dialog themes (which never ran
                        // applyStyle themselves) still resolve enforcement attrs.
                        // Without this, Material's ThemeEnforcement aborts every
                        // Material Components app at first layout inflation.
                        activity.getResources().loadArsc(apkFile, actTheme);
                    }
                } catch (Throwable t) {
                    System.err.println("[-] Notice applying activity theme: " + t.getMessage());
                }

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

                // PLATFORM-REAL window attach: on a device the window is
                // attached to its ViewRootImpl during the resume flow, so by
                // the time app code (fragments, androidx controllers) runs,
                // View.getViewRootImpl() / isAttachedToWindow() answer like a
                // device. Attach BEFORE onResume - androidx operations that
                // check container attachment during onResume (Fragment
                // SpecialEffectsController) then proceed instead of cancelling.
                System.out.println("[*] Attaching Activity Window and draining initial Looper queue...");
                org.headless.runner.core.ActivityStack.attachWindowIfNeeded(activity);
                int dispatched = Looper.getMainLooper().getQueue().drainPendingMessages();
                System.out.println("[+] Processed " + dispatched + " pending Looper task(s) on main event loop.");

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

                // Re-attach (idempotent) and drain any work onResume queued up
                org.headless.runner.core.ActivityStack.attachWindowIfNeeded(activity);
                dispatched = Looper.getMainLooper().getQueue().drainPendingMessages();
                System.out.println("[+] Processed " + dispatched + " pending Looper task(s) after onResume.");

                // Populate ViewPagers on initial activity
                try {
                    System.out.println("[*] [AppLauncher] Populating ViewPager child views on initial activity...");
                    org.headless.runner.ui.ViewPagerPopulator.populateAll(activity);
                    Looper.getMainLooper().getQueue().drainPendingMessages();
                } catch (Throwable t) {
                    System.err.println("[-] Notice populating ViewPager on initial activity: " + t.getMessage());
                }

                // MAIN LOADER: universal React Native auto-mount (app-agnostic).
                // If this APK is RN/Expo, drive its own ReactNativeHost ->
                // ReactRootView -> Hermes bundle pipeline to first JS commit.
                try {
                    org.headless.runner.core.MainLoader.ensureReactMounted(activity);
                    Looper.getMainLooper().getQueue().drainPendingMessages();
                } catch (Throwable t) {
                    System.err.println("[-] Notice in MainLoader: " + t.getMessage());
                }

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
                        // Force ViewPager children to populate (no drawing pass in headless mode)
                        System.out.println("[*] [AppLauncher] Forcing ViewPager populate() on settled activity...");
                        org.headless.runner.ui.ViewPagerPopulator.populateAll(topAct);
                        // Drain once more after populate() may have committed fragment transactions
                        for (int settle = 0; settle < 5; settle++) {
                            Thread.sleep(100);
                            Looper.getMainLooper().getQueue().drainPendingMessages();
                        }
                        System.out.println("[*] Dumping settled UI hierarchy for " + topAct.getClass().getSimpleName() + "...");
                        org.headless.runner.ui.HeadlessUiTree.dump(topAct);
                        File nextScreenFile = new File(context.getFilesDir().getParentFile(), "screen_home.png");
                        org.headless.runner.ui.HeadlessScreenCapture.capture(topAct, nextScreenFile);
                        System.out.println("[+] Captured next activity frame to: " + nextScreenFile.getAbsolutePath());

                        // RN app navigated to a new activity: re-run the universal
                        // RN auto-mount there too (same global loader, zero app code).
                        try {
                            org.headless.runner.core.MainLoader.ensureReactMounted(topAct);
                            Looper.getMainLooper().getQueue().drainPendingMessages();
                        } catch (Throwable t2) {
                            System.err.println("[-] Notice in MainLoader (top activity): " + t2.getMessage());
                        }
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
                    // SKIA RENDERER (default) with Java2D fallback inside.
                    org.headless.render.SkiaPlayerWindow.show(activity);
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
            org.headless.runner.core.HeadlessChoreographer.start();
            System.out.println("[+] [Headless Runner] Continuous Keep-Alive active. Host process will stay alive indefinitely.");
            System.out.println("[*] Press Ctrl+C to terminate headless runner.\n");

            while (true) {
                try {
                    Looper.loop();
                } catch (Throwable t) {
                    System.err.println("[-] Main Looper recovered from: " + t);
                    StackTraceElement[] st = t.getStackTrace();
                    int n = Math.min(st.length, 10);
                    for (int i = 0; i < n; i++) {
                        System.err.println("      at " + st[i]);
                    }
                    if (st.length > n) System.err.println("      ... " + (st.length - n) + " more");
                }
            }

        } catch (Throwable e) {
            // Unwrap reflective InvocationTargetException chains for a real cause.
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof java.lang.VerifyError) {
                System.err.println("[-] App Launch Error: bytecode VERIFICATION failed: " + cause.getMessage());
                System.err.println("    The launch script must start the JVM with the diagnostic flags");
                System.err.println("    -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationLocal -XX:-BytecodeVerificationRemote");
                System.err.println("    (launch.bat writes them into data\\java-launch.args). If you launched java.exe");
                System.err.println("    manually, add those flags. JDK 25 ignores -noverify entirely.");
            } else {
                System.err.println("[-] App Launch Error: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
}
