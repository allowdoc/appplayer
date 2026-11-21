package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Desktop-runner framework VectorDrawable (shadows the android-all class; bin/
 * comes first on the classpath).
 *
 * WHY THIS EXISTS: appcompat's ResourceManagerInternal self-test requires the
 * drawable returned for R.drawable.abc_vector_test to be either the app's own
 * VectorDrawableCompat or a class literally named
 * "android.graphics.drawable.VectorDrawable" (the framework component). The
 * android-all framework VectorDrawable is native-only, so the runner previously
 * fell back to a placeholder drawable and EVERY obfuscated appcompat app died
 * at startup with "This app has been built with an incorrect configuration.
 * Please configure your build for VectorDrawableCompat."
 *
 * Pure Java (javac emits valid stack maps - no runtime bytecode surgery),
 * parses the APK's real binary vector XML through the runner's
 * Resources.getXml()/TypedArray stack, and stores geometry for the Skia
 * rendering backend (Phase 2). draw() is a soft no-op until the pixel backend
 * lands - matching the rest of the graphics stub layer.
 */
public class VectorDrawable extends Drawable {

    // Framework attribute ids (confirmed against real APK binary XML).
    static final int ATTR_WIDTH = 0x01010155;
    static final int ATTR_HEIGHT = 0x01010159;
    static final int ATTR_VIEWPORT_WIDTH = 0x01010402;
    static final int ATTR_VIEWPORT_HEIGHT = 0x01010403;
    static final int ATTR_ALPHA = 0x01010603;
    static final int ATTR_AUTO_MIRRORED = 0x010107d5;
    // path attributes
    static final int ATTR_PATH_DATA = 0x01010405;
    static final int ATTR_FILL_COLOR = 0x01010404;
    static final int ATTR_STROKE_COLOR = 0x01010406;
    static final int ATTR_STROKE_WIDTH = 0x01010407;
    static final int ATTR_FILL_ALPHA = 0x01010408;
    static final int ATTR_STROKE_ALPHA = 0x01010409;

    float mViewportWidth = 1f, mViewportHeight = 1f;
    int mIntrinsicWidth = 0, mIntrinsicHeight = 0;
    float mAlpha = 1f;
    boolean mAutoMirrored = false;
    final List<VPath> mPaths = new ArrayList<>();

    static final class VPath {
        String pathData;
        int fillColor = 0;
        int strokeColor = 0;
        float strokeWidth = 0f;
        float fillAlpha = 1f;
        float strokeAlpha = 1f;
    }

    public VectorDrawable() {
    }

    /** Framework-compatible factory: inflate the vector XML for a resource id. */
    public static Drawable create(Resources res, int resId, Resources.Theme theme) {
        try {
            XmlResourceParser parser = res.getXml(resId);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // skip to start tag
            }
            if (type != XmlPullParser.START_TAG) return null;
            if (!"vector".equals(parser.getName())) return null;
            VectorDrawable d = new VectorDrawable();
            d.inflate(res, parser, attrs, theme);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void inflate(Resources res, XmlPullParser parser, AttributeSet attrs,
                        Resources.Theme theme) throws org.xmlpull.v1.XmlPullParserException,
            java.io.IOException {
        org.headless.runner.core.RealXmlResourceParser.Node node = null;
        if (parser instanceof org.headless.runner.core.RealXmlResourceParser) {
            node = ((org.headless.runner.core.RealXmlResourceParser) parser).currentNode();
        }

        int[] styleable = new int[]{ATTR_WIDTH, ATTR_HEIGHT, ATTR_VIEWPORT_WIDTH,
                ATTR_VIEWPORT_HEIGHT, ATTR_ALPHA, ATTR_AUTO_MIRRORED};
        TypedArray a = (theme != null)
                ? theme.obtainStyledAttributes(attrs, styleable, 0, 0)
                : res.obtainStyledAttributes(attrs, styleable, 0, 0);
        try {
            mIntrinsicWidth = Math.round(a.getDimension(0, 0f));
            mIntrinsicHeight = Math.round(a.getDimension(1, 0f));
            mViewportWidth = a.getFloat(2, 1f);
            mViewportHeight = a.getFloat(3, 1f);
            mAlpha = a.getFloat(4, 1f);
            mAutoMirrored = a.getBoolean(5, false);
        } finally {
            a.recycle();
        }
        if (mViewportWidth <= 0f) mViewportWidth = 1f;
        if (mViewportHeight <= 0f) mViewportHeight = 1f;

        // Child <path>/<group> elements: capture pathData + colors from the
        // parsed binary XML node tree (attribute ids or names).
        if (node != null) {
            collectPaths(node);
        }
    }

    private void collectPaths(org.headless.runner.core.RealXmlResourceParser.Node node) {
        for (org.headless.runner.core.RealXmlResourceParser.Node child : node.children) {
            if ("path".equals(child.tag)) {
                VPath p = new VPath();
                Map<Integer, String> av = child.attrValues;
                p.pathData = av.get(ATTR_PATH_DATA);
                if (p.pathData == null) p.pathData = child.attributes.get("pathData");
                p.fillColor = parseColor(av.get(ATTR_FILL_COLOR));
                if (p.fillColor == 0) p.fillColor = parseColor(child.attributes.get("fillColor"));
                p.strokeColor = parseColor(av.get(ATTR_STROKE_COLOR));
                p.strokeWidth = parseFloat(av.get(ATTR_STROKE_WIDTH), 0f);
                p.fillAlpha = parseFloat(av.get(ATTR_FILL_ALPHA), 1f);
                p.strokeAlpha = parseFloat(av.get(ATTR_STROKE_ALPHA), 1f);
                if (p.pathData != null) mPaths.add(p);
            } else if ("group".equals(child.tag)) {
                collectPaths(child);
            }
        }
    }

    static int parseColor(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim();
        try {
            if (s.startsWith("#")) {
                long v = Long.parseLong(s.substring(1), 16);
                switch (s.length() - 1) {
                    case 6: return (int) (0xFF000000L | v);
                    case 8: return (int) v;
                    default: return 0;
                }
            }
            if (s.startsWith("@0x")) return (int) Long.parseLong(s.substring(3), 16);
        } catch (Throwable ignored) {}
        return 0;
    }

    static float parseFloat(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try { return Float.parseFloat(s); } catch (Throwable ignored) { return def; }
    }

    @Override
    public void draw(Canvas canvas) {
        // Pixel rendering arrives with the Skia backend; geometry is captured.
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha / 255f;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    public float getViewportWidth() { return mViewportWidth; }
    public float getViewportHeight() { return mViewportHeight; }
    public List<VPath> getPaths() { return mPaths; }
}
