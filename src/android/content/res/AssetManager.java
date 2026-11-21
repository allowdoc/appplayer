package android.content.res;

import android.util.SparseArray;
import android.util.TypedValue;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Universal In-Process Android AssetManager.
 * Zero VM, Zero QEMU, Zero background daemons.
 * Directly reads genuine asset streams and listings from APK zips and workspace directories.
 */
public final class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0;
    public static final int ACCESS_RANDOM = 1;
    public static final int ACCESS_STREAMING = 2;
    public static final int ACCESS_BUFFER = 3;

    private static String sDefaultApkPath = null;
    private static AssetManager sSystem = null;
    private final List<String> mApkPaths = new ArrayList<>();

    public static synchronized void setDefaultApkPath(String apkPath) {
        sDefaultApkPath = apkPath;
    }

    public static synchronized String getDefaultApkPath() {
        return sDefaultApkPath;
    }

    public AssetManager() {
        if (sDefaultApkPath != null && !sDefaultApkPath.isEmpty()) {
            mApkPaths.add(sDefaultApkPath);
        }
    }

    public static synchronized AssetManager getSystem() {
        if (sSystem == null) {
            sSystem = new AssetManager();
        }
        return sSystem;
    }

    @Override
    public void close() {
        mApkPaths.clear();
    }

    public void setApkAssets(Object[] apkAssets, boolean invalidateCaches) {}
    public Object[] getApkAssets() { return new Object[0]; }
    public String[] getApkPaths() { return mApkPaths.toArray(new String[0]); }
    public int findCookieForPath(String path) { return 1; }

    public int addAssetPath(String path) {
        if (path != null && !mApkPaths.contains(path)) {
            mApkPaths.add(path);
        }
        return mApkPaths.size();
    }

    public int addAssetPathAsSharedLibrary(String path) {
        return addAssetPath(path);
    }

    public int addOverlayPath(String path) { return 0; }

    boolean getResourceValue(int ident, int density, TypedValue outValue, boolean resolveRefs) { return false; }
    CharSequence getResourceText(int ident) { return ""; }
    CharSequence getResourceBagText(int ident, int bagEntryId) { return ""; }
    int getResourceArraySize(int ident) { return 0; }
    int getResourceArray(int ident, int[] outArray) { return 0; }
    String[] getResourceStringArray(int ident) { return new String[0]; }
    CharSequence[] getResourceTextArray(int ident) { return new CharSequence[0]; }
    int[] getResourceIntArray(int ident) { return new int[0]; }
    int[] getStyleAttributes(int runStId) { return new int[0]; }
    boolean getThemeValue(long theme, int ident, TypedValue outValue, boolean resolveRefs) { return false; }
    void dumpTheme(long theme, int priority, String tag, String prefix) {}
    String getResourceName(int resid) { return ""; }
    String getResourcePackageName(int resid) { return ""; }
    String getResourceTypeName(int resid) { return ""; }
    String getResourceEntryName(int resid) { return ""; }
    int getResourceIdentifier(String name, String defType, String defPackage) { return 0; }
    public void setResourceResolutionLoggingEnabled(boolean enabled) {}
    public String getLastResourceResolution() { return ""; }
    CharSequence getPooledStringForCookie(int cookie, int id) { return ""; }

    public InputStream open(String fileName) throws IOException {
        return open(fileName, ACCESS_STREAMING);
    }

    public InputStream open(String fileName, int accessMode) throws IOException {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName cannot be null");
        }
        String clean = fileName.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        String assetPath = clean.startsWith("assets/") ? clean : ("assets/" + clean);

        // 1. Try local filesystem
        File f1 = new File(clean);
        if (f1.exists() && f1.isFile()) {
            return new FileInputStream(f1);
        }
        File f2 = new File("assets", clean);
        if (f2.exists() && f2.isFile()) {
            return new FileInputStream(f2);
        }
        File f3 = new File(assetPath);
        if (f3.exists() && f3.isFile()) {
            return new FileInputStream(f3);
        }

        // 2. Try configured APK paths
        List<String> paths = new ArrayList<>(mApkPaths);
        if (sDefaultApkPath != null && !paths.contains(sDefaultApkPath)) {
            paths.add(sDefaultApkPath);
        }

        for (String apk : paths) {
            File apkFile = new File(apk);
            if (!apkFile.exists() || !apkFile.isFile()) continue;
            try {
                ZipFile zf = new ZipFile(apkFile);
                ZipEntry entry = zf.getEntry(assetPath);
                if (entry == null) {
                    entry = zf.getEntry(clean);
                }
                if (entry != null) {
                    byte[] bytes;
                    try (InputStream is = zf.getInputStream(entry)) {
                        bytes = is.readAllBytes();
                    }
                    zf.close();
                    return new ByteArrayInputStream(bytes);
                }
                zf.close();
            } catch (Throwable ignored) {}
        }

        throw new FileNotFoundException("Asset file not found in APK or filesystem: " + fileName);
    }

    public AssetFileDescriptor openFd(String fileName) throws IOException {
        throw new IOException("Asset openFd not supported in synthetic mode: " + fileName);
    }

    public String[] list(String path) throws IOException {
        String clean = path == null ? "" : path.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        String prefix = clean.isEmpty() ? "assets/" : (clean.startsWith("assets/") ? (clean + "/") : ("assets/" + clean + "/"));

        Set<String> entries = new LinkedHashSet<>();

        // Local directory
        File dir = clean.isEmpty() ? new File("assets") : new File("assets", clean);
        if (dir.exists() && dir.isDirectory()) {
            String[] list = dir.list();
            if (list != null) {
                Collections.addAll(entries, list);
            }
        }

        // APK zip entries
        List<String> paths = new ArrayList<>(mApkPaths);
        if (sDefaultApkPath != null && !paths.contains(sDefaultApkPath)) {
            paths.add(sDefaultApkPath);
        }

        for (String apk : paths) {
            File apkFile = new File(apk);
            if (!apkFile.exists() || !apkFile.isFile()) continue;
            try (ZipFile zf = new ZipFile(apkFile)) {
                Enumeration<? extends ZipEntry> e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry ze = e.nextElement();
                    String name = ze.getName();
                    if (name.startsWith(prefix) && name.length() > prefix.length()) {
                        String rel = name.substring(prefix.length());
                        int slash = rel.indexOf('/');
                        if (slash > 0) {
                            entries.add(rel.substring(0, slash));
                        } else if (!rel.isEmpty()) {
                            entries.add(rel);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return entries.toArray(new String[0]);
    }

    public InputStream openNonAsset(String fileName) throws IOException {
        return open(fileName);
    }

    public InputStream openNonAsset(String fileName, int accessMode) throws IOException {
        return open(fileName, accessMode);
    }

    public InputStream openNonAsset(int cookie, String fileName) throws IOException {
        return open(fileName);
    }

    public InputStream openNonAsset(int cookie, String fileName, int accessMode) throws IOException {
        return open(fileName, accessMode);
    }

    public AssetFileDescriptor openNonAssetFd(String fileName) throws IOException {
        return openFd(fileName);
    }

    public AssetFileDescriptor openNonAssetFd(int cookie, String fileName) throws IOException {
        return openFd(fileName);
    }

    public XmlResourceParser openXmlResourceParser(String fileName) throws IOException {
        return null;
    }

    public XmlResourceParser openXmlResourceParser(int cookie, String fileName) throws IOException {
        return null;
    }

    public boolean isUpToDate() { return true; }
    public String[] getLocales() { return new String[]{"en-US"}; }
    public String[] getNonSystemLocales() { return new String[]{"en-US"}; }
    Configuration[] getSizeConfigurations() { return new Configuration[0]; }
    public void setConfiguration(int mcc, int mnc, String locale, int orientation, int touchscreen,
                                 int density, int keyboard, int keyboardHidden, int navigation,
                                 int screenWidth, int screenHeight, int smallestScreenWidthDp,
                                 int screenWidthDp, int screenHeightDp, int screenLayout, int uiMode,
                                 int colorMode, int majorVersion) {}
    public SparseArray<String> getAssignedPackageIdentifiers() { return new SparseArray<>(); }
    public Map<String, String> getOverlayableMap(String packageName) { return Collections.emptyMap(); }

    public static int getGlobalAssetCount() { return 0; }
    public static String getAssetAllocations() { return ""; }
    public static int getGlobalAssetManagerCount() { return 0; }
}
