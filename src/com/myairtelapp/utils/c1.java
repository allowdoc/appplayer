package com.myairtelapp.utils;

// NOTE: class name is intentionally lowercase "c1" (not "C1") to exactly match
// the binary name the app's real obfuscated bytecode references
// (com/myairtelapp/utils/c1). The JVM classloader validates a loaded class's
// internal name against the requested name, so a case mismatch here throws
// NoClassDefFoundError even though Windows' case-insensitive filesystem lets
// a wrongly-cased source/class file "work" locally.
public abstract class c1 {
    public static B1 a;
    public static final B1 b = null;

    public static void a(String tag, String msg, Throwable t) {
        System.err.println("[c1-LOG:a] " + tag + ": " + msg + " -> " + t);
        if (t != null) t.printStackTrace();
    }

    public static void b(String tag, String msg, Throwable t) {
        System.err.println("[c1-LOG:b] " + tag + ": " + msg + " -> " + t);
        if (t != null) t.printStackTrace();
    }

    public static void c(String tag, String msg, Exception e) {
        System.err.println("[c1-LOG:c] " + tag + ": " + msg + " -> " + e);
        if (e != null) e.printStackTrace();
    }

    public static void d(String tag, String msg) {
        System.out.println("[c1-LOG:d] " + tag + ": " + msg);
    }

    public static void e(String tag, String msg, Exception e) {
        System.err.println("[c1-LOG:e] " + tag + ": " + msg + " -> " + e);
        if (e != null) e.printStackTrace();
    }

    public static void f(String tag, String msg, Throwable t) {
        System.err.println("[c1-LOG:f] " + tag + ": " + msg + " -> " + t);
        if (t != null) t.printStackTrace();
    }

    public static void g(String msg) {
        System.out.println("[c1-LOG:g] " + msg);
    }

    public static B1 h() {
        return a != null ? a : b;
    }

    public static void i(String tag, String msg) {
        System.out.println("[c1-LOG:i] " + tag + ": " + msg);
    }

    public static void j(String tag) {
        System.out.println("[c1-LOG:j] " + tag);
    }

    public static void k(String tag, String msg) {
        System.out.println("[c1-LOG:k] " + tag + ": " + msg);
    }

    public static void l(String tag, String msg) {
        System.out.println("[c1-LOG:l] " + tag + ": " + msg);
    }

    public static void m(String tag, String msg) {
        System.out.println("[c1-LOG:m] " + tag + ": " + msg);
    }
}
