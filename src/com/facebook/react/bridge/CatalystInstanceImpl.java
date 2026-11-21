package com.facebook.react.bridge;

import com.facebook.react.bridge.queue.MessageQueueThread;
import com.facebook.react.bridge.queue.QueueThreadExceptionHandler;
import com.facebook.react.bridge.queue.ReactQueueConfiguration;
import com.facebook.react.bridge.queue.ReactQueueConfigurationImpl;
import com.facebook.react.bridge.queue.ReactQueueConfigurationSpec;
import com.facebook.react.internal.turbomodule.core.interfaces.TurboModuleRegistry;
import com.facebook.react.turbomodule.core.interfaces.CallInvokerHolder;
import com.facebook.react.turbomodule.core.interfaces.NativeMethodCallInvokerHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure Java in-process CatalystInstance implementation.
 * Eliminates the C++ JNI wall (libreactnativejni.so) and allows standard OpenJDK to run
 * React Native components, background tasks, and native modules directly.
 */
public class CatalystInstanceImpl implements CatalystInstance {
    private static final AtomicInteger sNextInstanceId = new AtomicInteger(1);
    private static volatile CatalystInstanceImpl sLiveInstance;

    /** The most recently created catalyst instance (for UI-queue routing). */
    public static CatalystInstanceImpl live() {
        return sLiveInstance;
    }

    /**
     * Run a task on the catalyst's UI queue thread - the SAME thread the
     * app's own ReactInstanceManager flow uses for every UIManager/shadow-tree
     * operation, which arms ShadowNodeRegistry's single-thread asserter.
     */
    public void runOnUiQueue(Runnable task) {
        try {
            if (mReactQueueConfiguration != null && mReactQueueConfiguration.getUIQueueThread() != null) {
                mReactQueueConfiguration.getUIQueueThread().runOnQueue(task);
                return;
            }
        } catch (Throwable ignored) {}
        new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
    }

    private final ReactQueueConfigurationImpl mReactQueueConfiguration;
    private final NativeModuleRegistry mNativeModuleRegistry;
    private final JSBundleLoader mJSBundleLoader;
    private final JSExceptionHandler mJSExceptionHandler;
    private final JavaScriptModuleRegistry mJSModuleRegistry = new JavaScriptModuleRegistry();
    private final List<NotThreadSafeBridgeIdleDebugListener> mBridgeIdleListeners = new CopyOnWriteArrayList<>();

    private volatile boolean mDestroyed = false;
    private volatile boolean mInitialized = false;
    private volatile boolean mJSBundleHasLoaded = false;
    private volatile boolean mAcceptCalls = false;
    private volatile boolean mRunApplicationSeen = false;
    private String mSourceURL = "assets://index.android.bundle";
    private UIManager mFabricUIManager = null;
    private TurboModuleRegistry mTurboModuleRegistry = null;

    public static class PendingJSCall {
        public final String mModule;
        public final String mMethod;
        public final NativeArray mArguments;

        public PendingJSCall(String module, String method, NativeArray arguments) {
            this.mModule = module;
            this.mMethod = method;
            this.mArguments = arguments;
        }

        public void call(CatalystInstanceImpl instance) {
            if (instance != null) {
                instance.callFunction(mModule, mMethod, mArguments);
            }
        }
    }

    public static class Builder {
        private ReactQueueConfigurationSpec mReactQueueConfigurationSpec;
        private JavaScriptExecutor mJSExecutor;
        private NativeModuleRegistry mRegistry;
        private JSBundleLoader mJSBundleLoader;
        private JSExceptionHandler mJSExceptionHandler;
        private ReactInstanceManagerInspectorTarget mInspectorTarget;

        public Builder setReactQueueConfigurationSpec(ReactQueueConfigurationSpec spec) {
            this.mReactQueueConfigurationSpec = spec;
            return this;
        }

        public Builder setJSExecutor(JavaScriptExecutor jsExecutor) {
            this.mJSExecutor = jsExecutor;
            return this;
        }

        public Builder setRegistry(NativeModuleRegistry registry) {
            this.mRegistry = registry;
            return this;
        }

        public Builder setJSBundleLoader(JSBundleLoader jsBundleLoader) {
            this.mJSBundleLoader = jsBundleLoader;
            return this;
        }

        public Builder setJSExceptionHandler(JSExceptionHandler handler) {
            this.mJSExceptionHandler = handler;
            return this;
        }

        public Builder setInspectorTarget(ReactInstanceManagerInspectorTarget target) {
            this.mInspectorTarget = target;
            return this;
        }

        public CatalystInstanceImpl build() {
            return new CatalystInstanceImpl(
                    mReactQueueConfigurationSpec,
                    mJSExecutor,
                    mRegistry,
                    mJSBundleLoader,
                    mJSExceptionHandler,
                    mInspectorTarget,
                    0
            );
        }
    }

    public CatalystInstanceImpl(
            ReactQueueConfigurationSpec queueSpec,
            JavaScriptExecutor jsExecutor,
            NativeModuleRegistry registry,
            JSBundleLoader bundleLoader,
            JSExceptionHandler exceptionHandler,
            ReactInstanceManagerInspectorTarget inspectorTarget,
            int flags
    ) {
        System.out.println("[*] [CatalystInstanceImpl] Booting in-process Pure-Java React Native Bridge (Zero JNI)...");
        this.mNativeModuleRegistry = registry;
        this.mJSBundleLoader = bundleLoader;
        this.mJSExceptionHandler = exceptionHandler;

        ReactQueueConfigurationSpec spec = queueSpec != null ? queueSpec : ReactQueueConfigurationSpec.createDefault();
        this.mReactQueueConfiguration = ReactQueueConfigurationImpl.create(spec, e -> {
            System.err.println("[-] [ReactQueue] Error on React thread: " + e.getMessage());
            if (mJSExceptionHandler != null) {
                mJSExceptionHandler.handleException(e);
            }
        });

        System.out.println("[+] [CatalystInstanceImpl] React queues initialized (UI, JS, NativeModules threads active).");
        sLiveInstance = this;
    }

    @Override
    public void initialize() {
        if (mInitialized) return;
        mInitialized = true;
        if (mNativeModuleRegistry != null) {
            System.out.println("[*] [CatalystInstanceImpl] Initializing NativeModuleRegistry (" + mNativeModuleRegistry.getAllModules().size() + " modules)...");
            mNativeModuleRegistry.notifyJSInstanceInitialized();
            System.out.println("[+] [CatalystInstanceImpl] NativeModuleRegistry initialized successfully!");
        }
    }

    @Override
    public void runJSBundle() {
        System.out.println("[*] [CatalystInstanceImpl] Loading React Native JS Bundle...");
        // Boot the UNIVERSAL Hermes stdio bridge with the app's real native modules.
        // The patched Hermes VM executes the app's genuine index.android.bundle.
        try {
            File runnerDir = new File(System.getProperty("headless.runner.dir",
                    System.getProperty("user.dir", ".")));
            File apkFile = new File(System.getProperty("headless.runner.apk", ""));
            if (!apkFile.exists()) {
                apkFile = new File(runnerDir, "base.apk");
            }
            File bundleFile = org.headless.runner.rn.UniversalReactBridge.locateBundle(apkFile, runnerDir);
            if (bundleFile != null && mNativeModuleRegistry != null) {
                Object reactCtx = null;
                try {
                    reactCtx = mNativeModuleRegistry.getClass()
                            .getMethod("getReactApplicationContext").invoke(mNativeModuleRegistry);
                } catch (Throwable ignored) {}
                if (reactCtx == null) {
                    // R8 may strip the getter - read the field directly.
                    for (java.lang.reflect.Field f : mNativeModuleRegistry.getClass().getDeclaredFields()) {
                        if (f.getType().getName().equals("com.facebook.react.bridge.ReactApplicationContext")) {
                            try {
                                f.setAccessible(true);
                                reactCtx = f.get(mNativeModuleRegistry);
                            } catch (Throwable ignored) {}
                            break;
                        }
                    }
                }
                System.out.println("[*] [CatalystInstanceImpl] registry context: "
                        + (reactCtx == null ? "null" : reactCtx.getClass().getName()));
                org.headless.runner.rn.UniversalReactBridge.get()
                        .start(mNativeModuleRegistry.getAllModules(), runnerDir, bundleFile, reactCtx);
            }
        } catch (Throwable t) {
            System.out.println("[-] [CatalystInstanceImpl] Universal RN bridge start failed: " + t.getMessage());
        }
        if (mJSBundleLoader != null) {
            try {
                mJSBundleLoader.loadScript(this);
            } catch (Throwable t) {
                System.out.println("[-] [CatalystInstanceImpl] Notice loading JS bundle via loader: " + t.getMessage());
            }
        }
        this.mJSBundleHasLoaded = true;
        this.mAcceptCalls = true;
        System.out.println("[+] [CatalystInstanceImpl] React Native JS environment ready for execution!");
        scheduleRunApplicationKicker();
    }

    /**
     * UNIVERSAL BOOT KICKER. On a real device ReactInstanceManager calls
     * AppRegistry.runApplication once the bundle finishes loading. That path
     * is host-dependent (UI-thread attach callbacks); when it has not fired
     * within a grace period after the bundle executed, the runner dispatches
     * it itself from the app's own ReactRootView state - exactly what the
     * device does, just driven by the environment instead of the host
     * activity pipeline.
     */
    private void scheduleRunApplicationKicker() {
        try {
            // Run on the SAME single-thread executor the UIManager dispatches
            // use, so addRootView and every shadow-tree access share one thread.
            org.headless.runner.rn.UniversalReactBridge.get().scheduleOnUiThread(() -> {
                    if (mRunApplicationSeen || mDestroyed) return;
                    try {
                        android.app.Activity top =
                                org.headless.runner.core.ActivityStack.getInstance().getTopActivity();
                        if (top == null) return;
                        android.view.View root = findReactRootView(top.getWindow().getDecorView());
                        if (root == null) {
                            System.out.println("[*] [CatalystInstanceImpl] Kicker: no ReactRootView mounted - skipping runApplication.");
                            return;
                        }
                        if (root.getId() <= 0) root.setId(1);
                        // Register the root with the app's real UIManagerModule -
                        // same call ReactRootView makes on a device. Returns the
                        // actual root tag the shadow tree now knows.
                        int rootTag = root.getId();
                        try {
                            Object uim = org.headless.runner.rn.UniversalReactBridge.get()
                                    .getNativeModuleInstance("UIManager");
                            if (uim != null) {
                                java.lang.reflect.Method addRootView = null;
                                for (java.lang.reflect.Method m : uim.getClass().getMethods()) {
                                    if ("addRootView".equals(m.getName())
                                            && m.getParameterCount() >= 1
                                            && android.view.View.class.isAssignableFrom(m.getParameterTypes()[0])) {
                                        addRootView = m;
                                        break;
                                    }
                                }
                                if (addRootView != null) {
                                    Object tagObj = addRootView.getParameterCount() >= 2
                                            ? addRootView.invoke(uim, root, null)
                                            : addRootView.invoke(uim, root);
                                    if (tagObj instanceof Number) {
                                        rootTag = ((Number) tagObj).intValue();
                                        root.setId(rootTag);
                                    }
                                }
                            }
                        } catch (Throwable tUim) {
                            System.out.println("[*] [CatalystInstanceImpl] addRootView via UIManagerModule skipped: "
                                    + tUim.getClass().getSimpleName() + ": " + tUim.getMessage());
                        }
                        String jsModuleName = reflectStringField(root, "mJSModuleName");
                        if (jsModuleName == null || jsModuleName.isEmpty()) {
                            jsModuleName = reflectStringField(root, "jsModuleName");
                        }
                        if (jsModuleName == null || jsModuleName.isEmpty()) {
                            jsModuleName = top.getLocalClassName() != null
                                    ? top.getLocalClassName() : top.getClass().getSimpleName();
                        }
                        Object initialProps = reflectAnyField(root, "mInitialProperties");
                        if (initialProps == null) {
                            initialProps = reflectAnyField(root, "mAppProperties");
                        }
                        Map<String, Object> appParams = new java.util.LinkedHashMap<>();
                        appParams.put("rootTag", rootTag);
                        Object propsMap = bundleToMap(initialProps);
                        if (propsMap != null) {
                            appParams.put("initialProps", propsMap);
                        }
                        mRunApplicationSeen = true;
                        org.headless.runner.rn.UniversalReactBridge.get().callJsFunction(
                                "AppRegistry", "runApplication",
                                java.util.Arrays.asList((Object) jsModuleName, (Object) appParams));
                        System.out.println("[+] [CatalystInstanceImpl] AppRegistry.runApplication dispatched by runner kicker"
                                + " (module=" + jsModuleName + ", rootTag=" + rootTag + ")");
                } catch (Throwable t) {
                    System.out.println("[-] [CatalystInstanceImpl] runApplication kicker failed: " + t);
                }
            }, 2500);
        } catch (Throwable t) {
            System.out.println("[-] [CatalystInstanceImpl] kicker scheduling failed: " + t.getMessage());
        }
    }

    private static android.view.View findReactRootView(android.view.View v) {
        if (v == null) return null;
        String n = v.getClass().getName();
        if (n.contains("ReactRootView")) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View found = findReactRootView(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String reflectStringField(Object target, String name) {
        try {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(target);
                    return v != null ? String.valueOf(v) : null;
                } catch (NoSuchFieldException ignored) {}
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object reflectAnyField(Object target, String name) {
        try {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {}
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Map<String, Object> bundleToMap(Object bundle) {
        if (bundle == null) return null;
        try {
            android.os.Bundle b = (android.os.Bundle) bundle;
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (String key : b.keySet()) {
                out.put(key, b.get(key));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void callFunction(String module, String method, NativeArray arguments) {
        // Route EVERY JS-module invocation through the universal bridge when live.
        // This is how AppRegistry.runApplication, RCTDeviceEventEmitter.emit,
        // JSTimers callbacks and all other JS entry points reach the real bundle.
        org.headless.runner.rn.UniversalReactBridge bridge = org.headless.runner.rn.UniversalReactBridge.get();
        if (bridge.isRunning()) {
            List<Object> argsList = new ArrayList<>();
            if (arguments != null) {
                try {
                    argsList = arguments.toArrayList();
                } catch (Throwable t) {
                    System.err.println("[-] [CatalystInstanceImpl] arg serialization failed: " + t.getMessage());
                }
            }
            bridge.callJsFunction(module, method, argsList);
            if ("AppRegistry".equals(module)) {
                if ("runApplication".equals(method)) {
                    mRunApplicationSeen = true;
                    System.out.println("[+] [CatalystInstanceImpl] AppRegistry.runApplication dispatched to REAL Hermes bundle!");
                } else if ("startHeadlessTask".equals(method)) {
                    System.out.println("[+] [CatalystInstanceImpl] AppRegistry.startHeadlessTask dispatched to REAL Hermes bundle!");
                }
            }
            return;
        }
        System.out.println("[+] [CatalystInstanceImpl] JS Call: " + module + "." + method + "()");
        if ("AppRegistry".equals(module)) {
            if ("runApplication".equals(method)) {
                System.out.println("[+] [CatalystInstanceImpl] AppRegistry.runApplication dispatched (no live bridge; nothing to execute).");
            } else if ("startHeadlessTask".equals(method)) {
                System.out.println("[+] [CatalystInstanceImpl] AppRegistry.startHeadlessTask dispatched (no live bridge).");
            }
        }
    }

    public void callFunction(PendingJSCall pending) {
        if (pending != null) {
            callFunction(pending.mModule, pending.mMethod, pending.mArguments);
        }
    }

    @Override
    public void invokeCallback(int callbackId, NativeArrayInterface arguments) {
        System.out.println("[+] [CatalystInstanceImpl] invokeCallback: id=" + callbackId);
    }

    @Override
    public JavaScriptModule getJSModule(Class jsInterfaceClass) {
        return mJSModuleRegistry.getJavaScriptModule(this, jsInterfaceClass);
    }

    @Override
    public NativeModule getNativeModule(Class nativeModuleInterface) {
        return mNativeModuleRegistry != null ? mNativeModuleRegistry.getModule(nativeModuleInterface) : null;
    }

    public NativeModule getNativeModule(String moduleName) {
        return mNativeModuleRegistry != null ? mNativeModuleRegistry.getModule(moduleName) : null;
    }

    @Override
    public Collection<NativeModule> getNativeModules() {
        return mNativeModuleRegistry != null ? mNativeModuleRegistry.getAllModules() : Collections.emptyList();
    }

    @Override
    public boolean hasNativeModule(Class nativeModuleInterface) {
        return mNativeModuleRegistry != null && mNativeModuleRegistry.hasModule(nativeModuleInterface);
    }

    @Override
    public void extendNativeModules(NativeModuleRegistry modules) {
        if (mNativeModuleRegistry != null && modules != null) {
            mNativeModuleRegistry.registerModules(modules);
        }
    }

    @Override
    public boolean hasRunJSBundle() {
        return mJSBundleHasLoaded;
    }

    @Override
    public String getSourceURL() {
        return mSourceURL;
    }

    @Override
    public void loadScriptFromAssets(android.content.res.AssetManager assetManager, String assetURL, boolean loadSynchronously) {
        this.mSourceURL = assetURL;
        System.out.println("[+] [CatalystInstanceImpl] loadScriptFromAssets: " + assetURL);
    }

    @Override
    public void loadScriptFromFile(String fileName, String sourceURL, boolean loadSynchronously) {
        this.mSourceURL = sourceURL;
        System.out.println("[+] [CatalystInstanceImpl] loadScriptFromFile: " + fileName);
    }

    @Override
    public void loadSplitBundleFromFile(String fileName, String sourceURL) {
        this.mSourceURL = sourceURL;
        System.out.println("[+] [CatalystInstanceImpl] loadSplitBundleFromFile: " + fileName);
    }

    @Override
    public void setSourceURLs(String deviceURL, String remoteURL) {
        this.mSourceURL = deviceURL;
    }

    @Override
    public void setGlobalVariable(String propName, String jsonValue) {
        System.out.println("[+] [CatalystInstanceImpl] setGlobalVariable: " + propName);
    }

    @Override
    public void registerSegment(int segmentId, String path) {
        System.out.println("[+] [CatalystInstanceImpl] registerSegment: id=" + segmentId + ", path=" + path);
    }

    @Override
    public ReactQueueConfiguration getReactQueueConfiguration() {
        return mReactQueueConfiguration;
    }

    @Override
    public JavaScriptContextHolder getJavaScriptContextHolder() {
        return new JavaScriptContextHolder(10001L);
    }

    @Override
    public CallInvokerHolder getJSCallInvokerHolder() {
        // Pure-Java no-op holder: app-side Kotlin (expo RuntimeContext.installJSIContext)
        // casts this to CallInvokerHolderImpl before installing JSI bindings. Returning
        // null made that cast NPE and dropped expo's native-module proxy from the JS
        // registry ("EXNativeModulesProxy ... not exported" -> JS init chain aborted).
        try {
            return (CallInvokerHolder) Class
                    .forName("com.facebook.react.turbomodule.core.CallInvokerHolderImpl")
                    .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public NativeMethodCallInvokerHolder getNativeMethodCallInvokerHolder() {
        try {
            return (NativeMethodCallInvokerHolder) Class
                    .forName("com.facebook.react.turbomodule.core.NativeMethodCallInvokerHolderImpl")
                    .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public RuntimeExecutor getRuntimeExecutor() {
        return null;
    }

    @Override
    public RuntimeScheduler getRuntimeScheduler() {
        return null;
    }

    @Override
    public UIManager getFabricUIManager() {
        return mFabricUIManager;
    }

    @Override
    public void setFabricUIManager(UIManager uiManager) {
        this.mFabricUIManager = uiManager;
    }

    @Override
    public void setTurboModuleRegistry(TurboModuleRegistry turboModuleRegistry) {
        this.mTurboModuleRegistry = turboModuleRegistry;
    }

    @Override
    public void addBridgeIdleDebugListener(NotThreadSafeBridgeIdleDebugListener listener) {
        if (listener != null) mBridgeIdleListeners.add(listener);
    }

    @Override
    public void removeBridgeIdleDebugListener(NotThreadSafeBridgeIdleDebugListener listener) {
        if (listener != null) mBridgeIdleListeners.remove(listener);
    }

    @Override
    public void handleMemoryPressure(int level) {
    }

    @Override
    public void destroy() {
        if (mDestroyed) return;
        mDestroyed = true;
        if (mNativeModuleRegistry != null) {
            mNativeModuleRegistry.notifyJSInstanceDestroy();
        }
        mReactQueueConfiguration.destroy();
        System.out.println("[+] [CatalystInstanceImpl] In-process React instance destroyed.");
    }

    @Override
    public boolean isDestroyed() {
        return mDestroyed;
    }
}
