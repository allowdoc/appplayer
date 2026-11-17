package com.google.android.gms.common;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;

public class GoogleApiAvailabilityLight {
    public static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";
    public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE = 233013000;
    public static final String GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending";

    private static final GoogleApiAvailabilityLight INSTANCE = new GoogleApiAvailabilityLight();

    public static GoogleApiAvailabilityLight getInstance() {
        return INSTANCE;
    }

    public int isGooglePlayServicesAvailable(Context context) {
        return isGooglePlayServicesAvailable(context, GOOGLE_PLAY_SERVICES_VERSION_CODE);
    }

    public int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return 0; // ConnectionResult.SUCCESS
    }

    public int getApkVersion(Context context) {
        return GOOGLE_PLAY_SERVICES_VERSION_CODE;
    }

    public int getClientVersion(Context context) {
        return GOOGLE_PLAY_SERVICES_VERSION_CODE;
    }

    public String getErrorString(int errorCode) {
        return "SUCCESS";
    }

    public boolean isUserResolvableError(int errorCode) {
        return false;
    }

    public boolean isPlayServicesPossiblyUpdating(Context context, int errorCode) {
        return false;
    }

    public boolean isPlayStorePossiblyUpdating(Context context, int errorCode) {
        return false;
    }

    public boolean isUninstalledAppPossiblyUpdating(Context context, String packageName) {
        return false;
    }

    public Intent getErrorResolutionIntent(int errorCode) {
        return null;
    }

    public Intent getErrorResolutionIntent(Context context, int errorCode, String trackingSource) {
        return null;
    }

    public PendingIntent getErrorResolutionPendingIntent(Context context, int errorCode, int requestCode) {
        return null;
    }

    public PendingIntent getErrorResolutionPendingIntent(Context context, int errorCode, int requestCode, String trackingSource) {
        return null;
    }

    public void cancelAvailabilityErrorNotifications(Context context) {}

    public void verifyGooglePlayServicesIsAvailable(Context context, int minApkVersion) {}
}
