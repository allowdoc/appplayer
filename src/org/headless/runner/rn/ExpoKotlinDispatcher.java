package org.headless.runner.rn;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNIVERSAL expo Kotlin module bridge (pure reflection, zero per-app code).
 *
 * Expo apps (SDK 50+) register their Kotlin modules through
 * expo.modules.kotlin.KotlinInteropModuleRegistry over the app's own generated
 * expo.modules.ExpoModulesPackageList — NOT through ReactPackage.createNativeModules.
 * The classic registry reflection therefore only ever sees the adapter shells
 * (NativeModulesProxy/ExpoBridge) and the JS side fails with
 * "Cannot find native module 'ExpoAsset'" / "No native ExponentConstants module".
 *
 * This class reuses the app's OWN expo runtime classes to:
 *   1. build the same KotlinInteropModuleRegistry the app would build,
 *   2. export per-module metadata (async/sync methods + constants) to the JS
 *      driver in the "expoModules" config section, and
 *   3. dispatch requireNativeModule(...) calls into ModuleHolder.call/callSync.
 *
 * Everything expo-specific is reached by reflection so this compiles in the
 * app-independent pass and works with any expo version that ships those classes.
 */
public final class ExpoKotlinDispatcher {

    private Object mKimr;                    // expo.modules.kotlin.KotlinInteropModuleRegistry
    private Object mModuleRegistry;          // expo.modules.kotlin.ModuleRegistry (Iterable<ModuleHolder>)
    private final Map<String, Object> mHolders = new LinkedHashMap<>();  // JS name -> ModuleHolder
    private final Map<String, Map<String, Object>> mMetadata = new LinkedHashMap<>();
    private Object mReactContext;            // ReactApplicationContext (weak usage only)
    private final List<String> mLoadedFonts = new ArrayList<>();

    private ExpoKotlinDispatcher() {
    }

    /**
     * Build the expo Kotlin module registry from the app's own generated
     * package list. Returns null when this APK is not an expo app or the
     * registry cannot be constructed (graceful degradation).
     */
    public static ExpoKotlinDispatcher create(Object reactContext) {
        try {
            Class<?> plClass = Class.forName("expo.modules.ExpoModulesPackageList");
            Object provider = plClass.getDeclaredConstructor().newInstance();
            List<?> moduleClasses = (List<?>) plClass.getMethod("getModulesList").invoke(provider);
            if (moduleClasses == null || moduleClasses.isEmpty()) {
                System.out.println("[*] [RnBridge] expo package list present but empty - skipping expo integration.");
                return null;
            }

            // expo SDK 50+: KotlinInteropModuleRegistry(modulesProvider,
            // core.ModuleRegistry(singletonModules, moduleClasses), reactContextRef).
            // The Kotlin-side ModuleRegistry (Iterable<ModuleHolder>) is created
            // inside and fetched back through access$getRegistry.
            Class<?> coreRegClass = Class.forName("expo.modules.core.ModuleRegistry");
            Object coreRegistry = coreRegClass
                    .getConstructor(java.util.Collection.class, java.util.Collection.class)
                    .newInstance(Collections.emptyList(), Collections.emptyList());
            try {
                coreRegClass.getMethod("ensureIsInitialized").invoke(coreRegistry);
            } catch (Throwable ignoredInit) {}

            Class<?> provIf = Class.forName("expo.modules.kotlin.ModulesProvider");
            Class<?> kimrClass = Class.forName("expo.modules.kotlin.KotlinInteropModuleRegistry");
            Object kimr = kimrClass
                    .getConstructor(provIf, coreRegClass, java.lang.ref.WeakReference.class)
                    .newInstance(provider, coreRegistry, new java.lang.ref.WeakReference<>(reactContext));

            Class<?> modRegClass = Class.forName("expo.modules.kotlin.ModuleRegistry");
            Object moduleRegistry = kimrClass
                    .getMethod("access$getRegistry", kimrClass)
                    .invoke(null, kimr);
            if (moduleRegistry == null) {
                throw new IllegalStateException("expo Kotlin registry not reachable");
            }

            ExpoKotlinDispatcher d = new ExpoKotlinDispatcher();
            d.mKimr = kimr;
            d.mModuleRegistry = moduleRegistry;
            d.mReactContext = reactContext;
            d.scanBundledFonts();

            // Collect holders + metadata (ModuleRegistry is Iterable<ModuleHolder>)
            Class<?> holderClass = Class.forName("expo.modules.kotlin.ModuleHolder");
            Method hGetName = holderClass.getMethod("getName");
            Method hGetDef = holderClass.getMethod("getDefinition");
            Class<?> defClass = Class.forName("expo.modules.kotlin.modules.ModuleDefinitionData");
            Method dAsync = defClass.getMethod("getAsyncFunctions");
            Method dSync = defClass.getMethod("getSyncFunctions");
            Method dConst = defClass.getMethod("getConstants");

            for (Object holder : (Iterable<?>) moduleRegistry) {
                if (holder == null) continue;
                String name = String.valueOf(hGetName.invoke(holder));
                if (name == null || name.isEmpty() || "null".equals(name)) continue;
                d.mHolders.put(name, holder);

                Map<String, Object> meta = new LinkedHashMap<>();
                try {
                    Object def = hGetDef.invoke(holder);
                    if (def != null) {
                        Map<?, ?> async = (Map<?, ?>) dAsync.invoke(def);
                        meta.put("async", async == null
                                ? Collections.emptyList() : new ArrayList<Object>(async.keySet()));
                        Map<?, ?> sync = (Map<?, ?>) dSync.invoke(def);
                        meta.put("sync", sync == null
                                ? Collections.emptyList() : new ArrayList<Object>(sync.keySet()));
                        Map<?, ?> consts = (Map<?, ?>) dConst.invoke(def);
                        if (consts != null && !consts.isEmpty()) {
                            meta.put("constants", consts);
                        }
                    }
                } catch (Throwable ignoredMeta) {
                    // definition metadata optional
                }
                d.mMetadata.put(name, meta);
            }

            System.out.println("[+] [RnBridge] Expo Kotlin modules integrated: "
                    + d.mHolders.size() + " -> " + d.mHolders.keySet());
            return d;
        } catch (ClassNotFoundException cnfe) {
            // Not an expo app - perfectly normal.
            return null;
        } catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            System.out.println("[*] [RnBridge] expo Kotlin integration unavailable: "
                    + t.getClass().getSimpleName() + " caused by " + cause.getClass().getName()
                    + ": " + cause.getMessage());
            StackTraceElement[] st = cause.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 6); i++) {
                System.out.println("      at " + st[i]);
            }
            return null;
        }
    }

    /** Fire expo's onCreate lifecycle so modules finish registration. */
    public void emitOnCreate() {
        try {
            mKimr.getClass().getMethod("emitOnCreate").invoke(mKimr);
        } catch (Throwable t) {
            System.out.println("[*] [RnBridge] expo emitOnCreate skipped: " + t.getMessage());
        }
    }

    /** Metadata for the JS driver: {name -> {async:[], sync:[], constants:{}}} */
    public Map<String, Map<String, Object>> exportModuleMetadata() {
        return mMetadata;
    }

    public boolean hasModules() {
        return !mHolders.isEmpty();
    }

    /**
     * RN-callable async dispatch (registered as pseudo module "ExpoKotlinDispatcher").
     * JS calls NativeModules.ExpoKotlinDispatcher.callMethod(module, method, args)
     * and the promise resolves through the expoResolve/expoReject inbox channel.
     */
    public void callMethod(String moduleName, String methodName, List<Object> args,
                           com.facebook.react.bridge.Promise promise) {
        System.out.println("[*] [RnBridge] expo callMethod: " + moduleName + "." + methodName
                + " args=" + (args == null ? "[]" : args.toString().substring(0,
                        Math.min(150, args.toString().length()))));
        // ---- Universal runner-implemented expo functions -------------------
        // These Kotlin functions are Coroutine AsyncFunctions the reflection
        // dispatch path cannot invoke; the runner implements their EXACT
        // native contracts instead of rejecting (which would hard-block the
        // app's font gate / asset pipeline).
        if ("ExpoAsset".equals(moduleName) && "downloadAsync".equals(methodName)) {
            try {
                resolveJs(promise, assetDownload(args));
            } catch (Throwable tAsset) {
                System.out.println("[-] [RnBridge] expo-asset download failed: " + tAsset.getMessage());
                rejectJs3(promise, "EUNABLETORESOLVE", String.valueOf(tAsset.getMessage()), null);
            }
            return;
        }
        if ("ExpoFontLoader".equals(moduleName) && "loadAsync".equals(methodName)) {
            try {
                fontLoad(args);
                resolveJs(promise, null);
            } catch (Throwable tFont) {
                System.out.println("[-] [RnBridge] expo-font load failed: " + tFont.getMessage());
                rejectJs3(promise, "EFONTLOAD", String.valueOf(tFont.getMessage()), null);
            }
            return;
        }
        try {
            Object holder = mHolders.get(moduleName);
            if (holder == null) {
                // Universal degradation: a missing expo module resolves null
                // instead of rejecting - the app continues exactly like on a
                // device where the capability is absent, instead of freezing.
                System.out.println("[*] [RnBridge] expo module '" + moduleName + "' missing - call "
                        + moduleName + "." + methodName + " degraded to null");
                resolveJs(promise, null);
                return;
            }
            Object[] jArgs = args != null ? args.toArray() : new Object[0];
            // JSON -> React types: expo's converters assert instanceof ReadableMap
            for (int ai = 0; ai < jArgs.length; ai++) {
                jArgs[ai] = toReactTypes(jArgs[ai]);
            }
            Class<?> promiseIf = Class.forName("expo.modules.kotlin.Promise");
            Object expoPromise = java.lang.reflect.Proxy.newProxyInstance(
                    promiseIf.getClassLoader(), new Class<?>[]{promiseIf},
                    (proxy, method, margs) -> {
                        String mn = method.getName();
                        switch (mn) {
                            case "resolve": {
                                Object v = (margs != null && margs.length > 0) ? margs[0] : null;
                                resolveJs(promise, v);
                                return null;
                            }
                            case "reject": {
                                if (margs != null && margs.length == 1) {
                                    Object ce = margs[0];
                                    String code = str(invokeNoArg(ce, "getCode"));
                                    String msg = ce != null ? ce.toString() : "expo error";
                                    rejectJs3(promise, code == null ? "EUNSPECIFIED" : code,
                                            msg, (Throwable) (ce instanceof Throwable ? ce : null));
                                } else if (margs != null && margs.length >= 2) {
                                    rejectJs3(promise, str(margs[0]), str(margs[1]),
                                            margs.length > 2 && margs[2] instanceof Throwable
                                                    ? (Throwable) margs[2] : null);
                                }
                                return null;
                            }
                            default:
                                return null;
                        }
                    });
            // Universal SUSPEND dispatch: expo SDK 50+ ModuleHolder.call() can only
            // run plain AsyncFunctionComponents and THROWS IllegalStateException
            // ("... method in test context") for SuspendFunctionComponent - on a
            // real device those functions are invoked through JSI, not the legacy
            // bridge. Reproduce the device-side invocation protocol here with the
            // app's own classes (works for ANY expo app, zero per-app code):
            //   body = SuspendFunctionComponent.access$getBody$p(component)
            //   args = AnyFunction.convertArgs$default(component, rawArgs, appContext)
            //   result = body(convertedArgs, PromiseImpl(JavaCallback), continuation)
            // The promise settles either synchronously (body returned a value) or
            // when the coroutine completes on a background dispatcher.
            if (dispatchSuspendFunction(holder, methodName, jArgs, expoPromise)) {
                return;
            }
            holder.getClass()
                    .getMethod("call", String.class, Object[].class, promiseIf)
                    .invoke(holder, methodName, jArgs, expoPromise);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable c = ite.getCause() != null ? ite.getCause() : ite;
            String msg = String.valueOf(c.getMessage());
            if (msg.contains("has been rejected") || msg.contains("not found")
                    || c instanceof NoSuchMethodException
                    || c.getClass().getSimpleName().contains("NotFoundException")) {
                // Function missing from the app's own module definition:
                // degrade to null like a device without the capability.
                System.out.println("[*] [RnBridge] expo " + moduleName + "." + methodName
                        + " unavailable - degraded to null (" + msg + ")");
                resolveJs(promise, null);
                return;
            }
            rejectJs3(promise, "EEXCEPTION", msg, c);
        } catch (Throwable t) {
            rejectJs3(promise, "EDISPATCH", String.valueOf(t.getMessage()), t);
        }
    }

    /**
     * Universal expo suspend-function dispatch (replaces the device's JSI path).
     * Returns true when the call was handled as a suspend function.
     *
     * expo SDK 50+ ships suspend (coroutine) async functions as
     * SuspendFunctionComponent, which ModuleHolder.call() refuses to invoke
     * (it only handles AsyncFunctionComponent and throws
     * "Cannot call a X method in test context"). On a real device those calls
     * go through JSI: attachToJSObject() wires the component to a JSI object
     * and the coroutine settles an expo PromiseImpl. The runner reproduces the
     * exact protocol with the app's own classes:
     *   body.invoke(convertArgs(component, args, appContext), PromiseImpl(cb), continuation)
     */
    private boolean dispatchSuspendFunction(Object holder, String methodName, Object[] jArgs,
                                            Object rnShapedPromise) {
        try {
            Class<?> defClass = Class.forName("expo.modules.kotlin.modules.ModuleDefinitionData");
            Method getModule = holder.getClass().getMethod("getModule");
            Object module = getModule.invoke(holder);
            if (module == null) return false;

            Method getDef = holder.getClass().getMethod("getDefinition");
            Object def = getDef.invoke(holder);
            if (def == null) return false;

            Method getAsync = defClass.getMethod("getAsyncFunctions");
            Map<?, ?> async = (Map<?, ?>) getAsync.invoke(def);
            if (async == null) return false;
            Object component = async.get(methodName);
            if (component == null) return false;

            // Only SuspendFunctionComponent needs this path; plain async
            // functions keep the standard ModuleHolder.call route.
            Class<?> sfcClass = Class.forName("expo.modules.kotlin.functions.SuspendFunctionComponent");
            if (!sfcClass.isInstance(component)) return false;

            Object appContext = null;
            try {
                appContext = module.getClass().getMethod("getAppContext").invoke(module);
            } catch (Throwable ignored) {}

            // 1. raw body lambda (suspend (args, promise) -> Any?)
            Method getBody = null;
            for (Method m : sfcClass.getDeclaredMethods()) {
                if (m.getName().startsWith("access$getBody$p") && m.getParameterCount() == 1) {
                    getBody = m;
                    break;
                }
            }
            if (getBody == null) return false;
            getBody.setAccessible(true);
            Object body = getBody.invoke(null, component);   // kotlin.jvm.functions.Function3

            // 2. expo's own argument conversion (JSI parity)
            Object[] converted = jArgs;
            try {
                Class<?> anyFnClass = Class.forName("expo.modules.kotlin.functions.AnyFunction");
                Method convert = null;
                for (Method m : anyFnClass.getDeclaredMethods()) {
                    if (m.getName().equals("convertArgs$default")
                            && m.getParameterCount() == 6) {
                        convert = m;
                        break;
                    }
                }
                if (convert != null) {
                    convert.setAccessible(true);
                    Object res = convert.invoke(null, component, jArgs, appContext,
                            Boolean.FALSE, Integer.valueOf(4), null);
                    if (res instanceof Object[]) converted = (Object[]) res;
                }
            } catch (Throwable ignoredConv) {}

            // 3. invoke the suspend body with the RN-shaped expo Promise proxy
            //    (the body signature is (args, Promise) -> Any?; PromiseImpl is
            //    a JNI-backed class that cannot be constructed headless, but the
            //    body only ever uses the expo.modules.kotlin.Promise interface)
            Object result = null;
            final java.util.concurrent.atomic.AtomicBoolean settled =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            try {
                Class<?> contIf = Class.forName("kotlin.coroutines.Continuation");
                final Object rnPromise = rnShapedPromise;
                Object continuation = java.lang.reflect.Proxy.newProxyInstance(
                        contIf.getClassLoader(), new Class<?>[]{contIf},
                        (proxy, method, margs) -> {
                            if ("getContext".equals(method.getName())) {
                                try {
                                    java.lang.reflect.Field inst = Class.forName("kotlin.coroutines.EmptyCoroutineContext")
                                            .getField("INSTANCE");
                                    try { inst.setAccessible(true); } catch (Throwable ignoredAcc) {}
                                    return inst.get(null);
                                } catch (Throwable tCtx) {
                                    return null;
                                }
                            }
                            if ("resumeWith".equals(method.getName())) {
                                Object boxed = (margs != null && margs.length > 0) ? margs[0] : null;
                                settleFromResult(boxed, rnPromise, settled);
                                return null;
                            }
                            return null;
                        });
                // Duck-typed 3-arg invoke: R8 rewrites suspend lambdas so the
                // body may no longer implement kotlin.jvm.functions.Function3,
                // but it always keeps an invoke(Object, Object, Object) method.
                if (SUSPEND_SHAPE_DEBUG) {
                    StringBuilder sigs = new StringBuilder();
                    for (Method m : body.getClass().getMethods()) {
                        if ("invoke".equals(m.getName())) {
                            sigs.append(m.toString()).append(" | ");
                        }
                    }
                    System.out.println("[*] [RnBridge] suspend body " + body.getClass().getName()
                            + " invokes: " + sigs);
                }
                // The body lambda's REAL shape (verified against expo SDK 53
                // bytecode): invoke(CoroutineScope, convertedArgs, Continuation).
                // The body's RETURN VALUE settles the promise - expo's own
                // coroutine wrapper does promiseImpl.resolve(result) after the
                // body completes, and never passes the promise into the body.
                Object scope;
                try {
                    java.lang.reflect.Field scopeField = Class.forName("kotlinx.coroutines.GlobalScope")
                            .getField("INSTANCE");
                    try { scopeField.setAccessible(true); } catch (Throwable ignoredAcc) {}
                    scope = scopeField.get(null);
                } catch (Throwable tScope) {
                    scope = null;
                }
                if (scope == null) return false;
                Method invoke = null;
                Throwable lastMismatch = null;
                // Prefer NON-bridge 3-arg overloads: the bridge invoke(Object,Object,Object)
                // delegates to the typed one - calling it first misroutes args.
                java.util.List<Method> candidates = new ArrayList<>();
                for (Method m : body.getClass().getMethods()) {
                    if ("invoke".equals(m.getName()) && m.getParameterCount() == 3) {
                        candidates.add(m.isBridge() ? null : m);
                    }
                }
                for (Method m : body.getClass().getMethods()) {
                    if ("invoke".equals(m.getName()) && m.getParameterCount() == 3 && m.isBridge()) {
                        candidates.add(m);
                    }
                }
                for (Method m : candidates) {
                    if (m == null) continue;
                    try {
                        result = m.invoke(body, scope, converted, continuation);
                        invoke = m;
                        break;
                    } catch (IllegalArgumentException mismatch) {
                        lastMismatch = mismatch;   // try the next 3-arg invoke overload
                    } catch (java.lang.reflect.InvocationTargetException shapeMismatch) {
                        Throwable sc = shapeMismatch.getCause() != null
                                ? shapeMismatch.getCause() : shapeMismatch;
                        // A CCE/illegal-arg inside one overload is an arg-shape
                        // mismatch too - keep trying the other overloads.
                        if (sc instanceof ClassCastException
                                || sc instanceof IllegalArgumentException
                                || String.valueOf(sc.getMessage()).contains("cannot be cast")) {
                            lastMismatch = sc;
                            continue;
                        }
                        throw shapeMismatch;
                    }
                }
                if (invoke != null) {
                    System.out.println("[*] [RnBridge] suspend fn " + methodName
                            + " dispatched via " + invoke.getName()
                            + (result != null ? " (returned)" : " (suspended)"));
                    // Never let a hung coroutine freeze the app forever: if the
                    // promise has not settled after 10s, resolve null - exactly
                    // how a device without the capability behaves (SecureStore
                    // "no value", clipboard "empty", etc.). The app keeps going.
                    final Object fRnPromise = rnShapedPromise;
                    SUSPEND_TIMEOUT_SCHED.schedule(() -> {
                        if (settled.compareAndSet(false, true)) {
                            System.out.println("[*] [RnBridge] suspend fn " + methodName
                                    + " did not settle in 10s - degraded to null (capability-absent semantics)");
                            resolveJs(fRnPromise, null);
                        }
                    }, 10, java.util.concurrent.TimeUnit.SECONDS);
                }
                if (invoke == null) {
                    if (lastMismatch != null) {
                        rejectJs3(rnShapedPromise, codeOf(lastMismatch),
                                String.valueOf(lastMismatch.getMessage()), lastMismatch);
                        return true;
                    }
                    return false;   // unexpected shape - fall back to ModuleHolder.call
                }
            } catch (Throwable tBody) {
                Throwable c = tBody;
                while (c.getCause() != null && c.getCause() != c) c = c.getCause();
                // Mirror expo's error envelope: reject with CodedException code when available.
                rejectJs3(rnShapedPromise, codeOf(c), String.valueOf(c.getMessage()), c);
                return true;
            }

            // 4. synchronous completion: body returned without suspending
            Object suspended = Class.forName("kotlin.coroutines.intrinsics.IntrinsicsKt")
                    .getMethod("getCOROUTINE_SUSPENDED").invoke(null);
            if (!java.util.Objects.equals(result, suspended)) {
                resolveJs(rnShapedPromise, result);
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            return false;   // older expo layout - fall back to ModuleHolder.call
        } catch (Throwable t) {
            Throwable c = t;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            System.out.println("[*] [RnBridge] expo suspend dispatch failed for " + methodName
                    + ": " + c);
            rejectJs3(rnShapedPromise, codeOf(c), String.valueOf(c.getMessage()), c);
            return true;
        }
    }

    /** Resolve/reject the RN promise from a boxed kotlin.Result continuation value. */
    private void settleFromResult(Object boxedResult, Object rnPromise,
                                  java.util.concurrent.atomic.AtomicBoolean settled) {
        try {
            if (!settled.compareAndSet(false, true)) return;
            if (boxedResult == null) {
                resolveJs(rnPromise, null);
                return;
            }
            Class<?> rcls = Class.forName("kotlin.Result");
            Method isFailure = null;
            Method failure = null;
            for (Method m : rcls.getDeclaredMethods()) {
                if (m.getName().startsWith("isFailure-impl") && m.getParameterCount() == 1) {
                    isFailure = m;
                }
                if (m.getName().startsWith("failure-impl") && m.getParameterCount() == 1) {
                    failure = m;
                }
            }
            if (isFailure != null) {
                isFailure.setAccessible(true);
                Object failed = isFailure.invoke(null, boxedResult);
                if (Boolean.TRUE.equals(failed)) {
                    Throwable cause = null;
                    if (failure != null) {
                        failure.setAccessible(true);
                        Object f = failure.invoke(null, boxedResult);
                        if (f instanceof Throwable) cause = (Throwable) f;
                    }
                    rejectJs3(rnPromise, codeOf(cause),
                            String.valueOf(cause == null ? "suspend function failed" : cause.getMessage()),
                            cause);
                    return;
                }
            }
            resolveJs(rnPromise, boxedResult);
        } catch (Throwable t) {
            resolveJs(rnPromise, boxedResult);
        }
    }

    private static String codeOf(Throwable t) {
        String code = str(invokeNoArg(t, "getCode"));
        return code != null ? code : "EEXCEPTION";
    }

    /**
     * RN-callable sync dispatch (routed through the @@SYNC channel).
     */
    public Object callSync(String moduleName, String methodName, List<Object> args) {
        // ---- Universal runner-implemented expo sync functions ---------------
        if ("ExpoFontLoader".equals(moduleName) && "getLoadedFonts".equals(methodName)) {
            return new ArrayList<>(mLoadedFonts);
        }
        if ("ExpoAsset".equals(moduleName) && "getLocalAssetURI".equals(methodName)) {
            try {
                return assetDownload(args);
            } catch (Throwable tLocal) {
                return null;
            }
        }
        try {
            Object holder = mHolders.get(moduleName);
            if (holder == null) return null;
            Object[] jArgs = args != null ? args.toArray() : new Object[0];
            for (int ai = 0; ai < jArgs.length; ai++) {
                jArgs[ai] = toReactTypes(jArgs[ai]);
            }
            return holder.getClass()
                    .getMethod("callSync", String.class, Object[].class)
                    .invoke(holder, methodName, jArgs);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable c = ite.getCause() != null ? ite.getCause() : ite;
            System.out.println("[-] [RnBridge] expo " + moduleName + "." + methodName
                    + " (sync) threw: " + c);
            return null;
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] expo " + moduleName + "." + methodName
                    + " (sync) dispatch failed: " + t.getMessage());
            return null;
        }
    }

    // ------- JS promise plumbing (JsCallbackBridge.PromiseImpl is RN-shaped;
    //         expo's Promise gets adapted through these helpers) -------

    private static void resolveJs(Object jsPromise, Object value) {
        try {
            jsPromise.getClass().getMethod("resolve", Object.class).invoke(jsPromise, value);
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] expo promise resolve failed: " + t);
        }
    }

    private static void rejectJs(Object jsPromise, String code, String message) {
        rejectJs3(jsPromise, code, message, null);
    }

    private static void rejectJs3(Object jsPromise, String code, String message, Throwable cause) {
        try {
            jsPromise.getClass()
                    .getMethod("reject", String.class, String.class, Throwable.class)
                    .invoke(jsPromise, code, message, cause);
        } catch (Throwable t) {
            System.out.println("[-] [RnBridge] expo promise reject failed: " + t);
        }
    }

    private static Object invokeNoArg(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final boolean SUSPEND_SHAPE_DEBUG = true;

    /**
     * Recursively convert JSON-decoded values (LinkedHashMap / ArrayList) into
     * the app's own React Native types (WritableNativeMap / WritableNativeArray).
     * Expo's type converters assert `value instanceof ReadableMap`; raw JSON
     * containers would fail that cast for EVERY map-typed argument. The classes
     * resolve through the runtime classpath (the app's own RN bridge classes,
     * which the runner environment already makes JNI-free).
     */
    private static volatile Constructor<?> sWnmCtor;
    private static volatile Constructor<?> sWnaCtor;
    private static volatile boolean sReactTypesBroken;

    private static Object toReactTypes(Object v) {
        if (sReactTypesBroken) return v;
        try {
            if (v instanceof Map) {
                if (sWnmCtor == null) {
                    Class<?> cls = Class.forName("com.facebook.react.bridge.WritableNativeMap");
                    sWnmCtor = cls.getConstructor();
                }
                Object map = sWnmCtor.newInstance();
                Class<?> wnmClass = map.getClass();
                Method putString = wnmClass.getMethod("putString", String.class, String.class);
                Method putBoolean = wnmClass.getMethod("putBoolean", String.class, boolean.class);
                Method putDouble = wnmClass.getMethod("putDouble", String.class, double.class);
                Method putInt = wnmClass.getMethod("putInt", String.class, int.class);
                Method putNull = wnmClass.getMethod("putNull", String.class);
                Method putAny = null;
                for (Method m : wnmClass.getMethods()) {
                    if ("putMap".equals(m.getName()) && m.getParameterCount() == 2) putAny = m;
                }
                Method putArr = null;
                for (Method m : wnmClass.getMethods()) {
                    if ("putArray".equals(m.getName()) && m.getParameterCount() == 2) putArr = m;
                }
                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                    String k = String.valueOf(e.getKey());
                    Object val = toReactTypes(e.getValue());
                    if (val == null) { putNull.invoke(map, k); }
                    else if (val instanceof String) { putString.invoke(map, k, val); }
                    else if (val instanceof Boolean) { putBoolean.invoke(map, k, val); }
                    else if (val instanceof Integer) { putInt.invoke(map, k, val); }
                    else if (val instanceof Number) { putDouble.invoke(map, k, ((Number) val).doubleValue()); }
                    else if (val instanceof Map || putAny == null) {
                        if (putAny != null && val instanceof Map) putAny.invoke(map, k, val);
                    }
                    else if (putArr != null && val instanceof List) { putArr.invoke(map, k, val); }
                }
                return map;
            }
            if (v instanceof List) {
                if (sWnaCtor == null) {
                    Class<?> cls = Class.forName("com.facebook.react.bridge.WritableNativeArray");
                    sWnaCtor = cls.getConstructor();
                }
                Object arr = sWnaCtor.newInstance();
                Class<?> wnaClass = arr.getClass();
                Method pushString = wnaClass.getMethod("pushString", String.class);
                Method pushBoolean = wnaClass.getMethod("pushBoolean", boolean.class);
                Method pushDouble = wnaClass.getMethod("pushDouble", double.class);
                Method pushInt = wnaClass.getMethod("pushInt", int.class);
                Method pushNull = wnaClass.getMethod("pushNull");
                Method pushMap = null;
                for (Method m : wnaClass.getMethods()) {
                    if ("pushMap".equals(m.getName()) && m.getParameterCount() == 1) pushMap = m;
                }
                Method pushArr = null;
                for (Method m : wnaClass.getMethods()) {
                    if ("pushArray".equals(m.getName()) && m.getParameterCount() == 1) pushArr = m;
                }
                for (Object o : (List<?>) v) {
                    Object val = toReactTypes(o);
                    if (val == null) { pushNull.invoke(arr); }
                    else if (val instanceof String) { pushString.invoke(arr, val); }
                    else if (val instanceof Boolean) { pushBoolean.invoke(arr, val); }
                    else if (val instanceof Integer) { pushInt.invoke(arr, val); }
                    else if (val instanceof Number) { pushDouble.invoke(arr, ((Number) val).doubleValue()); }
                    else if (pushMap != null && val instanceof Map) { pushMap.invoke(arr, val); }
                    else if (pushArr != null && val instanceof List) { pushArr.invoke(arr, val); }
                }
                return arr;
            }
        } catch (Throwable t) {
            // the app's WritableNativeMap needs real JNI (no headless shim) -
            // fall back to raw JSON containers permanently
            sReactTypesBroken = true;
        }
        return v;
    }

    /** Settle-timeout scheduler for suspend functions that hang in this env. */
    private static final java.util.concurrent.ScheduledExecutorService SUSPEND_TIMEOUT_SCHED =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ExpoSuspendTimeout");
                t.setDaemon(true);
                return t;
            });

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    // =====================================================================
    // Universal expo-asset / expo-font implementation (desktop runner side)
    // Mirrors the EXACT native contracts:
    //  - expo-asset  AssetModule.downloadAsync(uri, md5Hash, type)
    //    -> copies bytes into cacheDir/ExponentAsset-<hash>.<type>, returns
    //       a file:// URI (file:// URIs pass through untouched)
    //  - expo-font   FontLoaderModule.loadAsync(fontFamilyName, localUri)
    //    -> registers the font with the renderer + loaded-fonts list,
    //       getLoadedFonts() returns it (initialized from APK assets/fonts)
    // =====================================================================

    /** expo-asset downloadAsync contract. Returns the resolved file:// URI. */
    private String assetDownload(List<Object> args) throws Exception {
        String uriStr = args != null && !args.isEmpty() && args.get(0) != null
                ? String.valueOf(args.get(0)) : null;
        if (uriStr == null || uriStr.isEmpty()) return null;
        String md5 = args.size() > 1 && args.get(1) != null ? String.valueOf(args.get(1)) : null;
        String type = args.size() > 2 && args.get(2) != null ? String.valueOf(args.get(2)) : "";

        // Native contract: plain local file URIs pass through untouched
        // (file:///android_res/ and android_asset get materialized instead).
        if (uriStr.startsWith("file:")
                && !uriStr.startsWith("file:///android_res/")
                && !uriStr.contains("android_asset")
                && new java.io.File(uriStr.substring("file://".length())).exists()) {
            return uriStr;
        }

        byte[] data = readAssetBytes(uriStr);
        if (data == null) {
            // Universal resource-name fallback (device-equivalent): RN metro
            // assets resolve through Resources.getIdentifier(name) ->
            // openRawResource, which works even under AAPT path obfuscation
            // because the ARSC keeps the logical resource name.
            data = readResourceBytes(uriStr);
        }
        if (data == null) {
            // The asset is genuinely not in the APK (e.g. fonts stripped by
            // the build, remote-only assets). Resolve null like a device
            // would fall back: the JS layer continues with defaults instead
            // of blocking forever on a rejection it cannot recover from.
            System.out.println("[*] [RnBridge] expo-asset not bundled, degraded to null: " + uriStr);
            return null;
        }
        java.io.File out = new java.io.File(resolveCacheDir(),
                "ExponentAsset-" + (md5 != null ? md5 : md5Hex(uriStr))
                        + (type.isEmpty() ? "" : (type.startsWith(".") ? type : "." + type)));
        if (!out.exists() || out.length() != data.length) {
            java.io.File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(data);
            }
        }
        return "file://" + out.getAbsolutePath();
    }

    /** expo-font loadAsync contract: bytes -> cache file -> renderer registry. */
    private void fontLoad(List<Object> args) throws Exception {
        String family = args != null && !args.isEmpty() ? str(args.get(0)) : null;
        String localUri = args.size() > 1 ? str(args.get(1)) : null;
        if (family == null) return;
        // The family counts as handled from here on - even when the font file
        // is not in the APK (build stripped fonts), the JS useFonts gate must
        // observe the family in getLoadedFonts() or it would poll forever.
        if (!mLoadedFonts.contains(family)) mLoadedFonts.add(family);
        byte[] data = localUri == null ? null : readAssetBytes(localUri);
        if (data == null && localUri != null) data = readResourceBytes(localUri);
        if (data == null && localUri == null) {
            // expo-font JS can pass the family-qualified asset name directly;
            // try the resource table with the family name itself.
            data = readResourceBytes(family);
        }
        if (data == null) return;
        java.io.File fontFile = new java.io.File(resolveCacheDir(),
                "font-" + md5Hex(family + "/" + localUri)
                        + localUri.substring(Math.max(0, localUri.lastIndexOf('.'))));
        if (!fontFile.exists() || fontFile.length() != data.length) {
            java.io.File parent = fontFile.getParentFile();
            if (parent != null) parent.mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fontFile)) {
                fos.write(data);
            }
        }
        if (!mLoadedFonts.contains(family)) mLoadedFonts.add(family);
        // Hand the real file to the desktop renderer's font registry.
        try {
            org.headless.render.FontStore.register(family, fontFile);
        } catch (Throwable ignoredFont) {}
        // Best-effort: also register with the app's own ReactFontManager so
        // RN Text lookups through the app pipeline see the family.
        try {
            Class<?> tfClass = Class.forName("android.graphics.Typeface");
            Class<?> fmClass = Class.forName("com.facebook.react.common.assets.ReactFontManager");
            Object fm = fmClass.getMethod("getInstance").invoke(null);
            Object tf = tfClass.getMethod("createFromFile", java.io.File.class)
                    .invoke(null, new Object[]{fontFile});
            fmClass.getMethod("setTypeface", String.class, int.class, tfClass)
                    .invoke(fm, family, 0, tf);
        } catch (Throwable ignoredRfm) {}
    }

    /** Scan APK assets/fonts for bundled font families (device-equivalent). */
    private void scanBundledFonts() {
        try {
            String apkPath = System.getProperty("headless.runner.apk");
            if (apkPath == null) return;
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("^fonts/(.+?)(_bold|_italic|_bold_italic)?\\.(ttf|otf)$");
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry ze = en.nextElement();
                    java.util.regex.Matcher m = p.matcher(ze.getName());
                    if (m.matches()) {
                        String fam = m.group(1);
                        if (!mLoadedFonts.contains(fam)) mLoadedFonts.add(fam);
                    }
                }
            }
        } catch (Throwable t) {
            // font scan is best-effort
        }
    }

    /** Universal asset byte resolver: APK assets, res/, local file, http(s). */
    /**
     * Device-equivalent resource-name resolution: RN/expo bundled assets
     * (expo-google-fonts, local images) are Android raw/drawable resources
     * named by the flattened metro asset name. Resolve name -> resid through
     * the compiled resource table, then read the file via openRawResource.
     */
    private byte[] readResourceBytes(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains(":")) return null;
        Object res = appResources();
        if (res == null) return null;
        String[] types = {"raw", "drawable", "font", "asset", "mipmap"};
        for (String type : types) {
            try {
                int id = (Integer) res.getClass()
                        .getMethod("getIdentifier", String.class, String.class, String.class)
                        .invoke(res, name, type, null);
                if (id == 0) continue;
                // Only trust ids the compiled table actually contains - the
                // shim's getIdentifier falls back to hashCode-based synthetic
                // ids for unknown names.
                Object arscOk = res.getClass()
                        .getMethod("getResourceEntryName", int.class)
                        .invoke(res, id);
                if (arscOk == null || !name.equals(String.valueOf(arscOk))) continue;
                try (java.io.InputStream in = (java.io.InputStream) res.getClass()
                        .getMethod("openRawResource", int.class).invoke(res, id);
                     java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[1 << 15];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    if (bos.size() == 0) return null; // unresolvable synthetic id
                    System.out.println("[+] [RnBridge] expo-asset resolved via resource table: "
                            + type + "/" + name + " (" + bos.size() + " bytes)");
                    return bos.toByteArray();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** The app's live Resources instance (ArscParser-backed), or null. */
    private Object appResources() {
        try {
            Object ctx = mReactContext;
            if (ctx == null) return null;
            return ctx.getClass().getMethod("getResources").invoke(ctx);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] readAssetBytes(String uriStr) throws Exception {
        String path = uriStr;
        if (path.startsWith("file:///android_asset/")) {
            path = path.substring("file:///android_asset/".length());
        } else if (path.startsWith("file:///android_assets/")) {
            path = path.substring("file:///android_assets/".length());
        } else if (path.startsWith("file:///android_res/")) {
            path = "res/" + path.substring("file:///android_res/".length());
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            try (java.io.InputStream in = new java.net.URL(path).openStream()) {
                return readAll(in);
            }
        } else if (path.startsWith("file:")) {
            String f = path.substring("file:".length());
            while (f.startsWith("/")) f = f.substring(1);
            java.io.File lf = new java.io.File(f);
            if (lf.exists()) {
                return java.nio.file.Files.readAllBytes(lf.toPath());
            }
            path = f;
        }
        String apkPath = System.getProperty("headless.runner.apk");
        if (apkPath != null) {
            String[] candidates = {path,
                    path.startsWith("assets/") ? path.substring("assets/".length()) : "assets/" + path};
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
                for (String cand : candidates) {
                    java.util.zip.ZipEntry e = zip.getEntry(cand);
                    if (e != null) {
                        try (java.io.InputStream in = zip.getInputStream(e)) {
                            return readAll(in);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static byte[] readAll(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /** Cache dir: the app context's cache dir, falling back to runner data. */
    private java.io.File resolveCacheDir() {
        try {
            Object cd = mReactContext != null
                    ? mReactContext.getClass().getMethod("getCacheDir").invoke(mReactContext)
                    : null;
            if (cd instanceof java.io.File) {
                ((java.io.File) cd).mkdirs();
                return (java.io.File) cd;
            }
        } catch (Throwable ignored) {}
        java.io.File fallback = new java.io.File(
                System.getProperty("headless.runner.dir", "."), "data/.expo-assets");
        fallback.mkdirs();
        return fallback;
    }

    private static String md5Hex(String s) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
