package dalvik.system;

public final class VMDebug {
    private VMDebug() {}

    public static boolean isDebuggerConnected() {
        return false;
    }

    public static boolean isDebuggingEnabled() {
        return false;
    }

    public static long lastDebuggerActivity() {
        return -1;
    }

    public static void startMethodTracing(String traceFileName, int bufferSize, int flags, boolean samplingEnabled, int intervalUs) {}
    public static void stopMethodTracing() {}
    public static void startAllocCounting() {}
    public static void stopAllocCounting() {}
    public static int getGlobalAllocCount() { return 0; }
    public static int getGlobalAllocSize() { return 0; }
    public static int getGlobalFreedCount() { return 0; }
    public static int getGlobalFreedSize() { return 0; }
}
