package com.facebook.react.runtime.hermes;

import com.facebook.jni.HybridData;

/**
 * Pure-Java shadow of the app's bridgeless HermesInstance (bin-app shadows
 * the APK class). On a device this constructs the native Hermes runtime
 * through libhermes.so (initHybrid JNI); in this runner the app's real JS
 * bundle executes on the platform Hermes VM process via the UniversalReact
 * stdio bridge. The constructor is the exact device moment the JS engine is
 * born inside the bridgeless ReactHost (ReactHostImpl -> ReactInstanceImpl
 * -> HermesInstance), so it boots the universal bridge - same sequence, no
 * app knowledge involved.
 */
public final class HermesInstance extends com.facebook.react.runtime.JSRuntimeFactory {

    /** Kotlin companion surface the app's bytecode references directly. */
    public static final Companion Companion = new Companion();

    public HermesInstance() {
        super(new HybridData());
        System.out.println("[+] [HermesInstance] Bridgeless Hermes runtime initialized (universal JS engine).");
        org.headless.runner.rn.UniversalReactBridge.get().onJsExecutorCreated();
    }

    public HermesInstance(boolean enableRuntimeConfig) {
        super(new HybridData());
        System.out.println("[+] [HermesInstance] Bridgeless Hermes runtime initialized (runtimeConfig=" + enableRuntimeConfig + ").");
        org.headless.runner.rn.UniversalReactBridge.get().onJsExecutorCreated();
    }

    /** Shadow of the native chunk initializer (pure JVM: nothing to load). */
    protected static HybridData initHybrid(boolean enableRuntimeConfig) {
        return new HybridData();
    }

    /** Mirrors com.facebook.react.runtime.hermes.HermesInstance$Companion. */
    public static final class Companion {
        private Companion() {}

        public Companion(kotlin.jvm.internal.DefaultConstructorMarker marker) {}

        protected HybridData initHybrid(boolean enableRuntimeConfig) {
            return HermesInstance.initHybrid(enableRuntimeConfig);
        }
    }
}
