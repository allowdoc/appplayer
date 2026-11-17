package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;

public class GoogleApiAvailability extends GoogleApiAvailabilityLight {
    private static final GoogleApiAvailability INSTANCE = new GoogleApiAvailability();

    public static GoogleApiAvailability getInstance() {
        return INSTANCE;
    }

    @Override
    public int isGooglePlayServicesAvailable(Context context) {
        return 0; // ConnectionResult.SUCCESS
    }

    @Override
    public int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return 0; // ConnectionResult.SUCCESS
    }

    public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode) {
        return null;
    }

    public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode, DialogInterface.OnCancelListener cancelListener) {
        return null;
    }

    public boolean showErrorDialogFragment(Activity activity, int errorCode, int requestCode) {
        return false;
    }

    public boolean showErrorDialogFragment(Activity activity, int errorCode, int requestCode, DialogInterface.OnCancelListener cancelListener) {
        return false;
    }

    public void showErrorNotification(Context context, int errorCode) {}

    public void setDefaultNotificationChannelId(Context context, String channelId) {}
}
