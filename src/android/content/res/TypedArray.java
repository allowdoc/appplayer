package android.content.res;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import org.headless.runner.core.RealXmlResourceParser;
import org.headless.runner.core.SyntheticDrawable;

public class TypedArray {
    final Resources mResources;
    final int[] mAttrs;
    final RealXmlResourceParser.Node mNode;
    final int[] mIndices;
    int mLength;

    protected TypedArray(Resources resources) {
        this(resources, null, null, 0);
    }

    public TypedArray(Resources resources, int len) {
        this(resources, null, null, len);
    }

    public TypedArray(Resources resources, int[] attrs, RealXmlResourceParser.Node node, int len) {
        this.mResources = resources;
        this.mAttrs = attrs;
        this.mNode = node;
        this.mLength = len;

        if (attrs != null && node != null) {
            int[] temp = new int[attrs.length];
            int count = 0;
            for (int i = 0; i < attrs.length; i++) {
                if (node.attrValues.containsKey(attrs[i])) {
                    temp[count++] = i;
                }
            }
            this.mIndices = new int[count];
            System.arraycopy(temp, 0, this.mIndices, 0, count);
        } else if (attrs != null && node == null) {
            // Theme attributes pass-through (no XML node attached)
            this.mIndices = new int[attrs.length];
            for (int i = 0; i < attrs.length; i++) {
                this.mIndices[i] = i;
            }
        } else {
            this.mIndices = new int[0];
        }
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
        return defValue;
    }

    public int getLayoutDimension(int index, int defValue) {
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val == null && attrId == 0x010100f4) val = mNode.attributes.get("layout_width");
            if (val == null && attrId == 0x010100f5) val = mNode.attributes.get("layout_height");
            if (val != null) {
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
            }
        }
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
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null) {
                val = val.trim();
                try {
                    if (val.startsWith("0x") || val.startsWith("0X")) {
                        return (int) Long.parseLong(val.substring(2), 16);
                    }
                    return Integer.parseInt(val);
                } catch (Throwable ignored) {}
            }
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
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null) {
                if ("true".equalsIgnoreCase(val)) return true;
                if ("false".equalsIgnoreCase(val)) return false;
            }
        }
        if (mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attr = mAttrs[index];
            if (attr == 0x01010056 || attr == 2130970342) { // windowNoTitle
                return true;
            }
            if (attr == 0x010102eb || attr == 2130970333) { // windowActionBar
                return false;
            }
        }
        return defValue;
    }

    public float getFloat(int index, float defValue) {
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null) {
                try {
                    return Float.parseFloat(val);
                } catch (Throwable ignored) {}
            }
        }
        return defValue;
    }

    public int getColor(int index, int defValue) {
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null && val.startsWith("#")) {
                try {
                    return (int) Long.parseLong(val.substring(1), 16);
                } catch (Throwable ignored) {}
            }
        }
        return defValue;
    }

    public float getDimension(int index, float defValue) {
        return (float) getLayoutDimension(index, (int) defValue);
    }
    public int getDimensionPixelOffset(int index, int defValue) {
        return getLayoutDimension(index, defValue);
    }
    public int getDimensionPixelSize(int index, int defValue) {
        return getLayoutDimension(index, defValue);
    }

    public String getString(int index) {
        if (mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attr = mAttrs[index];
            if (attr == 0x01010586 || attr == 0x01010557) { // windowLayoutInDisplayCutoutMode
                return "default";
            }
        }
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null) {
                if (val.startsWith("@string/") || val.startsWith("@")) {
                    Integer resId = mNode.attrResIds.get(attrId);
                    if (resId != null) {
                        return mResources.getString(resId);
                    }
                    String entry = val.startsWith("@string/") ? val.substring(8) : val.substring(1);
                    int id = mResources.getIdentifier(entry, "string", mResources.getResourcePackageName(0));
                    if (id != 0) {
                        String s = mResources.getString(id);
                        if (s != null && !s.isEmpty()) return s;
                    }
                }
                return val;
            }
        }
        return null;
    }

    public CharSequence getText(int index) { return getString(index); }
    public String getNonResourceString(int index) { return getString(index); }
    public String getNonConfigurationString(int index, int allowedChangingConfigs) { return getString(index); }

    public Drawable getDrawable(int index) { return new SyntheticDrawable(48, 48); }
    public Drawable getDrawableForDensity(int index, int density) { return new SyntheticDrawable(48, 48); }
    public Typeface getFont(int index) { return null; }
    public ComplexColor getComplexColor(int index) { return null; }
    public ColorStateList getColorStateList(int index) { return ColorStateList.valueOf(getColor(index, 0xFF000000)); }
    public int getInteger(int index, int defValue) { return getInt(index, defValue); }
    public float getFraction(int index, int base, int pbase, float defValue) { return defValue; }
    public int getThemeAttributeId(int index, int defValue) { return defValue; }
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
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            return mNode.attrValues.containsKey(mAttrs[index]);
        }
        if (mNode == null && mAttrs != null && index >= 0 && index < mAttrs.length) {
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
        if (mNode != null && mAttrs != null && index >= 0 && index < mAttrs.length) {
            int attrId = mAttrs[index];
            String val = mNode.attrValues.get(attrId);
            if (val != null) {
                TypedValue tv = new TypedValue();
                val = val.trim();
                if (val.startsWith("@0x") || val.startsWith("@")) {
                    tv.type = TypedValue.TYPE_REFERENCE;
                    try {
                        tv.resourceId = (int) Long.parseLong(val.replace("@0x", "").replace("@", ""), 16);
                        tv.data = tv.resourceId;
                    } catch (Throwable ignored) {}
                    return tv;
                }
                if (val.endsWith("dp") || val.endsWith("dip") || val.endsWith("sp") || val.endsWith("px")) {
                    tv.type = TypedValue.TYPE_DIMENSION;
                    int px = getLayoutDimension(index, 0);
                    tv.data = px;
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
        }
        return null;
    }
    public String getPositionDescription() { return ""; }
    public void recycle() {}
    public int[] extractThemeAttrs() { return null; }
    public int[] extractThemeAttrs(int[] scrap) { return null; }
    public int getChangingConfigurations() { return 0; }
}
