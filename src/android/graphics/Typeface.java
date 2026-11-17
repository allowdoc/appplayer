package android.graphics;

import android.content.res.AssetManager;
import android.content.res.FontResourcesParser;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.FontVariationAxis;
import android.text.FontConfig;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;
    public static final int STYLE_MASK = 3;
    public static final int RESOLVE_BY_FONT_TABLE = -1;

    public static final Typeface DEFAULT = new Typeface(1L, 400, NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface(2L, 700, BOLD);
    public static final Typeface SANS_SERIF = new Typeface(3L, 400, NORMAL);
    public static final Typeface SERIF = new Typeface(4L, 400, NORMAL);
    public static final Typeface MONOSPACE = new Typeface(5L, 400, NORMAL);

    static Typeface[] sDefaults = new Typeface[] { DEFAULT, DEFAULT_BOLD, DEFAULT, DEFAULT_BOLD };
    static Typeface sDefaultTypeface = DEFAULT;
    static final Map<String, Typeface> sSystemFontMap = new HashMap<>();
    static final Map<String, FontFamily[]> sSystemFallbackMap = new HashMap<>();

    public long native_instance;
    private final int mWeight;
    private final int mStyle;

    static {
        sSystemFontMap.put("sans-serif", SANS_SERIF);
        sSystemFontMap.put("serif", SERIF);
        sSystemFontMap.put("monospace", MONOSPACE);
    }

    public Typeface() {
        this(1L, 400, NORMAL);
    }

    public Typeface(long ni) {
        this(ni, 400, NORMAL);
    }

    private Typeface(long ni, int weight, int style) {
        this.native_instance = ni;
        this.mWeight = weight;
        this.mStyle = style;
    }

    public int getWeight() {
        return mWeight;
    }

    public int getStyle() {
        return mStyle;
    }

    public final boolean isBold() {
        return (mStyle & BOLD) != 0;
    }

    public final boolean isItalic() {
        return (mStyle & ITALIC) != 0;
    }

    public static Typeface create(String familyName, int style) {
        Typeface tf = sSystemFontMap.get(familyName);
        if (tf == null) tf = DEFAULT;
        return create(tf, style);
    }

    public static Typeface create(Typeface family, int style) {
        return create(family, style, false);
    }

    public static Typeface create(Typeface family, int style, boolean exact) {
        if (family == null) family = DEFAULT;
        int weight = (style & BOLD) != 0 ? 700 : 400;
        return new Typeface(family.native_instance + style, weight, style);
    }

    public static Typeface createFromTypefaceWithVariation(Typeface family, List<FontVariationAxis> axes) {
        return family != null ? family : DEFAULT;
    }

    public static Typeface defaultFromStyle(int style) {
        return sDefaults[style & STYLE_MASK];
    }

    public static Typeface createFromAsset(AssetManager mgr, String path) {
        return new Typeface(10L, 400, NORMAL);
    }

    public static Typeface createFromFile(File file) {
        return new Typeface(11L, 400, NORMAL);
    }

    public static Typeface createFromFile(String path) {
        return new Typeface(12L, 400, NORMAL);
    }

    public static Typeface createFromResources(FontResourcesParser.FamilyResourceEntry entry,
                                              AssetManager mgr, String path) {
        return DEFAULT;
    }

    public static Typeface findFromCache(AssetManager mgr, String path) {
        return null;
    }

    public static void initSystemDefaultTypefaces(Map<String, Typeface> systemFontMap,
                                                 Map<String, FontFamily[]> fallbacks,
                                                 FontConfig.Alias[] aliases) {}

    public boolean isSupportedAxes(int axis) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Typeface typeface = (Typeface) o;
        return mWeight == typeface.mWeight && mStyle == typeface.mStyle;
    }

    @Override
    public int hashCode() {
        return 31 * mWeight + mStyle;
    }

    public static final class Builder {
        public Builder(AssetManager assetManager, String path) {}
        public Builder(File path) {}
        public Builder(String path) {}
        public Builder(FileDescriptor fd) {}
        public Builder setWeight(int weight) { return this; }
        public Builder setItalic(boolean italic) { return this; }
        public Builder setTtcIndex(int ttcIndex) { return this; }
        public Builder setFontVariationSettings(String variationSettings) { return this; }
        public Builder setFontVariationSettings(FontVariationAxis[] axes) { return this; }
        public Builder setFallback(String fallbackFamilyName) { return this; }
        public Typeface build() {
            return DEFAULT;
        }
    }

    public static final class CustomFallbackBuilder {
        public CustomFallbackBuilder(android.graphics.fonts.FontFamily family) {}
        public CustomFallbackBuilder setStyle(android.graphics.fonts.FontStyle style) { return this; }
        public CustomFallbackBuilder setSystemFallback(String familyName) { return this; }
        public Typeface build() {
            return DEFAULT;
        }
    }
}
