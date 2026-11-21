package android.graphics;

import java.io.PrintWriter;
import java.util.Arrays;

public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    public enum ScaleToFit {
        FILL(0), START(1), CENTER(2), END(3);
        final int nativeInt;
        ScaleToFit(int nativeInt) { this.nativeInt = nativeInt; }
    }

    public static final Matrix IDENTITY_MATRIX = new Matrix();

    public final long native_instance = 1L;
    private final float[] values = new float[9];

    public Matrix() {
        reset();
    }

    public Matrix(Matrix src) {
        set(src);
    }

    public boolean isIdentity() {
        return values[MSCALE_X] == 1 && values[MSKEW_X] == 0 && values[MTRANS_X] == 0 &&
               values[MSKEW_Y] == 0 && values[MSCALE_Y] == 1 && values[MTRANS_Y] == 0 &&
               values[MPERSP_0] == 0 && values[MPERSP_1] == 0 && values[MPERSP_2] == 1;
    }

    public boolean isAffine() {
        return values[MPERSP_0] == 0 && values[MPERSP_1] == 0 && values[MPERSP_2] == 1;
    }

    public boolean rectStaysRect() {
        return (values[MSKEW_X] == 0 && values[MSKEW_Y] == 0) ||
               (values[MSCALE_X] == 0 && values[MSCALE_Y] == 0);
    }

    public void reset() {
        Arrays.fill(values, 0);
        values[MSCALE_X] = 1.0f;
        values[MSCALE_Y] = 1.0f;
        values[MPERSP_2] = 1.0f;
    }

    public void set(Matrix src) {
        if (src == null) {
            reset();
        } else {
            System.arraycopy(src.values, 0, values, 0, 9);
        }
    }

    public void setTranslate(float dx, float dy) {
        reset();
        values[MTRANS_X] = dx;
        values[MTRANS_Y] = dy;
    }

    public void setScale(float sx, float sy, float px, float py) {
        reset();
        values[MSCALE_X] = sx;
        values[MSCALE_Y] = sy;
        values[MTRANS_X] = px - sx * px;
        values[MTRANS_Y] = py - sy * py;
    }

    public void setScale(float sx, float sy) {
        reset();
        values[MSCALE_X] = sx;
        values[MSCALE_Y] = sy;
    }

    public void setRotate(float degrees, float px, float py) {
        reset();
        double rad = Math.toRadians(degrees);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        values[MSCALE_X] = cos;
        values[MSKEW_X] = -sin;
        values[MSKEW_Y] = sin;
        values[MSCALE_Y] = cos;
        values[MTRANS_X] = px * (1 - cos) + py * sin;
        values[MTRANS_Y] = py * (1 - cos) - px * sin;
    }

    public void setRotate(float degrees) {
        reset();
        double rad = Math.toRadians(degrees);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        values[MSCALE_X] = cos;
        values[MSKEW_X] = -sin;
        values[MSKEW_Y] = sin;
        values[MSCALE_Y] = cos;
    }

    public void setSinCos(float sinValue, float cosValue, float px, float py) {
        reset();
        values[MSCALE_X] = cosValue;
        values[MSKEW_X] = -sinValue;
        values[MSKEW_Y] = sinValue;
        values[MSCALE_Y] = cosValue;
        values[MTRANS_X] = px * (1 - cosValue) + py * sinValue;
        values[MTRANS_Y] = py * (1 - cosValue) - px * sinValue;
    }

    public void setSinCos(float sinValue, float cosValue) {
        reset();
        values[MSCALE_X] = cosValue;
        values[MSKEW_X] = -sinValue;
        values[MSKEW_Y] = sinValue;
        values[MSCALE_Y] = cosValue;
    }

    public void setSkew(float kx, float ky, float px, float py) {
        reset();
        values[MSKEW_X] = kx;
        values[MSKEW_Y] = ky;
        values[MTRANS_X] = -kx * py;
        values[MTRANS_Y] = -ky * px;
    }

    public void setSkew(float kx, float ky) {
        reset();
        values[MSKEW_X] = kx;
        values[MSKEW_Y] = ky;
    }

    public boolean setConcat(Matrix a, Matrix b) {
        float[] res = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                res[r * 3 + c] =
                    a.values[r * 3 + 0] * b.values[0 * 3 + c] +
                    a.values[r * 3 + 1] * b.values[1 * 3 + c] +
                    a.values[r * 3 + 2] * b.values[2 * 3 + c];
            }
        }
        System.arraycopy(res, 0, values, 0, 9);
        return true;
    }

    public boolean preTranslate(float dx, float dy) {
        Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        return setConcat(this, m);
    }

    public boolean preScale(float sx, float sy, float px, float py) {
        Matrix m = new Matrix();
        m.setScale(sx, sy, px, py);
        return setConcat(this, m);
    }

    public boolean preScale(float sx, float sy) {
        Matrix m = new Matrix();
        m.setScale(sx, sy);
        return setConcat(this, m);
    }

    public boolean preRotate(float degrees, float px, float py) {
        Matrix m = new Matrix();
        m.setRotate(degrees, px, py);
        return setConcat(this, m);
    }

    public boolean preRotate(float degrees) {
        Matrix m = new Matrix();
        m.setRotate(degrees);
        return setConcat(this, m);
    }

    public boolean preSkew(float kx, float ky, float px, float py) {
        Matrix m = new Matrix();
        m.setSkew(kx, ky, px, py);
        return setConcat(this, m);
    }

    public boolean preSkew(float kx, float ky) {
        Matrix m = new Matrix();
        m.setSkew(kx, ky);
        return setConcat(this, m);
    }

    public boolean preConcat(Matrix other) {
        return setConcat(this, other);
    }

    public boolean postTranslate(float dx, float dy) {
        Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        return setConcat(m, this);
    }

    public boolean postScale(float sx, float sy, float px, float py) {
        Matrix m = new Matrix();
        m.setScale(sx, sy, px, py);
        return setConcat(m, this);
    }

    public boolean postScale(float sx, float sy) {
        Matrix m = new Matrix();
        m.setScale(sx, sy);
        return setConcat(m, this);
    }

    public boolean postRotate(float degrees, float px, float py) {
        Matrix m = new Matrix();
        m.setRotate(degrees, px, py);
        return setConcat(m, this);
    }

    public boolean postRotate(float degrees) {
        Matrix m = new Matrix();
        m.setRotate(degrees);
        return setConcat(m, this);
    }

    public boolean postSkew(float kx, float ky, float px, float py) {
        Matrix m = new Matrix();
        m.setSkew(kx, ky, px, py);
        return setConcat(m, this);
    }

    public boolean postSkew(float kx, float ky) {
        Matrix m = new Matrix();
        m.setSkew(kx, ky);
        return setConcat(m, this);
    }

    public boolean postConcat(Matrix other) {
        return setConcat(other, this);
    }

    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (src.isEmpty()) {
            reset();
            return false;
        }
        if (dst.isEmpty()) {
            reset();
            return true;
        }
        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();
        if (stf != ScaleToFit.FILL) {
            float s = (stf == ScaleToFit.START || stf == ScaleToFit.CENTER || stf == ScaleToFit.END)
                ? Math.min(sx, sy) : Math.max(sx, sy);
            sx = s;
            sy = s;
        }
        float tx = dst.left - src.left * sx;
        float ty = dst.top - src.top * sy;
        if (stf == ScaleToFit.CENTER) {
            tx += (dst.width() - src.width() * sx) * 0.5f;
            ty += (dst.height() - src.height() * sy) * 0.5f;
        } else if (stf == ScaleToFit.END) {
            tx += (dst.width() - src.width() * sx);
            ty += (dst.height() - src.height() * sy);
        }
        reset();
        values[MSCALE_X] = sx;
        values[MSCALE_Y] = sy;
        values[MTRANS_X] = tx;
        values[MTRANS_Y] = ty;
        return true;
    }

    public boolean setPolyToPoly(float[] src, int srcIndex, float[] dst, int dstIndex, int pointCount) {
        reset();
        return true;
    }

    public boolean invert(Matrix inverse) {
        float det = values[MSCALE_X] * values[MSCALE_Y] - values[MSKEW_X] * values[MSKEW_Y];
        if (det == 0.0f) return false;
        float invDet = 1.0f / det;
        if (inverse != null) {
            inverse.reset();
            inverse.values[MSCALE_X] = values[MSCALE_Y] * invDet;
            inverse.values[MSKEW_X] = -values[MSKEW_X] * invDet;
            inverse.values[MTRANS_X] = (values[MSKEW_X] * values[MTRANS_Y] - values[MSCALE_Y] * values[MTRANS_X]) * invDet;
            inverse.values[MSKEW_Y] = -values[MSKEW_Y] * invDet;
            inverse.values[MSCALE_Y] = values[MSCALE_X] * invDet;
            inverse.values[MTRANS_Y] = (values[MSKEW_Y] * values[MTRANS_X] - values[MSCALE_X] * values[MTRANS_Y]) * invDet;
        }
        return true;
    }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        for (int i = 0; i < pointCount; i++) {
            float x = src[srcIndex + i * 2 + 0];
            float y = src[srcIndex + i * 2 + 1];
            dst[dstIndex + i * 2 + 0] = values[MSCALE_X] * x + values[MSKEW_X] * y + values[MTRANS_X];
            dst[dstIndex + i * 2 + 1] = values[MSKEW_Y] * x + values[MSCALE_Y] * y + values[MTRANS_Y];
        }
    }

    public void mapVectors(float[] dst, int dstIndex, float[] src, int srcIndex, int vectorCount) {
        for (int i = 0; i < vectorCount; i++) {
            float x = src[srcIndex + i * 2 + 0];
            float y = src[srcIndex + i * 2 + 1];
            dst[dstIndex + i * 2 + 0] = values[MSCALE_X] * x + values[MSKEW_X] * y;
            dst[dstIndex + i * 2 + 1] = values[MSKEW_Y] * x + values[MSCALE_Y] * y;
        }
    }

    public void mapPoints(float[] dst, float[] src) {
        mapPoints(dst, 0, src, 0, dst.length >> 1);
    }

    public void mapVectors(float[] dst, float[] src) {
        mapVectors(dst, 0, src, 0, dst.length >> 1);
    }

    public void mapPoints(float[] pts) {
        mapPoints(pts, 0, pts, 0, pts.length >> 1);
    }

    public void mapVectors(float[] vecs) {
        mapVectors(vecs, 0, vecs, 0, vecs.length >> 1);
    }

    public boolean mapRect(RectF dst, RectF src) {
        if (dst == null || src == null) return false;
        float[] pts = new float[]{src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom};
        mapPoints(pts);
        float minX = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        float maxX = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        float minY = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        float maxY = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        dst.set(minX, minY, maxX, maxY);
        return true;
    }

    public boolean mapRect(RectF rect) {
        return mapRect(rect, rect);
    }

    public float mapRadius(float radius) {
        return radius * Math.max(Math.abs(values[MSCALE_X]), Math.abs(values[MSCALE_Y]));
    }

    public void getValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(this.values, 0, values, 0, 9);
    }

    public void setValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(values, 0, this.values, 0, 9);
    }

    public final long ni() {
        return native_instance;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Matrix) {
            return Arrays.equals(values, ((Matrix) obj).values);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("Matrix{");
        toShortString(sb);
        sb.append('}');
        return sb.toString();
    }

    public String toShortString() {
        StringBuilder sb = new StringBuilder(64);
        toShortString(sb);
        return sb.toString();
    }

    public void toShortString(StringBuilder sb) {
        sb.append('[');
        sb.append(values[0]).append(", ").append(values[1]).append(", ").append(values[2]).append("][");
        sb.append(values[3]).append(", ").append(values[4]).append(", ").append(values[5]).append("][");
        sb.append(values[6]).append(", ").append(values[7]).append(", ").append(values[8]).append(']');
    }

    public void printShortString(PrintWriter pw) {
        pw.print('[');
        pw.print(values[0]); pw.print(", "); pw.print(values[1]); pw.print(", "); pw.print(values[2]); pw.print("][");
        pw.print(values[3]); pw.print(", "); pw.print(values[4]); pw.print(", "); pw.print(values[5]); pw.print("][");
        pw.print(values[6]); pw.print(", "); pw.print(values[7]); pw.print(", "); pw.print(values[8]); pw.print(']');
    }
}
