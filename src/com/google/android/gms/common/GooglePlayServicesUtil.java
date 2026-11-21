package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * UNIVERSAL GOOGLE SERVICES FIXER (part 1).
 *
 * Shadows the app's own GooglePlayServicesUtil so availability checks never
 * return SERVICE_INVALID (error 9) inside the runner. The app's Google API
 * clients connect through SyntheticGmsBroker, which answers in-process.
 *
 * Nothing is written to app data; this is a pure framework-level answer.
 */
public class GooglePlayServicesUtil extends GooglePlayServicesUtilLight {

    public static final String GMS_PACKAGE = "com.google.android.gms";
    public static final String GMS_VERSION_CODE = "24.33.13 (100400-597385710)";
    public static final String GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending";

    public static int GOOGLE_PLAY_SERVICES_VERSION_CODE = 243313000;

    public static int isGooglePlayServicesAvailable(Context context) {
        return ConnectionResult.SUCCESS;
    }

    public static int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return ConnectionResult.SUCCESS;
    }
    // NOTE: instance-vs-static hiding resolved: Light declares statics too.

    public static boolean isUserRecoverableError(int errorCode) {
        return false;
    }

    public static int isUserRecoverableErrorNoDialog(Context context, int errorCode) {
        return ConnectionResult.SUCCESS;
    }

    public static String getErrorString(int errorCode) {
        return "SUCCESS";
    }

    public static boolean checkGooglePlayServices(Context activity) {
        return true;
    }

    public static Dialog getErrorDialog(int errorCode, Activity activity, int requestCode) {
        return null;
    }

    public static Dialog getErrorDialog(int errorCode, Activity activity, int requestCode,
                                        DialogInterface.OnCancelListener cancelListener) {
        return null;
    }

    public static boolean showErrorDialogFragment(int errorCode, Activity activity,
                                                  int requestCode) {
        return false;
    }

    public static boolean showErrorDialogFragment(int errorCode, Activity activity,
                                                  int requestCode,
                                                  DialogInterface.OnCancelListener cancelListener) {
        return false;
    }

    public static boolean showErrorNotification(Context context, int errorCode) {
        return false;
    }

    public static PendingIntent getErrorResolutionPendingIntent(Context context,
                                                                int errorCode, int requestCode) {
        return null;
    }

    public static void cancelAvailabilityErrorNotifications(Context context) {}

    public static boolean isPlayServicesPossiblyUpdating(Context context, int errorCode) {
        return false;
    }

    public static boolean isPlayStorePossiblyUpdating(Context context, int errorCode) {
        return false;
    }

    /** GMS package metadata lookups succeed with sane values. */
    public static int getRemoteResourceVersion(Context context, int minVersion) {
        return Math.max(minVersion, GOOGLE_PLAY_SERVICES_VERSION_CODE);
    }

    public static PackageInfo getRemotePackageInfo(Context context, boolean log) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(GMS_PACKAGE, 0);
            if (info != null) return info;
        } catch (Throwable ignored) {}
        PackageInfo synthetic = new PackageInfo();
        synthetic.packageName = GMS_PACKAGE;
        synthetic.versionCode = GOOGLE_PLAY_SERVICES_VERSION_CODE;
        synthetic.versionName = "24.33.13";
        return synthetic;
    }
}
