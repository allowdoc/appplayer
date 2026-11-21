package org.headless.runner.core;

import android.os.SystemProperties;

public class DeviceProfile {
    // 1. Device Hardware Identity
    public final String brand = "google";
    public final String manufacturer = "Google";
    public final String model = "Pixel 5";
    public final String device = "redfin";
    public final String product = "redfin";
    public final String board = "redfin";
    public final String hardware = "redfin";
    public final String bootloader = "c2f-0.4-7484666";
    public final String serial = "984AY048J";

    // 2. OS & Build Metadata (Aligned with Android 10 / API 29 framework)
    public final String osVersion = "10";
    public final int sdkInt = 29;
    public final String buildId = "QQ3A.200805.001";
    public final String buildType = "user";
    public final String buildTags = "release-keys";
    public final String fingerprint = "google/flame/flame:10/QQ3A.200805.001/6578210:user/release-keys";

    // 3. Telephony & Cellular Network (generic GSM profile — no carrier hardcoding)
    public final String networkOperator = "00101";
    public final String networkOperatorName = "";
    public final String simOperator = "00101";
    public final String simOperatorName = "";
    public final String countryIso = "us";
    public final int simState = 5; // SIM_STATE_READY
    public final int networkType = 13; // NETWORK_TYPE_LTE
    public final int dataState = 2; // DATA_CONNECTED
    public final String imei = "356938035643809";

    // 4. Battery & Power
    public final int batteryLevel = 94;
    public final int batteryScale = 100;
    public final int batteryTemperature = 285; // 28.5 C
    public final int batteryStatus = 3; // BATTERY_STATUS_DISCHARGING

    private static DeviceProfile sInstance = new DeviceProfile();

    public static DeviceProfile get() {
        return sInstance;
    }

    /**
     * Injects the pristine, genuine device profile into SystemProperties
     * so apps querying Build, hardware, or properties receive authentic physical device data.
     */
    public void applyToSystemProperties() {
        SystemProperties.set("ro.product.brand", brand);
        SystemProperties.set("ro.product.manufacturer", manufacturer);
        SystemProperties.set("ro.product.model", model);
        SystemProperties.set("ro.product.device", device);
        SystemProperties.set("ro.product.name", product);
        SystemProperties.set("ro.product.board", board);
        SystemProperties.set("ro.product.hardware", hardware);
        SystemProperties.set("ro.hardware", hardware);
        SystemProperties.set("ro.bootloader", bootloader);
        SystemProperties.set("ro.serialno", serial);

        SystemProperties.set("ro.build.version.release", osVersion);
        SystemProperties.set("ro.build.version.sdk", String.valueOf(sdkInt));
        SystemProperties.set("ro.build.id", buildId);
        SystemProperties.set("ro.build.display.id", buildId);
        SystemProperties.set("ro.build.type", buildType);
        SystemProperties.set("ro.build.tags", buildTags);
        SystemProperties.set("ro.build.fingerprint", fingerprint);
        SystemProperties.set("ro.debuggable", "0");
        SystemProperties.set("ro.secure", "1");

        // Clean SELinux & Non-Root indicators
        SystemProperties.set("ro.boot.selinux", "1");
        SystemProperties.set("ro.build.selinux", "1");
        SystemProperties.set("ro.product.cpu.abi", "arm64-v8a");
        SystemProperties.set("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a,armeabi");
        SystemProperties.set("ro.product.cpu.abilist64", "arm64-v8a");
        SystemProperties.set("ro.product.cpu.abilist32", "armeabi-v7a,armeabi");
        SystemProperties.set("persist.sys.timezone", "UTC");
        SystemProperties.set("persist.sys.locale", "en-US");

        System.out.println("[+] [DeviceProfile] Pristine Pixel 5 (generic GSM / user / release-keys) environment active.");
    }
}
