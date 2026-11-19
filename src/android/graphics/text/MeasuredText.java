package android.graphics.text;

import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Pure-Java In-Process Stub for android.graphics.text.MeasuredText.
 * Eliminates native JNI nGetReleaseFunc / NativeAllocationRegistry crashes
 * during Android 10+ TextView measurement.
 */
public class MeasuredText {
    private final char[] mChars;
    private final boolean mComputeHyphenation;
    private final boolean mComputeLayout;
    private final long mNativePtr = 1L;

    public MeasuredText(char[] chars, boolean computeHyphenation, boolean computeLayout) {
        this.mChars = chars != null ? chars : new char[0];
        this.mComputeHyphenation = computeHyphenation;
        this.mComputeLayout = computeLayout;
    }

    public char[] getChars() {
        return mChars;
    }

    public float getWidth(int start, int end) {
        int len = Math.max(0, end - start);
        return len * 16.0f;
    }

    public int getMemoryUsage() {
        return mChars != null ? mChars.length * 2 : 0;
    }

    public void getBounds(int start, int end, Rect bounds) {
        if (bounds != null) {
            float w = getWidth(start, end);
            bounds.set(0, 0, (int) Math.ceil(w), 32);
        }
    }

    public float getCharWidthAt(int offset) {
        return 16.0f;
    }

    public long getNativePtr() {
        return mNativePtr;
    }

    public static final class Builder {
        private final char[] mText;
        private boolean mComputeHyphenation = false;
        private boolean mComputeLayout = true;

        public Builder(char[] text) {
            if (text == null) {
                throw new IllegalArgumentException("text must not be null");
            }
            this.mText = text;
        }

        public Builder(MeasuredText text) {
            if (text == null) {
                throw new IllegalArgumentException("text must not be null");
            }
            this.mText = text.getChars();
        }

        public Builder appendStyleRun(Paint paint, int length, boolean isRtl) {
            return this;
        }

        public Builder appendReplacementRun(Paint paint, int length, float width) {
            return this;
        }

        public Builder setComputeHyphenation(boolean computeHyphenation) {
            this.mComputeHyphenation = computeHyphenation;
            return this;
        }

        public Builder setComputeLayout(boolean computeLayout) {
            this.mComputeLayout = computeLayout;
            return this;
        }

        public MeasuredText build() {
            return new MeasuredText(mText, mComputeHyphenation, mComputeLayout);
        }
    }
}
