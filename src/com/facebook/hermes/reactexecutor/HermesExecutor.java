package com.facebook.hermes.reactexecutor;

import com.facebook.jni.HybridData;
import com.facebook.react.bridge.JavaScriptExecutor;

/**
 * Pure-Java shadow of the app's HermesExecutor. On a device this class
 * constructs the native Hermes VM (libhermes.so) through JNI; in this runner
 * the app's real JS bundle is executed by the platform Hermes VM process
 * (UniversalReactBridge stdio bridge) and this executor simply represents
 * "the JS engine" to the app's own React code. Both the legacy
 * (ReactInstanceManager) and bridgeless (ReactHost) paths pick the executor
 * from HermesExecutorFactory exactly like on a device - no app knowledge.
 *
 * The Kotlin surface (Companion, static access$ bridges, mode field) is
 * mirrored 1:1 because the app's own bytecode references it directly.
 */
public class HermesExecutor extends JavaScriptExecutor {
    private static String mode;

    /** Kotlin companion surface (app bytecode reads HermesExecutor.Companion). */
    public static final Companion Companion = new Companion();

    /** RN 0.7x+ signature (Kotlin @JvmOverloads): (enableDebugger, debuggerName). */
    public HermesExecutor(boolean enableDebugger, String debuggerName) {
        super(new HybridData());
        mode = debuggerName;
        System.out.println("[+] [HermesExecutor] In-process HermesExecutor initialized (debugger=" + enableDebugger + ", name=" + debuggerName + ")");
        org.headless.runner.rn.UniversalReactBridge.get().onJsExecutorCreated();
    }

    public static void loadLibrary() {}

    public String getName() {
        return "HermesExecutor";
    }

    public static HybridData initHybrid(boolean enableDebugger, String debuggerName, long heapSizeMB) {
        return new HybridData();
    }

    public static HybridData initHybridDefaultConfig(boolean enableDebugger, String debuggerName) {
        return new HybridData();
    }

    // ---- Kotlin static-bridge accessors (referenced by app bytecode) ----

    public static final String access$getMode$cp() {
        return mode;
    }

    public static final void access$setMode$cp(String v) {
        mode = v;
    }

    public static final HybridData access$initHybrid(boolean enableDebugger, String debuggerName, long heapSizeMB) {
        return initHybrid(enableDebugger, debuggerName, heapSizeMB);
    }

    public static final HybridData access$initHybridDefaultConfig(boolean enableDebugger, String debuggerName) {
        return initHybridDefaultConfig(enableDebugger, debuggerName);
    }

    /** Mirrors com.facebook.hermes.reactexecutor.HermesExecutor$Companion. */
    public static final class Companion {
        private Companion() {}

        public Companion(kotlin.jvm.internal.DefaultConstructorMarker marker) {}

        public final void loadLibrary() {
            HermesExecutor.loadLibrary();
        }

        private final HybridData initHybrid(boolean enableDebugger, String debuggerName, long heapSizeMB) {
            return HermesExecutor.initHybrid(enableDebugger, debuggerName, heapSizeMB);
        }

        private final HybridData initHybridDefaultConfig(boolean enableDebugger, String debuggerName) {
            return HermesExecutor.initHybridDefaultConfig(enableDebugger, debuggerName);
        }

        public static final HybridData access$initHybridDefaultConfig(Companion c, boolean enableDebugger, String debuggerName) {
            return HermesExecutor.initHybridDefaultConfig(enableDebugger, debuggerName);
        }
    }
}
