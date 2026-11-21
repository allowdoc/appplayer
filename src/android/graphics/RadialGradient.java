package android.graphics;

/** Pure-Java RadialGradient shim for desktop headless rendering. */
public class RadialGradient extends Shader {

    private final float mCx, mCy, mRadius;
    private final int[] mColors;
    private final float[] mPositions;
    private final TileMode mTile;

    public RadialGradient(float centerX, float centerY, float radius,
                          int color0, int color1, TileMode tile) {
        this(centerX, centerY, radius, new int[]{color0, color1}, null, tile);
    }

    public RadialGradient(float centerX, float centerY, float radius,
                          int[] colors, float[] positions, TileMode tile) {
        mCx = centerX; mCy = centerY; mRadius = radius;
        mColors = colors != null ? colors.clone() : new int[]{0xFF000000, 0xFFFFFFFF};
        mPositions = positions != null ? positions.clone() : null;
        mTile = tile != null ? tile : TileMode.CLAMP;
    }

    public RadialGradient(float centerX, float centerY, float radius,
                          long[] colors, float[] positions, TileMode tile) {
        this(centerX, centerY, radius, toIntColors(colors), positions, tile);
    }

    private static int[] toIntColors(long[] colors) {
        if (colors == null) return new int[]{0xFF000000, 0xFFFFFFFF};
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = (int) colors[i];
        return out;
    }
}
