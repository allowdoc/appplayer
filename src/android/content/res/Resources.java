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
import org.headless.runner.core.ArscParser;
import org.headless.runner.core.RealXmlResourceParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    /** Compiled resource table of the running APK (null when unavailable). */
    private volatile ArscParser mArsc;

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {}
        public NotFoundException(String name) { super(name); }
    }

    public final class Theme {
        private final Map<Integer, ArscParser.ResVal> mValues = new LinkedHashMap<>();
        private int mStyleResId = 0;

        public void applyStyle(int resId, boolean force) {
            if (resId == 0) return;
            mStyleResId = resId;
            if (mValues.isEmpty()) {
                seedAppCompatDefaults(mValues);
            }
            ArscParser a = mArsc;
            if (a != null) {
                mValues.putAll(a.resolveTheme(resId));
            }
        }

        /** AppCompat-safe baseline so androidx views always find a valid theme. */
        private void seedAppCompatDefaults(Map<Integer, ArscParser.ResVal> m) {
            ArscParser a = mArsc;
            int abId = a != null ? a.getIdentifier("attr", "windowActionBar") : 0;
            int aboId = a != null ? a.getIdentifier("attr", "windowActionBarOverlay") : 0;
            int amoId = a != null ? a.getIdentifier("attr", "windowActionModeOverlay") : 0;
            int ntId = a != null ? a.getIdentifier("attr", "windowNoTitle") : 0;
            int flId = a != null ? a.getIdentifier("attr", "windowIsFloating") : 0;
            putBool(m, abId != 0 ? abId : 0x010102cd, false);  // windowActionBar (framework)
            putBool(m, aboId != 0 ? aboId : 0x010102eb, false); // windowActionBarOverlay
            putBool(m, ntId != 0 ? ntId : 0x01010056, true);   // windowNoTitle
            putBool(m, flId != 0 ? flId : 0x01010057, false);  // windowIsFloating
            if (amoId != 0) putBool(m, amoId, false);          // windowActionModeOverlay
            // also mirror the framework ids so framework views resolve them too
            if (abId != 0) putBool(m, 0x010102cd, false);
            if (ntId != 0) putBool(m, 0x01010056, true);
        }

        private static void putBool(Map<Integer, ArscParser.ResVal> m, int id, boolean v) {
            if (id != 0) m.put(id, new ArscParser.ResVal(ArscParser.TYPE_INT_BOOLEAN, v ? 1 : 0, null));
        }

        public void setTo(Theme other) {
            if (other != null) {
                mValues.clear();
                mValues.putAll(other.mValues);
                mStyleResId = other.mStyleResId;
            }
        }

        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
            ArscParser.ResVal v = mValues.get(resid);
            if (v == null) {
                // Resources-level theme defaults: values of the theme the runner
                // applied to the activity (applyThemeBase publishes them). Window
                // wrapper / inflater / dialog themes never got applyStyle() on
                // their own instance - without this fallback Material's
                // ThemeEnforcement (obtainStyledAttributes.hasValue) fails and
                // every Material app aborts at first inflation.
                v = defaultThemeValues.get(resid);
            }
            if (v == null && mArsc != null && resid != 0) {
                // Framework semantics: resolveAttribute works for ATTRIBUTE ids
                // (e.g. ?attr/foo lookups). Returning values for layout/string
                // ids broke LayoutInflater.parseInclude which relies on a false
                // return for non-attr resIds to keep its own resolved id.
                ArscParser.Entry e = mArsc.getEntry(resid);
                if (e != null && "attr".equals(e.type)) {
                    v = mArsc.getValue(resid);
                }
            }
            if (v == null) return false;
            copyToTypedValue(outValue, v, resolveRefs, mArsc);
            return true;
        }

        public int[] getAllAttributes() {
            int[] ids = new int[mValues.size()];
            int i = 0;
            for (Integer k : mValues.keySet()) ids[i++] = k;
            return ids;
        }

        public Resources getResources() { return Resources.this; }
        public void dump(int priority, String tag, String prefix) {}
        public int getChangingConfigurations() { return 0; }
        public void rebase() {}
        public String[] getTheme() { return new String[0]; }
        public Drawable getDrawable(int resid) { return Resources.this.getDrawable(resid); }

        public TypedArray obtainStyledAttributes(int[] attrs) {
            Map<Integer, TypedValue> vals = new LinkedHashMap<>();
            if (attrs != null) {
                for (int attrId : attrs) {
                    ArscParser.ResVal v = mValues.get(attrId);
                    if (v == null) {
                        // Resources-level theme defaults (see resolveAttribute).
                        // ThemeEnforcement checks in Material apps run against
                        // inflater/window context themes that never had the
                        // activity style applied to their own instance.
                        v = defaultThemeValues.get(attrId);
                    }
                    if (v != null) vals.put(attrId, toTypedValue(v));
                }
            }
            return new TypedArray(Resources.this, attrs, vals, null,
                    attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(int resid, int[] attrs) {
            Map<Integer, TypedValue> vals = new LinkedHashMap<>();
            ArscParser a = mArsc;
            if (a != null && resid != 0) {
                collectStyleValues(resid, vals);
            }
            resolveAttributeChains(vals);
            return new TypedArray(Resources.this, attrs, vals, null,
                    attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            org.headless.runner.core.RealXmlResourceParser.Node node = null;
            if (set instanceof org.headless.runner.core.RealXmlResourceParser) {
                node = ((org.headless.runner.core.RealXmlResourceParser) set).currentNode();
            }
            Map<Integer, TypedValue> vals = new LinkedHashMap<>();
            ArscParser a = mArsc;
            if (a != null) {
                // 1. defStyleAttr: attr id -> theme value (style reference) -> style bag
                if (defStyleAttr != 0) {
                    ArscParser.ResVal defVal = mValues.get(defStyleAttr);
                    if (defVal != null && defVal.isReference()) {
                        collectStyleValues(defVal.data, vals);
                    }
                }
                // 2. explicit style reference from the XML node (style="...")
                if (node != null) {
                    int styleId = 0;
                    Integer explicit = node.attrResIds.get(0x01010039); // android.R.attr.style
                    if (explicit != null) {
                        styleId = explicit;
                    } else {
                        Object sv = node.attributes.get("style");
                        if (sv != null) {
                            styleId = getIdentifier(String.valueOf(sv), "style", mPackageName);
                        }
                    }
                    if (styleId != 0) collectStyleValues(styleId, vals);
                }
                // 3. defStyleRes: direct style resource
                if (defStyleRes != 0) collectStyleValues(defStyleRes, vals);
                // 4. theme-level defaults for whatever is still missing
                if (attrs != null) {
                    for (int attrId : attrs) {
                        if (!vals.containsKey(attrId)) {
                            ArscParser.ResVal v = mValues.get(attrId);
                            if (v != null) vals.put(attrId, toTypedValue(v));
                        }
                    }
                }
            }
            // 5. androidfw: ?attr values (TYPE_ATTRIBUTE) from style bags are
            //    resolved THROUGH THE THEME at obtainStyledAttributes time
            //    (ResTable::getBagAttribute loop), never handed to the app raw.
            resolveAttributeChains(vals);
            return new TypedArray(Resources.this, attrs, vals, node,
                    attrs != null ? attrs.length : 0);
        }

        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) {
            return obtainStyledAttributes(set, attrs, 0, 0);
        }

        public TypedArray resolveAttributes(int[] values, int[] attrs) {
            Map<Integer, TypedValue> vals = new LinkedHashMap<>();
            if (attrs != null) {
                for (int attrId : attrs) {
                    ArscParser.ResVal v = mValues.get(attrId);
                    if (v != null) vals.put(attrId, toTypedValue(v));
                }
            }
            resolveAttributeChains(vals);
            return new TypedArray(Resources.this, attrs, vals, null,
                    attrs != null ? attrs.length : 0);
        }

        /**
         * Resolve remaining ?attr (TYPE_ATTRIBUTE) values through this theme,
         * exactly like androidfw's bag-attribute resolution loop: a style bag
         * may say background="?attr/colorPrimary"; the APP receives the theme's
         * concrete value for colorPrimary (possibly itself another ?attr -
         * follow the chain, cycle-guarded, device semantics).
         */
        private void resolveAttributeChains(Map<Integer, TypedValue> vals) {
            if (vals.isEmpty()) return;
            for (Map.Entry<Integer, TypedValue> e : vals.entrySet()) {
                TypedValue tv = e.getValue();
                int hops = 0;
                while (tv != null && tv.type == TypedValue.TYPE_ATTRIBUTE && hops < 8) {
                    ArscParser.ResVal next = mValues.get(tv.data);
                    if (next == null) break;
                    tv = toTypedValue(next);
                    hops++;
                }
                e.setValue(tv);
            }
        }

        private void collectStyleValues(int styleResId, Map<Integer, TypedValue> out) {
            ArscParser a = mArsc;
            if (a == null || styleResId == 0) return;
            for (Map.Entry<Integer, ArscParser.ResVal> e : a.resolveTheme(styleResId).entrySet()) {
                if (!out.containsKey(e.getKey())) {
                    out.put(e.getKey(), toTypedValue(e.getValue()));
                }
            }
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

    /**
     * Load the compiled resource table from the APK and optionally resolve the
     * given theme style (activity/application theme) as this Resources' theme
     * base. Safe to call multiple times.
     */
    private java.io.File mApkFile;

    /** The APK backing this resource table (drawable factory lookups). */
    public java.io.File getApkFile() {
        return mApkFile;
    }

    public void loadArsc(File apkFile, int themeResId) {
        if (apkFile == null || !apkFile.exists()) return;
        this.mApkFile = apkFile;
        if (mArsc != null) {
            applyThemeBase(themeResId);
            return;
        }
        ArscParser parser = ArscParser.fromApk(apkFile);
        if (parser != null && parser.isParsed()) {
            mArsc = parser;
            System.out.println("[+] [ArscParser] Loaded resources.arsc: "
                    + "package 0x" + Integer.toHexString(parser.packageId));
            applyThemeBase(themeResId);
        }
    }

    private void applyThemeBase(int themeResId) {
        if (themeResId != 0) {
            Theme t = newTheme();
            t.applyStyle(themeResId, true);
            // Publish resolved values for every future Theme instance of this
            // Resources so ContextThemeWrapper-created themes inherit them.
            defaultThemeValues.clear();
            defaultThemeValues.putAll(t.mValues);
        }
    }

    final Map<Integer, ArscParser.ResVal> defaultThemeValues = new LinkedHashMap<>();

    private void initDefaultStrings() {
        registerSyntheticString("google_app_id", "1:1234567890:android:abcdef0123456789");
        registerSyntheticString("google_api_key", "AIzaSyntheticGenericKey0000000000000");
        registerSyntheticString("google_crash_reporting_api_key", "AIzaSyntheticGenericKey0000000000000");
        registerSyntheticString("google_storage_bucket", "generic-app-prod.appspot.com");
        registerSyntheticString("gcm_defaultSenderId", "1234567890");
        registerSyntheticString("project_id", "generic-app-prod");
        registerSyntheticString("default_web_client_id", "1234567890-abcdef.apps.googleusercontent.com");
        registerSyntheticString("firebase_database_url", "https://generic-app.firebaseio.com");
        registerSyntheticString("common_google_play_services_unknown_issue", "Google Play services error");
        registerSyntheticString("app_name", "Android App");

        File tsv = new File(org.headless.runner.core.RunnerPaths.dataDir(), "strings.tsv");
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

        File idsFile = new File(org.headless.runner.core.RunnerPaths.dataDir(), "ids.tsv");
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
    private Theme mDefaultTheme;

    /** AOSP API: the Resources' cached default theme (created on first use,
     *  seeded with the published default theme values). */
    public final Theme getTheme() {
        synchronized (this) {
            if (mDefaultTheme == null) {
                mDefaultTheme = newTheme();
                if (defaultThemeValues != null && !defaultThemeValues.isEmpty()) {
                    mDefaultTheme.mValues.putAll(defaultThemeValues);
                }
            }
            return mDefaultTheme;
        }
    }

    public final Theme newTheme() {
        Theme t = new Theme();
        if (!defaultThemeValues.isEmpty()) {
            t.mValues.putAll(defaultThemeValues);
        }
        return t;
    }

    public TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
        org.headless.runner.core.RealXmlResourceParser.Node node = null;
        if (set instanceof org.headless.runner.core.RealXmlResourceParser) {
            node = ((org.headless.runner.core.RealXmlResourceParser) set).currentNode();
        }
        return new TypedArray(this, attrs, null, node, attrs != null ? attrs.length : 0);
    }

    public CharSequence getText(int id) {
        String s = getString(id);
        return (s != null) ? s : "";
    }
    public CharSequence getText(int id, CharSequence def) {
        String s = getString(id);
        return (s != null && !s.isEmpty()) ? s : def;
    }
    public CharSequence[] getTextArray(int id) { return new CharSequence[0]; }

    public String getString(int id) {
        ArscParser a = mArsc;
        if (a != null && id != 0) {
            String s = a.getString(id);
            if (s != null) return s;
            ArscParser.Entry e = a.getEntry(id);
            if (e != null) {
                // entry exists but is not a string: render its raw value
                ArscParser.ResVal v = a.getValue(id);
                if (v != null) {
                    String rendered = v.asString();
                    if (rendered != null) return rendered;
                }
                return "";
            }
        }
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
        ArscParser a = mArsc;
        if (a != null) {
            String type = (defType != null && !defType.isEmpty()) ? defType : "string";
            int id = a.getIdentifier(type, name);
            if (id != 0) return id;
        }
        Integer id = idMap.get(name);
        if (id != null) return id;
        int newId = Math.abs(name.hashCode());
        if (newId == 0) newId = 1;
        idMap.put(name, newId);
        stringMap.put(newId, name);
        return newId;
    }

    public String getResourceName(int resid) {
        return getResourcePackageName(resid) + ":" + getEntryTypeOrString(resid) + "/" + getResourceEntryName(resid);
    }
    public String getResourcePackageName(int resid) { return mPackageName != null ? mPackageName : "android"; }
    public String getResourceTypeName(int resid) { return getEntryTypeOrString(resid); }
    public String getResourceEntryName(int resid) {
        ArscParser a = mArsc;
        if (a != null) {
            String n = a.getEntryName(resid);
            if (n != null) return n;
        }
        String name = resNameMap.get(resid);
        return name != null ? name : "entry_" + resid;
    }
    private String getEntryTypeOrString(int resid) {
        ArscParser a = mArsc;
        if (a != null) {
            String t = a.getEntryType(resid);
            if (t != null) return t;
        }
        return "string";
    }
    public String getLastResourceResolution() { return ""; }
    public static boolean resourceHasPackage(int resid) { return true; }

    public float getDimension(int id) {
        ArscParser a = mArsc;
        if (a != null) {
            ArscParser.ResVal v = a.getValue(id);
            if (v != null) {
                float d = v.asDimension(metricsHolder(), 0.0f);
                if (v.type == ArscParser.TYPE_DIMENSION || v.type == ArscParser.TYPE_FRACTION) return d;
            }
        }
        return 0.0f;
    }
    public int getDimensionPixelOffset(int id) { return Math.round(getDimension(id)); }
    public int getDimensionPixelSize(int id) { return Math.round(getDimension(id)); }
    public float getFraction(int id, int base, int pbase) { return 0.0f; }

    private ArscParser.DisplayMetricsHolder metricsHolder() {
        ArscParser.DisplayMetricsHolder h = new ArscParser.DisplayMetricsHolder();
        h.density = mMetrics.density;
        h.scaledDensity = mMetrics.scaledDensity;
        h.xdpi = mMetrics.xdpi;
        return h;
    }

    public int getColor(int id) {
        ArscParser a = mArsc;
        if (a != null) {
            ArscParser.ResVal v = a.getValue(id);
            if (v != null && (v.isColor() || v.type == ArscParser.TYPE_REFERENCE)) {
                return v.asInt(0xFF000000);
            }
        }
        return 0xFF000000;
    }
    public int getColor(int id, Theme theme) { return getColor(id); }
    /**
     * PLATFORM-REAL color-state-list resolution (androidfw loadColorStateList
     * equivalent): follow reference chains, inflate color-list XML files
     * (res/color/*.xml) through the framework's own ColorStateList parser,
     * and fall back to plain colors. This is exactly what a device does -
     * the APP's obfuscated ResourcesCompat then consumes a real ColorStateList.
     */
    public ColorStateList getColorStateList(int id) {
        return loadColorStateList(id, null, 0);
    }
    public ColorStateList getColorStateList(int id, Theme theme) {
        return loadColorStateList(id, theme, 0);
    }

    private ColorStateList loadColorStateList(int id, Theme theme, int depth) {
        if (id == 0 || depth > 8) return null;
        ArscParser a = mArsc;
        // 1. File-valued entry -> inflate the real color-list XML
        if (a != null) {
            try {
                ArscParser.ResVal v = a.getValue(id);
                if (v != null && v.isReference()) {
                    return loadColorStateList(v.data, theme, depth + 1);
                }
                if (v != null && v.isString() && v.str != null && v.str.endsWith(".xml")) {
                    try {
                        XmlResourceParser p = getXml(id);
                        ColorStateList csl = ColorStateList.createFromXml(this, p, theme);
                        if (csl != null) return csl;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        // 2. Framework package (android:color/*) - plain int colors for now
        if ((id >>> 24) == 0x01) {
            return ColorStateList.valueOf(getColor(id));
        }
        // 3. Plain int color value
        try {
            return ColorStateList.valueOf(getColor(id));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean getBoolean(int id) {
        ArscParser a = mArsc;
        if (a != null) return a.getBoolean(id, false);
        return false;
    }
    public int getInteger(int id) {
        ArscParser a = mArsc;
        if (a != null) return a.getInt(id, 0);
        return 0;
    }
    public float getFloat(int id) { return 0.0f; }

    /** XML-valued drawable ids resolve to a real framework VectorDrawable
     *  (pure-Java shadow) so appcompat's self-test and tint pipelines work;
     *  everything else falls back to the placeholder drawable. */
    public Drawable getDrawable(int id) {
        Drawable d = org.headless.runner.core.SyntheticDrawableFactory.vector(this, id, null);
        if (d != null) return d;
        return new org.headless.runner.core.SyntheticDrawable();
    }
    public Drawable getDrawable(int id, Theme theme) {
        Drawable d = org.headless.runner.core.SyntheticDrawableFactory.vector(this, id, theme);
        if (d != null) return d;
        return new org.headless.runner.core.SyntheticDrawable();
    }
    public Drawable getDrawableForDensity(int id, int density) { return getDrawable(id); }
    public Drawable getDrawableForDensity(int id, int density, Theme theme) { return getDrawable(id, theme); }
    public Movie getMovie(int id) { return null; }
    public Typeface getFont(int id) { return null; }
    public void preloadFonts(int id) {}

    public CompatibilityInfo getCompatibilityInfo() { return CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO; }

    public ConfigurationBoundResourceCache getStateListAnimatorCache() { return null; }

    /** Resources-level styled-attribute access (AOSP API surface) - delegates
     *  to this Resources' default theme so callers without a Context work. */
    public TypedArray obtainStyledAttributes(int[] attrs) {
        return getTheme().obtainStyledAttributes(attrs);
    }
    public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
        return getTheme().obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes);
    }

    public XmlResourceParser getLayout(int id) {
        XmlResourceParser p = org.headless.runner.core.AxmlLayoutTreeParser.getParser(id);
        if (p != null) return p;
        throw new NotFoundException("Layout XML resource #0x"
                + Integer.toHexString(id) + " not found in resource table");
    }
    public XmlResourceParser getAnimation(int id) {
        XmlResourceParser p = org.headless.runner.core.AxmlLayoutTreeParser.getParser(id);
        if (p != null) return p;
        throw new NotFoundException("Animation XML resource #0x"
                + Integer.toHexString(id) + " not found in resource table");
    }
    /**
     * Real binary-XML resolution for ANY XML-valued resource id (drawable
     * vectors/shapes, colors, menus, xml, anim). appcompat's
     * VectorDrawableCompat self-test (abc_vector_test) and Material's
     * ThemeEnforcement both parse through here; a dummy parser made every
     * obfuscated appcompat app abort with "configure your build for
     * VectorDrawableCompat".
     */
    public XmlResourceParser getXml(int id) {
        XmlResourceParser p = org.headless.runner.core.AxmlLayoutTreeParser.getParser(id);
        if (p != null) return p;
        // PLATFORM-REAL (AOSP semantics): an unresolvable XML resource id
        // throws NotFoundException exactly like androidfw does. App-side
        // catch blocks (appcompat ResourcesCompat, Material...) handle it
        // gracefully - they do the same on a device. A dummy parser made
        // obfuscated inflate paths fail with confusing XmlPullParser errors.
        throw new NotFoundException("File XML resource #0x"
                + Integer.toHexString(id) + " not found in resource table");
    }

    /**
     * Genuine openRawResource: resolves the resource's file path through the
     * compiled resource table (resources.arsc values for raw/drawable/font
     * file entries ARE the in-APK path, e.g. "res/-k.ttf" even under AAPT
     * path obfuscation) and reads the bytes straight out of the APK zip.
     */
    public InputStream openRawResource(int id) {
        ArscParser a = mArsc;
        if (a != null && id != 0 && mApkFile != null && mApkFile.exists()) {
            String path = null;
            try { path = a.getString(id); } catch (Throwable ignored) {}
            if (path == null) {
                ArscParser.ResVal v = null;
                try { v = a.getValue(id); } catch (Throwable ignored) {}
                if (v != null && v.isString()) path = v.str;
            }
            if (path != null && !path.isEmpty() && !path.startsWith("@")) {
                String[] candidates = {path, "res/" + path, "assets/" + path};
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mApkFile)) {
                    for (String cand : candidates) {
                        java.util.zip.ZipEntry e = zip.getEntry(cand);
                        if (e != null) {
                            try (java.io.InputStream in = zip.getInputStream(e);
                                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                                byte[] buf = new byte[1 << 15];
                                int n;
                                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                                return new ByteArrayInputStream(bos.toByteArray());
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return new ByteArrayInputStream(new byte[0]);
    }
    public InputStream openRawResource(int id, TypedValue value) { return openRawResource(id); }
    public AssetFileDescriptor openRawResourceFd(int id) { return null; }

    public void getValue(int id, TypedValue outValue, boolean resolveRefs) {
        if (outValue == null) return;
        ArscParser a = mArsc;
        if (a != null && id != 0) {
            ArscParser.ResVal v = a.getValue(id);
            if (v != null) {
                copyToTypedValue(outValue, v, resolveRefs, a);
                return;
            }
        }
        // Fallback: string table values
        String s = stringMap.get(id);
        if (s != null) {
            outValue.type = TypedValue.TYPE_STRING;
            outValue.string = s;
            outValue.data = 0;
        }
    }
    public void getValueForDensity(int id, int density, TypedValue outValue, boolean resolveRefs) {
        getValue(id, outValue, resolveRefs);
    }
    public void getValue(String name, TypedValue outValue, boolean resolveRefs) {}

    /** Convert a parsed ResVal into a framework TypedValue. */
    static TypedValue toTypedValue(ArscParser.ResVal v) {
        TypedValue tv = new TypedValue();
        if (v == null) return tv;
        tv.type = v.type;
        tv.data = v.data;
        if (v.str != null) tv.string = v.str;
        if (v.type == ArscParser.TYPE_REFERENCE || v.type == ArscParser.TYPE_ATTRIBUTE) {
            tv.resourceId = v.data;
        }
        return tv;
    }

    static void copyToTypedValue(TypedValue outValue, ArscParser.ResVal v, boolean resolveRefs, ArscParser arsc) {
        if (outValue == null || v == null) return;
        TypedValue tv = toTypedValue(v);
        outValue.type = tv.type;
        outValue.data = tv.data;
        outValue.string = tv.string;
        outValue.resourceId = tv.resourceId;
        if (resolveRefs && v.isReference() && arsc != null) {
            ArscParser.ResVal target = arsc.getValue(v.data);
            if (target != null) {
                TypedValue tv2 = toTypedValue(target);
                outValue.type = tv2.type;
                outValue.data = tv2.data;
                outValue.string = tv2.string;
            }
        }
    }

    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {}
    public void parseBundleExtras(XmlResourceParser parser, Bundle outBundle) throws XmlPullParserException, IOException {}
    public void parseBundleExtra(String tagName, AttributeSet attrs, Bundle outBundle) throws XmlPullParserException {}
    public final void flushLayoutCache() {}
    public final void startPreloading() {}
    public final void finishPreloading() {}
    public int calcConfigChanges(Configuration config) { return 0; }
}
