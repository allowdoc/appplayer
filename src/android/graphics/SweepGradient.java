package android.graphics;

/** Pure-Java SweepGradient shim for desktop headless rendering. */
public class SweepGradient extends Shader {

    private final float mCx, mCy;
    private final int[] mColors;
    private final float[] mPositions;

    public SweepGradient(float cx, float cy, int color0, int color1) {
        this(cx, cy, new int[]{color0, color1}, null);
    }

    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        mCx = cx; mCy = cy;
        mColors = colors != null ? colors.clone() : new int[]{0xFF000000, 0xFFFFFFFF};
        mPositions = positions != null ? positions.clone() : null;
    }

    public SweepGradient(float cx, float cy, long[] colors, float[] positions) {
        this(cx, cy, toIntColors(colors), positions);
    }

    private static int[] toIntColors(long[] colors) {
        if (colors == null) return new int[]{0xFF000000, 0xFFFFFFFF};
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = (int) colors[i];
        return out;
    }
}
