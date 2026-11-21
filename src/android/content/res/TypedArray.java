package android.content.res;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import org.headless.runner.core.RealXmlResourceParser;
import org.headless.runner.core.SyntheticDrawable;

import java.util.LinkedHashMap;
import java.util.Map;

public class TypedArray {
    final Resources mResources;
    final int[] mAttrs;
    /** Theme/style-resolved values keyed by attribute resource id. */
    final Map<Integer, TypedValue> mAttrValues;
    final RealXmlResourceParser.Node mNode;
    final int[] mIndices;
    int mLength;

    protected TypedArray(Resources resources) {
        this(resources, null, null, null, 0);
    }

    public TypedArray(Resources resources, int len) {
        this(resources, null, null, null, len);
    }

    /** Legacy constructor: XML-node-backed array without theme values. */
    public TypedArray(Resources resources, int[] attrs, RealXmlResourceParser.Node node, int len) {
        this(resources, attrs, null, node, len);
    }

    /** Full constructor: XML node attrs take precedence, then resolved theme/style values. */
    public TypedArray(Resources resources, int[] attrs, Map<Integer, TypedValue> attrValues,
                      RealXmlResourceParser.Node node, int len) {
        this.mResources = resources;
        this.mAttrs = attrs;
        this.mAttrValues = attrValues;
        this.mNode = node;
        this.mLength = len;

        // AOSP semantics: getIndexCount() must report ONLY the attributes that
        // actually resolved (node attribute, explicit style, defStyleAttr chain
        // or theme default). Reporting every index made the View constructor's
        // styleable loop read getters for ALL ~55 View attrs for every
        // programmatically-created view; the getters then handed back their
        // non-null fallbacks (e.g. an opaque-black ColorStateList) so every
        // view silently received a background + backgroundTint that real
        // Android would never set — the direct cause of blank/black screens
        // and of LayerDrawable.mutate() crashes on RN backgrounds.
        int[] temp = new int[attrs != null ? attrs.length : 0];
        int count = 0;
        boolean legacyNoArsc = (mAttrValues == null && node == null);
        if (attrs != null) {
            for (int i = 0; i < attrs.length; i++) {
                int attrId = attrs[i];
                boolean present = (mAttrValues != null && mAttrValues.containsKey(attrId))
                        || (node != null && node.attrValues.containsKey(attrId));
                if (present || legacyNoArsc) {
                    temp[count++] = i;
                }
            }
        }
        this.mIndices = new int[count];
        System.arraycopy(temp, 0, this.mIndices, 0, count);
    }

    public static TypedArray obtain(Resources res, int len) {
        return new TypedArray(res, len);
    }

    public static TypedArray obtain(Resources res, int[] attrs, RealXmlResourceParser.Node node) {
        return new TypedArray(res, attrs, node, attrs != null ? attrs.length : 0);
    }

    public int length() { return mLength; }
    public int getIndexCount() { return mIndices.length; }
    public int getIndex(int at) {
        if (at >= 0 && at < mIndices.length) {
            return mIndices[at];
        }
        return 0;
    }
    public Resources getResources() { return mResources; }

    // ------------------------------------------------------------------
    //  Raw value resolution: XML node first, then theme/style values.
    // ------------------------------------------------------------------

    private int attrIdAt(int index) {
        if (mAttrs != null && index >= 0 && index < mAttrs.length) return mAttrs[index];
        return 0;
    }

    private TypedValue rawValue(int index) {
        int attrId = attrIdAt(index);
        if (attrId == 0) return null;
        if (mNode != null) {
            String val = mNode.attrValues.get(attrId);
            if (val != null) return parseNodeValue(val, index);
        }
        if (mAttrValues != null) {
            return mAttrValues.get(attrId);
        }
        return null;
    }

    private boolean rawHasValue(int index) {
        int attrId = attrIdAt(index);
        if (attrId == 0) return false;
        if (mNode != null && mNode.attrValues.containsKey(attrId)) return true;
        if (mAttrValues != null && mAttrValues.containsKey(attrId)) return true;
        return false;
    }

    /** Convert an XML-node string attribute value into a TypedValue (legacy path). */
    private TypedValue parseNodeValue(String val, int index) {
        TypedValue tv = new TypedValue();
        val = val.trim();
        if (val.startsWith("@0x") || (val.startsWith("@") && val.matches("@[0-9a-fA-FxX]+"))) {
            tv.type = TypedValue.TYPE_REFERENCE;
            try {
                tv.resourceId = (int) Long.parseLong(val.replace("@0x", "").replace("@", ""), 16);
                tv.data = tv.resourceId;
            } catch (Throwable ignored) {}
            return tv;
        }
        if (val.endsWith("dp") || val.endsWith("dip") || val.endsWith("sp") || val.endsWith("px")) {
            tv.type = TypedValue.TYPE_DIMENSION;
            tv.data = getLayoutDimension(index, 0);
            return tv;
        }
        if ("-1".equals(val) || "match_parent".equalsIgnoreCase(val) || "fill_parent".equalsIgnoreCase(val)) {
            tv.type = TypedValue.TYPE_FIRST_INT;
            tv.data = -1;
            return tv;
        }
        if ("-2".equals(val) || "wrap_content".equalsIgnoreCase(val)) {
            tv.type = TypedValue.TYPE_FIRST_INT;
            tv.data = -2;
            return tv;
        }
        if (val.startsWith("#")) {
            tv.type = TypedValue.TYPE_INT_COLOR_ARGB8;
            try {
                tv.data = (int) Long.parseLong(val.substring(1), 16);
            } catch (Throwable ignored) {}
            return tv;
        }
        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
            tv.type = TypedValue.TYPE_INT_BOOLEAN;
            tv.data = "true".equalsIgnoreCase(val) ? 1 : 0;
            return tv;
        }
        try {
            if (val.startsWith("0x") || val.startsWith("0X")) {
                tv.type = TypedValue.TYPE_INT_HEX;
                tv.data = (int) Long.parseLong(val.substring(2), 16);
                return tv;
            }
            tv.type = TypedValue.TYPE_INT_DEC;
            tv.data = Integer.parseInt(val);
            return tv;
        } catch (Throwable ignored) {}
        try {
            tv.type = TypedValue.TYPE_FLOAT;
            Float.parseFloat(val);
            return tv;
        } catch (Throwable ignored) {}
        tv.type = TypedValue.TYPE_STRING;
        tv.string = val;
        return tv;
    }

    private String nodeString(int index) {
        int attrId = attrIdAt(index);
        if (attrId == 0 || mNode == null) return null;
        return mNode.attrValues.get(attrId);
    }

    private static boolean tvTrue(TypedValue tv) {
        return tv != null && tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data != 0;
    }

    private static boolean tvFalse(TypedValue tv) {
        return tv != null && tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data == 0;
    }

    public int getResourceId(int index, int defValue) {
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            Integer resId = mNode.attrResIds.get(attrId);
            if (resId != null) return resId;

            String val = mNode.attrValues.get(attrId);
            if (val != null && (val.startsWith("@0x") || val.startsWith("@"))) {
                try {
                    return (int) Long.parseLong(val.replace("@0x", "").replace("@", ""), 16);
                } catch (Throwable ignored) {}
            }
            if (attrId == 0x010100d0) { // android.R.attr.id
                Integer id = mNode.resIds.get("id");
                if (id != null) return id;
            }
        }
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE) {
            return tv.resourceId != 0 ? tv.resourceId : tv.data;
        }
        // androidfw: unresolved ?attr values are NOT resource ids - the
        // accessor falls back to defValue exactly like TypedArray.cpp.
        return defValue;
    }

    public int getLayoutDimension(int index, int defValue) {
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val == null && attrId == 0x010100f4) val = mNode.attributes.get("layout_width");
            if (val == null && attrId == 0x010100f5) val = mNode.attributes.get("layout_height");
            if (val != null) {
                return parseDimensionString(val, defValue);
            }
        }
        TypedValue tv = rawValue(index);
        if (tv != null) {
            if (tv.type == TypedValue.TYPE_DIMENSION) {
                return TypedValue.complexToDimensionPixelSize(tv.data, mResources.getDisplayMetrics());
            }
            if (tv.type == TypedValue.TYPE_FIRST_INT || tv.type == TypedValue.TYPE_INT_DEC) {
                return tv.data;
            }
        }
        return defValue;
    }

    private int parseDimensionString(String val, int defValue) {
        val = val.trim();
        if ("-1".equals(val) || "match_parent".equalsIgnoreCase(val) || "fill_parent".equalsIgnoreCase(val)) {
            return -1;
        }
        if ("-2".equals(val) || "wrap_content".equalsIgnoreCase(val)) {
            return -2;
        }
        try {
            if (val.endsWith("dp") || val.endsWith("dip")) {
                float dp = Float.parseFloat(val.replace("dp", "").replace("dip", "").trim());
                return Math.round(dp * 2.625f);
            }
            if (val.endsWith("px")) {
                return (int) Float.parseFloat(val.replace("px", "").trim());
            }
            return Integer.parseInt(val);
        } catch (Throwable ignored) {}
        return defValue;
    }

    public int getLayoutDimension(int index, String name) {
        return getLayoutDimension(index, -2);
    }

    public int getInt(int index, int defValue) {
        if (mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attr = mAttrs[index];
            if (attr == 0x01010586 || attr == 0x01010557) { // windowLayoutInDisplayCutoutMode
                return 0; // LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        TypedValue tv = rawValue(index);
        if (tv != null) {
            if (tv.type == TypedValue.TYPE_FIRST_INT || tv.type == TypedValue.TYPE_INT_DEC
                    || tv.type == TypedValue.TYPE_INT_HEX || tv.type == TypedValue.TYPE_INT_BOOLEAN
                    || tv.type == TypedValue.TYPE_REFERENCE || tv.type == TypedValue.TYPE_ATTRIBUTE) {
                return tv.data;
            }
        }
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            if (attrId == 0x010100dc) { // visibility
                String v = mNode.attributes.get("visibility");
                if (v != null) {
                    if ("visible".equalsIgnoreCase(v) || "0".equals(v)) return 0;
                    if ("invisible".equalsIgnoreCase(v) || "1".equals(v)) return 4;
                    if ("gone".equalsIgnoreCase(v) || "2".equals(v)) return 8;
                }
            }
            if (attrId == 0x010100c4) { // orientation
                String o = mNode.attributes.get("orientation");
                if (o != null) {
                    if ("vertical".equalsIgnoreCase(o) || "1".equals(o)) return 1;
                    if ("horizontal".equalsIgnoreCase(o) || "0".equals(o)) return 0;
                }
            }
        }
        return defValue;
    }

    public boolean getBoolean(int index, boolean defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_INT_BOOLEAN) {
            return tv.data != 0;
        }
        String nodeVal = nodeString(index);
        if ("true".equalsIgnoreCase(nodeVal)) return true;
        if ("false".equalsIgnoreCase(nodeVal)) return false;
        // Legacy hardcoded behavior only applies when NO resolved values exist
        // (i.e. no arsc table and no theme). Keeps old-runner semantics safe.
        if (mAttrValues == null) {
            if (mAttrs != null && index >= 0 && index < mAttrs.length) {
                int attr = mAttrs[index];
                if (attr == 0x01010056 || attr == 2130970342) { // windowNoTitle
                    return true;
                }
                if (attr == 0x010102eb || attr == 2130970333) { // windowActionBar
                    return false;
                }
            }
        }
        return defValue;
    }

    public float getFloat(int index, float defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null) {
            if (tv.type == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(tv.data);
            if (tv.type == TypedValue.TYPE_DIMENSION) return tv.data;
        }
        return defValue;
    }

    public int getColor(int index, int defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.type == TypedValue.TYPE_INT_COLOR_ARGB8 || tv.type == TypedValue.TYPE_INT_COLOR_RGB8) {
                return tv.data;
            }
            // androidfw: color REFERENCES dereference through Resources
            // (@color/x, @android:color/...)
            if (tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
                int resId = tv.resourceId != 0 ? tv.resourceId : tv.data;
                try {
                    return mResources.getColor(resId);
                } catch (Throwable ignored) {}
            }
        }
        String val = nodeString(index);
        if (val != null && val.startsWith("#")) {
            try {
                return (int) Long.parseLong(val.substring(1), 16);
            } catch (Throwable ignored) {}
        }
        return defValue;
    }

    public float getDimension(int index, float defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimension(tv.data, mResources.getDisplayMetrics());
        }
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
            try {
                return mResources.getDimension(tv.resourceId != 0 ? tv.resourceId : tv.data);
            } catch (Throwable ignored) {}
        }
        String val = nodeString(index);
        if (val != null) {
            return parseDimensionString(val, (int) defValue);
        }
        return defValue;
    }
    public int getDimensionPixelOffset(int index, int defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimensionPixelOffset(tv.data, mResources.getDisplayMetrics());
        }
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
            try {
                return mResources.getDimensionPixelOffset(tv.resourceId != 0 ? tv.resourceId : tv.data);
            } catch (Throwable ignored) {}
        }
        String val = nodeString(index);
        if (val != null) {
            return parseDimensionString(val, defValue);
        }
        return defValue;
    }
    public int getDimensionPixelSize(int index, int defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimensionPixelSize(tv.data, mResources.getDisplayMetrics());
        }
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
            try {
                return mResources.getDimensionPixelSize(tv.resourceId != 0 ? tv.resourceId : tv.data);
            } catch (Throwable ignored) {}
        }
        String val = nodeString(index);
        if (val != null) {
            return parseDimensionString(val, defValue);
        }
        return defValue;
    }

    public String getString(int index) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.string != null) {
            return tv.string.toString();
        }
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
            try {
                String s = mResources.getString(tv.resourceId != 0 ? tv.resourceId : tv.data);
                if (s != null) return s;
            } catch (Throwable ignored) {}
        }
        if (tv != null && tv.type == TypedValue.TYPE_STRING && tv.string == null) {
            return null;
        }
        String val = nodeString(index);
        if (val != null) {
            if (val.startsWith("@string/")) {
                String entry = val.substring(8);
                int id = mResources.getIdentifier(entry, "string", mResources.getResourcePackageName(0));
                if (id != 0) {
                    String s = mResources.getString(id);
                    if (s != null && !s.isEmpty()) return s;
                }
            }
            return val;
        }
        return null;
    }

    public CharSequence getText(int index) { return getString(index); }
    public String getNonResourceString(int index) { return getString(index); }
    public String getNonConfigurationString(int index, int allowedChangingConfigs) { return getString(index); }

    /**
     * AOSP returns null when the attribute is unset. When set, resolve the
     * reference through the resource pipeline (theme @drawable/..., style
     * reference or explicit node value). Never fabricate a placeholder for
     * unset attributes: the View constructor then applies backgrounds/tints
     * to views that have none.
     */
    public Drawable getDrawable(int index) {
        TypedValue tv = rawValue(index);
        if (tv != null) {
            if (tv.type == TypedValue.TYPE_REFERENCE || tv.type == TypedValue.TYPE_ATTRIBUTE) {
                int resId = tv.resourceId != 0 ? tv.resourceId : tv.data;
                if (resId != 0) {
                    try {
                        return mResources.getDrawable(resId);
                    } catch (Throwable ignored) {}
                }
                return null;
            }
        }
        String val = nodeString(index);
        if (val != null) {
            String trimmed = val.trim();
            int slash = trimmed.indexOf('/');
            if (slash > 1) {
                String type = trimmed.substring(1, slash);
                String name = trimmed.substring(slash + 1);
                if ("drawable".equals(type) || "mipmap".equals(type) || "color".equals(type)) {
                    int id = mResources.getIdentifier(name, type, mResources.getResourcePackageName(0));
                    if (id != 0) {
                        try {
                            return mResources.getDrawable(id);
                        } catch (Throwable ignored) {}
                    }
                }
            }
            return null;
        }
        return null;
    }

    public Drawable getDrawableForDensity(int index, int density) { return getDrawable(index); }
    public Typeface getFont(int index) { return null; }
    public Object getComplexColor(int index) { return null; }
    /**
     * AOSP: null when the attribute is unset. References resolve through the
     * resource pipeline (color-list XML inflation happens in Resources).
     */
    public ColorStateList getColorStateList(int index) {
        if (!rawHasValue(index)) {
            String val = nodeString(index);
            if (val == null || !val.startsWith("#")) return null;
        }
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE && (tv.resourceId != 0 || tv.data != 0)) {
            try {
                ColorStateList csl = mResources.getColorStateList(tv.resourceId != 0 ? tv.resourceId : tv.data);
                if (csl != null) return csl;
            } catch (Throwable ignored) {}
        }
        return ColorStateList.valueOf(getColor(index, 0));
    }
    public int getInteger(int index, int defValue) { return getInt(index, defValue); }
    public float getFraction(int index, int base, int pbase, float defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_FRACTION) {
            float f = TypedValue.complexToFraction(tv.data, base, pbase);
            return f;
        }
        return defValue;
    }
    public int getThemeAttributeId(int index, int defValue) {
        TypedValue tv = rawValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_ATTRIBUTE) return tv.data;
        return defValue;
    }
    public CharSequence[] getTextArray(int index) { return new CharSequence[0]; }

    public boolean getValue(int index, TypedValue outValue) {
        TypedValue val = peekValue(index);
        if (val != null) {
            if (outValue != null) outValue.setTo(val);
            return true;
        }
        return false;
    }

    public int getType(int index) {
        TypedValue val = peekValue(index);
        return val != null ? val.type : TypedValue.TYPE_NULL;
    }

    public int getSourceResourceId(int index, int defValue) { return defValue; }

    public boolean hasValue(int index) {
        if (rawHasValue(index)) return true;
        if (mNode == null && mAttrValues == null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attr = mAttrs[index];
            if (attr == 0x01010586 || attr == 0x01010557) { // windowLayoutInDisplayCutoutMode
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean hasValueOrEmpty(int index) { return hasValue(index); }

    public TypedValue peekValue(int index) {
        return rawValue(index);
    }
    public String getPositionDescription() { return ""; }
    public void recycle() {}
    public int[] extractThemeAttrs() { return null; }
    public int[] extractThemeAttrs(int[] scrap) { return null; }
    public int getChangingConfigurations() { return 0; }
}
