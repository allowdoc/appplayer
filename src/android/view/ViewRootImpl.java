package android.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Desktop-runner ViewRootImpl shadow (bin/ shadows the native-backed
 * android-all class).
 *
 * On a device every window is attached to exactly one ViewRootImpl - it owns
 * the AttachInfo, the traversal loop and the invalidate channel. Framework
 * classes (View.invalidate, DrawerLayout insets, WindowInsetsCompat,
 * TextView...) consult it through View.getViewRootImpl() and
 * AttachInfo.mViewRootImpl. The android-all implementation is native-heavy
 * (IWindowSession, ThreadedRenderer), so the runner provides this pure-Java
 * ancestor: same identity contract (attachInfo.mViewRootImpl = this,
 * getViewRootImpl() non-null after attach), traversals driven by the runner's
 * HeadlessChoreographer pump on the main looper - exactly one traversal
 * pipeline, like a device.
 */
public class ViewRootImpl {

    // Framework-visible statics the android-all View code reads at
    // construction time (ViewRootImpl.sNewInsetsMode). NEW_INSETS_MODE_FULL
    // (2) is what an API-29 device runs with; View branches on it to build
    // modern insets handling.
    public static final int NEW_INSETS_MODE_NONE = 0;
    public static final int NEW_INSETS_MODE_IME = 1;
    public static final int NEW_INSETS_MODE_FULL = 2;
    public static volatile int sNewInsetsMode = NEW_INSETS_MODE_FULL;

    // Direct-field-access surface: framework classes read these fields
    // straight off ViewRootImpl (View scroll capture, Choreographer frame
    // callbacks, thread checks, window attributes...).
    public final Choreographer mChoreographer = Choreographer.getInstance();
    public final Surface mSurface = new Surface();
    public int mCurScrollY = 0;
    public final Thread mThread = Thread.currentThread();
    public boolean mIsAnimating = false;
    public WindowManager.LayoutParams mWindowAttributes;
    /** Device contract: androidx WindowInsetsCompat reflects this exact field
     *  (ViewRootImpl.class.getDeclaredField("mAttachInfo")) to read the
     *  visible insets through the window's AttachInfo. Must stay present and
     *  be wired at attach time (ActivityStack.attachWindowIfNeeded). */
    public View.AttachInfo mAttachInfo;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private View mView;
    private boolean mAdded;
    private static volatile WindowInsets sRootInsets;

    private static boolean sInTouchMode = false;

    public ViewRootImpl(Context context, Display display) {
        this.mContext = context;
    }

    public ViewRootImpl(Context context, Display display, IWindowSession session, boolean useBLAST) {
        this(context, display);
    }

    public Context getContext() { return mContext; }

    /** The root view this ancestor drives (set via setView, like on a device). */
    public View getView() { return mView; }

    /** ViewRootImpl.setView: the Window addView entry point. */
    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        mView = view;
        mWindowAttributes = attrs;
        mAdded = true;
    }

    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView, int userId) {
        setView(view, attrs, panelParentView);
    }

    public boolean isAdded() { return mAdded; }

    public WindowManager.LayoutParams getWindowAttributes() { return mWindowAttributes; }

    // ---------------- invalidate channel (View.invalidate flows here) --------

    public void dispatchInvalidateOnAnimation(View view) {
        if (view != null) {
            view.invalidate();
        }
    }

    public void dispatchInvalidateOnAnimation(View[] views) {
        if (views != null) {
            for (View v : views) {
                if (v != null) v.invalidate();
            }
        }
    }

    public void dispatchInvalidateDelayed(View view, long delayMs) {
        if (view != null) {
            view.invalidate();
        }
    }

    public void dispatchInvalidateRectDelayed(View.AttachInfo.InvalidateInfo info, long delayMs) {
        dispatchInvalidateRectOnAnimation(info);
    }

    public void dispatchInvalidateRectOnAnimation(View.AttachInfo.InvalidateInfo info) {
        if (info == null) return;
        try {
            java.lang.reflect.Field f = info.getClass().getField("owner");
            Object owner = f.get(info);
            if (owner instanceof View) {
                ((View) owner).invalidate();
            }
        } catch (Throwable ignored) {
        }
    }

    public void cancelInvalidate(View view) {
        // Pure-Java ancestor: pending invalidations ride the main queue and
        // are harmless to run; nothing to cancel.
    }

    public void dispatchCheckFocus() {}

    public void dispatchSystemUiVisibilityChanged(int visibility) {}

    public void dispatchWindowSystemUiVisiblityChanged(int visibility) {}

    public void dispatchWindowInsetsAnimationFinished(Object animation) {}

    // ---------------- traversal ----------------

    /** Real devices: ViewRootImpl owns the traversal loop. The runner's
     *  HeadlessChoreographer pump drives measure/layout/draw on the main
     *  looper, so this schedules nothing extra (single traversal pipeline). */
    public void scheduleTraversals() {}

    public void unscheduleTraversals() {}

    public void doTraversal() {}

    /** Full traversal entry point (measure/layout/draw of the root view). */
    public void performTraversals() {
        if (mView == null) return;
        try {
            mView.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            mView.layout(0, 0, 1080, 1920);
        } catch (Throwable ignored) {}
    }

    // ---------------- insets / window frames ----------------

    // ---------------- misc framework touch points ----------------

    public boolean isRTL() {
        return mContext != null
                && mContext.getResources() != null
                && mContext.getResources().getConfiguration() != null
                && mContext.getResources().getConfiguration().getLayoutDirection()
                        == android.view.View.LAYOUT_DIRECTION_RTL;
    }

    public boolean isHardwareAccelerated() {
        // Skia renderer is the hardware-accelerated equivalent here.
        return true;
    }

    // ---------------- full framework surface (device-parity methods) ---------

    private final android.graphics.Point mLastTouchPoint = new android.graphics.Point(0, 0);

    /** The android-all View calls ViewRootImpl.isInTouchMode() STATICALLY. */
    public static boolean isInTouchMode() { return sInTouchMode; }

    public boolean ensureTouchMode(boolean inTouchMode) {
        if (sInTouchMode == inTouchMode) return false;
        sInTouchMode = inTouchMode;
        return true;
    }

    public boolean isInLayout() { return false; }

    /** ViewGroup asks whether a requestLayout() during layout is consumed by
     *  the current pass; the choreographer pump re-lays-out anyway, so TRUE
     *  matches the device behavior of folding the request into this pass. */
    public boolean requestLayoutDuringLayout(View view) { return true; }

    public int getLastTouchSource() { return 0; }

    public void getLastTouchPoint(android.graphics.Point point) {
        if (point != null) {
            point.set(mLastTouchPoint.x, mLastTouchPoint.y);
        }
    }

    public boolean hasPointerCapture() { return false; }

    public void requestPointerCapture(boolean enabled) {}

    public View getAccessibilityFocusedHost() { return null; }

    public android.view.accessibility.AccessibilityNodeInfo getAccessibilityFocusedVirtualView() {
        return null;
    }

    public void setAccessibilityFocus(View view, android.view.accessibility.AccessibilityNodeInfo info) {}

    public android.view.InsetsController getInsetsController() { return null; }

    public void requestTransitionStart(android.animation.LayoutTransition transition) {}

    /** The android-all View calls ViewRootImpl.isViewDescendantOf STATICALLY. */
    public static boolean isViewDescendantOf(View view, View ancestor) {
        if (view == ancestor) return true;
        android.view.ViewParent p = view != null ? view.getParent() : null;
        while (p instanceof View) {
            if (p == ancestor) return true;
            p = ((View) p).getParent();
        }
        return false;
    }

    public void setDragFocus(View view, android.view.DragEvent event) {}

    public void setLocalDragState(Object state) {}

    public void updateSystemGestureExclusionRectsForView(View view) {}

    /** Desktop window: no root transform - the matrices pass through. */
    public void transformMatrixToGlobal(android.graphics.Matrix m) {
        if (m != null) m.reset();
    }

    public void transformMatrixToLocal(android.graphics.Matrix m) {
        if (m != null) m.reset();
    }

    public Object getSurfaceControl() { return null; }

    /** Device: returns the latest WindowInsets dispatched to this window. */
    public WindowInsets getWindowInsets() {
        return getWindowInsets(false);
    }

    public WindowInsets getWindowInsets(boolean forceRecompute) {
        if (sRootInsets == null || forceRecompute) {
            try {
                sRootInsets = org.headless.runner.agent.DesktopWindowInsetsProvider.getRootWindowInsets();
            } catch (Throwable ignored) {}
        }
        return sRootInsets;
    }

    public void dispatchWindowInsetsChanged(WindowInsets insets) {
        if (insets != null) {
            sRootInsets = insets;
        }
    }
}
