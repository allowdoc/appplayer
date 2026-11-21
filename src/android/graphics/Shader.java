package android.graphics;

/**
 * Pure-Java Shader shim for desktop headless rendering.
 * Shadows the framework Shader so gradients never need native color-space
 * instances (ColorSpace.getNativeInstance is unavailable on desktop).
 */
public class Shader {

    public enum TileMode {
        CLAMP(0),
        REPEAT(1),
        MIRROR(2);

        public final int nativeInt;
        TileMode(int nativeInt) {
            this.nativeInt = nativeInt;
        }
    }

    private Matrix mLocalMatrix;

    public Shader() {
    }

    public boolean getLocalMatrix(Matrix localM) {
        if (mLocalMatrix == null) {
            return false;
        }
        localM.set(mLocalMatrix);
        return true;
    }

    public void setLocalMatrix(Matrix localM) {
        if (localM == null) {
            mLocalMatrix = null;
        } else {
            if (mLocalMatrix == null) {
                mLocalMatrix = new Matrix();
            }
            mLocalMatrix.set(localM);
        }
    }

    /** Framework parity helper — desktop builds never create native shaders. */
    protected boolean shouldTryToUseSRGBConversion() {
        return false;
    }
}
