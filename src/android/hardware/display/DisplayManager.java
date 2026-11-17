package android.hardware.display;

import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.view.Display;
import java.util.Collections;
import java.util.List;

public final class DisplayManager {
    public static final String ACTION_WIFI_DISPLAY_STATUS_CHANGED = "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED";
    public static final String EXTRA_WIFI_DISPLAY_STATUS = "android.hardware.display.extra.WIFI_DISPLAY_STATUS";
    public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";

    public interface DisplayListener {
        void onDisplayAdded(int displayId);
        void onDisplayRemoved(int displayId);
        void onDisplayChanged(int displayId);
    }

    private final Context mContext;
    private final Display mDefaultDisplay;

    public DisplayManager(Context context) {
        this.mContext = context;
        this.mDefaultDisplay = new Display(0);
    }

    public Display getDisplay(int displayId) {
        return mDefaultDisplay;
    }

    public Display[] getDisplays() {
        return new Display[]{mDefaultDisplay};
    }

    public Display[] getDisplays(String category) {
        return new Display[]{mDefaultDisplay};
    }

    public void registerDisplayListener(DisplayListener listener, Handler handler) {}
    public void unregisterDisplayListener(DisplayListener listener) {}

    public void startWifiDisplayScan() {}
    public void stopWifiDisplayScan() {}
    public void connectWifiDisplay(String deviceAddress) {}
    public void pauseWifiDisplay() {}
    public void resumeWifiDisplay() {}
    public void disconnectWifiDisplay() {}
    public void renameWifiDisplay(String deviceAddress, String alias) {}
    public void forgetWifiDisplay(String deviceAddress) {}
    public WifiDisplayStatus getWifiDisplayStatus() { return null; }
    public void setSaturationLevel(float level) {}

    public Point getStableDisplaySize() {
        return new Point(1080, 1920);
    }

    public List<BrightnessChangeEvent> getBrightnessEvents() {
        return Collections.emptyList();
    }

    public List<AmbientBrightnessDayStats> getAmbientBrightnessStats() {
        return Collections.emptyList();
    }

    public void setBrightnessConfiguration(BrightnessConfiguration config) {}
    public void setBrightnessConfigurationForUser(BrightnessConfiguration config, int userId, String packageName) {}
    public BrightnessConfiguration getBrightnessConfiguration() { return null; }
    public BrightnessConfiguration getBrightnessConfigurationForUser(int userId) { return null; }
    public BrightnessConfiguration getDefaultBrightnessConfiguration() { return null; }
    public void setTemporaryBrightness(int brightness) {}
    public void setTemporaryAutoBrightnessAdjustment(float adjustment) {}
    public android.util.Pair<float[], float[]> getMinimumBrightnessCurve() { return null; }
}
