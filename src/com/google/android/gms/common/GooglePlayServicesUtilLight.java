package com.google.android.gms.common;

import android.content.Context;

/**
 * UNIVERSAL GOOGLE SERVICES FIXER (part 2) — light availability checks.
 */
public class GooglePlayServicesUtilLight {

    public static final String GMS_PACKAGE = "com.google.android.gms";
    public static final String GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending";
    public static int GOOGLE_PLAY_SERVICES_VERSION_CODE = 243313000;

    public static int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return ConnectionResult.SUCCESS;
    }

    public static int isGooglePlayServicesAvailable(Context context) {
        return ConnectionResult.SUCCESS;
    }

    public static boolean isUserRecoverableError(int errorCode) {
        return false;
    }

    public static String getErrorString(int errorCode) {
        return "SUCCESS";
    }

    public static int getApkVersion(Context context) {
        return GOOGLE_PLAY_SERVICES_VERSION_CODE;
    }

    public static boolean isDeviceRestricted(Context context) {
        return false;
    }

    public static boolean isPlayServicesPossiblyUpdating(Context context, int errorCode) {
        return false;
    }
}
