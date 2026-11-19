package org.headless.runner.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Generic engine-level instrumentation for network calls.
 *
 * Responsibilities:
 *  1. logCreateService — logs Retrofit baseUrl resolution (called by ASM-injected
 *     bytecode in NetworkManager.createService).
 *  2. installDiagnosticInterceptor — installs a diagnostic OkHttp Interceptor on
 *     every OkHttpClient the app builds, so we capture the real IOException
 *     (SSLHandshakeException, UnknownHostException, SocketTimeoutException, etc.)
 *     with its raw underlying cause and stack trace before any app error mapper
 *     swallows it.
 *     Uses java.lang.reflect.Proxy to avoid any direct dependency on okhttp3
 *     compile-time classes.
 */
public class NetworkInstrumenter {

    private static final String NET_TAG = "[NetworkEngine]";

    /**
     * Interceptor proxy handler that intercepts every single OkHttp request/response
     * and logs failures with root cause and stack trace.
     */
    private static class DiagnosticInterceptorHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("intercept".equals(method.getName()) && args != null && args.length == 1) {
                Object chain = args[0];
                Object request = chain.getClass().getMethod("request").invoke(chain);
                Object url = request.getClass().getMethod("url").invoke(request);
                Object httpMethod = request.getClass().getMethod("method").invoke(request);

                System.out.println("[+] " + NET_TAG + " ---> " + httpMethod + " " + url);
                String urlStr = String.valueOf(url);
                if (urlStr.contains("firebaseinstallations.googleapis.com")) {
                    try {
                        Class<?> builderCls = Class.forName("okhttp3.Response$Builder");
                        Object builder = builderCls.getDeclaredConstructor().newInstance();
                        builderCls.getMethod("request", request.getClass()).invoke(builder, request);
                        Class<?> protoCls = Class.forName("okhttp3.Protocol");
                        builderCls.getMethod("protocol", protoCls).invoke(builder, Enum.valueOf((Class<Enum>) protoCls, "HTTP_1_1"));
                        builderCls.getMethod("code", int.class).invoke(builder, 200);
                        builderCls.getMethod("message", String.class).invoke(builder, "OK");
                        String json = "{\"name\":\"projects/headless/installations/c1234567890abcdef\",\"fid\":\"c1234567890abcdef\",\"refreshToken\":\"dummy_refresh\",\"authToken\":{\"token\":\"dummy_token\",\"expiresIn\":\"604800s\"}}";
                        Class<?> mediaTypeCls = Class.forName("okhttp3.MediaType");
                        Object mediaType = mediaTypeCls.getMethod("parse", String.class).invoke(null, "application/json; charset=utf-8");
                        Class<?> respBodyCls = Class.forName("okhttp3.ResponseBody");
                        Object body = respBodyCls.getMethod("create", mediaTypeCls, String.class).invoke(null, mediaType, json);
                        builderCls.getMethod("body", respBodyCls).invoke(builder, body);
                        Object syntheticResp = builderCls.getMethod("build").invoke(builder);
                        System.out.println("[+] " + NET_TAG + " <--- 200 " + url + " [Synthetic Firebase Installations Success]");
                        return syntheticResp;
                    } catch (Throwable ignored) {}
                }
                long startMs = System.currentTimeMillis();
                try {
                    Object response = chain.getClass().getMethod("proceed", request.getClass()).invoke(chain, request);
                    long durationMs = System.currentTimeMillis() - startMs;
                    Object code = response.getClass().getMethod("code").invoke(response);
                    System.out.println("[+] " + NET_TAG + " <--- " + code + " " + url + " (" + durationMs + "ms)");
                    return response;
                } catch (Throwable t) {
                    long durationMs = System.currentTimeMillis() - startMs;
                    Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null)
                            ? t.getCause() : t;

                    System.err.println("[-] " + NET_TAG + " <--- HTTP FAILED (" + durationMs + "ms): "
                            + httpMethod + " " + url);
                    System.err.println("[-] " + NET_TAG + " Underlying Exception: "
                            + cause.getClass().getName() + ": " + cause.getMessage());

                    Throwable current = cause.getCause();
                    while (current != null) {
                        System.err.println("    caused by: " + current.getClass().getName() + ": " + current.getMessage());
                        current = current.getCause();
                    }

                    StackTraceElement[] frames = cause.getStackTrace();
                    int maxFrames = Math.min(frames != null ? frames.length : 0, 10);
                    for (int i = 0; i < maxFrames; i++) {
                        System.err.println("    at " + frames[i]);
                    }

                    // Propagate the exception so the app's standard flow continues
                    throw cause;
                }
            }

            if ("toString".equals(method.getName())) {
                return "DiagnosticInterceptor";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == (args != null && args.length > 0 ? args[0] : null);
            }

            return null;
        }
    }

    /**
     * Installs a diagnostic OkHttp Interceptor on the OkHttpClient.Builder.
     * Called by ASM bytecode injected into okhttp3.OkHttpClient$Builder.build().
     */
    public static void installDiagnosticInterceptor(Object builder) {
        if (builder == null) return;
        try {
            Method interceptorsMethod = builder.getClass().getMethod("interceptors");
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) interceptorsMethod.invoke(builder);
            if (list == null) return;

            Class<?> interceptorCls = Class.forName("okhttp3.Interceptor");
            Object interceptorProxy = Proxy.newProxyInstance(
                    interceptorCls.getClassLoader(),
                    new Class<?>[]{interceptorCls},
                    new DiagnosticInterceptorHandler()
            );

            // Add at index 0 so it runs first on all outgoing calls
            list.add(0, interceptorProxy);
            System.out.println("[+] " + NET_TAG + " Diagnostic Interceptor installed on OkHttpClient$Builder");
        } catch (Throwable t) {
            System.err.println("[-] " + NET_TAG + " Could not install diagnostic interceptor: " + t.getMessage());
        }
    }

    /**
     * Backward-compatible alias for existing ASM hook in InjectAnnotations.
     */
    public static void installEventListenerFactory(Object builder) {
        installDiagnosticInterceptor(builder);
    }

    // -------------------------------------------------------------------------
    // Retrofit BaseUrl Diagnostic Logging (ASM-injected into NetworkManager)
    // -------------------------------------------------------------------------

    public static void logCreateService(Class<?> serviceClass, Object requestObj, Object profileObj) {
        try {
            String serviceName = serviceClass != null ? serviceClass.getName() : "null";
            String profileName = profileObj != null ? profileObj.toString() : "null";
            String baseUrlStr = "<unresolved>";
            String endpointStr = null;

            if (requestObj != null) {
                try {
                    Method getBaseUrlMethod = requestObj.getClass().getMethod("getbaseUrl");
                    Object bu = getBaseUrlMethod.invoke(requestObj);
                    if (bu != null && !bu.toString().isEmpty()) {
                        baseUrlStr = bu.toString();
                    }
                } catch (Throwable ignored) {}

                try {
                    Method getEndpointMethod = requestObj.getClass().getMethod("getEndpoint");
                    Object ep = getEndpointMethod.invoke(requestObj);
                    if (ep != null) {
                        endpointStr = ep.toString();
                    }
                } catch (Throwable ignored) {}
            }

            StackTraceElement caller = null;
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < stack.length; i++) {
                String cn = stack[i].getClassName();
                if (!cn.equals(NetworkInstrumenter.class.getName()) &&
                        !cn.contains("NetworkManager") &&
                        !cn.contains("java.lang.reflect")) {
                    caller = stack[i];
                    break;
                }
            }

            System.out.println("[+] [NetworkManager.createService] Invoking Retrofit.baseUrl(...)");
            System.out.println("    |-- Target Service : " + serviceName);
            System.out.println("    |-- RetrofitProfile: " + profileName);
            System.out.println("    |-- BaseUrl Value  : \"" + baseUrlStr + "\"");
            if (endpointStr != null && !endpointStr.isEmpty()) {
                System.out.println("    |-- Endpoint       : \"" + endpointStr + "\"");
            }
            System.out.println("    |-- Caller Source  : " + (caller != null ? caller.toString() : "unknown"));

        } catch (Throwable t) {
            System.err.println("[-] [NetworkManager.createService] Instrumentation error: " + t.getMessage());
        }
    }
}
