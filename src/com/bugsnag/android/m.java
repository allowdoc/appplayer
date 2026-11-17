package com.bugsnag.android;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import k1.c;

/**
 * Universal safe Bugsnag facade preventing uninitialized recursion / StackOverflowError.
 */
public abstract class m {
    public static final Object a = new Object();
    public static t b = null;
    public static K0 c = null;
    public static Method d;
    public static Method e;
    public static Method f;
    public static Method g;
    public static Method h;
    public static Method i;

    public static void a(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {}
        }
    }

    public static boolean b(Set set) {
        return false;
    }

    public static long c(File file) {
        return file != null && file.exists() ? file.length() : 0L;
    }

    public static final ActivityManager d(Context context) {
        return context != null ? (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE) : null;
    }

    public static t e() {
        return b;
    }

    public static String f(G gObj) {
        return "";
    }

    public static Method g(String str, Class<?>... parameterTypes) {
        return null;
    }

    public static Object h(String str, Map map) {
        return null;
    }

    public static Object i(String str, Map map) {
        return null;
    }

    public static Set j(Bundle bundle, String str, Set set) {
        return set;
    }

    public static Long k(String str) {
        return 0L;
    }

    public static void l(BreadcrumbType breadcrumbType, String str, Map map) {
        // Safe no-op without throwing IllegalStateException
    }

    public static c m(Bundle bundle) {
        return null;
    }

    public static ConcurrentHashMap n(List list) {
        return new ConcurrentHashMap();
    }

    public static void o(Throwable th) {
        // Safe no-op error capture
    }

    public static final Intent p(Context context, BroadcastReceiver broadcastReceiver, IntentFilter intentFilter, y0 y0Var) {
        return null;
    }

    public static final List q(Throwable th) {
        return Collections.emptyList();
    }

    public static final String r(Set set) {
        return "";
    }

    public static com.facebook.react.bridge.WritableArray s(Collection collection) {
        return null;
    }

    public static com.facebook.react.bridge.WritableMap t(Map map) {
        return null;
    }
}
