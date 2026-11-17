package android.graphics;

import android.view.NativeVectorDrawableAnimator;
import android.view.RenderNodeAnimator;

public final class RenderNode {
    public final long mNativeRenderNode = 1L;
    public static final int USAGE_UNKNOWN = 0;
    public static final int USAGE_BACKGROUND = 1;

    public interface AnimationHost {
        void registerAnimatingRenderNode(RenderNode node);
        void registerVectorDrawableAnimator(NativeVectorDrawableAnimator animator);
    }

    public interface PositionUpdateListener {
        void positionChanged(long frameNumber, int left, int top, int right, int bottom);
        void positionLost(long frameNumber);
    }

    private int mLeft, mTop, mRight, mBottom;
    private float mAlpha = 1.0f;
    private float mTranslationX, mTranslationY, mTranslationZ;
    private float mElevation;
    private float mRotationX, mRotationY, mRotationZ;
    private float mScaleX = 1.0f, mScaleY = 1.0f;
    private float mPivotX, mPivotY;
    private boolean mPivotExplicitlySet;
    private boolean mClipToBounds;
    private boolean mProjectBackwards;
    private boolean mProjectionReceiver;
    private boolean mHasOverlappingRendering = true;
    private Matrix mMatrix;

    public RenderNode(String name) {
        this(name, null);
    }

    private RenderNode(String name, AnimationHost host) {}
    private RenderNode(long nativePtr) {}

    public static RenderNode create(String name, AnimationHost host) {
        return new RenderNode(name, host);
    }

    public static RenderNode adopt(long nativePtr) {
        return new RenderNode(nativePtr);
    }

    public void addPositionUpdateListener(PositionUpdateListener listener) {}
    public void removePositionUpdateListener(PositionUpdateListener listener) {}

    public RecordingCanvas beginRecording(int width, int height) { return null; }
    public RecordingCanvas beginRecording() { return null; }
    public void endRecording() {}
    public RecordingCanvas start(int width, int height) { return null; }
    public void end(RecordingCanvas canvas) {}
    public void discardDisplayList() {}
    public boolean hasDisplayList() { return false; }
    public boolean hasIdentityMatrix() { return true; }
    public void getMatrix(Matrix outMatrix) { if (outMatrix != null) outMatrix.reset(); }
    public void getInverseMatrix(Matrix outMatrix) { if (outMatrix != null) outMatrix.reset(); }

    public boolean setLayerType(int layerType) { return true; }
    public boolean setLayerPaint(Paint paint) { return true; }
    public boolean setUseCompositingLayer(boolean forceHasDisplayList, Paint paint) { return true; }
    public boolean getUseCompositingLayer() { return false; }
    public boolean setClipRect(Rect rect) { return true; }
    public boolean setClipToBounds(boolean clipToBounds) { mClipToBounds = clipToBounds; return true; }
    public boolean getClipToBounds() { return mClipToBounds; }
    public boolean setProjectBackwards(boolean b) { mProjectBackwards = b; return true; }
    public boolean setProjectionReceiver(boolean b) { mProjectionReceiver = b; return true; }
    public boolean setOutline(Outline outline) { return true; }
    public boolean hasShadow() { return false; }
    public boolean setSpotShadowColor(int color) { return true; }
    public int getSpotShadowColor() { return 0; }
    public boolean setAmbientShadowColor(int color) { return true; }
    public int getAmbientShadowColor() { return 0; }
    public boolean setClipToOutline(boolean clipToOutline) { return true; }
    public boolean getClipToOutline() { return false; }
    public boolean setRevealClip(boolean shouldClip, float x, float y, float radius) { return true; }
    public boolean setStaticMatrix(Matrix matrix) { return true; }
    public boolean setAnimationMatrix(Matrix matrix) { mMatrix = matrix; return true; }
    public Matrix getAnimationMatrix() { return mMatrix; }

    public boolean setAlpha(float alpha) { mAlpha = alpha; return true; }
    public float getAlpha() { return mAlpha; }
    public boolean setHasOverlappingRendering(boolean hasOverlappingRendering) { mHasOverlappingRendering = hasOverlappingRendering; return true; }
    public void setUsageHint(int usageHint) {}
    public boolean hasOverlappingRendering() { return mHasOverlappingRendering; }
    public boolean setElevation(float elevation) { mElevation = elevation; return true; }
    public float getElevation() { return mElevation; }
    public boolean setTranslationX(float translationX) { mTranslationX = translationX; return true; }
    public float getTranslationX() { return mTranslationX; }
    public boolean setTranslationY(float translationY) { mTranslationY = translationY; return true; }
    public float getTranslationY() { return mTranslationY; }
    public boolean setTranslationZ(float translationZ) { mTranslationZ = translationZ; return true; }
    public float getTranslationZ() { return mTranslationZ; }
    public boolean setRotationZ(float rotation) { mRotationZ = rotation; return true; }
    public float getRotationZ() { return mRotationZ; }
    public boolean setRotationX(float rotationX) { mRotationX = rotationX; return true; }
    public float getRotationX() { return mRotationX; }
    public boolean setRotationY(float rotationY) { mRotationY = rotationY; return true; }
    public float getRotationY() { return mRotationY; }
    public boolean setScaleX(float scaleX) { mScaleX = scaleX; return true; }
    public float getScaleX() { return mScaleX; }
    public boolean setScaleY(float scaleY) { mScaleY = scaleY; return true; }
    public float getScaleY() { return mScaleY; }
    public boolean setPivotX(float pivotX) { mPivotX = pivotX; mPivotExplicitlySet = true; return true; }
    public float getPivotX() { return mPivotX; }
    public boolean setPivotY(float pivotY) { mPivotY = pivotY; mPivotExplicitlySet = true; return true; }
    public float getPivotY() { return mPivotY; }
    public boolean isPivotExplicitlySet() { return mPivotExplicitlySet; }
    public boolean resetPivot() { mPivotExplicitlySet = false; return true; }
    public boolean setCameraDistance(float distance) { return true; }
    public float getCameraDistance() { return 0f; }

    public boolean setLeft(int left) { mLeft = left; return true; }
    public boolean setTop(int top) { mTop = top; return true; }
    public boolean setRight(int right) { mRight = right; return true; }
    public boolean setBottom(int bottom) { mBottom = bottom; return true; }
    public int getLeft() { return mLeft; }
    public int getTop() { return mTop; }
    public int getRight() { return mRight; }
    public int getBottom() { return mBottom; }
    public int getWidth() { return mRight - mLeft; }
    public int getHeight() { return mBottom - mTop; }

    public boolean setLeftTopRightBottom(int left, int top, int right, int bottom) {
        mLeft = left; mTop = top; mRight = right; mBottom = bottom; return true;
    }
    public boolean setPosition(int left, int top, int right, int bottom) {
        return setLeftTopRightBottom(left, top, right, bottom);
    }
    public boolean setPosition(Rect position) {
        if (position != null) return setPosition(position.left, position.top, position.right, position.bottom);
        return false;
    }
    public boolean offsetLeftAndRight(int offset) { mLeft += offset; mRight += offset; return true; }
    public boolean offsetTopAndBottom(int offset) { mTop += offset; mBottom += offset; return true; }
    public void output() {}
    public long computeApproximateMemoryUsage() { return 0L; }
    public boolean setForceDarkAllowed(boolean allow) { return true; }
    public boolean isForceDarkAllowed() { return false; }
    public long getUniqueId() { return 1L; }
    public void addAnimator(RenderNodeAnimator animator) {}
    public boolean isAttached() { return false; }
    public void registerVectorDrawableAnimator(NativeVectorDrawableAnimator animator) {}
    public void endAllAnimators() {}
    static long access$000() { return 0L; }
}
