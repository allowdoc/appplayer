package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pure Java in-process android.graphics.Canvas backed by java.awt.Graphics2D.
 * Allows genuine Android View.draw() and ViewGroup layout rendering on OpenJDK without C++ JNI.
 */
public class Canvas extends BaseCanvas {
    public static boolean sCompatibilityRestore = false;
    public static boolean sCompatibilitySetBitmap = false;
    public static final int MATRIX_SAVE_FLAG = 1;
    public static final int CLIP_SAVE_FLAG = 2;
    public static final int HAS_ALPHA_LAYER_SAVE_FLAG = 4;
    public static final int FULL_COLOR_LAYER_SAVE_FLAG = 8;
    public static final int CLIP_TO_LAYER_SAVE_FLAG = 16;
    public static final int ALL_SAVE_FLAG = 31;

    public static void setCompatibilityVersion(int version) {}
    public static void freeCaches() {}
    public static void freeTextLayoutCaches() {}

    private Graphics2D mG2d;
    private int mWidth = 1080;
    private int mHeight = 1920;
    private int mDensity = 420;

    private static class SaveState {
        final AffineTransform transform;
        final Shape clip;
        SaveState(AffineTransform t, Shape c) {
            this.transform = t != null ? (AffineTransform) t.clone() : new AffineTransform();
            this.clip = c;
        }
    }

    private final Deque<SaveState> mSaveStack = new ArrayDeque<>();

    public Canvas() {
        BufferedImage img = new BufferedImage(mWidth, mHeight, BufferedImage.TYPE_INT_ARGB);
        initGraphics(img.createGraphics(), mWidth, mHeight);
    }

    public Canvas(Bitmap bitmap) {
        if (bitmap != null) {
            mWidth = Math.max(1, bitmap.getWidth());
            mHeight = Math.max(1, bitmap.getHeight());
            mDensity = bitmap.getDensity();
        }
        BufferedImage img = new BufferedImage(mWidth, mHeight, BufferedImage.TYPE_INT_ARGB);
        initGraphics(img.createGraphics(), mWidth, mHeight);
    }

    public Canvas(long nativeCanvas) {
        this();
    }

    public Canvas(Graphics2D g2d, int width, int height) {
        initGraphics(g2d, width, height);
    }

    private void initGraphics(Graphics2D g2d, int w, int h) {
        this.mG2d = g2d;
        this.mWidth = w;
        this.mHeight = h;
        if (this.mG2d != null) {
            this.mG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.mG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.mG2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }
    }

    public void setGraphics(Graphics2D g2d, int w, int h) {
        initGraphics(g2d, w, h);
        mSaveStack.clear();
    }

    public Graphics2D getGraphics2D() {
        return mG2d;
    }

    public long getNativeCanvasWrapper() {
        return 1L;
    }

    @Override
    public boolean isHardwareAccelerated() {
        return false;
    }

    public boolean isRecordingFor(Object o) {
        return false;
    }

    public void enableZ() {}

    public void disableZ() {}

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getDensity() {
        return mDensity;
    }

    public void setDensity(int density) {
        this.mDensity = density;
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            initGraphics(new BufferedImage(Math.max(1, bitmap.getWidth()), Math.max(1, bitmap.getHeight()), BufferedImage.TYPE_INT_ARGB).createGraphics(), bitmap.getWidth(), bitmap.getHeight());
        }
    }

    public int save() {
        if (mG2d == null) return 1;
        mSaveStack.push(new SaveState(mG2d.getTransform(), mG2d.getClip()));
        return mSaveStack.size();
    }

    public int save(int saveFlags) {
        return save();
    }

    public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
        return save();
    }

    public int saveLayer(RectF bounds, Paint paint) {
        return save();
    }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint, int saveFlags) {
        return save();
    }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint) {
        return save();
    }

    public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
        return save();
    }

    public int saveLayerAlpha(RectF bounds, int alpha) {
        return save();
    }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int saveFlags) {
        return save();
    }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        return save();
    }

    public void restore() {
        if (mG2d == null || mSaveStack.isEmpty()) return;
        SaveState state = mSaveStack.pop();
        mG2d.setTransform(state.transform);
        mG2d.setClip(state.clip);
    }

    public int getSaveCount() {
        return mSaveStack.size() + 1;
    }

    public void restoreToCount(int count) {
        while (mSaveStack.size() >= count && !mSaveStack.isEmpty()) {
            restore();
        }
    }

    public void translate(float dx, float dy) {
        if (mG2d != null) {
            mG2d.translate(dx, dy);
        }
    }

    public void scale(float sx, float sy) {
        if (mG2d != null) {
            mG2d.scale(sx, sy);
        }
    }

    public final void scale(float sx, float sy, float px, float py) {
        translate(px, py);
        scale(sx, sy);
        translate(-px, -py);
    }

    public void rotate(float degrees) {
        if (mG2d != null) {
            mG2d.rotate(Math.toRadians(degrees));
        }
    }

    public final void rotate(float degrees, float px, float py) {
        translate(px, py);
        rotate(degrees);
        translate(-px, -py);
    }

    public void skew(float sx, float sy) {
        if (mG2d != null) {
            mG2d.shear(sx, sy);
        }
    }

    public void concat(Matrix matrix) {
        if (mG2d != null && matrix != null) {
            // Android matrix to AWT AffineTransform
            float[] vals = new float[9];
            matrix.getValues(vals);
            AffineTransform at = new AffineTransform(vals[0], vals[3], vals[1], vals[4], vals[2], vals[5]);
            mG2d.transform(at);
        }
    }

    public void setMatrix(Matrix matrix) {
        if (mG2d != null) {
            if (matrix == null) {
                mG2d.setTransform(new AffineTransform());
            } else {
                float[] vals = new float[9];
                matrix.getValues(vals);
                mG2d.setTransform(new AffineTransform(vals[0], vals[3], vals[1], vals[4], vals[2], vals[5]));
            }
        }
    }

    public void getMatrix(Matrix matrix) {
        if (mG2d != null && matrix != null) {
            AffineTransform at = mG2d.getTransform();
            float[] vals = new float[] {
                (float) at.getScaleX(), (float) at.getShearX(), (float) at.getTranslateX(),
                (float) at.getShearY(), (float) at.getScaleY(), (float) at.getTranslateY(),
                0f, 0f, 1f
            };
            matrix.setValues(vals);
        }
    }

    public final Matrix getMatrix() {
        Matrix m = new Matrix();
        getMatrix(m);
        return m;
    }

    public boolean clipRect(RectF rect) {
        if (rect == null) return false;
        return clipRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    public boolean clipRect(Rect rect) {
        if (rect == null) return false;
        return clipRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    public boolean clipRect(float left, float top, float right, float bottom) {
        if (mG2d != null) {
            mG2d.clip(new Rectangle2D.Float(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
            return true;
        }
        return false;
    }

    public boolean clipRect(int left, int top, int right, int bottom) {
        return clipRect((float) left, (float) top, (float) right, (float) bottom);
    }

    public boolean clipOutRect(RectF rect) {
        return clipRect(rect);
    }

    public boolean clipOutRect(Rect rect) {
        return clipRect(rect);
    }

    public boolean clipOutRect(float left, float top, float right, float bottom) {
        return clipRect(left, top, right, bottom);
    }

    public boolean clipPath(Path path) {
        if (mG2d != null && path != null && path.getPath2D() != null) {
            mG2d.clip(path.getPath2D());
            return true;
        }
        return false;
    }

    public boolean clipOutPath(Path path) {
        return clipPath(path);
    }

    public boolean getClipBounds(Rect bounds) {
        if (mG2d != null && bounds != null) {
            java.awt.Rectangle r = mG2d.getClipBounds();
            if (r != null) {
                bounds.set(r.x, r.y, r.x + r.width, r.y + r.height);
                return true;
            }
        }
        if (bounds != null) {
            bounds.set(0, 0, mWidth, mHeight);
        }
        return false;
    }

    public final Rect getClipBounds() {
        Rect r = new Rect();
        getClipBounds(r);
        return r;
    }

    public enum EdgeType {
        BW(0),
        AA(1);

        public final int nativeInt;
        EdgeType(int nativeInt) {
            this.nativeInt = nativeInt;
        }
    }

    public boolean quickReject(RectF rect, EdgeType type) {
        return false;
    }

    public boolean quickReject(Path path, EdgeType type) {
        return false;
    }

    public boolean quickReject(float left, float top, float right, float bottom, EdgeType type) {
        return false;
    }

    public boolean quickReject(RectF rect) {
        return false;
    }

    public boolean quickReject(Path path) {
        return false;
    }

    public boolean quickReject(float left, float top, float right, float bottom) {
        return false;
    }

    private void applyPaint(Paint paint) {
        if (mG2d == null || paint == null) return;
        int color = paint.getColor();
        mG2d.setColor(new Color(color, true));
        float strokeW = paint.getStrokeWidth();
        if (strokeW > 0) {
            mG2d.setStroke(new BasicStroke(strokeW));
        } else {
            mG2d.setStroke(new BasicStroke(1.0f));
        }
    }

    @Override
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        if (mG2d == null) return;
        applyPaint(paint);
        float w = Math.max(0, right - left);
        float h = Math.max(0, bottom - top);
        if (paint == null || paint.getStyle() == Paint.Style.FILL || paint.getStyle() == Paint.Style.FILL_AND_STROKE) {
            mG2d.fill(new Rectangle2D.Float(left, top, w, h));
        }
        if (paint != null && (paint.getStyle() == Paint.Style.STROKE || paint.getStyle() == Paint.Style.FILL_AND_STROKE)) {
            mG2d.draw(new Rectangle2D.Float(left, top, w, h));
        }
    }

    @Override
    public void drawRect(Rect r, Paint paint) {
        if (r != null) drawRect(r.left, r.top, r.right, r.bottom, paint);
    }

    @Override
    public void drawRect(RectF r, Paint paint) {
        if (r != null) drawRect(r.left, r.top, r.right, r.bottom, paint);
    }

    @Override
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        if (mG2d == null) return;
        applyPaint(paint);
        float w = Math.max(0, right - left);
        float h = Math.max(0, bottom - top);
        RoundRectangle2D.Float rr = new RoundRectangle2D.Float(left, top, w, h, rx * 2, ry * 2);
        if (paint == null || paint.getStyle() == Paint.Style.FILL || paint.getStyle() == Paint.Style.FILL_AND_STROKE) {
            mG2d.fill(rr);
        }
        if (paint != null && (paint.getStyle() == Paint.Style.STROKE || paint.getStyle() == Paint.Style.FILL_AND_STROKE)) {
            mG2d.draw(rr);
        }
    }

    @Override
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        if (rect != null) drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint);
    }

    @Override
    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        if (mG2d == null) return;
        applyPaint(paint);
        Ellipse2D.Float el = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2);
        if (paint == null || paint.getStyle() == Paint.Style.FILL || paint.getStyle() == Paint.Style.FILL_AND_STROKE) {
            mG2d.fill(el);
        }
        if (paint != null && (paint.getStyle() == Paint.Style.STROKE || paint.getStyle() == Paint.Style.FILL_AND_STROKE)) {
            mG2d.draw(el);
        }
    }

    @Override
    public void drawOval(float left, float top, float right, float bottom, Paint paint) {
        if (mG2d == null) return;
        applyPaint(paint);
        Ellipse2D.Float el = new Ellipse2D.Float(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        if (paint == null || paint.getStyle() == Paint.Style.FILL || paint.getStyle() == Paint.Style.FILL_AND_STROKE) {
            mG2d.fill(el);
        }
        if (paint != null && (paint.getStyle() == Paint.Style.STROKE || paint.getStyle() == Paint.Style.FILL_AND_STROKE)) {
            mG2d.draw(el);
        }
    }

    @Override
    public void drawOval(RectF oval, Paint paint) {
        if (oval != null) drawOval(oval.left, oval.top, oval.right, oval.bottom, paint);
    }

    @Override
    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        if (mG2d == null) return;
        applyPaint(paint);
        mG2d.draw(new Line2D.Float(startX, startY, stopX, stopY));
    }

    @Override
    public void drawLines(float[] pts, int offset, int count, Paint paint) {
        if (pts == null || mG2d == null) return;
        applyPaint(paint);
        for (int i = 0; i < count - 3; i += 4) {
            mG2d.draw(new Line2D.Float(pts[offset + i], pts[offset + i + 1], pts[offset + i + 2], pts[offset + i + 3]));
        }
    }

    @Override
    public void drawLines(float[] pts, Paint paint) {
        if (pts != null) drawLines(pts, 0, pts.length, paint);
    }

    @Override
    public void drawPath(Path path, Paint paint) {
        if (mG2d == null || path == null || path.getPath2D() == null) return;
        applyPaint(paint);
        if (paint == null || paint.getStyle() == Paint.Style.FILL || paint.getStyle() == Paint.Style.FILL_AND_STROKE) {
            mG2d.fill(path.getPath2D());
        }
        if (paint != null && (paint.getStyle() == Paint.Style.STROKE || paint.getStyle() == Paint.Style.FILL_AND_STROKE)) {
            mG2d.draw(path.getPath2D());
        }
    }

    @Override
    public void drawText(String text, float x, float y, Paint paint) {
        if (mG2d == null || text == null || text.isEmpty()) return;
        applyPaint(paint);
        if (paint != null) {
            int style = Font.PLAIN;
            if (paint.getTypeface() != null && paint.getTypeface().isBold()) {
                style = Font.BOLD;
            }
            int size = Math.max(8, Math.round(paint.getTextSize()));
            mG2d.setFont(new Font("SansSerif", style, size));
        }
        mG2d.drawString(text, x, y);
    }

    @Override
    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        if (text != null && start >= 0 && end <= text.length() && end > start) {
            drawText(text.subSequence(start, end).toString(), x, y, paint);
        }
    }

    @Override
    public void drawText(char[] index, int start, int count, float x, float y, Paint paint) {
        if (index != null && start >= 0 && count > 0) {
            drawText(new String(index, start, count), x, y, paint);
        }
    }

    @Override
    public void drawText(String text, int start, int end, float x, float y, Paint paint) {
        if (text != null && start >= 0 && end <= text.length() && end > start) {
            drawText(text.substring(start, end), x, y, paint);
        }
    }

    @Override
    public void drawColor(int color) {
        if (mG2d != null) {
            mG2d.setColor(new Color(color, true));
            mG2d.fillRect(0, 0, mWidth, mHeight);
        }
    }

    @Override
    public void drawARGB(int a, int r, int g, int b) {
        drawColor(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    @Override
    public void drawRGB(int r, int g, int b) {
        drawARGB(0xFF, r, g, b);
    }

    @Override
    public void drawPaint(Paint paint) {
        if (mG2d != null && paint != null) {
            drawColor(paint.getColor());
        }
    }

    @Override
    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        if (mG2d == null || bitmap == null) return;
        // Placeholder for bitmap drawing in pure JVM mode
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        if (mG2d == null || bitmap == null || dst == null) return;
        // Placeholder for bitmap drawing in pure JVM mode
    }

    @Override
    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (mG2d == null || bitmap == null || dst == null) return;
        // Placeholder for bitmap drawing in pure JVM mode
    }

    @Override
    public void drawPoint(float x, float y, Paint paint) {
        drawLine(x, y, x, y, paint);
    }

    @Override
    public void drawPoints(float[] pts, int offset, int count, Paint paint) {
        if (pts != null) {
            for (int i = 0; i < count - 1; i += 2) {
                drawPoint(pts[offset + i], pts[offset + i + 1], paint);
            }
        }
    }

    @Override
    public void drawPoints(float[] pts, Paint paint) {
        if (pts != null) drawPoints(pts, 0, pts.length, paint);
    }
}
