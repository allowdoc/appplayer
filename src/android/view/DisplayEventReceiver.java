package android.view;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import java.lang.ref.WeakReference;

public abstract class DisplayEventReceiver {
    public static final int VSYNC_SOURCE_APP = 0;
    public static final int VSYNC_SOURCE_SURFACE_FLINGER = 1;

    private static final String TAG = "DisplayEventReceiver";
    private long mReceiverPtr = 1L;
    private MessageQueue mMessageQueue;
    private final Handler mHandler;

    public DisplayEventReceiver(Looper looper) {
        this(looper, VSYNC_SOURCE_APP);
    }

    public DisplayEventReceiver(Looper looper, int vsyncSource) {
        if (looper == null) {
            throw new IllegalArgumentException("looper must not be null");
        }
        this.mMessageQueue = looper.getQueue();
        this.mHandler = new Handler(looper);
        this.mReceiverPtr = 1L;
    }

    public void dispose() {
        mReceiverPtr = 0L;
    }

    public void scheduleVsync() {
        if (mHandler != null) {
            mHandler.postDelayed(() -> {
                long now = System.nanoTime();
                onVsync(now, 0, 0);
            }, 16);
        }
    }

    public void onVsync(long timestampNanos, long physicalDisplayId, int frame) {}
    public void onHotplug(long timestampNanos, long physicalDisplayId, boolean connected) {}
    public void onConfigChanged(long timestampNanos, long physicalDisplayId, int configId) {}
}
