package org.headless.runner.rn;

import com.facebook.react.bridge.NativeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * UNIVERSAL React Native runtime bridge ("Android Wine").
 *
 * Executes the app's REAL index.android.bundle (Hermes bytecode) inside a
 * patched Hermes VM child process and shuttles bridge calls over stdio:
 *
 *   JS -> Native : "@@Q [[moduleIds],[methodIds],[params],callId]" batches
 *   Native -> JS : inbox NDJSON {type:'call'|'callback'|'eval'|'die'}
 *   Sync calls   : "@@SYNC <req>" then one reply line
 *
 * Completely app-agnostic: module configs are reflected out of the app's own
 * NativeModuleRegistry, the bundle is located inside the APK itself.
 */
public final class UniversalReactBridge {

    private static final UniversalReactBridge sInstance = new UniversalReactBridge();

    public static UniversalReactBridge get() {
        return sInstance;
    }

    private UniversalReactBridge() {}

    // ------- state -------
    private volatile boolean mRunning = false;
    private Process mProcess;
    private BufferedWriter mInbox;
    private BufferedReader mOutbox;
    private Thread mReaderThread;
    private final Object mInboxLock = new Object();

    private List<RnModuleConfigGenerator.ModuleSpec> mSpecs = new ArrayList<>();
    /** RN's ShadowNodeRegistry asserts a single accessing thread, and the
     *  app's OWN ReactRootView flow already arms it on the MAIN looper thread
     *  (its addRootView runs there). ALL runner-issued UIManager traffic
     *  (async dispatch, sync hooks, addRootView) therefore goes through the
     *  main looper - FIFO, one thread, exactly like a real device. */
    private final android.os.Handler mUiThreadHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Map<String, Map<String, Object>> mExpoMetadata = null;
    private static final int BATCH_ID_BASE = 1_000_000;
    private static final java.util.concurrent.atomic.AtomicInteger HOOK_BATCH_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();
    private Map<Integer, RnModuleConfigGenerator.ModuleSpec> mSpecsById = new ConcurrentHashMap<>();
    private Map<String, RnModuleConfigGenerator.ModuleSpec> mSpecsByName = new ConcurrentHashMap<>();

    private volatile boolean mBootlessStarted = false;

    private final ScheduledExecutorService mTimerExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RnBridgeTimers");
                t.setDaemon(true);
                return t;
            });
    private final java.util.Map<Integer, ScheduledFuture<?>> mTimers = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger mNextTimerId =
            new java.util.concurrent.atomic.AtomicInteger(1000);

    // ------- public API -------

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Locate the React Native JS bundle. Order:
     *  1. assets/index.android.bundle inside the APK
     *  2. any *.bundle / *.hbc asset in the APK containing the Hermes magic
     *  3. runnerDir/assets/index.android.bundle (previous extraction)
     */
    public static File locateBundle(File apkFile, File runnerDir) {
        try {
            if (apkFile != null && apkFile.exists()) {
                try (ZipFile zip = new ZipFile(apkFile)) {
                    ZipEntry e = zip.getEntry("assets/index.android.bundle");
                    if (e != null) {
                        return extractEntry(zip, e, runnerDir, "index.android.bundle");
                    }
                    // Universal fallback: scan all asset entries for Hermes bytecode
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry ze = entries.nextElement();
                        String name = ze.getName();
                        if (!name.startsWith("assets/") || ze.isDirectory()) continue;
                        if (name.endsWith(".bundle") || name.endsWith(".hbc")) {
                            if (looksLikeHermes(zip.getInputStream(ze))) {
                                return extractEntry(zip, ze, runnerDir,
                                        name.substring(name.lastIndexOf('/') + 1));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] Bundle locate failed: " + t.getMessage());
        }
        // Previously-extracted bundle next to the runner
        File local = new File(runnerDir, "assets/index.android.bundle");
        if (local.exists()) return local;
        return null;
    }

    private static boolean looksLikeHermes(InputStream in) {
        try {
            byte[] magic = new byte[8];
            int n = in.read(magic, 0, 8);
            // Skip the first entry that may have been read fully later; magic check:
            if (n == 8) {
                long m = ((magic[7] & 0xFFL) << 56) | ((magic[6] & 0xFFL) << 48)
                        | ((magic[5] & 0xFFL) << 40) | ((magic[4] & 0xFFL) << 32)
                        | ((magic[3] & 0xFFL) << 24) | ((magic[2] & 0xFFL) << 16)
                        | ((magic[1] & 0xFFL) << 8) | (magic[0] & 0xFFL);
                return m == 0x1F1903C103BC1FC6L;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static File extractEntry(ZipFile zip, ZipEntry entry, File runnerDir, String name) throws Exception {
        File rnCache = new File(runnerDir, "data/.rn-cache");
        if (!rnCache.exists()) rnCache.mkdirs();
        File out = new File(rnCache, name);
        if (out.exists() && out.length() == entry.getSize() && entry.getSize() > 0) {
            return out;
        }
        try (InputStream in = zip.getInputStream(entry);
             FileOutputStream fout = new FileOutputStream(out)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) fout.write(buf, 0, n);
        }
        System.out.println("[+] [RnBridge] Extracted JS bundle: " + out.getAbsolutePath()
                + " (" + out.length() + " bytes)");
        return out;
    }

    /**
     * Expo Kotlin dispatcher, registered like any other remote module. The
     * generic dispatch path invokes methodTable entries on spec.moduleInstance,
     * so this spec binds the live dispatcher instance directly.
     */
    private RnModuleConfigGenerator.ModuleSpec makeBoundExpoSpec(ExpoKotlinDispatcher expo) throws Exception {
        RnModuleConfigGenerator.ModuleSpec spec = new RnModuleConfigGenerator.ModuleSpec();
        spec.name = "ExpoKotlinDispatcher";
        spec.moduleInstance = expo;
        spec.methodNames.add("callMethod");
        spec.methodTable.put("callMethod", ExpoKotlinDispatcher.class.getMethod(
                "callMethod", String.class, String.class, List.class,
                com.facebook.react.bridge.Promise.class));
        spec.methodNames.add("callSync");
        spec.methodTable.put("callSync", ExpoKotlinDispatcher.class.getMethod(
                "callSync", String.class, String.class, List.class));
        spec.promiseMethodIds.add(0);
        spec.syncMethodIds.add(1);
        return spec;
    }

    /**
     * Read the app's own expo config (assets/app.config or app.json) straight
     * out of the APK. The runner pre-fills expo-constants from it - on a real
     * device those values come from expo's native ConstantsModule.
     */
    private static Map<String, Object> readExpoConfig(File apkFile) {
        if (apkFile == null || !apkFile.exists()) return null;
        String[] candidates = {"assets/app.config", "app.config", "assets/app.json", "app.json"};
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkFile)) {
            for (String name : candidates) {
                java.util.zip.ZipEntry e = zip.getEntry(name);
                if (e == null) continue;
                String json = new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                Object v = JsonCodec.decode(json);
                if (v instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) v;
                    System.out.println("[+] [RnBridge] expo config loaded from " + name
                            + " (name=" + map.get("name") + ", slug=" + map.get("slug")
                            + ", sdkVersion=" + map.get("sdkVersion") + ")");
                    return map;
                }
            }
        } catch (Throwable t) {
            System.out.println("[*] [RnBridge] no expo config in APK: " + t.getMessage());
        }
        return null;
    }

    public synchronized boolean start(Collection<NativeModule> modules, File runnerDir, File bundleFile) {
        return start(modules, runnerDir, bundleFile, null);
    }

    public synchronized boolean start(Collection<NativeModule> modules, File runnerDir, File bundleFile,
                                      Object precomputedReactContext) {
        if (mRunning) return true;
        if (bundleFile == null || !bundleFile.exists()) {
            System.out.println("[-] [RnBridge] No React Native bundle found; bridge not started.");
            return false;
        }

        // 1. Build config from the app's own native modules
        mSpecs = RnModuleConfigGenerator.generate(modules);
        mSpecsById.clear();
        mSpecsByName.clear();

        // 1b. UNIVERSAL expo Kotlin module integration (expo SDK 50+ apps).
        // Kotlin expo modules register through the app's own
        // KotlinInteropModuleRegistry, not ReactPackage.createNativeModules,
        // so registry reflection alone never sees them. Reuse the app's
        // generated ExpoModulesPackageList + its expo runtime classes.
        try {
            Object reactCtx = precomputedReactContext;
            Class<?> racClass = null;
            try { racClass = Class.forName("com.facebook.react.bridge.ReactApplicationContext"); } catch (Throwable ignored) {}
            Object anyCtx = null;
            if (reactCtx == null && modules != null && racClass != null) {
                for (NativeModule nm : modules) {
                    try {
                        Object ctx = nm.getClass().getMethod("getReactApplicationContext").invoke(nm);
                        if (ctx == null) continue;
                        if (anyCtx == null) anyCtx = ctx;
                        // expo requires a genuine ReactApplicationContext instance
                        if (racClass.isInstance(ctx)) { reactCtx = ctx; break; }
                    } catch (Throwable ignored) {}
                }
                if (reactCtx == null && anyCtx != null) {
                    // Wrap whatever context the app modules carry into a
                    // ReactApplicationContext so expo's AppContext accepts it.
                    try {
                        reactCtx = racClass
                                .getConstructor(android.content.Context.class)
                                .newInstance(anyCtx);
                    } catch (Throwable ignored) {}
                }
            }
            System.out.println("[*] [RnBridge] expo integration context: "
                    + (reactCtx == null ? "NONE" : reactCtx.getClass().getName())
                    + (anyCtx != null ? " (anyCtx=" + anyCtx.getClass().getName() + ")" : ""));
            ExpoKotlinDispatcher expo = ExpoKotlinDispatcher.create(reactCtx);
            if (expo != null && expo.hasModules()) {
                expo.emitOnCreate();
                mSpecs.add(makeBoundExpoSpec(expo));
                mExpoMetadata = expo.exportModuleMetadata();
            }
        } catch (Throwable t) {
            System.out.println("[*] [RnBridge] expo Kotlin bridge not available: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        for (int i = 0; i < mSpecs.size(); i++) {
            mSpecsById.put(i, mSpecs.get(i));
            mSpecsByName.put(mSpecs.get(i).name, mSpecs.get(i));
        }
        // CRITICAL: the JS bridge numbers modules by POSITION in the JSON
        // remoteModuleConfig array. Expand the spec list with the expo legacy
        // aliases FIRST and use that same list for BOTH the dispatch map and
        // the config write - otherwise ids shift and every call after the
        // first alias lands on the wrong module ("Unknown module id 50",
        // Timing calls hitting random modules, all expo sync hooks null).
        mSpecs = RnModuleConfigGenerator.expandForConfig(mSpecs);
        mSpecsById.clear();
        mSpecsByName.clear();
        for (int i = 0; i < mSpecs.size(); i++) {
            mSpecsById.put(i, mSpecs.get(i));
            mSpecsByName.put(mSpecs.get(i).name, mSpecs.get(i));
        }
        System.out.println("[+] [RnBridge] Universal bridge config built from "
                + mSpecs.size() + " native modules (app registry reflection, alias-expanded).");

        File configFile = new File(runnerDir, "data/.rn-cache/rn-config.json");
        try {
            configFile.getParentFile().mkdirs();
            Map<String, Object> cfg = new java.util.LinkedHashMap<>();
            cfg.put("remoteModuleConfig", RnModuleConfigGenerator.buildRemoteModuleConfig(mSpecs));
            if (mExpoMetadata != null && !mExpoMetadata.isEmpty()) {
                cfg.put("expoModules", mExpoMetadata);
            }
            File apkFileForConfig = new File(System.getProperty("headless.runner.apk",
                    new File(runnerDir, "base.apk").getAbsolutePath()));
            Map<String, Object> expoConfig = readExpoConfig(apkFileForConfig);
            if (expoConfig != null) {
                cfg.put("expoConfig", expoConfig);
                Map<String, Object> manifest = new java.util.LinkedHashMap<>(expoConfig);
                manifest.put("__internal", java.util.Collections.singletonMap(
                        "testEnvironment", "none"));
                cfg.put("expoManifest", manifest);
            }
            try (java.io.Writer w = new OutputStreamWriter(
                    new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                w.write(JsonCodec.encode(cfg));
            }
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Failed writing config: " + t.getMessage());
            return false;
        }

        // 2. Locate platform Hermes VM + driver
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        File hermesBin = new File(runnerDir, isWindows
                ? "tools\\hermes.exe" : "tools/hermes-linux/hermes");
        if (!hermesBin.exists()) {
            hermesBin = new File(runnerDir, isWindows ? "tools/hermes.exe" : "tools/hermes-linux/hermes");
        }
        File driverHbc = new File(runnerDir, "tools/rn/driver.hbc");
        if (!hermesBin.exists() || !driverHbc.exists()) {
            System.out.println("[-] [RnBridge] Hermes runtime missing ("
                    + hermesBin.getAbsolutePath() + " / " + driverHbc.getAbsolutePath() + ").");
            return false;
        }

        // 3. Spawn the VM process
        try {
            // NOTE: do not pass -Xhermes-internal-tti-metrics here — that flag
            // only exists in Facebook-internal Hermes builds; in this universal
            // build an unknown -X argument is FATAL and would kill the bridge.
            ProcessBuilder pb = new ProcessBuilder(
                    hermesBin.getAbsolutePath(),
                    // -Xmicrotask-queue enables the promise microtask queue:
                    // without it, `await` continuations after native callbacks
                    // never run (the runtime drains jobs but not microtasks).
                    "-Xmicrotask-queue",
                    driverHbc.getAbsolutePath());
            pb.environment().put("RN_BRIDGE_CONFIG", configFile.getAbsolutePath());
            pb.environment().put("RN_BRIDGE_BUNDLE", bundleFile.getAbsolutePath());
            pb.redirectErrorStream(false);
            mProcess = pb.start();
            mInbox = new BufferedWriter(new OutputStreamWriter(
                    mProcess.getOutputStream(), StandardCharsets.UTF_8));
            mOutbox = new BufferedReader(new InputStreamReader(
                    mProcess.getInputStream(), StandardCharsets.UTF_8));

            BufferedReader errReader = new BufferedReader(new InputStreamReader(
                    mProcess.getErrorStream(), StandardCharsets.UTF_8));
            Thread errThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        System.err.println("[Hermes-stderr] " + line);
                    }
                } catch (Throwable ignored) {}
            }, "RnBridgeHermesStderr");
            errThread.setDaemon(true);
            errThread.start();

            mReaderThread = new Thread(this::readerLoop, "RnBridgeReader");
            mReaderThread.setDaemon(true);

            // STALL WATCHDOG (self-healing): React's scheduler and app async
            // chains park work behind microtasks/timers that only drain when
            // the NEXT bridge message arrives - when none does, the app waits
            // forever with an empty-looking queue. Every few seconds of VM
            // silence, pump the job queue + React microtasks + flush the
            // MessageQueue over the @@Q channel until work stops appearing.
            Thread watchdog = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        if (!mRunning) break;
                        long idle = System.currentTimeMillis() - mLastVmActivityMs;
                        if (idle > 5000 && mLastVmActivityMs > 0) {
                            System.out.println("[*] [RnBridge] stall watchdog pump (idle " + idle + "ms)");
                            evalJs("(function(){try{"
                    + "var flushed=0;"
                    + "for (var round = 0; round < 30; round++) {"
                    + "  if (typeof __rnDrainJobs === 'function') { try { __rnDrainJobs(); } catch(je) {} }"
                    + "  try { if (typeof JSTimers !== 'undefined' && JSTimers.callReactNativeMicrotasks) {"
                    + "    __fbBatchedBridge.callFunctionReturnFlushedQueue('JSTimers','callReactNativeMicrotasks',[]);"
                    + "  } } catch(me) {}"
                    + "  var q = __fbBatchedBridge.flushedQueue();"
                    + "  if (q && q[0] && q[0].length) {"
                    + "    flushed += q[0].length;"
                    + "    __rnWriteOutbox('@@Q ' + JSON.stringify(q));"
                    + "  } else {"
                    + "    if (typeof __rnDrainJobs !== 'function') break;"
                    + "  }"
                    + "}"
                    + "return flushed > 0 ? ('PUMPED ' + flushed + ' call(s)') : 'IDLE';"
                    + "}catch(e){return 'DIAGERR ' + String(e);}})()");
                        }
                    } catch (Throwable ignored) {}
                }
            }, "RnBridgeStallWatchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            mReaderThread.start();

            mRunning = true;
            System.out.println("[+] [RnBridge] Universal React Native bridge LIVE ("
                    + hermesBin.getName() + ", bundle=" + bundleFile.getName()
                    + ", " + mSpecs.size() + " modules).");
            return true;
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Failed spawning Hermes VM: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    public void shutdown() {
        mRunning = false;
        try {
            if (mInbox != null) mInbox.close();
        } catch (Throwable ignored) {}
        try {
            if (mProcess != null) mProcess.destroy();
        } catch (Throwable ignored) {}
    }

    /**
     * BRIDGELESS RN BOOT HOOK. Expo SDK 50+ / RN 0.7x apps boot through
     * ReactHostImpl (bridgeless) instead of ReactInstanceManager. On a device
     * the JS engine moment is exactly here: HermesExecutorFactory.create()
     * constructs the native Hermes VM. In this runner the same hook starts
     * the platform Hermes stdio bridge with modules collected from the APP's
     * OWN packages (createNativeModules on a real device too - no per-app
     * knowledge), then dispatches AppRegistry.runApplication once the app's
     * own ReactRootView is mounted - the same sequence a device runs.
     */
    public void onJsExecutorCreated() {
        if (mRunning || mBootlessStarted) return;
        mBootlessStarted = true;
        // SEQUENCING (device semantics): the executor is born before the JS
        // runs, but the module registry that feeds the bridge config is
        // assembled by the app's own React flow right after this point
        // (legacy: CatalystInstanceImpl.runJSBundle calls start() with the
        // app's registry; bridgeless: ReactHostImpl has no catalyst, so the
        // runner boots on its own). Give the app's own flow a grace period
        // to call start() first - exactly like a device where executor and
        // module registry initialize together.
        Thread boot = new Thread(() -> {
            try {
                // Deterministic sequencing: the app's own React flow decides.
                // LEGACY (ReactInstanceManager): builder.build() constructs the
                // CatalystInstanceImpl right after the executor - its
                // runJSBundle calls start() with the app's real registry.
                // BRIDGELESS (ReactHostImpl): no CatalystInstanceImpl is ever
                // constructed, so the runner boots on its own.
                boolean sawCatalyst = false;
                long deadline = System.currentTimeMillis() + 60000;
                while (System.currentTimeMillis() < deadline) {
                    if (mRunning) {
                        System.out.println("[*] [RnBridge] JS executor created - app's own React flow already started the bridge.");
                        return;
                    }
                    if (!sawCatalyst && catalystInstanceLive()) {
                        sawCatalyst = true;
                        System.out.println("[*] [RnBridge] legacy Catalyst instance detected - waiting for its runJSBundle start().");
                    }
                    Thread.sleep(250);
                }
                if (mRunning || sawCatalyst) {
                    if (!mRunning) {
                        System.out.println("[*] [RnBridge] catalyst instance present but bundle not started - staying on the app's flow.");
                    }
                    return;
                }
                System.out.println("[*] [RnBridge] Bridgeless ReactHost JS executor created - booting universal bridge...");
                File runnerDir = new File(System.getProperty("headless.runner.dir",
                        System.getProperty("user.dir", ".")));
                File apkFile = new File(System.getProperty("headless.runner.apk", ""));
                if (!apkFile.exists()) {
                    apkFile = new File(runnerDir, "base.apk");
                }
                File bundleFile = locateBundle(apkFile, runnerDir);
                if (bundleFile == null) {
                    System.out.println("[-] [RnBridge] No JS bundle in APK - bridgeless boot skipped.");
                    return;
                }

                // 1. Gather the app's own native modules from its packages.
                Collection<NativeModule> modules = collectAppNativeModules();
                Object reactCtx = null;
                try {
                    Class<?> rac = Class.forName("com.facebook.react.bridge.ReactApplicationContext");
                    for (NativeModule nm : modules) {
                        Object c = nm.getClass().getMethod("getReactApplicationContext").invoke(nm);
                        if (c != null && rac.isInstance(c)) { reactCtx = c; break; }
                    }
                } catch (Throwable ignored) {}
                start(modules, runnerDir, bundleFile, reactCtx);
                if (mRunning) {
                    scheduleRunApplicationForBridgelessRoot();
                }
            } catch (Throwable t) {
                System.out.println("[-] [RnBridge] bridgeless boot failed: " + t);
            }
        }, "RnBridgeBridgelessBoot");
        boot.setDaemon(true);
        boot.start();
    }

    /** True when the app's own flow constructed a (runner-side) catalyst. */
    private static boolean catalystInstanceLive() {
        try {
            Class<?> liveCls = Class.forName("com.facebook.react.bridge.CatalystInstanceImpl");
            Object live = liveCls.getMethod("live").invoke(null);
            return live != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Reflect the app's own ReactPackage list and create its native modules. */
    @SuppressWarnings("unchecked")
    private Collection<NativeModule> collectAppNativeModules() {
        List<NativeModule> all = new ArrayList<>();
        try {
            android.app.Activity top = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
            android.app.Application app = top != null ? top.getApplication() : null;
            if (app == null) return all;

            // Expo/RN apps: Application.getReactHost() -> ReactHostImpl (bridgeless).
            // The packages live on the host (mPackages / getPackages()).
            Object packages = null;
            for (String[] path : new String[][]{
                    {"getReactHost", "getPackages"},
                    {"getReactNativeHost", "getPackages"},
                    {"getPackages"}}) {
                try {
                    Object cur = app;
                    for (String m : path) {
                        if (cur == null) break;
                        Object next = cur.getClass().getMethod(m).invoke(cur);
                        cur = next;
                    }
                    if (cur instanceof List && !((List<Object>) cur).isEmpty()) {
                        packages = cur;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (!(packages instanceof List)) return all;

            // A fabricated ReactContext like the app's own flow builds. RN
            // (post-R8 builds) keeps ReactApplicationContext abstract and
            // constructs the concrete BridgeReactContext subclass - exactly
            // what ReactInstanceManagerImpl.createReactContext does on a
            // device. Try the concrete class first, then the abstract base.
            Object reactCtx = null;
            for (String cls : new String[]{
                    "com.facebook.react.bridge.BridgeReactContext",
                    "com.facebook.react.bridge.ReactApplicationContext"}) {
                try {
                    Class<?> rac = Class.forName(cls);
                    reactCtx = rac.getConstructor(android.content.Context.class).newInstance(app);
                    break;
                } catch (Throwable ignored) {}
            }

            for (Object pkg : (List<Object>) packages) {
                // ReactPackage.createNativeModules(ReactContext) - find by name
                // with whatever context parameter shape this RN build declares.
                boolean added = false;
                Throwable lastFailure = null;
                for (java.lang.reflect.Method m : pkg.getClass().getMethods()) {
                    if (!"createNativeModules".equals(m.getName())) continue;
                    if (m.getParameterCount() != 1) continue;
                    Object ctxArg = m.getParameterTypes()[0].isInstance(reactCtx)
                            ? reactCtx : app;
                    try {
                        Object mods = m.invoke(pkg, ctxArg);
                        if (mods instanceof List) {
                            for (Object mm : (List<Object>) mods) {
                                if (mm instanceof NativeModule) {
                                    all.add((NativeModule) mm);
                                    added = true;
                                }
                            }
                        }
                    } catch (Throwable perMethod) {
                        lastFailure = perMethod instanceof java.lang.reflect.InvocationTargetException
                                ? ((java.lang.reflect.InvocationTargetException) perMethod).getCause()
                                : perMethod;
                    }
                }
                // TurboReactPackage/BaseReactPackage (RN 0.7x): lazy modules
                // resolved by NAME through getModule(name, context) with the
                // name set enumerated by getReactModuleInfoProvider() - the
                // same path TurboModuleManager uses on a device.
                if (!added) {
                    try {
                        Map<String, Object> infos = null;
                        try {
                            Object provider = pkg.getClass()
                                    .getMethod("getReactModuleInfoProvider").invoke(pkg);
                            for (java.lang.reflect.Method m : provider.getClass().getMethods()) {
                                if ("getReactModuleInfos".equals(m.getName())
                                        || "getModuleInfos".equals(m.getName())) {
                                    Object r = m.invoke(provider);
                                    if (r instanceof Map) infos = (Map<String, Object>) r;
                                    break;
                                }
                            }
                        } catch (Throwable ignoredProvider) {}
                        if (infos != null) {
                            java.lang.reflect.Method getModule = null;
                            for (java.lang.reflect.Method m : pkg.getClass().getMethods()) {
                                if ("getModule".equals(m.getName()) && m.getParameterCount() == 2
                                        && m.getParameterTypes()[0] == String.class) {
                                    getModule = m;
                                    break;
                                }
                            }
                            if (getModule != null) {
                                for (String name : infos.keySet()) {
                                    try {
                                        Object mod = getModule.invoke(pkg, name, reactCtx);
                                        if (mod instanceof NativeModule) {
                                            all.add((NativeModule) mod);
                                            added = true;
                                        }
                                    } catch (Throwable perMod) {
                                        Throwable cause = perMod instanceof java.lang.reflect.InvocationTargetException
                                                ? ((java.lang.reflect.InvocationTargetException) perMod).getCause()
                                                : perMod;
                                        System.out.println("[*] [RnBridge] module '" + name + "' creation skipped: " + cause);
                                    }
                                }
                            }
                        }
                    } catch (Throwable tTurbo) {
                        lastFailure = tTurbo;
                    }
                }
                if (!added && lastFailure != null) {
                    System.out.println("[*] [RnBridge] package " + pkg.getClass().getName()
                            + " createNativeModules failed: " + lastFailure);
                }
            }
            System.out.println("[+] [RnBridge] bridgeless: collected " + all.size()
                    + " native module(s) from " + ((List<Object>) packages).size() + " app package(s).");
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] bridgeless module collection failed: " + t);
        }
        return all;
    }

    /**
     * Device flow after the bundle runs: ReactRootView registers with the
     * UIManager and AppRegistry.runApplication mounts the component. Poll
     * for the app's own ReactRootView (mounted by its own ReactActivity)
     * and dispatch runApplication through the app's registered UIManager.
     */
    private void scheduleRunApplicationForBridgelessRoot() {
        Thread kicker = new Thread(() -> {
            try {
                android.view.View root = null;
                long deadline = System.currentTimeMillis() + 15000;
                while (System.currentTimeMillis() < deadline && root == null) {
                    android.app.Activity top = org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
                    if (top != null && top.getWindow() != null) {
                        root = findViewByClassName(top.getWindow().getDecorView(), "ReactRootView");
                        if (root == null) {
                            root = findViewByClassName(top.getWindow().getDecorView(), "ReactRoot");
                        }
                    }
                    if (root == null) Thread.sleep(200);
                }
                if (root == null) {
                    System.out.println("[*] [RnBridge] bridgeless kicker: no ReactRootView mounted - skipping runApplication.");
                    return;
                }
                if (root.getId() <= 0) root.setId(1);
                int rootTag = root.getId();
                try {
                    Object uim = getNativeModuleInstance("UIManager");
                    if (uim != null) {
                        for (java.lang.reflect.Method m : uim.getClass().getMethods()) {
                            if ("addRootView".equals(m.getName())
                                    && m.getParameterCount() >= 1
                                    && android.view.View.class.isAssignableFrom(m.getParameterTypes()[0])) {
                                Object tagObj = m.getParameterCount() >= 2
                                        ? m.invoke(uim, root, null) : m.invoke(uim, root);
                                if (tagObj instanceof Number) {
                                    rootTag = ((Number) tagObj).intValue();
                                    root.setId(rootTag);
                                }
                                break;
                            }
                        }
                    }
                } catch (Throwable ignoredUim) {}
                String jsModuleName = reflectStringFieldDeep(root, "mJSModuleName");
                if (jsModuleName == null || jsModuleName.isEmpty()) {
                    jsModuleName = reflectStringFieldDeep(root, "jsModuleName");
                }
                if (jsModuleName == null || jsModuleName.isEmpty()) {
                    jsModuleName = "main";
                }
                Map<String, Object> appParams = new java.util.LinkedHashMap<>();
                appParams.put("rootTag", rootTag);
                callJsFunction("AppRegistry", "runApplication",
                        java.util.Arrays.asList((Object) jsModuleName, (Object) appParams));
                System.out.println("[+] [RnBridge] bridgeless AppRegistry.runApplication dispatched (module="
                        + jsModuleName + ", rootTag=" + rootTag + ")");
            } catch (Throwable t) {
                System.out.println("[-] [RnBridge] bridgeless kicker failed: " + t);
            }
        }, "RnBridgeBridgelessKicker");
        kicker.setDaemon(true);
        kicker.start();
    }

    private static android.view.View findViewByClassName(android.view.View v, String simpleName) {
        if (v == null) return null;
        String n = v.getClass().getSimpleName();
        if (n.equals(simpleName) || v.getClass().getName().endsWith("." + simpleName)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View found = findViewByClassName(g.getChildAt(i), simpleName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String reflectStringFieldDeep(Object target, String name) {
        Class<?> c = target != null ? target.getClass() : null;
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(target);
                return v != null ? String.valueOf(v) : null;
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    // ------- native -> JS -------

    /** Call a JS callable module (AppRegistry, RCTDeviceEventEmitter, JSTimers...). */
    /** Run a task on the RN UI thread the app's own flow uses for the
     *  shadow tree (the catalyst UI queue - it arms ShadowNodeRegistry's
     *  single-thread asserter). Falls back to the main looper. */
    public void scheduleOnUiThread(Runnable task) {
        scheduleOnUiThread(task, 0);
    }

    /** Run a task on the RN UI thread after a delay (approximately). */
    public void scheduleOnUiThread(Runnable task, long delayMs) {
        Runnable wrapped = delayMs > 0
                ? () -> mUiThreadHandler.postDelayed(task, delayMs)
                : task;
        try {
            Class<?> liveCls = Class.forName("com.facebook.react.bridge.CatalystInstanceImpl");
            Object live = liveCls.getMethod("live").invoke(null);
            if (live != null) {
                Object ok = liveCls.getMethod("runOnUiQueue", Runnable.class).invoke(live, wrapped);
                if (Boolean.FALSE.equals(ok)) {
                    System.out.println("[*] [RnBridge] catalyst UI queue refused the task - falling back to main");
                }
                return;
            }
            System.out.println("[*] [RnBridge] no live catalyst for UI routing - falling back to main");
        } catch (Throwable tRoute) {
            System.out.println("[*] [RnBridge] catalyst UI routing failed: " + tRoute);
            mUiThreadHandler.post(wrapped);
            return;
        }
        mUiThreadHandler.post(wrapped);
    }

    /** Live instance of a registered native module (e.g. "UIManager"), or null. */
    public Object getNativeModuleInstance(String name) {
        RnModuleConfigGenerator.ModuleSpec spec = mSpecsByName.get(name);
        return spec != null ? spec.moduleInstance : null;
    }

    public void callJsFunction(String module, String method, List<Object> args) {
        if (!mRunning) return;
        try {
            Map<String, Object> req = new java.util.LinkedHashMap<>();
            req.put("type", "call");
            req.put("module", module);
            req.put("method", method);
            req.put("args", args != null ? args : new ArrayList<>());
            writeInbox(JsonCodec.encode(req));
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] callJsFunction failed: " + t.getMessage());
        }
    }

    public void callJsFunctionJson(String module, String method, String argsJson) {
        callJsFunction(module, method, JsonCodec.decodeList(argsJson));
    }

    /** Invoke a JS callback created by a prior native call. */
    public void invokeJsCallback(int callbackId, List<Object> args) {
        System.out.println("[*] [RnBridge] invokeJsCallback id=" + callbackId
                + " args=" + (args == null ? "null" : args.size()));
        if (!mRunning) return;
        try {
            Map<String, Object> req = new java.util.LinkedHashMap<>();
            req.put("type", "callback");
            req.put("id", callbackId);
            req.put("args", args != null ? args : new ArrayList<>());
            writeInbox(JsonCodec.encode(req));
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] invokeJsCallback failed: " + t.getMessage());
        }
    }

    /** Evaluate arbitrary JS inside the app VM. */
    public void evalJs(String code) {
        if (!mRunning) return;
        try {
            Map<String, Object> req = new java.util.LinkedHashMap<>();
            req.put("type", "eval");
            req.put("code", code);
            writeInbox(JsonCodec.encode(req));
        } catch (Throwable ignored) {}
    }

    private void writeInbox(String json) throws Exception {
        synchronized (mInboxLock) {
            System.out.println("[*] [RnBridge] ->VM: " + (json == null ? "null"
                    : json.substring(0, Math.min(100, json.length()))));
            mInbox.write(json);
            mInbox.write("\n");
            mInbox.flush();
        }
    }

    // ------- JS -> native -------

    private volatile long mLastVmActivityMs = 0;

    private void readerLoop() {
        try {
            String line;
            while ((line = mOutbox.readLine()) != null) {
                mLastVmActivityMs = System.currentTimeMillis();
                if (line.isEmpty()) continue;
                if (line.startsWith("@@SYNC ")) {
                    handleSyncCall(line.substring(7).trim());
                } else if (line.startsWith("@@Q ")) {
                    dispatchBatch(line.substring(4).trim());
                } else if (line.startsWith("@@")) {
                    System.out.println("[RnBridge-ctl] " + line);
                } else {
                    // JS console.log / driver diagnostics
                    System.out.println("[JS] " + line);
                }
            }
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] Reader ended: " + t.getMessage());
        }
        mRunning = false;
        System.out.println("[*] [RnBridge] Hermes VM stream closed.");
    }

    /** Handle a batch: [moduleIds[], methodIds[], params[], callId] */
    private void dispatchBatch(String json) {
        try {
            Object decoded = JsonCodec.decode(json);
            if (!(decoded instanceof List)) {
                // Driver replies ({ok,result} / {ok:false,error}) arrive as
                // JSON objects - surface them, they previously vanished
                // silently inside decodeList's empty-list fallback.
                if (decoded instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) decoded;
                    Object ok = m.get("ok");
                    if (Boolean.FALSE.equals(ok) || "false".equals(String.valueOf(ok))) {
                        System.err.println("[-] [RnBridge] JS driver error reply: " + m.get("error"));
                    } else if (Boolean.TRUE.equals(ok) || "true".equals(String.valueOf(ok))) {
                        System.out.println("[*] [RnBridge] eval reply: " + m.get("result"));
                    }
                }
                return;
            }
            List<Object> batch = (List<Object>) decoded;
            if (batch.size() < 3) {
                // Driver replies ({ok,result} / {ok:false,error}) arrive here
                // - surface them, they previously vanished silently.
                Object first = batch.isEmpty() ? null : batch.get(0);
                if (first instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) first;
                    Object ok = m.get("ok");
                    if (Boolean.FALSE.equals(ok) || "false".equals(String.valueOf(ok))) {
                        System.err.println("[-] [RnBridge] JS driver error reply: " + m.get("error"));
                    } else if (Boolean.TRUE.equals(ok) || "true".equals(String.valueOf(ok))) {
                        System.out.println("[*] [RnBridge] eval reply: " + m.get("result"));
                    }
                }
                return;
            }
            List<Object> moduleIds = (List<Object>) batch.get(0);
            List<Object> methodIds = (List<Object>) batch.get(1);
            List<Object> params = (List<Object>) batch.get(2);

            for (int i = 0; i < moduleIds.size(); i++) {
                int moduleId = (int) toNumber(moduleIds.get(i));
                int methodId = (int) toNumber(methodIds.get(i));
                List<Object> callArgs = (i < params.size() && params.get(i) instanceof List)
                        ? (List<Object>) params.get(i) : new ArrayList<>();
                dispatchCall(moduleId, methodId, callArgs);
            }
            // Mirror the native bridge's post-batch hook: on a real device
            // CatalystInstanceImpl calls UIManagerModule.onBatchComplete()
            // after every JS batch, which is what flushes the accumulated
            // view operations through UIViewOperationQueue to the screen.
            try {
                RnModuleConfigGenerator.ModuleSpec uim = mSpecsByName.get("UIManager");
                if (uim != null && uim.moduleInstance != null) {
                    java.lang.reflect.Method onBatch = null;
                    Class<?> c = uim.moduleInstance.getClass();
                    while (c != null && c != Object.class && onBatch == null) {
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if ("onBatchComplete".equals(m.getName())) { onBatch = m; break; }
                        }
                        if (onBatch == null) c = c.getSuperclass();
                    }
                    if (onBatch != null) {
                        onBatch.setAccessible(true);
                        final Object fUim = uim.moduleInstance;
                        final java.lang.reflect.Method fOnBatch = onBatch;
                        // dispatchViewUpdates registers a Choreographer frame
                        // callback on the CALLING thread's Choreographer. The
                        // context-creation thread dies right after the batch,
                        // taking its Choreographer (and every queued view
                        // operation) with it. Dispatch on the main looper -
                        // the same thread a real device uses for UI ops.
                        System.out.println("[*] [RnBridge] posting onBatchComplete to main looper");
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            try {
                                System.out.println("[*] [RnBridge] batch hook running on "
                                        + Thread.currentThread().getName());
                                fOnBatch.invoke(fUim);
                            } catch (Throwable tBatch) {
                                System.err.println("[-] [RnBridge] onBatchComplete failed: " + tBatch);
                            }
                            // The JS's commit dispatches the accumulated view
                            // operations via UIManager.dispatchViewUpdates(batchId).
                            // Some R8 builds drop the call chain through
                            // onBatchComplete, so issue it here directly - the
                            // exact method the JS would have called.
                            try {
                                // RN 0.8x: the dispatch lives on UIImplementation,
                                // reachable from UIManagerModule.getUIImplementation().
                                Object uiImpl = fUim.getClass()
                                        .getMethod("getUIImplementation").invoke(fUim);
                                if (uiImpl != null) {
                                    java.lang.reflect.Method dvu = null;
                                    Class<?> cu = uiImpl.getClass();
                                    while (cu != null && cu != Object.class && dvu == null) {
                                        for (java.lang.reflect.Method m : cu.getDeclaredMethods()) {
                                            if ("dispatchViewUpdates".equals(m.getName())) {
                                                dvu = m;
                                                break;
                                            }
                                        }
                                        if (dvu == null) cu = cu.getSuperclass();
                                    }
                                    if (dvu != null) {
                                        dvu.setAccessible(true);
                                        Object[] dvuArgs = new Object[dvu.getParameterCount()];
                                        for (int ai = 0; ai < dvuArgs.length; ai++) {
                                            Class<?> pt = dvu.getParameterTypes()[ai];
                                            // Unique batch id per flush: UIViewOperationQueue
                                            // drops duplicate/stale batch ids, so reusing -1
                                            // would silently swallow every batch after the
                                            // first. High ids avoid clashing with JS ids.
                                            int hookBatchId = BATCH_ID_BASE + HOOK_BATCH_COUNTER.incrementAndGet();
                                            if (pt == int.class) dvuArgs[ai] = hookBatchId;
                                            else if (pt == long.class) dvuArgs[ai] = (long) hookBatchId;
                                            else if (pt == boolean.class) dvuArgs[ai] = Boolean.FALSE;
                                            else dvuArgs[ai] = null;
                                        }
                                        dvu.invoke(uiImpl, dvuArgs);
                                        System.out.println("[*] [RnBridge] dispatchViewUpdates(" + BATCH_ID_BASE + "+n=" + HOOK_BATCH_COUNTER.get() + ") issued from batch hook");
                                    } else {
                                        System.out.println("[*] [RnBridge] no dispatchViewUpdates on UIImplementation");
                                    }
                                } else {
                                    System.out.println("[*] [RnBridge] no UIImplementation on UIManager");
                                }
                            } catch (Throwable tDvu) {
                                System.err.println("[-] [RnBridge] dispatchViewUpdates failed: " + tDvu);
                            }
                        });
                    } else {
                        System.out.println("[*] [RnBridge] no onBatchComplete on UIManager (RN variant)");
                    }
                }
            } catch (Throwable t) {
                System.err.println("[-] [RnBridge] onBatchComplete failed: " + t);
            }
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Batch dispatch error: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchCall(int moduleId, int methodId, List<Object> rawArgs) {
        // NAME-BASED DISPATCH (TurboModuleRegistry / __turboModuleProxy):
        // RN 0.68+ JS resolves core modules by NAME. The driver sends
        // moduleId=-1, methodId=-1 with params [moduleName, methodName, args,
        // ...trailing callback ids].
        if (moduleId == -1 && methodId == -1) {
            try {
                String name = String.valueOf(rawArgs.get(0));
                String meth = String.valueOf(rawArgs.get(1));
                List<Object> combined = new ArrayList<>();
                if (rawArgs.size() > 2 && rawArgs.get(2) instanceof List) {
                    combined.addAll((List<Object>) rawArgs.get(2));
                }
                for (int i = 3; i < rawArgs.size(); i++) combined.add(rawArgs.get(i));
                dispatchCallByName(name, meth, combined);
                return;
            } catch (Throwable tName) {
                System.err.println("[-] [RnBridge] name-based dispatch failed: " + tName);
                return;
            }
        }
        RnModuleConfigGenerator.ModuleSpec spec = mSpecsById.get(moduleId);
        if (spec != null && requiresUiThread(spec.name)) {
            // Keep call order: FIFO onto the dedicated single-thread executor.
            final int fModuleId = moduleId, fMethodId = methodId;
            final List<Object> fArgs = rawArgs;
            scheduleOnUiThread(() -> dispatchCallOnUiThread(fModuleId, fMethodId, fArgs));
            return;
        }
        dispatchCallOnBridgeThread(moduleId, methodId, rawArgs);
    }

    /** Resolve a module by NAME and invoke one method (turbo module path). */
    private void dispatchCallByName(String moduleName, String methodName, List<Object> rawArgs) {
        RnModuleConfigGenerator.ModuleSpec spec = mSpecsByName.get(moduleName);
        if (spec == null) {
            // Platform-provided Cxx turbo modules (DeviceInfo,
            // NativeReactNativeFeatureFlagsCxx, ExceptionsManager...) ship
            // inside RN's native binary on a device - the runner supplies
            // them here, framework-side.
            syntheticTurboCallAsync(moduleName, methodName, rawArgs);
            return;
        }
        if (spec != null && requiresUiThread(spec.name)) {
            final RnModuleConfigGenerator.ModuleSpec fSpec = spec;
            final String fMeth = methodName;
            final List<Object> fArgs = rawArgs;
            scheduleOnUiThread(() -> {
                try {
                    invokeSpecMethod(fSpec, fMeth, fArgs);
                } catch (Throwable t) {
                    System.err.println("[-] [RnBridge] " + fSpec.name + "." + fMeth + " ui dispatch failed: " + t);
                }
            });
            return;
        }
        invokeSpecMethod(spec, methodName, rawArgs);
    }

    /**
     * Platform-native turbo modules that RN core ships as C++ (never Java):
     * DeviceInfo, NativeReactNativeFeatureFlagsCxx, ExceptionsManager.
     * Device-faithful values, no app involvement.
     */
    private static final String[] RN_CXX_TURBO_MODULES = {
            "DeviceInfo", "NativeReactNativeFeatureFlagsCxx", "ExceptionsManager",
            "NativePlatformConstantsAndroid",
    };

    private boolean isCxxTurboModule(String name) {
        for (String m : RN_CXX_TURBO_MODULES) {
            if (m.equals(name)) return true;
        }
        return false;
    }

    /** Resolve a name-based call synchronously for Cxx modules (null otherwise). */
    private Object syntheticTurboCallSync(String name, String method, List<Object> args) {
        if ("DeviceInfo".equals(name)) {
            if ("getConstants".equals(method)) return deviceInfoConstants();
            return null;
        }
        if ("NativeReactNativeFeatureFlagsCxx".equals(name)
                || "NativePlatformConstantsAndroid".equals(name)) {
            // Feature flags: device default builds return false for optional
            // flags, letting RN JS fall back to its own defaults.
            return Boolean.FALSE;
        }
        if ("ExceptionsManager".equals(name)) {
            if (method != null && method.startsWith("report")) {
                try {
                    Object first = args != null && !args.isEmpty() ? args.get(0) : null;
                    System.err.println("[JS Exception] " + first);
                } catch (Throwable ignored) {}
            }
            return null;
        }
        return null;
    }

    /** DeviceInfo.getConstants equivalent (Pixel-class desktop profile). */
    private static Map<String, Object> deviceInfoConstants() {
        Map<String, Object> constants = new java.util.LinkedHashMap<>();
        Map<String, Object> window = new java.util.LinkedHashMap<>();
        window.put("width", 1080);
        window.put("height", 1857);
        window.put("scale", 2.625);
        window.put("fontScale", 1.0);
        window.put("densityDpi", 420);
        Map<String, Object> screen = new java.util.LinkedHashMap<>();
        screen.put("width", 1080);
        screen.put("height", 1920);
        screen.put("scale", 2.625);
        screen.put("fontScale", 1.0);
        screen.put("densityDpi", 420);
        Map<String, Object> dimensions = new java.util.LinkedHashMap<>();
        dimensions.put("windowPhysicalPixels", window);
        dimensions.put("screenPhysicalPixels", screen);
        Map<String, Object> dims = new java.util.LinkedHashMap<>();
        dims.put("window", dimensions);
        dims.put("screen", dimensions);
        constants.put("Dimensions", dims);
        constants.put("isTesting", Boolean.FALSE);
        constants.put("isTurboModule", Boolean.TRUE);
        Map<String, Object> rnVersion = new java.util.LinkedHashMap<>();
        rnVersion.put("major", 0);
        rnVersion.put("minor", 81);
        rnVersion.put("patch", 4);
        constants.put("reactNativeVersion", rnVersion);
        return constants;
    }

    /** Async path for Cxx turbo modules: resolve trailing promise callbacks. */
    private void syntheticTurboCallAsync(String name, String method, List<Object> rawArgs) {
        if (!isCxxTurboModule(name)) {
            System.err.println("[-] [RnBridge] turbo module not registered: " + name);
            return;
        }
        if ("ExceptionsManager".equals(name)) {
            try {
                Object first = rawArgs != null && !rawArgs.isEmpty() ? rawArgs.get(0) : null;
                System.err.println("[JS Exception via ExceptionsManager] " + first);
            } catch (Throwable ignored) {}
            // reportException/reportFatalException have NO callbacks.
            return;
        }
        // Promise-based Cxx methods carry [.., successId, failId] at the end.
        Object value = syntheticTurboCallSync(name, method, rawArgs);
        if (rawArgs != null && rawArgs.size() >= 2) {
            int succId = toIntId(rawArgs.get(rawArgs.size() - 2));
            int failId = toIntId(rawArgs.get(rawArgs.size() - 1));
            if (succId > 0) {
                invokeJsCallback(succId, new ArrayList<>(java.util.Collections.singletonList(value)));
            } else if (failId > 0) {
                invokeJsCallback(failId, new ArrayList<>(java.util.Collections.singletonList(value)));
            }
        }
    }

    /** Reflect getConstants() for turbo module constant access (device: the
     *  JSI turbo module object exposes constants synchronously). */
    private Object resolveTurboConstants(RnModuleConfigGenerator.ModuleSpec spec) {
        try {
            Method gm = spec.moduleInstance.getClass().getMethod("getConstants");
            return gm.invoke(spec.moduleInstance);
        } catch (Throwable ignored) {
        }
        return new java.util.LinkedHashMap<>();
    }

    private boolean requiresUiThread(String moduleName) {
        return "UIManager".equals(moduleName);
    }

    @SuppressWarnings("unchecked")
    private void dispatchCallOnUiThread(int moduleId, int methodId, List<Object> rawArgs) {
        try {
            dispatchCallOnBridgeThread(moduleId, methodId, rawArgs);
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] UI-thread dispatch failed: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchCallOnBridgeThread(int moduleId, int methodId, List<Object> rawArgs) {
        RnModuleConfigGenerator.ModuleSpec spec = mSpecsById.get(moduleId);
        if (spec == null) {
            System.err.println("[-] [RnBridge] Unknown module id " + moduleId);
            return;
        }
        if (methodId < 0 || methodId >= spec.methodNames.size()) {
            System.err.println("[-] [RnBridge] Unknown method id " + methodId + " on " + spec.name);
            return;
        }
        invokeSpecMethod(spec, spec.methodNames.get(methodId), rawArgs);
    }

    @SuppressWarnings("unchecked")
    private void invokeSpecMethod(RnModuleConfigGenerator.ModuleSpec spec,
                                  String methodName, List<Object> rawArgs) {
        if ("UIManager".equals(spec.name)) {
            System.out.println("[*] [RnBridge] UIManager." + methodName + " on thread "
                    + Thread.currentThread().getName() + " (id=" + Thread.currentThread().getId() + ")");
        }

        // Universal framework-level interceptions (replace broken host-dependent impls)
        if ("Timing".equals(spec.name)) {
            if (handleTimingCall(methodName, rawArgs)) return;
        }

        Method method = spec.methodTable.get(methodName);
        if (method == null) {
            System.err.println("[-] [RnBridge] No @ReactMethod '" + methodName + "' on " + spec.name);
            return;
        }

        try {
            Object[] javaArgs = convertArguments(spec, method, rawArgs);
            method.setAccessible(true);
            final String moduleLog = spec.name + "." + methodName;
            if (!"UIManager".equals(spec.name)) {
                System.out.println("[+] [RnBridge] JS->Native: " + moduleLog
                        + " (" + countNonNull(javaArgs) + " args)");
            }
            method.invoke(spec.moduleInstance, javaArgs);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            System.err.println("[-] [RnBridge] " + spec.name + "." + methodName
                    + " threw: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            StackTraceElement[] st = cause.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 8); i++) {
                System.err.println("      at " + st[i]);
            }
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] " + spec.name + "." + methodName
                    + " dispatch failed: " + t.getMessage());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 6); i++) {
                System.err.println("      at " + st[i]);
            }
        }
    }

    private int countNonNull(Object[] a) {
        int n = 0;
        for (Object o : a) if (o != null) n++;
        return n;
    }

    /**
     * Convert the raw JSON args into the Java parameter types the @ReactMethod
     * expects, replicating the C++ ArgumentExtractor semantics:
     * regular args from the front, callbacks/promises from the end.
     */
    private Object[] convertArguments(RnModuleConfigGenerator.ModuleSpec spec,
                                      Method method, List<Object> rawArgs) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] out = new Object[paramTypes.length];

        // Count trailing callback-consuming slots
        int slotsFromEnd = 0;
        for (Class<?> p : paramTypes) {
            String pn = p.getName();
            if ("com.facebook.react.bridge.Promise".equals(pn)) {
                slotsFromEnd += 2;
            } else if ("com.facebook.react.bridge.Callback".equals(pn)) {
                slotsFromEnd += 1;
            }
        }
        int regularCount = Math.max(0, paramTypes.length - (slotsFromEnd > 0 ? callbackSlotCount(paramTypes) : 0));

        int frontIdx = 0;
        int endIdx = rawArgs.size() - 1;

        for (int i = 0; i < paramTypes.length; i++) {
            String pn = paramTypes[i].getName();
            if ("com.facebook.react.bridge.Promise".equals(pn)) {
                Object succId = (endIdx >= 0) ? rawArgs.get(endIdx--) : null;
                Object failId = (endIdx >= 0) ? rawArgs.get(endIdx--) : null;
                out[i] = new JsCallbackBridge.PromiseImpl(
                        toIntId(succId), toIntId(failId), this);
            } else if ("com.facebook.react.bridge.Callback".equals(pn)) {
                Object id = (endIdx >= 0) ? rawArgs.get(endIdx--) : null;
                out[i] = new JsCallbackBridge.CallbackImpl(toIntId(id), this);
            } else {
                Object val = (frontIdx < endIdx + 1 - remainingEndSlots(paramTypes, i))
                        ? rawArgs.get(frontIdx++) : null;
                if (val == null && frontIdx < rawArgs.size() - slotsFromEnd) {
                    val = rawArgs.get(frontIdx++);
                }
                out[i] = coerceValue(val, paramTypes[i]);
            }
        }
        return out;
    }

    private int callbackSlotCount(Class<?>[] paramTypes) {
        int n = 0;
        for (Class<?> p : paramTypes) {
            String pn = p.getName();
            if ("com.facebook.react.bridge.Promise".equals(pn)) n += 2;
            else if ("com.facebook.react.bridge.Callback".equals(pn)) n += 1;
        }
        return n;
    }

    private int remainingEndSlots(Class<?>[] paramTypes, int current) {
        int n = 0;
        for (int i = current + 1; i < paramTypes.length; i++) {
            String pn = paramTypes[i].getName();
            if ("com.facebook.react.bridge.Promise".equals(pn)) n += 2;
            else if ("com.facebook.react.bridge.Callback".equals(pn)) n += 1;
        }
        return n;
    }

    private Object coerceValue(Object val, Class<?> targetType) {
        String tn = targetType.getName();
        if (val == null) return null;
        if ("com.facebook.react.bridge.ReadableArray".equals(tn)
                || "com.facebook.react.bridge.ReadableNativeArray".equals(tn)) {
            if (val instanceof List) return new com.facebook.react.bridge.ReadableNativeArray((List<Object>) val);
            return new com.facebook.react.bridge.ReadableNativeArray(new ArrayList<>());
        }
        if ("com.facebook.react.bridge.ReadableMap".equals(tn)
                || "com.facebook.react.bridge.ReadableNativeMap".equals(tn)) {
            if (val instanceof Map) return new com.facebook.react.bridge.ReadableNativeMap((Map<String, Object>) val);
            return new com.facebook.react.bridge.ReadableNativeMap(new java.util.LinkedHashMap<>());
        }
        if ("com.facebook.react.bridge.WritableMap".equals(tn)) {
            if (val instanceof Map) return new com.facebook.react.bridge.WritableNativeMap((Map<String, Object>) val);
            return new com.facebook.react.bridge.WritableNativeMap();
        }
        if ("com.facebook.react.bridge.WritableArray".equals(tn)) {
            if (val instanceof List) return new com.facebook.react.bridge.WritableNativeArray((List<Object>) val);
            return new com.facebook.react.bridge.WritableNativeArray();
        }
        if (targetType == String.class) return String.valueOf(val);
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (val instanceof Boolean) return val;
            return Boolean.parseBoolean(String.valueOf(val));
        }
        if (targetType == int.class || targetType == Integer.class) {
            return (int) Math.round(toNumber(val));
        }
        if (targetType == long.class || targetType == Long.class) {
            return (long) Math.round(toNumber(val));
        }
        if (targetType == double.class || targetType == Double.class) return toNumber(val);
        if (targetType == float.class || targetType == Float.class) {
            return (float) toNumber(val);
        }
        // Fallback: try List/Map direct pass-through
        if (val instanceof List && List.class.isAssignableFrom(targetType)) return val;
        if (val instanceof Map && Map.class.isAssignableFrom(targetType)) return val;
        return null;
    }

    private double toNumber(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return (Boolean) o ? 1 : 0;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Throwable t) {
            return 0;
        }
    }

    private int toIntId(Object o) {
        if (o == null) return -1;
        return (int) Math.round(toNumber(o));
    }

    // ------- sync calls -------

    private void handleSyncCall(String reqJson) {
        String reply = "null";
        System.out.println("[*] [RnBridge] @@SYNC recv: " + reqJson + " (thread="
                + Thread.currentThread().getName() + ")");
        try {
            List<Object> req = JsonCodec.decodeList(reqJson);
            if (req.size() >= 3) {
                int moduleId = toIntId(req.get(0));
                int methodId = toIntId(req.get(1));
                List<Object> args = (List<Object>) req.get(2);
                // NAME-BASED SYNC (turbo module constants / sync hooks):
                // [-1, -1, [moduleName, methodName, ...args]]
                if (moduleId == -1 && methodId == -1) {
                    try {
                        String name = String.valueOf(args.get(0));
                        String meth = String.valueOf(args.get(1));
                        RnModuleConfigGenerator.ModuleSpec named = mSpecsByName.get(name);
                        if (named == null) {
                            // Cxx turbo modules resolve synchronously here.
                            reply = JsonCodec.encode(syntheticTurboCallSync(name, meth, args));
                        } else if ("getConstants".equals(meth)
                                && !named.methodTable.containsKey("getConstants")) {
                            reply = JsonCodec.encode(resolveTurboConstants(named));
                        } else {
                            List<Object> sub = new ArrayList<>(args.subList(2, args.size()));
                            Method m = named.methodTable.get(meth);
                            int mIdx = named.methodNames.indexOf(meth);
                            if (m != null && named.promiseMethodIds.contains(mIdx)) {
                                // Promise-returning methods cannot complete
                                // synchronously - tell the driver to re-route
                                // through the async name dispatch.
                                Map<String, Object> marker = new java.util.LinkedHashMap<>();
                                marker.put("__rnAsync", Boolean.TRUE);
                                reply = JsonCodec.encode(marker);
                            } else if (m != null) {
                                Object[] javaArgs = convertArguments(named, m, sub);
                                reply = JsonCodec.encode(m.invoke(named.moduleInstance, javaArgs));
                            } else {
                                reply = "null";
                            }
                        }
                    } catch (Throwable tName) {
                        System.err.println("[-] [RnBridge] sync name-based call failed: " + tName);
                        reply = "null";
                    }
                } else {
                RnModuleConfigGenerator.ModuleSpec spec = mSpecsById.get(moduleId);
                if (spec != null && requiresUiThread(spec.name)) {
                    // Keep the ShadowNodeRegistry single-thread invariant: sync
                    // hooks (nativeCallSyncHook) also land on the main looper.
                    final Object[] holder = new Object[1];
                    final java.util.concurrent.CountDownLatch latch =
                            new java.util.concurrent.CountDownLatch(1);
                    final int fModuleId = moduleId, fMethodId = methodId;
                    scheduleOnUiThread(() -> {
                        try {
                            holder[0] = invokeSyncOnUiThread(fModuleId, fMethodId, args);
                        } finally {
                            latch.countDown();
                        }
                    });
                    boolean got = latch.await(15, java.util.concurrent.TimeUnit.SECONDS);
                    if (!got) {
                        System.out.println("[-] [RnBridge] SYNC latch TIMEOUT for mod " + fModuleId
                                + ":" + fMethodId + " (main looper busy?)");
                    }
                    reply = JsonCodec.encode(holder[0]);
                    // NOTE: no early return here - the reply MUST fall through
                    // to the common write below, otherwise the VM waits forever
                    // for its sync-hook reply and the whole render pipeline
                    // stalls (this was the RNCSafeAreaProvider freeze).
                }
                if (spec != null && methodId >= 0 && methodId < spec.methodNames.size()) {
                    Method m = spec.methodTable.get(spec.methodNames.get(methodId));
                    if (m != null) {
                        Object[] javaArgs = convertArguments(spec, m, args);
                        Object result = m.invoke(spec.moduleInstance, javaArgs);
                        reply = JsonCodec.encode(result);
                    }
                }
                }
            }
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Sync call failed: " + t.getMessage());
            reply = "null";
        }
        try {
            synchronized (mInboxLock) {
                // "@@R " prefix: the VM's __rnSyncCall reads stdin lines until
                // it sees a reply marker; unrelated inbox lines written
                // concurrently by Java are parked by the VM instead of being
                // consumed as the reply (protocol race fix).
                mInbox.write("@@R " + (reply == null ? "null" : reply));
                mInbox.write("\n");
                mInbox.flush();
            }
            System.out.println("[*] [RnBridge] @@SYNC reply: " + (reply == null ? "null"
                    : reply.substring(0, Math.min(120, reply.length()))));
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Sync reply failed: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Object invokeSyncOnUiThread(int moduleId, int methodId, List<Object> args) {
        try {
            RnModuleConfigGenerator.ModuleSpec spec = mSpecsById.get(moduleId);
            if (spec == null || methodId < 0 || methodId >= spec.methodNames.size()) return null;
            Method m = spec.methodTable.get(spec.methodNames.get(methodId));
            if (m == null) return null;
            Object[] javaArgs = convertArguments(spec, m, args);
            return m.invoke(spec.moduleInstance, javaArgs);
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] sync-on-UI failed: " + t);
            return null;
        }
    }

    // ------- universal Timing interception -------

    /**
     * The real TimingModule depends on Choreographer (host display pipeline),
     * which does not exist headless. Own the timer lifecycle here so ANY
     * React Native app gets working setTimeout/setInterval.
     */
    @SuppressWarnings("unchecked")
    private boolean handleTimingCall(String methodName, List<Object> rawArgs) {
        try {
            switch (methodName) {
                case "createTimer": {
                    // [callbackId, duration, jsSchedulingTime, repeats]
                    int callbackId = toIntId(rawArgs.size() > 0 ? rawArgs.get(0) : null);
                    long duration = (long) toNumber(rawArgs.size() > 1 ? rawArgs.get(1) : 0);
                    boolean repeats = rawArgs.size() > 3 && toNumber(rawArgs.get(3)) != 0;
                    if (duration < 1) duration = 1;
                    int timerId = mNextTimerId.incrementAndGet();
                    ScheduledFuture<?> future;
                    if (repeats) {
                        future = mTimerExecutor.scheduleAtFixedRate(
                                () -> fireTimer(timerId, callbackId),
                                duration, duration, TimeUnit.MILLISECONDS);
                    } else {
                        future = mTimerExecutor.schedule(
                                () -> fireTimer(timerId, callbackId),
                                duration, TimeUnit.MILLISECONDS);
                    }
                    mTimers.put(timerId, future);
                    // Return the timerId via the createTimerFinished callback:
                    // JS createTimer has NO callback; the id is only used by deleteTimer.
                    return true;
                }
                case "deleteTimer": {
                    int timerId = toIntId(rawArgs.size() > 0 ? rawArgs.get(0) : null);
                    ScheduledFuture<?> f = mTimers.remove(timerId);
                    if (f != null) f.cancel(false);
                    return true;
                }
                default:
                    return false;
            }
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] Timing interception failed: " + t.getMessage());
            return false;
        }
    }

    private void fireTimer(int timerId, int callbackId) {
        try {
            List<Object> ids = new ArrayList<>();
            List<Object> wrapper = new ArrayList<>();
            wrapper.add((long) callbackId);
            ids.add(wrapper);
            callJsFunction("JSTimers", "callTimers", ids);
        } catch (Throwable t) {
            System.err.println("[-] [RnBridge] fireTimer failed: " + t.getMessage());
        }
    }
}
