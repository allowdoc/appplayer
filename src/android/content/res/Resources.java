package android.content.res;

import android.animation.Animator;
import android.animation.StateListAnimator;
import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableInflater;
import android.os.Bundle;
import android.util.*;
import android.view.DisplayAdjustments;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Resources {
    public static final int ID_NULL = 0;
    static final String TAG = "Resources";
    static Resources mSystem = null;

    final ClassLoader mClassLoader;
    final AssetManager mAssets;
    final DisplayMetrics mMetrics;
    final Configuration mConfiguration;
    private String mPackageName = "android";
    private final Map<String, Integer> idMap = new HashMap<>();
    private final Map<Integer, String> stringMap = new HashMap<>();
    private final Map<Integer, String> resNameMap = new HashMap<>();

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {}
        public NotFoundException(String name) { super(name); }
    }

    public final class Theme {
        public void applyStyle(int resId, boolean force) {}
        public void setTo(Theme other) {}
        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) { return false; }
        public int[] getAllAttributes() { return new int[0]; }
        public Resources getResources() { return Resources.this; }
        public void dump(int priority, String tag, String prefix) {}
        public int getChangingConfigurations() { return 0; }
        public void rebase() {}
        public String[] getTheme() { return new String[0]; }
        public Drawable getDrawable(int resid) { return null; }

        public TypedArray obtainStyledAttributes(int[] attrs) {
            return new TypedArray(Resources.this, attrs, null, attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(int resid, int[] attrs) {
            return new TypedArray(Resources.this, attrs, null, attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            org.headless.runner.core.RealXmlResourceParser.Node node = null;
            if (set instanceof org.headless.runner.core.RealXmlResourceParser) {
                node = ((org.headless.runner.core.RealXmlResourceParser) set).currentNode();
            }
            return new TypedArray(Resources.this, attrs, node, attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) {
            return obtainStyledAttributes(set, attrs, 0, 0);
        }

        public TypedArray resolveAttributes(int[] values, int[] attrs) {
            return new TypedArray(Resources.this, attrs, null, attrs != null ? attrs.length : 0);
        }
    }

    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        this.mClassLoader = ClassLoader.getSystemClassLoader();
        this.mAssets = (assets != null) ? assets : new AssetManager();

        if (metrics != null) {
            this.mMetrics = metrics;
        } else {
            this.mMetrics = new DisplayMetrics();
            this.mMetrics.widthPixels = 1080;
            this.mMetrics.heightPixels = 1920;
            this.mMetrics.density = 2.625f;
            this.mMetrics.densityDpi = 420;
        }

        this.mConfiguration = (config != null) ? config : new Configuration();
        this.mConfiguration.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;

        initDefaultStrings();
    }

    public Resources(ClassLoader classLoader) {
        this(new AssetManager(), null, null);
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
    }

    private void initDefaultStrings() {
        registerSyntheticString("google_app_id", "1:1234567890:android:abcdef0123456789");
        registerSyntheticString("gcm_defaultSenderId", "1234567890");
        registerSyntheticString("project_id", "generic-app-prod");
        registerSyntheticString("default_web_client_id", "1234567890-abcdef.apps.googleusercontent.com");
        registerSyntheticString("firebase_database_url", "https://generic-app.firebaseio.com");
        registerSyntheticString("common_google_play_services_unknown_issue", "Google Play services error");
        registerSyntheticString("app_name", "Android App");

        File tsv = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data\\strings.tsv");
        if (tsv.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(tsv), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\t", 3);
                    if (parts.length >= 3) {
                        try {
                            int id = Integer.parseInt(parts[0]);
                            String name = parts[1];
                            String val = parts[2].replace("\\n", "\n");
                            idMap.put(name, id);
                            stringMap.put(id, val);
                            resNameMap.put(id, name);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }

        File idsFile = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data\\ids.tsv");
        if (idsFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(idsFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split("\t", 2);
                    if (parts.length >= 2) {
                        try {
                            int id = Integer.parseInt(parts[0]);
                            String name = parts[1];
                            idMap.put(name, id);
                            resNameMap.put(id, name);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        System.out.println("[+] [Resources] Loaded " + resNameMap.size() + " resource names and " + stringMap.size() + " strings!");
    }

    public void registerSyntheticString(String name, String value) {
        int id = Math.abs(name.hashCode());
        if (id == 0) id = 1;
        idMap.put(name, id);
        stringMap.put(id, value);
    }

    public static synchronized Resources getSystem() {
        if (mSystem == null) {
            mSystem = new Resources(ClassLoader.getSystemClassLoader());
        }
        return mSystem;
    }

    public static int selectDefaultTheme(int curTheme, int targetSdkVersion) { return 0; }
    public static int selectSystemTheme(int curTheme, int targetSdkVersion, int orig, int holodark, int hololight, int deviceDefault) { return 0; }
    public static int getAttributeSetSourceResId(android.util.AttributeSet set) { return 0; }

    public ClassLoader getClassLoader() { return mClassLoader; }
    public final AssetManager getAssets() { return mAssets; }
    public DisplayMetrics getDisplayMetrics() { return mMetrics; }
    public Configuration getConfiguration() { return mConfiguration; }
    public DisplayAdjustments getDisplayAdjustments() { return new DisplayAdjustments(); }
    public Configuration[] getSizeConfigurations() { return new Configuration[0]; }
    public final Theme newTheme() { return new Theme(); }

    public TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
        org.headless.runner.core.RealXmlResourceParser.Node node = null;
        if (set instanceof org.headless.runner.core.RealXmlResourceParser) {
            node = ((org.headless.runner.core.RealXmlResourceParser) set).currentNode();
        }
        return new TypedArray(this, attrs, node, attrs != null ? attrs.length : 0);
    }

    public CharSequence getText(int id) { return getString(id); }
    public CharSequence getText(int id, CharSequence def) {
        String val = stringMap.get(id);
        return (val != null) ? val : def;
    }
    public CharSequence[] getTextArray(int id) { return new CharSequence[0]; }

    public String getString(int id) {
        String val = stringMap.get(id);
        return (val != null) ? val : "";
    }

    public String getString(int id, Object... formatArgs) { return getString(id); }
    public String[] getStringArray(int id) { return new String[0]; }
    public int[] getIntArray(int id) { return new int[0]; }

    public CharSequence getQuantityText(int id, int quantity) { return getString(id); }
    public String getQuantityString(int id, int quantity, Object... formatArgs) { return getString(id); }
    public String getQuantityString(int id, int quantity) { return getString(id); }

    public int getIdentifier(String name, String defType, String defPackage) {
        if (name == null) return 0;
        Integer id = idMap.get(name);
        if (id != null) return id;
        int newId = Math.abs(name.hashCode());
        if (newId == 0) newId = 1;
        idMap.put(name, newId);
        stringMap.put(newId, name);
        return newId;
    }

    public String getResourceName(int resid) { return getResourcePackageName(resid) + ":string/" + getResourceEntryName(resid); }
    public String getResourcePackageName(int resid) { return mPackageName != null ? mPackageName : "android"; }
    public String getResourceTypeName(int resid) { return "string"; }
    public String getResourceEntryName(int resid) {
        String name = resNameMap.get(resid);
        return name != null ? name : "entry_" + resid;
    }
    public String getLastResourceResolution() { return ""; }
    public static boolean resourceHasPackage(int resid) { return true; }

    public float getDimension(int id) { return 0.0f; }
    public int getDimensionPixelOffset(int id) { return 0; }
    public int getDimensionPixelSize(int id) { return 0; }
    public float getFraction(int id, int base, int pbase) { return 0.0f; }

    public int getColor(int id) { return 0xFF000000; }
    public int getColor(int id, Theme theme) { return 0xFF000000; }
    public ColorStateList getColorStateList(int id) { return ColorStateList.valueOf(0xFF000000); }
    public ColorStateList getColorStateList(int id, Theme theme) { return ColorStateList.valueOf(0xFF000000); }

    public boolean getBoolean(int id) { return false; }
    public int getInteger(int id) { return 0; }
    public float getFloat(int id) { return 0.0f; }

    public Drawable getDrawable(int id) { return new org.headless.runner.core.SyntheticDrawable(); }
    public Drawable getDrawable(int id, Theme theme) { return new org.headless.runner.core.SyntheticDrawable(); }
    public Drawable getDrawableForDensity(int id, int density) { return new org.headless.runner.core.SyntheticDrawable(); }
    public Drawable getDrawableForDensity(int id, int density, Theme theme) { return new org.headless.runner.core.SyntheticDrawable(); }
    public Movie getMovie(int id) { return null; }
    public Typeface getFont(int id) { return null; }
    public void preloadFonts(int id) {}

    public CompatibilityInfo getCompatibilityInfo() { return CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO; }

    public ConfigurationBoundResourceCache getStateListAnimatorCache() { return null; }

    public XmlResourceParser getLayout(int id) {
        XmlResourceParser p = org.headless.runner.core.AxmlLayoutTreeParser.getParser(id);
        if (p != null) return p;
        return new org.headless.runner.core.SyntheticXmlResourceParser("FrameLayout");
    }
    public XmlResourceParser getAnimation(int id) { return new org.headless.runner.core.SyntheticXmlResourceParser("linearInterpolator"); }
    public XmlResourceParser getXml(int id) { return new org.headless.runner.core.SyntheticXmlResourceParser("xml"); }

    public InputStream openRawResource(int id) { return new ByteArrayInputStream(new byte[0]); }
    public InputStream openRawResource(int id, TypedValue value) { return openRawResource(id); }
    public AssetFileDescriptor openRawResourceFd(int id) { return null; }

    public void getValue(int id, TypedValue outValue, boolean resolveRefs) {}
    public void getValueForDensity(int id, int density, TypedValue outValue, boolean resolveRefs) {}
    public void getValue(String name, TypedValue outValue, boolean resolveRefs) {}

    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {}
    public void parseBundleExtras(XmlResourceParser parser, Bundle outBundle) throws XmlPullParserException, IOException {}
    public void parseBundleExtra(String tagName, AttributeSet attrs, Bundle outBundle) throws XmlPullParserException {}
    public final void flushLayoutCache() {}
    public final void startPreloading() {}
    public final void finishPreloading() {}
    public int calcConfigChanges(Configuration config) { return 0; }
}
