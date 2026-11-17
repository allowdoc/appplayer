package dalvik.system;

import java.lang.reflect.Array;

public final class VMRuntime {
    private static final VMRuntime THE_ONE = new VMRuntime();
    private int targetSdkVersion = 10000;

    private VMRuntime() {}

    public static VMRuntime getRuntime() {
        return THE_ONE;
    }

    public Object newUnpaddedArray(Class<?> componentType, int minLength) {
        return Array.newInstance(componentType, minLength);
    }

    public Object newNonMovableArray(Class<?> componentType, int minLength) {
        return Array.newInstance(componentType, minLength);
    }

    public String vmLibrary() {
        return "libart.so";
    }

    public String vmVersion() {
        return "2.1.0";
    }

    public String vmInstructionSet() {
        return "x86_64";
    }

    public boolean is64Bit() {
        return true;
    }

    public static boolean is64BitAbi(String abi) {
        return abi != null && abi.contains("64");
    }

    public boolean isBootClassPathOnDisk() {
        return true;
    }

    public boolean hasBootImageSpaces() {
        return true;
    }

    public void registerNativeAllocation(long bytes) {}
    public void registerNativeAllocation(int bytes) {}
    public void registerNativeFree(long bytes) {}
    public void registerNativeFree(int bytes) {}

    public synchronized void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    public synchronized int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setHiddenApiExemptions(String[] signaturePrefixes) {}
    public void setHiddenApiAccessLogSamplingRate(int rate) {}
    public static void setDedupeHiddenApiWarnings(boolean dedupe) {}
    public static void setNonSdkApiUsageConsumer(java.util.function.Consumer<String> consumer) {}
    public void clampGrowthLimit() {}
    public void clearGrowthLimit() {}
    public void requestConcurrentGC() {}
    public void requestHeapTrim() {}
}
