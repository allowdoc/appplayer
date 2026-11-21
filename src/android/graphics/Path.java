package android.graphics;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;

public class Path {
    public enum FillType {
        WINDING(0),
        EVEN_ODD(1),
        INVERSE_WINDING(2),
        INVERSE_EVEN_ODD(3);

        final int nativeInt;
        FillType(int nativeInt) { this.nativeInt = nativeInt; }
    }

    public enum Direction {
        CW(0),
        CCW(1);

        final int nativeInt;
        Direction(int nativeInt) { this.nativeInt = nativeInt; }
    }

    public enum Op {
        DIFFERENCE,
        INTERSECT,
        UNION,
        XOR,
        REVERSE_DIFFERENCE
    }

    public final long mNativePath = 1L;
    public boolean isSimplePath = true;
    public Region rects;
    static final FillType[] sFillTypeArray = FillType.values();

    private Path2D.Float mPath2D;
    private FillType mFillType = FillType.WINDING;
    private float mLastX = 0f;
    private float mLastY = 0f;

    public Path() {
        mPath2D = new Path2D.Float();
    }

    public Path(Path src) {
        if (src != null) {
            mPath2D = (Path2D.Float) src.mPath2D.clone();
            mFillType = src.mFillType;
            mLastX = src.mLastX;
            mLastY = src.mLastY;
        } else {
            mPath2D = new Path2D.Float();
        }
    }

    public void reset() {
        mPath2D.reset();
        mFillType = FillType.WINDING;
        mLastX = 0f;
        mLastY = 0f;
    }

    public void rewind() {
        mPath2D.reset();
        mLastX = 0f;
        mLastY = 0f;
    }

    public void set(Path src) {
        if (src != null) {
            mPath2D = (Path2D.Float) src.mPath2D.clone();
            mFillType = src.mFillType;
            mLastX = src.mLastX;
            mLastY = src.mLastY;
        } else {
            reset();
        }
    }

    public boolean op(Path path, Op op) {
        return op(this, path, op);
    }

    public boolean op(Path path1, Path path2, Op op) {
        return true;
    }

    public boolean isConvex() {
        return true;
    }

    public FillType getFillType() {
        return mFillType;
    }

    public void setFillType(FillType ft) {
        mFillType = ft;
        if (ft == FillType.EVEN_ODD || ft == FillType.INVERSE_EVEN_ODD) {
            mPath2D.setWindingRule(Path2D.WIND_EVEN_ODD);
        } else {
            mPath2D.setWindingRule(Path2D.WIND_NON_ZERO);
        }
    }

    public boolean isInverseFillType() {
        return mFillType == FillType.INVERSE_WINDING || mFillType == FillType.INVERSE_EVEN_ODD;
    }

    public void toggleInverseFillType() {
        if (mFillType == FillType.WINDING) setFillType(FillType.INVERSE_WINDING);
        else if (mFillType == FillType.INVERSE_WINDING) setFillType(FillType.WINDING);
        else if (mFillType == FillType.EVEN_ODD) setFillType(FillType.INVERSE_EVEN_ODD);
        else if (mFillType == FillType.INVERSE_EVEN_ODD) setFillType(FillType.EVEN_ODD);
    }

    public boolean isEmpty() {
        return mPath2D.getCurrentPoint() == null;
    }

    public boolean isRect(RectF rect) {
        return false;
    }

    public void computeBounds(RectF bounds, boolean exact) {
        Rectangle2D b = mPath2D.getBounds2D();
        if (b != null && bounds != null) {
            bounds.set((float) b.getMinX(), (float) b.getMinY(), (float) b.getMaxX(), (float) b.getMaxY());
        }
    }

    public void incReserve(int extraPtCount) {}

    public void moveTo(float x, float y) {
        mPath2D.moveTo(x, y);
        mLastX = x;
        mLastY = y;
    }

    public void rMoveTo(float dx, float dy) {
        moveTo(mLastX + dx, mLastY + dy);
    }

    public void lineTo(float x, float y) {
        mPath2D.lineTo(x, y);
        mLastX = x;
        mLastY = y;
    }

    public void rLineTo(float dx, float dy) {
        lineTo(mLastX + dx, mLastY + dy);
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        mPath2D.quadTo(x1, y1, x2, y2);
        mLastX = x2;
        mLastY = y2;
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
        quadTo(mLastX + dx1, mLastY + dy1, mLastX + dx2, mLastY + dy2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        mPath2D.curveTo(x1, y1, x2, y2, x3, y3);
        mLastX = x3;
        mLastY = y3;
    }

    public void rCubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        cubicTo(mLastX + x1, mLastY + y1, mLastX + x2, mLastY + y2, mLastX + x3, mLastY + y3);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
        if (oval != null) {
            arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, forceMoveTo);
        }
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle) {
        arcTo(oval, startAngle, sweepAngle, false);
    }

    public void arcTo(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean forceMoveTo) {
        float width = right - left;
        float height = bottom - top;
        Arc2D.Float arc = new Arc2D.Float(left, top, width, height, -startAngle, -sweepAngle, Arc2D.OPEN);
        mPath2D.append(arc, !forceMoveTo);
    }

    public void close() {
        mPath2D.closePath();
    }

    public void addRect(RectF rect, Direction dir) {
        if (rect != null) addRect(rect.left, rect.top, rect.right, rect.bottom, dir);
    }

    public void addRect(float left, float top, float right, float bottom, Direction dir) {
        mPath2D.append(new Rectangle2D.Float(left, top, right - left, bottom - top), false);
    }

    public void addOval(RectF oval, Direction dir) {
        if (oval != null) addOval(oval.left, oval.top, oval.right, oval.bottom, dir);
    }

    public void addOval(float left, float top, float right, float bottom, Direction dir) {
        mPath2D.append(new Ellipse2D.Float(left, top, right - left, bottom - top), false);
    }

    public void addCircle(float x, float y, float radius, Direction dir) {
        mPath2D.append(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), false);
    }

    public void addArc(RectF oval, float startAngle, float sweepAngle) {
        if (oval != null) addArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle);
    }

    public void addArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle) {
        mPath2D.append(new Arc2D.Float(left, top, right - left, bottom - top, -startAngle, -sweepAngle, Arc2D.OPEN), false);
    }

    public void addRoundRect(RectF rect, float rx, float ry, Direction dir) {
        if (rect != null) addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, dir);
    }

    public void addRoundRect(float left, float top, float right, float bottom, float rx, float ry, Direction dir) {
        mPath2D.append(new RoundRectangle2D.Float(left, top, right - left, bottom - top, rx * 2, ry * 2), false);
    }

    public void addRoundRect(RectF rect, float[] radii, Direction dir) {
        if (rect != null) {
            float rx = (radii != null && radii.length > 0) ? radii[0] : 0;
            float ry = (radii != null && radii.length > 1) ? radii[1] : rx;
            addRoundRect(rect, rx, ry, dir);
        }
    }

    public void addRoundRect(float left, float top, float right, float bottom, float[] radii, Direction dir) {
        float rx = (radii != null && radii.length > 0) ? radii[0] : 0;
        float ry = (radii != null && radii.length > 1) ? radii[1] : rx;
        addRoundRect(left, top, right, bottom, rx, ry, dir);
    }

    public void addPath(Path src, float dx, float dy) {
        if (src != null) {
            AffineTransform tx = AffineTransform.getTranslateInstance(dx, dy);
            mPath2D.append(src.mPath2D.getPathIterator(tx), false);
        }
    }

    public void addPath(Path src) {
        if (src != null) {
            mPath2D.append(src.mPath2D, false);
        }
    }

    public void addPath(Path src, Matrix matrix) {
        if (src != null) {
            mPath2D.append(src.mPath2D, false);
        }
    }

    public void offset(float dx, float dy, Path dst) {
        if (dst != null) {
            dst.set(this);
            dst.offset(dx, dy);
        } else {
            offset(dx, dy);
        }
    }

    public void offset(float dx, float dy) {
        mPath2D.transform(AffineTransform.getTranslateInstance(dx, dy));
    }

    public void setLastPoint(float dx, float dy) {
        mLastX = dx;
        mLastY = dy;
    }

    public void transform(Matrix matrix, Path dst) {
        if (dst != null) {
            dst.set(this);
            dst.transform(matrix);
        } else {
            transform(matrix);
        }
    }

    public void transform(Matrix matrix) {}

    public final long readOnlyNI() { return 1L; }
    final long mutateNI() { return 1L; }
    public java.awt.geom.Path2D.Float getPath2D() { return mPath2D; }
    public float[] approximate(float acceptableError) {
        return new float[] { 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f };
    }
}
