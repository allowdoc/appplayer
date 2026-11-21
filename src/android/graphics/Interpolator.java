package android.graphics;

public class Interpolator {
    public enum Result {
        NORMAL,
        FREEZE_START,
        FREEZE_END
    }

    private int mValueCount;
    private int mFrameCount;

    public Interpolator(int valueCount) {
        this(valueCount, 2);
    }

    public Interpolator(int valueCount, int frameCount) {
        mValueCount = valueCount;
        mFrameCount = frameCount;
    }

    public void reset(int valueCount) {
        reset(valueCount, 2);
    }

    public void reset(int valueCount, int frameCount) {
        mValueCount = valueCount;
        mFrameCount = frameCount;
    }

    public final int getKeyFrameCount() {
        return mFrameCount;
    }

    public final int getValueCount() {
        return mValueCount;
    }

    public void setKeyFrame(int index, int msec, float[] values) {}

    public void setKeyFrame(int index, int msec, float[] values, float[] blend) {}

    public void setRepeatMirror(float repeatCount, boolean mirror) {}

    public Result timeToValues(float[] values) {
        return timeToValues((int) android.os.SystemClock.uptimeMillis(), values);
    }

    public Result timeToValues(int msec, float[] values) {
        if (values != null && values.length > 0) {
            values[0] = 1.0f;
        }
        return Result.NORMAL;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
