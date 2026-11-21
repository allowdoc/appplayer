package android.graphics;

import android.os.LocaleList;
import java.util.Locale;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int FILTER_BITMAP_FLAG = 2;
    public static final int DITHER_FLAG = 4;
    public static final int UNDERLINE_TEXT_FLAG = 8;
    public static final int STRIKE_THRU_TEXT_FLAG = 16;
    public static final int FAKE_BOLD_TEXT_FLAG = 32;
    public static final int LINEAR_TEXT_FLAG = 64;
    public static final int SUBPIXEL_TEXT_FLAG = 128;
    public static final int DEV_KERN_TEXT_FLAG = 256;
    public static final int LCD_RENDER_TEXT_FLAG = 512;
    public static final int EMBEDDED_BITMAP_TEXT_FLAG = 1024;
    public static final int AUTO_HINTING_TEXT_FLAG = 2048;
    public static final int VERTICAL_TEXT_FLAG = 4096;
    public static final int HINTING_OFF = 0;
    public static final int HINTING_ON = 1;
    public static final int BIDI_LTR = 0;
    public static final int BIDI_RTL = 1;
    public static final int BIDI_DEFAULT_LTR = 2;
    public static final int BIDI_DEFAULT_RTL = 3;
    public static final int BIDI_FORCE_LTR = 4;
    public static final int BIDI_FORCE_RTL = 5;
    public static final int DIRECTION_LTR = 0;
    public static final int DIRECTION_RTL = 1;
    public static final int CURSOR_AFTER = 0;
    public static final int CURSOR_AT_OR_AFTER = 1;
    public static final int CURSOR_BEFORE = 2;
    public static final int CURSOR_AT_OR_BEFORE = 3;
    public static final int CURSOR_AT = 4;
    public static final int START_HYPHEN_EDIT_NO_EDIT = 0;
    public static final int START_HYPHEN_EDIT_INSERT_HYPHEN = 1;
    public static final int START_HYPHEN_EDIT_INSERT_ZWJ = 2;
    public static final int END_HYPHEN_EDIT_NO_EDIT = 0;
    public static final int END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN = 1;
    public static final int END_HYPHEN_EDIT_INSERT_HYPHEN = 2;
    public static final int END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN = 3;
    public static final int END_HYPHEN_EDIT_INSERT_MAQAF = 4;
    public static final int END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN = 5;
    public static final int END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN = 6;

    public enum Style {
        FILL(0), STROKE(1), FILL_AND_STROKE(2);
        final int nativeInt;
        Style(int i) { nativeInt = i; }
    }

    public enum Cap {
        BUTT(0), ROUND(1), SQUARE(2);
        final int nativeInt;
        Cap(int i) { nativeInt = i; }
    }

    public enum Join {
        MITER(0), ROUND(1), BEVEL(2);
        final int nativeInt;
        Join(int i) { nativeInt = i; }
    }

    public enum Align {
        LEFT(0), CENTER(1), RIGHT(2);
        final int nativeInt;
        Align(int i) { nativeInt = i; }
    }

    public static class FontMetrics {
        public float top = -16.0f;
        public float ascent = -14.0f;
        public float descent = 4.0f;
        public float bottom = 6.0f;
        public float leading = 0.0f;
    }

    public static class FontMetricsInt {
        public int top = -16;
        public int ascent = -14;
        public int descent = 4;
        public int bottom = 6;
        public int leading = 0;
    }

    public int mBidiFlags = 2;
    private int mColor = 0xFF000000;
    private long mColorLong = 0L;
    private int mFlags = 0;
    private Style mStyle = Style.FILL;
    private Cap mCap = Cap.BUTT;
    private Join mJoin = Join.MITER;
    private Align mAlign = Align.LEFT;
    private float mStrokeWidth = 0.0f;
    private float mStrokeMiter = 4.0f;
    private float mTextSize = 12.0f;
    private float mTextScaleX = 1.0f;
    private float mTextSkewX = 0.0f;
    private float mLetterSpacing = 0.0f;
    private float mWordSpacing = 0.0f;
    private Typeface mTypeface;
    private Shader mShader;
    private ColorFilter mColorFilter;
    private Xfermode mXfermode;
    private PathEffect mPathEffect;
    private MaskFilter mMaskFilter;
    private LocaleList mLocales;
    private String mFontFeatureSettings;
    private String mFontVariationSettings;
    private boolean mShadowLayer;
    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;
    private long mShadowColorLong;
    private boolean mUnderlineText;
    private boolean mStrikeThruText;
    private boolean mFakeBoldText;
    private boolean mFilterBitmap;
    private boolean mAntiAlias;
    private boolean mDither;
    private boolean mLinearText;
    private boolean mSubpixelText;
    private boolean mElegantTextHeight;

    public Paint() {
        this(0);
    }

    public Paint(int flags) {
        setFlags(flags | 1282);
    }

    public Paint(Paint paint) {
        set(paint);
    }

    public void reset() {
        mColor = 0xFF000000;
        mFlags = 1282;
        mStyle = Style.FILL;
        mTextSize = 12.0f;
    }

    public void set(Paint other) {
        if (other != null) {
            this.mColor = other.mColor;
            this.mFlags = other.mFlags;
            this.mStyle = other.mStyle;
            this.mTextSize = other.mTextSize;
            this.mTypeface = other.mTypeface;
            this.mStrokeWidth = other.mStrokeWidth;
        }
    }

    public void setCompatibilityScaling(float scaling) {}
    public long getNativeInstance() { return 1L; }

    public int getBidiFlags() { return mBidiFlags; }
    public void setBidiFlags(int flags) { mBidiFlags = flags; }
    public int getFlags() { return mFlags; }
    public void setFlags(int flags) { mFlags = flags; }
    public int getHinting() { return HINTING_ON; }
    public void setHinting(int mode) {}
    public boolean isAntiAlias() { return (mFlags & ANTI_ALIAS_FLAG) != 0; }
    public void setAntiAlias(boolean aa) { if (aa) mFlags |= ANTI_ALIAS_FLAG; else mFlags &= ~ANTI_ALIAS_FLAG; }
    public boolean isDither() { return (mFlags & DITHER_FLAG) != 0; }
    public void setDither(boolean d) { if (d) mFlags |= DITHER_FLAG; else mFlags &= ~DITHER_FLAG; }
    public boolean isLinearText() { return (mFlags & LINEAR_TEXT_FLAG) != 0; }
    public void setLinearText(boolean l) {}
    public boolean isSubpixelText() { return (mFlags & SUBPIXEL_TEXT_FLAG) != 0; }
    public void setSubpixelText(boolean s) {}
    public boolean isUnderlineText() { return mUnderlineText; }
    public float getUnderlinePosition() { return 0f; }
    public float getUnderlineThickness() { return 1f; }
    public void setUnderlineText(boolean u) { mUnderlineText = u; }
    public boolean isStrikeThruText() { return mStrikeThruText; }
    public float getStrikeThruPosition() { return 0f; }
    public float getStrikeThruThickness() { return 1f; }
    public void setStrikeThruText(boolean s) { mStrikeThruText = s; }
    public boolean isFakeBoldText() { return mFakeBoldText; }
    public void setFakeBoldText(boolean f) { mFakeBoldText = f; }
    public boolean isFilterBitmap() { return mFilterBitmap; }
    public void setFilterBitmap(boolean f) { mFilterBitmap = f; }
    public Style getStyle() { return mStyle; }
    public void setStyle(Style s) { if (s != null) mStyle = s; }
    public int getColor() { return mColor; }
    public long getColorLong() { return mColorLong; }
    public void setColor(int color) { mColor = color; }
    public void setColor(long color) { mColorLong = color; mColor = (int) color; }
    public int getAlpha() { return (mColor >>> 24); }
    public void setAlpha(int a) { mColor = ((a & 0xFF) << 24) | (mColor & 0x00FFFFFF); }
    public void setARGB(int a, int r, int g, int b) { setColor(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF)); }
    public float getStrokeWidth() { return mStrokeWidth; }
    public void setStrokeWidth(float width) { mStrokeWidth = width; }
    public float getStrokeMiter() { return mStrokeMiter; }
    public void setStrokeMiter(float miter) { mStrokeMiter = miter; }
    public Cap getStrokeCap() { return mCap; }
    public void setStrokeCap(Cap cap) { if (cap != null) mCap = cap; }
    public Join getStrokeJoin() { return mJoin; }
    public void setStrokeJoin(Join join) { if (join != null) mJoin = join; }
    public boolean getFillPath(Path src, Path dst) { return true; }
    public Shader getShader() { return mShader; }
    public Shader setShader(Shader shader) { return mShader = shader; }
    public ColorFilter getColorFilter() { return mColorFilter; }
    public ColorFilter setColorFilter(ColorFilter filter) { return mColorFilter = filter; }
    public Xfermode getXfermode() { return mXfermode; }
    public BlendMode getBlendMode() { return null; }
    public Xfermode setXfermode(Xfermode xfermode) { return mXfermode = xfermode; }
    public void setBlendMode(BlendMode blendMode) {}
    public PathEffect getPathEffect() { return mPathEffect; }
    public PathEffect setPathEffect(PathEffect effect) { return mPathEffect = effect; }
    public MaskFilter getMaskFilter() { return mMaskFilter; }
    public MaskFilter setMaskFilter(MaskFilter maskfilter) { return mMaskFilter = maskfilter; }
    public Typeface getTypeface() { return mTypeface; }
    public Typeface setTypeface(Typeface typeface) { return mTypeface = typeface; }
    public Rasterizer getRasterizer() { return null; }
    public Rasterizer setRasterizer(Rasterizer rasterizer) { return null; }
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        mShadowRadius = radius; mShadowDx = dx; mShadowDy = dy; mShadowColor = color; mShadowLayer = true;
    }
    public void setShadowLayer(float radius, float dx, float dy, long color) {
        setShadowLayer(radius, dx, dy, (int) color);
    }
    public void clearShadowLayer() { mShadowLayer = false; }
    public boolean hasShadowLayer() { return mShadowLayer; }
    public float getShadowLayerRadius() { return mShadowRadius; }
    public float getShadowLayerDx() { return mShadowDx; }
    public float getShadowLayerDy() { return mShadowDy; }
    public int getShadowLayerColor() { return mShadowColor; }
    public long getShadowLayerColorLong() { return mShadowColorLong; }
    public Align getTextAlign() { return mAlign; }
    public void setTextAlign(Align align) { if (align != null) mAlign = align; }
    public Locale getTextLocale() { return Locale.getDefault(); }
    public LocaleList getTextLocales() { return LocaleList.getDefault(); }
    public void setTextLocale(Locale locale) {}
    public void setTextLocales(LocaleList locales) { mLocales = locales; }
    public boolean isElegantTextHeight() { return mElegantTextHeight; }
    public void setElegantTextHeight(boolean e) { mElegantTextHeight = e; }
    public float getTextSize() { return mTextSize; }
    public void setTextSize(float textSize) { mTextSize = textSize; }
    public float getTextScaleX() { return mTextScaleX; }
    public void setTextScaleX(float scaleX) { mTextScaleX = scaleX; }
    public float getTextSkewX() { return mTextSkewX; }
    public void setTextSkewX(float skewX) { mTextSkewX = skewX; }
    public float getLetterSpacing() { return mLetterSpacing; }
    public void setLetterSpacing(float letterSpacing) { mLetterSpacing = letterSpacing; }
    public float getWordSpacing() { return mWordSpacing; }
    public void setWordSpacing(float wordSpacing) { mWordSpacing = wordSpacing; }
    public String getFontFeatureSettings() { return mFontFeatureSettings; }
    public void setFontFeatureSettings(String settings) { mFontFeatureSettings = settings; }
    public String getFontVariationSettings() { return mFontVariationSettings; }
    public boolean setFontVariationSettings(String settings) { mFontVariationSettings = settings; return true; }
    public int getStartHyphenEdit() { return 0; }
    public int getEndHyphenEdit() { return 0; }
    public void setStartHyphenEdit(int edit) {}
    public void setEndHyphenEdit(int edit) {}
    public float ascent() { return -mTextSize * 0.8f; }
    public float descent() { return mTextSize * 0.2f; }
    public float getFontMetrics(FontMetrics metrics) {
        if (metrics != null) {
            metrics.top = -mTextSize;
            metrics.ascent = -mTextSize * 0.8f;
            metrics.descent = mTextSize * 0.2f;
            metrics.bottom = mTextSize * 0.3f;
            metrics.leading = 0f;
        }
        return mTextSize;
    }
    public FontMetrics getFontMetrics() {
        FontMetrics fm = new FontMetrics();
        getFontMetrics(fm);
        return fm;
    }
    public int getFontMetricsInt(FontMetricsInt metrics) {
        if (metrics != null) {
            metrics.top = -(int) mTextSize;
            metrics.ascent = -(int)(mTextSize * 0.8f);
            metrics.descent = (int)(mTextSize * 0.2f);
            metrics.bottom = (int)(mTextSize * 0.3f);
            metrics.leading = 0;
        }
        return (int) mTextSize;
    }
    public FontMetricsInt getFontMetricsInt() {
        FontMetricsInt fmi = new FontMetricsInt();
        getFontMetricsInt(fmi);
        return fmi;
    }
    public float getFontSpacing() { return mTextSize * 1.2f; }
    public float measureText(char[] text, int index, int count) { return count * (mTextSize * 0.6f); }
    public float measureText(String text, int start, int end) { return (end - start) * (mTextSize * 0.6f); }
    public float measureText(String text) { return text != null ? text.length() * (mTextSize * 0.6f) : 0f; }
    public float measureText(CharSequence text, int start, int end) { return (end - start) * (mTextSize * 0.6f); }
    public int breakText(char[] text, int index, int count, float maxWidth, float[] measuredWidth) {
        if (measuredWidth != null && measuredWidth.length > 0) measuredWidth[0] = Math.min(count * (mTextSize * 0.6f), maxWidth);
        return count;
    }
    public int breakText(CharSequence text, int start, int end, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        int count = end - start;
        if (measuredWidth != null && measuredWidth.length > 0) measuredWidth[0] = Math.min(count * (mTextSize * 0.6f), maxWidth);
        return count;
    }
    public int breakText(String text, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        return breakText(text, 0, text != null ? text.length() : 0, measureForwards, maxWidth, measuredWidth);
    }
    public int getTextWidths(char[] text, int index, int count, float[] widths) {
        if (widths != null) for (int i = 0; i < Math.min(count, widths.length); i++) widths[i] = mTextSize * 0.6f;
        return count;
    }
    public int getTextWidths(CharSequence text, int start, int end, float[] widths) {
        int count = end - start;
        if (widths != null) for (int i = 0; i < Math.min(count, widths.length); i++) widths[i] = mTextSize * 0.6f;
        return count;
    }
    public int getTextWidths(String text, int start, int end, float[] widths) {
        return getTextWidths((CharSequence) text, start, end, widths);
    }
    public int getTextWidths(String text, float[] widths) {
        return getTextWidths(text, 0, text != null ? text.length() : 0, widths);
    }
    public float getTextRunAdvances(char[] chars, int index, int count, int contextIndex, int contextCount, boolean isRtl, float[] advances, int advancesIndex) {
        return count * (mTextSize * 0.6f);
    }
    public int getTextRunCursor(char[] text, int contextStart, int contextLength, boolean isRtl, int offset, int cursorOpt) { return offset; }
    public int getTextRunCursor(CharSequence text, int contextStart, int contextLength, boolean isRtl, int offset, int cursorOpt) { return offset; }
    public int getTextRunCursor(String text, int contextStart, int contextLength, boolean isRtl, int offset, int cursorOpt) { return offset; }
    public void getTextPath(char[] text, int index, int count, float x, float y, Path path) {}
    public void getTextPath(String text, int start, int end, float x, float y, Path path) {}
    public void getTextBounds(String text, int start, int end, Rect bounds) {
        if (bounds != null) bounds.set(0, 0, (int)((end - start) * (mTextSize * 0.6f)), (int) mTextSize);
    }
    public void getTextBounds(CharSequence text, int start, int end, Rect bounds) {
        if (bounds != null) bounds.set(0, 0, (int)((end - start) * (mTextSize * 0.6f)), (int) mTextSize);
    }
    public void getTextBounds(char[] text, int index, int count, Rect bounds) {
        if (bounds != null) bounds.set(0, 0, (int)(count * (mTextSize * 0.6f)), (int) mTextSize);
    }
    public boolean hasGlyph(String string) { return true; }
    public float getRunAdvance(char[] text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return (offset - start) * (mTextSize * 0.6f);
    }
    public float getRunAdvance(CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return (offset - start) * (mTextSize * 0.6f);
    }
    public int getOffsetForAdvance(char[] text, int start, int end, int contextStart, int contextEnd, boolean isRtl, float advance) {
        return start + (int)(advance / (mTextSize * 0.6f));
    }
    public int getOffsetForAdvance(CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, float advance) {
        return start + (int)(advance / (mTextSize * 0.6f));
    }
    public boolean equalsForTextMeasurement(Paint other) { return true; }
}
