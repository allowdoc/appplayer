package org.headless.runner.core;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class SyntheticInstrumentation extends Instrumentation {
    private static final String TAG = "[SyntheticInstrumentation]";

    @Override
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        handleIntent(intent);
        if (intent != null && intent.getAction() != null && intent.getAction().contains("REQUEST_PERMISSIONS")) {
            System.out.println("[*] [Universal Permission Engine] Intercepted runtime permission request! Auto-granting all permissions.");
            String[] perms = intent.getStringArrayExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
            if (perms == null) perms = new String[0];
            int[] grantResults = new int[perms.length]; // 0 = PERMISSION_GRANTED
            for (String p : perms) {
                System.out.println("[+] [Universal Permission Engine] Granted: " + p);
            }
            final String[] finalPerms = perms;
            final int finalCode = requestCode;
            final Activity actTarget = target;
            if (actTarget != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        java.lang.reflect.Method m = Activity.class.getDeclaredMethod("dispatchRequestPermissionsResult", int.class, Intent.class);
                        m.setAccessible(true);
                        Intent result = new Intent();
                        result.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES", finalPerms);
                        result.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS", grantResults);
                        m.invoke(actTarget, finalCode, result);
                    } catch (Throwable t) {
                        try {
                            actTarget.onRequestPermissionsResult(finalCode, finalPerms, grantResults);
                        } catch (Throwable ignored) {}
                    }
                });
            }
            return null;
        }
        ActivityStack.getInstance().launchActivity(intent);
        return null;
    }

    @Override
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle options) {
        handleIntent(intent);
        if (intent != null && intent.getAction() != null && intent.getAction().contains("REQUEST_PERMISSIONS")) {
            System.out.println("[*] [Universal Permission Engine] Intercepted runtime permission request! Auto-granting all permissions.");
            String[] perms = intent.getStringArrayExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
            if (perms == null) perms = new String[0];
            int[] grantResults = new int[perms.length]; // 0 = PERMISSION_GRANTED
            for (String p : perms) {
                System.out.println("[+] [Universal Permission Engine] Granted: " + p);
            }
            final String[] finalPerms = perms;
            final int finalCode = requestCode;
            final Activity actTarget = ActivityStack.getInstance().getTopActivity();
            if (actTarget != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        java.lang.reflect.Method m = Activity.class.getDeclaredMethod("dispatchRequestPermissionsResult", int.class, Intent.class);
                        m.setAccessible(true);
                        Intent result = new Intent();
                        result.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_NAMES", finalPerms);
                        result.putExtra("android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS", grantResults);
                        m.invoke(actTarget, finalCode, result);
                    } catch (Throwable t) {
                        try {
                            actTarget.onRequestPermissionsResult(finalCode, finalPerms, grantResults);
                        } catch (Throwable ignored) {}
                    }
                });
            }
            return null;
        }
        ActivityStack.getInstance().launchActivity(intent);
        return null;
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        System.out.println(TAG + " callActivityOnCreate -> " + activity.getClass().getName());
        super.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnStart(Activity activity) {
        System.out.println(TAG + " callActivityOnStart -> " + activity.getClass().getName());
        super.callActivityOnStart(activity);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        System.out.println(TAG + " callActivityOnResume -> " + activity.getClass().getName());
        super.callActivityOnResume(activity);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        ComponentName component = intent.getComponent();
        String action = intent.getAction();
        System.out.println("\n------------------------------------------------------------------");
        System.out.println("[*] [INTENT DISPATCHER] Navigation intercepted!");
        if (component != null) {
            System.out.println("[+] Target Component : " + component.getClassName());
            System.out.println("[+] Package          : " + component.getPackageName());
        }
        if (action != null) {
            System.out.println("[+] Action           : " + action);
        }
        if (intent.getExtras() != null) {
            System.out.println("[+] Extras           : " + intent.getExtras());
        }
        System.out.println("------------------------------------------------------------------\n");
    }
}
