package com.facebook.hermes.reactexecutor;

import com.facebook.jni.HybridData;
import com.facebook.react.bridge.JavaScriptExecutor;

public class HermesExecutor extends JavaScriptExecutor {
    private static String mode_;

    public HermesExecutor(RuntimeConfig config, boolean enableDebugger, String debuggerName) {
        super(new HybridData());
        System.out.println("[+] [HermesExecutor] In-process HermesExecutor initialized (debugger=" + enableDebugger + ", name=" + debuggerName + ")");
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
}
