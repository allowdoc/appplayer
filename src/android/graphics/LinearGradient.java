package android.graphics;

/**
 * Pure-Java LinearGradient shim for desktop headless rendering.
 * Keeps the full framework constructor surface; colors/positions are retained
 * for the 2D spatial renderer.
 */
public class LinearGradient extends Shader {

    private final float mX0, mY0, mX1, mY1;
    private final int[] mColors;
    private final float[] mPositions;
    private final TileMode mTile;

    public LinearGradient(float x0, float y0, float x1, float y1,
                          int color0, int color1, TileMode tile) {
        this(x0, y0, x1, y1, new int[]{color0, color1}, null, tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1,
                          int[] colors, float[] positions, TileMode tile) {
        mX0 = x0; mY0 = y0; mX1 = x1; mY1 = y1;
        mColors = colors != null ? colors.clone() : new int[]{0xFF000000, 0xFFFFFFFF};
        mPositions = positions != null ? positions.clone() : null;
        mTile = tile != null ? tile : TileMode.CLAMP;
    }

    public LinearGradient(float x0, float y0, float x1, float y1,
                          long[] colors, float[] positions, TileMode tile) {
        this(x0, y0, x1, y1, toIntColors(colors), positions, tile);
    }

    private static int[] toIntColors(long[] colors) {
        if (colors == null) return new int[]{0xFF000000, 0xFFFFFFFF};
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = (int) colors[i];
        return out;
    }

    public int[] getColors() {
        return mColors.clone();
    }

    public float[] getPositions() {
        return mPositions != null ? mPositions.clone() : null;
    }
}
