package org.headless.runner.core;

import java.lang.reflect.Method;

public class NetworkInstrumenter {

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

        // Capture caller method & source location from stack trace
        String callerInfo = "unknown";
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < st.length; i++) {
            String cn = st[i].getClassName();
            if (!cn.startsWith("org.headless.runner") && !cn.startsWith("com.network.NetworkManager")) {
                callerInfo = cn + "." + st[i].getMethodName() + "(" + st[i].getFileName() + ":" + st[i].getLineNumber() + ")";
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
