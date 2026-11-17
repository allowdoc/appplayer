package org.headless.runner.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Generic engine-level instrumentation for network calls.
 *
 * Two responsibilities:
 *  1. logCreateService — logs Retrofit baseUrl resolution (called by ASM-injected
 *     bytecode in NetworkManager.createService).
 *  2. installEventListenerFactory — installs a diagnostic OkHttp EventListener
 *     on every OkHttpClient the app builds, so we capture the real IOException
 *     (SSLHandshakeException, UnknownHostException, ConnectException, etc.)
 *     before the app's generic error mapper swallows it.
 *     Uses java.lang.reflect.Proxy to avoid any direct dependency on okhttp3
 *     compile-time classes.
 */
public class NetworkInstrumenter {

    private static final String NET_TAG = "[NetworkEngine]";

    // -------------------------------------------------------------------------
    // Bug 2 Fix: Generic OkHttp EventListener via Proxy (reflection-only, no
    // compile-time dependency on okhttp3).
    // -------------------------------------------------------------------------

    private static class NetworkEventHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            try {
                switch (methodName) {
                    case "create":
                        // EventListener.Factory.create(Call) -> return an EventListener proxy
                        return makeEventListenerProxy();

                    case "connectFailed":
                        // connectFailed(Call, InetSocketAddress, Proxy, Protocol, IOException)
                        if (args != null && args.length >= 5 && args[4] instanceof Throwable) {
                            Throwable ioe = (Throwable) args[4];
                            String addr = args[1] != null ? args[1].toString() : "unknown";
                            System.err.println("[-] " + NET_TAG + " connectFailed to " + addr
                                    + " | " + ioe.getClass().getName() + ": " + ioe.getMessage());
                            logCauseChain(ioe);
                        }
                        return null;

                    case "callFailed":
                        // callFailed(Call, IOException)
                        if (args != null && args.length >= 2 && args[1] instanceof Throwable) {
                            Throwable ioe = (Throwable) args[1];
                            String url = "unknown";
                            try {
                                Object request = args[0].getClass().getMethod("request").invoke(args[0]);
                                url = request.getClass().getMethod("url").invoke(request).toString();
                            } catch (Throwable ignored) {}
                            System.err.println("[-] " + NET_TAG + " callFailed url=" + url
                                    + " | " + ioe.getClass().getName() + ": " + ioe.getMessage());
                            logCauseChain(ioe);
                            logStackTop(ioe, 10);
                        }
                        return null;

                    case "connectionAcquired":
                        // connectionAcquired(Call, Connection)
                        if (args != null && args.length >= 2 && args[1] != null) {
                            try {
                                Object socket = args[1].getClass().getMethod("socket").invoke(args[1]);
                                if (socket != null) {
                                    Object remoteAddr = socket.getClass()
                                            .getMethod("getRemoteSocketAddress").invoke(socket);
                                    System.out.println("[+] " + NET_TAG + " connectionAcquired: " + remoteAddr);
                                }
                            } catch (Throwable ignored) {}
                        }
                        return null;

                    default:
                        // All other EventListener methods — safe no-op defaults
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class)     return 0;
                        if (rt == long.class)    return 0L;
                        return null;
                }
            } catch (Throwable t) {
                // Never let our instrumentation crash the app
                return null;
            }
        }

        private static void logCauseChain(Throwable t) {
            Throwable cause = t.getCause();
            while (cause != null) {
                System.err.println("    caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
        }

        private static void logStackTop(Throwable t, int limit) {
            StackTraceElement[] frames = t.getStackTrace();
            int n = Math.min(frames != null ? frames.length : 0, limit);
            for (int i = 0; i < n; i++) {
                System.err.println("    at " + frames[i]);
            }
        }
    }

    private static final NetworkEventHandler HANDLER = new NetworkEventHandler();

    /** Create a Proxy implementing okhttp3.EventListener. */
    private static Object makeEventListenerProxy() {
        try {
            Class<?> listenerClass = Class.forName("okhttp3.EventListener");
            return Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class[]{listenerClass},
                    HANDLER);
        } catch (Throwable t) {
            System.err.println("[-] " + NET_TAG + " Could not create EventListener proxy: " + t.getMessage());
            return null;
        }
    }

    /** Create a Proxy implementing okhttp3.EventListener$Factory. */
    private static Object makeEventListenerFactoryProxy() {
        try {
            Class<?> factoryClass = Class.forName("okhttp3.EventListener$Factory");
            return Proxy.newProxyInstance(
                    factoryClass.getClassLoader(),
                    new Class[]{factoryClass},
                    HANDLER);
        } catch (Throwable t) {
            System.err.println("[-] " + NET_TAG + " Could not create EventListener.Factory proxy: " + t.getMessage());
            return null;
        }
    }

    /**
     * Called by ASM-injected bytecode at the start of OkHttpClient.Builder.build().
     * Installs our diagnostic EventListener.Factory so we see the real IOException
     * before the app's error mapper swallows it. Generic — no app-specific code.
     */
    public static void installEventListenerFactory(Object okHttpClientBuilder) {
        if (okHttpClientBuilder == null) return;
        try {
            Class<?> builderClass = okHttpClientBuilder.getClass();

            // Check if the app already set a custom factory
            java.lang.reflect.Field factoryField = null;
            Class<?> cls = builderClass;
            while (cls != null) {
                try {
                    factoryField = cls.getDeclaredField("eventListenerFactory");
                    break;
                } catch (NoSuchFieldException ignored) {
                    cls = cls.getSuperclass();
                }
            }

            boolean alreadyCustom = false;
            if (factoryField != null) {
                factoryField.setAccessible(true);
                Object existing = factoryField.get(okHttpClientBuilder);
                if (existing != null) {
                    // OkHttp's default is a singleton no-op from EventListener.Companion
                    // If it's a Proxy or non-EventListener class, the app set a custom one
                    alreadyCustom = Proxy.isProxyClass(existing.getClass()) ||
                            (!existing.getClass().getName().contains("EventListener"));
                }
            }

            if (!alreadyCustom) {
                Object factoryProxy = makeEventListenerFactoryProxy();
                if (factoryProxy != null) {
                    // Look up the eventListenerFactory(Factory) setter on the builder
                    Method setter = null;
                    for (Method m : builderClass.getMethods()) {
                        if ("eventListenerFactory".equals(m.getName()) &&
                                m.getParameterCount() == 1) {
                            setter = m;
                            break;
                        }
                    }
                    if (setter != null) {
                        setter.invoke(okHttpClientBuilder, factoryProxy);
                        System.out.println("[+] " + NET_TAG +
                                " Installed diagnostic EventListener on OkHttpClient.Builder.");
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[-] " + NET_TAG + " Could not install EventListener: " +
                    t.getClass().getName() + ": " + t.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Pre-existing: Retrofit createService instrumentation
    // -------------------------------------------------------------------------

    public static void logCreateService(Class<?> serviceClass, Object request, Object profile) {
        String serviceName = (serviceClass != null) ? serviceClass.getName() : "null";
        String profileName = (profile != null) ? profile.toString() : "null";
        String baseUrl = "null";

        if (request != null) {
            try {
                Method m = request.getClass().getMethod("getbaseUrl");
                Object val = m.invoke(request);
                baseUrl = (val != null) ? val.toString() : "null";
            } catch (Throwable ignored) {}
        }

        String callerInfo = "unknown";
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < st.length; i++) {
            String cn = st[i].getClassName();
            if (!cn.startsWith("org.headless.runner") && !cn.startsWith("com.network.NetworkManager")) {
                callerInfo = cn + "." + st[i].getMethodName() +
                        "(" + st[i].getFileName() + ":" + st[i].getLineNumber() + ")";
                break;
            }
        }

        System.out.println("[+] [NetworkManager.createService] Invoking Retrofit.baseUrl(...)");
        System.out.println("    |-- Target Service : " + serviceName);
        System.out.println("    |-- RetrofitProfile: " + profileName);
        System.out.println("    |-- BaseUrl Value  : \"" + baseUrl + "\"");
        System.out.println("    |-- Caller Source  : " + callerInfo);
    }
}
