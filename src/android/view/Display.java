package android.view;

import android.content.res.Resources;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManagerGlobal;
import android.util.DisplayMetrics;

public final class Display {
    public static final int DEFAULT_DISPLAY = 0;
    public static final int INVALID_DISPLAY = -1;
    public static final int FLAG_SUPPORTS_PROTECTED_BUFFERS = 1;
    public static final int FLAG_SECURE = 2;
    public static final int FLAG_PRIVATE = 4;
    public static final int FLAG_PRESENTATION = 8;
    public static final int FLAG_ROUND = 16;
    public static final int FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 32;
    public static final int FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 64;
    public static final int FLAG_SCALING_DISABLED = 1073741824;
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BUILT_IN = 1;
    public static final int TYPE_HDMI = 2;
    public static final int TYPE_WIFI = 3;
    public static final int TYPE_OVERLAY = 4;
    public static final int TYPE_VIRTUAL = 5;
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_OFF = 1;
    public static final int STATE_ON = 2;
    public static final int STATE_DOZE = 3;
    public static final int STATE_DOZE_SUSPEND = 4;
    public static final int STATE_VR = 5;
    public static final int STATE_ON_SUSPEND = 6;
    public static final int COLOR_MODE_INVALID = -1;
    public static final int COLOR_MODE_DEFAULT = 0;
    public static final int COLOR_MODE_BT601_625 = 1;
    public static final int COLOR_MODE_BT601_625_UNADJUSTED = 2;
    public static final int COLOR_MODE_BT601_525 = 3;
    public static final int COLOR_MODE_BT601_525_UNADJUSTED = 4;
    public static final int COLOR_MODE_BT709 = 5;
    public static final int COLOR_MODE_DCI_P3 = 6;
    public static final int COLOR_MODE_SRGB = 7;
    public static final int COLOR_MODE_ADOBE_RGB = 8;
    public static final int COLOR_MODE_DISPLAY_P3 = 9;
    public static final int REMOVE_MODE_MOVE_CONTENT_TO_PRIMARY = 0;
    public static final int REMOVE_MODE_DESTROY_CONTENT = 1;

    public static final class Mode {
        public static final Mode[] EMPTY_ARRAY = new Mode[0];
        private final int mModeId;
        private final int mWidth;
        private final int mHeight;
        private final float mRefreshRate;

        public Mode(int modeId, int width, int height, float refreshRate) {
            mModeId = modeId;
            mWidth = width;
            mHeight = height;
            mRefreshRate = refreshRate;
        }

        public int getModeId() { return mModeId; }
        public int getPhysicalWidth() { return mWidth; }
        public int getPhysicalHeight() { return mHeight; }
        public float getRefreshRate() { return mRefreshRate; }
    }

    public static final class HdrCapabilities {
        public int[] getSupportedHdrTypes() { return new int[0]; }
        public float getDesiredMaxLuminance() { return 500f; }
        public float getDesiredMinLuminance() { return 0f; }
        public float getDesiredMaxAverageLuminance() { return 500f; }
    }

    private final int mDisplayId;
    private final Mode mMode = new Mode(1, 1080, 1920, 60.0f);

    public Display() {
        this(DEFAULT_DISPLAY);
    }

    public Display(int displayId) {
        this.mDisplayId = displayId;
    }

    public Display(DisplayManagerGlobal global, int displayId, DisplayInfo info, DisplayAdjustments adjustments) {
        this.mDisplayId = displayId;
    }

    public Display(DisplayManagerGlobal global, int displayId, DisplayInfo info, Resources res) {
        this.mDisplayId = displayId;
    }

    public int getDisplayId() { return mDisplayId; }
    public String getUniqueId() { return "local:" + mDisplayId; }
    public boolean isValid() { return true; }

    public boolean getDisplayInfo(DisplayInfo out) {
        if (out != null) {
            out.logicalWidth = 1080;
            out.logicalHeight = 1920;
            out.appWidth = 1080;
            out.appHeight = 1920;
            out.rotation = 0;
            out.logicalDensityDpi = 420;
            return true;
        }
        return false;
    }

    public int getLayerStack() { return 0; }
    public int getFlags() { return 0; }
    public int getType() { return TYPE_BUILT_IN; }
    public DisplayAddress getAddress() { return null; }
    public int getOwnerUid() { return 0; }
    public String getOwnerPackageName() { return null; }
    public DisplayAdjustments getDisplayAdjustments() { return null; }
    public String getName() { return "Built-in Screen"; }

    public void getSize(Point outSize) {
        if (outSize != null) {
            outSize.x = 1080;
            outSize.y = 1920;
        }
    }

    public void getRectSize(Rect outSize) {
        if (outSize != null) {
            outSize.set(0, 0, 1080, 1920);
        }
    }

    public void getCurrentSizeRange(Point outSmallestSize, Point outLargestSize) {
        if (outSmallestSize != null) {
            outSmallestSize.x = 1080;
            outSmallestSize.y = 1080;
        }
        if (outLargestSize != null) {
            outLargestSize.x = 1920;
            outLargestSize.y = 1920;
        }
    }

    public int getMaximumSizeDimension() { return 1920; }
    public int getWidth() { return 1080; }
    public int getHeight() { return 1920; }
    public void getOverscanInsets(Rect outInsets) {
        if (outInsets != null) outInsets.setEmpty();
    }
    public int getRotation() { return 0; }
    public int getOrientation() { return 0; }
    public DisplayCutout getCutout() { return null; }
    public int getPixelFormat() { return 1; }
    public float getRefreshRate() { return 60.0f; }
    public float[] getSupportedRefreshRates() { return new float[]{60.0f}; }
    public Mode getMode() { return mMode; }
    public Mode[] getSupportedModes() { return new Mode[]{mMode}; }
    public void requestColorMode(int colorMode) {}
    public int getColorMode() { return COLOR_MODE_DEFAULT; }
    public int getRemoveMode() { return REMOVE_MODE_MOVE_CONTENT_TO_PRIMARY; }
    public HdrCapabilities getHdrCapabilities() { return new HdrCapabilities(); }
    public boolean isHdr() { return false; }
    public boolean isWideColorGamut() { return false; }
    public ColorSpace getPreferredWideGamutColorSpace() { return null; }
    public int[] getSupportedColorModes() { return new int[]{COLOR_MODE_DEFAULT}; }
    public long getAppVsyncOffsetNanos() { return 0L; }
    public long getPresentationDeadlineNanos() { return 16666666L; }

    public void getMetrics(DisplayMetrics outMetrics) {
        if (outMetrics != null) {
            outMetrics.widthPixels = 1080;
            outMetrics.heightPixels = 1920;
            outMetrics.density = 2.625f;
            outMetrics.densityDpi = 420;
            outMetrics.scaledDensity = 2.625f;
            outMetrics.xdpi = 420f;
            outMetrics.ydpi = 420f;
        }
    }

    public void getRealSize(Point outSize) {
        if (outSize != null) {
            outSize.x = 1080;
            outSize.y = 1920;
        }
    }

    public void getRealMetrics(DisplayMetrics outMetrics) {
        getMetrics(outMetrics);
    }

    public int getState() { return STATE_ON; }
    public boolean hasAccess(int uid) { return true; }
    public static boolean hasAccess(int uid, int flags, int ownerUid, int displayId) { return true; }
    public boolean isPublicPresentation() { return false; }
    public String toString() { return "Display id 0: 1080x1920 60fps"; }
    public static String typeToString(int type) { return "BUILT_IN"; }
    public static String stateToString(int state) { return "ON"; }
    public static boolean isSuspendedState(int state) { return false; }
    public static boolean isDozeState(int state) { return false; }
}
