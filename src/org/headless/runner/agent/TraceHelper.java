package org.headless.runner.agent;

/**
 * Called from agent-instrumented code to log native view hierarchy operations
 * with their tags (render diagnostics).
 */
public final class TraceHelper {
    private TraceHelper() {}

    public static void logCreateView(int tag, String className) {
        System.out.println("[trace] NVHM.createView tag=" + tag + " class=" + className);
    }

    public static void logmanageChildren(int tag) {
        System.out.println("[trace] NVHM.manageChildren parent=" + tag);
    }

    public static void logsetChildren(int tag) {
        System.out.println("[trace] NVHM.setChildren parent=" + tag);
    }

    public static void logMarker(String label, String detail) {
        System.out.println("[trace] " + label + ": " + detail);
    }
}
