package libcore.util;

public class NativeAllocationRegistry {
    public static NativeAllocationRegistry createNonmalloced(ClassLoader classLoader, long freeFunction, long size) {
        return new NativeAllocationRegistry(classLoader, freeFunction, size);
    }

    public static NativeAllocationRegistry createMalloced(ClassLoader classLoader, long freeFunction, long size) {
        return new NativeAllocationRegistry(classLoader, freeFunction, size);
    }

    public static NativeAllocationRegistry createMalloced(ClassLoader classLoader, long freeFunction) {
        return new NativeAllocationRegistry(classLoader, freeFunction, 0);
    }

    public NativeAllocationRegistry(ClassLoader classLoader, long freeFunction, long size) {}

    public Runnable registerNativeAllocation(Object referent, long nativePtr) {
        return () -> {};
    }

    public static void applyFreeFunction(long freeFunction, long nativePtr) {}
}
