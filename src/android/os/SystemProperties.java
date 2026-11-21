package android.os;

import java.util.HashMap;
import java.util.Map;

public class SystemProperties {
    private static final Map<String, String> PROPS = new HashMap<>();

    static {
        PROPS.put("ro.build.version.release", "10");
        PROPS.put("ro.build.version.sdk", "29");
        PROPS.put("ro.build.version.incremental", "6578210");
        PROPS.put("ro.build.version.codename", "REL");
        PROPS.put("ro.build.version.all_codenames", "REL");
        PROPS.put("ro.build.id", "QQ3A.200805.001");
        PROPS.put("ro.build.display.id", "QQ3A.200805.001");
        PROPS.put("ro.build.date.utc", "1596240000");
        PROPS.put("ro.build.type", "user");
        PROPS.put("ro.build.tags", "release-keys");
        PROPS.put("ro.build.user", "android-build");
        PROPS.put("ro.build.host", "abfarm-builder");
        PROPS.put("ro.build.fingerprint", "google/flame/flame:10/QQ3A.200805.001/6578210:user/release-keys");

        PROPS.put("ro.product.model", "Pixel 4");
        PROPS.put("ro.product.brand", "google");
        PROPS.put("ro.product.manufacturer", "Google");
        PROPS.put("ro.product.name", "flame");
        PROPS.put("ro.product.device", "flame");
        PROPS.put("ro.product.board", "flame");
        PROPS.put("ro.product.hardware", "flame");

        PROPS.put("ro.product.cpu.abi", "arm64-v8a");
        PROPS.put("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a,armeabi");
        PROPS.put("ro.product.cpu.abilist64", "arm64-v8a");
        PROPS.put("ro.product.cpu.abilist32", "armeabi-v7a,armeabi");

        PROPS.put("ro.board.platform", "msm7227a");
        PROPS.put("ro.bootloader", "unknown");
        PROPS.put("ro.hardware", "flame");
        PROPS.put("ro.boot.container", "0");
        PROPS.put("ro.treble.enabled", "true");
        PROPS.put("ro.debuggable", "0");

        PROPS.put("persist.sys.timezone", "Asia/Kolkata");
        PROPS.put("persist.sys.locale", "en-IN");
    }

    public static String get(String key) {
        return PROPS.getOrDefault(key, "");
    }

    public static String get(String key, String def) {
        String val = PROPS.get(key);
        return (val != null && !val.isEmpty()) ? val : def;
    }

    public static int getInt(String key, int def) {
        String val = PROPS.get(key);
        if (val == null || val.isEmpty()) return def;
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }

    public static long getLong(String key, long def) {
        String val = PROPS.get(key);
        if (val == null || val.isEmpty()) return def;
        try { return Long.parseLong(val); } catch (Exception e) { return def; }
    }

    public static boolean getBoolean(String key, boolean def) {
        String val = PROPS.get(key);
        if (val == null || val.isEmpty()) return def;
        return Boolean.parseBoolean(val);
    }

    public static void set(String key, String val) {
        PROPS.put(key, val);
    }

    public static void addChangeCallback(Runnable callback) {}
    public static void reportSyspropChanged() {}
    public static String digestOf(String... keys) { return ""; }
}
