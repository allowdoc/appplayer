package org.headless.render;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * java.awt.Graphics2D implementation backed by GOOGLE SKIA (via Skija).
 *
 * This is the graphics back-end swap point of the runner: our pure-Java
 * android.graphics.Canvas shim (src/android/graphics/Canvas.java) talks to a
 * plain Graphics2D. Pass it a SkiaGraphics2D and every Android View,
 * Drawable, TextView and Paint operation renders through real Skia - the same
 * engine the device uses - instead of Java2D. Zero JNI, zero VM: Skija ships
 * the native Skia in a jar and loads it from the classpath.
 *
 * NOTE: skija Font/Paint/Image are fully qualified to avoid collisions with
 * the java.awt types used by the Graphics2D API.
 */
public class SkiaGraphics2D extends Graphics2D {

    private final io.github.humbleui.skija.Canvas mCanvas;
    private final int mW;
    private final int mH;

    private Color mColor = Color.BLACK;
    private Color mBackground = Color.WHITE;
    private Font mAwtFont = new Font("SansSerif", Font.PLAIN, 32);
    private BasicStroke mStroke = new BasicStroke(1f);

    /** Current transform (Graphics2D semantics) and clip bounds. */
    private AffineTransform mCTM = new AffineTransform();
    private Rectangle2D mClip = new Rectangle2D.Float(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private static final Map<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, io.github.humbleui.skija.Font> FONT_CACHE = new ConcurrentHashMap<>();
    private static volatile Typeface sFallbackTypeface;
    private static boolean sFontManagerLogged = false;

    public SkiaGraphics2D(io.github.humbleui.skija.Canvas canvas, int width, int height) {
        this.mCanvas = canvas;
        this.mW = width;
        this.mH = height;
    }

    // ------------------------------------------------------------------
    // Font plumbing
    // ------------------------------------------------------------------

    private static Typeface systemTypeface(boolean bold) {
        try {
            FontMgr mgr = FontMgr.getDefault();
            String[] families = new String[]{"Segoe UI", "Roboto", "Noto Sans",
                    "DejaVu Sans", "Helvetica", "Arial", "sans-serif"};
            for (String fam : families) {
                try {
                    Typeface t = mgr.matchFamilyStyle(fam, bold ? FontStyle.BOLD : FontStyle.NORMAL);
                    if (t != null) return t;
                } catch (Throwable ignored) {}
            }
            // File-based fallbacks per host OS.
            String[][] files = {
                    {"C:\\Windows\\Fonts\\segoeui.ttf", "C:\\Windows\\Fonts\\segoeuib.ttf"},
                    {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                     "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"},
                    {"/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
                     "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"},
                    {"/System/Library/Fonts/Helvetica.ttc", "/System/Library/Fonts/HelveticaNeue.ttc"}
            };
            for (String[] pair : files) {
                String path = bold ? pair[1] : pair[0];
                try {
                    java.io.File f = new java.io.File(path);
                    if (f.exists()) {
                        Typeface t = mgr.makeFromFile(path);
                        if (t != null) return t;
                    }
                } catch (Throwable ignored) {}
            }
            if (sFallbackTypeface == null) {
                synchronized (SkiaGraphics2D.class) {
                    if (sFallbackTypeface == null) {
                        sFallbackTypeface = mgr.matchFamilyStyle(null, FontStyle.NORMAL);
                    }
                }
            }
            return sFallbackTypeface;
        } catch (Throwable t) {
            if (!sFontManagerLogged) {
                sFontManagerLogged = true;
                System.err.println("[-] [Skia] No system fonts available: " + t);
            }
            return null;
        }
    }

    private io.github.humbleui.skija.Font skijaFont() {
        String key = mAwtFont.getFamily() + "|" + mAwtFont.getStyle() + "|"
                + Math.round(mAwtFont.getSize2D() * 100) / 100.0;
        io.github.humbleui.skija.Font cached = FONT_CACHE.get(key);
        if (cached != null) return cached;
        io.github.humbleui.skija.Font f = skijaFontFor(mAwtFont);
        if (f == null) return null;
        try {
            f.setSubpixel(true);
        } catch (Throwable ignored) {}
        FONT_CACHE.put(key, f);
        return f;
    }

    private static io.github.humbleui.skija.Font skijaFontFor(Font f) {
        boolean bold = (f.getStyle() & Font.BOLD) != 0;
        Typeface tf = TYPEFACE_CACHE.computeIfAbsent((bold ? "B" : "N"), k -> systemTypeface(bold));
        if (tf == null) return null;
        return new io.github.humbleui.skija.Font(tf, f.getSize2D());
    }

    // ------------------------------------------------------------------
    // Shape conversion (user space -> device space via current transform)
    // ------------------------------------------------------------------

    private Path shapeToPath(Shape s) {
        PathBuilder pb = new PathBuilder();
        float[] coords = new float[6];
        for (PathIterator pi = s.getPathIterator(mCTM); !pi.isDone(); pi.next()) {
            int seg = pi.currentSegment(coords);
            switch (seg) {
                case PathIterator.SEG_MOVETO:
                    pb.moveTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_LINETO:
                    pb.lineTo(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_QUADTO:
                    pb.quadTo(coords[0], coords[1], coords[2], coords[3]);
                    break;
                case PathIterator.SEG_CUBICTO:
                    pb.cubicTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    break;
                case PathIterator.SEG_CLOSE:
                    pb.closePath();
                    break;
                default:
                    break;
            }
        }
        return pb.detach();
    }

    private float scale() {
        double det = Math.abs(mCTM.getDeterminant());
        return det > 1e-9 ? (float) Math.sqrt(det) : 1f;
    }

    private io.github.humbleui.skija.Paint fillPaint() {
        io.github.humbleui.skija.Paint p = new io.github.humbleui.skija.Paint();
        p.setAntiAlias(true);
        p.setMode(PaintMode.FILL);
        p.setColor(mColor.getRGB());
        return p;
    }

    private io.github.humbleui.skija.Paint strokePaint() {
        io.github.humbleui.skija.Paint p = new io.github.humbleui.skija.Paint();
        p.setAntiAlias(true);
        p.setMode(PaintMode.STROKE);
        p.setColor(mColor.getRGB());
        p.setStrokeWidth(Math.max(0.5f, mStroke.getLineWidth() * scale()));
        return p;
    }

    private io.github.humbleui.skija.Paint textPaint() {
        io.github.humbleui.skija.Paint p = new io.github.humbleui.skija.Paint();
        p.setAntiAlias(true);
        p.setColor(mColor.getRGB());
        return p;
    }

    // ------------------------------------------------------------------
    // Drawing (Graphics)
    // ------------------------------------------------------------------

    @Override
    public void draw(Shape s) {
        if (s == null) return;
        Path p = shapeToPath(s);
        try (io.github.humbleui.skija.Paint paint = strokePaint()) {
            mCanvas.drawPath(p, paint);
        }
    }

    @Override
    public void fill(Shape s) {
        if (s == null) return;
        Path p = shapeToPath(s);
        try (io.github.humbleui.skija.Paint paint = fillPaint()) {
            mCanvas.drawPath(p, paint);
        }
    }

    private void drawTransformed(Path p) {
        try (io.github.humbleui.skija.Paint paint = strokePaint()) {
            mCanvas.drawPath(p, paint);
        }
    }

    private void fillTransformed(Path p) {
        try (io.github.humbleui.skija.Paint paint = fillPaint()) {
            mCanvas.drawPath(p, paint);
        }
    }

    @Override
    public void drawString(String str, int x, int y) {
        drawString(str, (float) x, (float) y);
    }

    @Override
    public void drawString(String str, float x, float y) {
        if (str == null || str.isEmpty()) return;
        io.github.humbleui.skija.Font skFont = skijaFont();
        if (skFont == null) return;
        Point2D pt = mCTM.transform(new Point2D.Float(x, y), null);
        try (io.github.humbleui.skija.Paint paint = textPaint()) {
            mCanvas.drawString(str, (float) pt.getX(), (float) pt.getY(), skFont, paint);
        }
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        drawString(stringOf(iterator), x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        drawString(stringOf(iterator), x, y);
    }

    private static String stringOf(AttributedCharacterIterator it) {
        StringBuilder sb = new StringBuilder();
        for (char c = it.first(); c != AttributedCharacterIterator.DONE; c = it.next()) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        // Rarely used by the Android view pipeline; degrade to no-op.
    }

    @Override
    public void fillRect(int x, int y, int w, int h) {
        fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void drawRect(int x, int y, int w, int h) {
        draw(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void fillRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        fill(new RoundRectangle2D.Float(x, y, w, h, arcWidth, arcHeight));
    }

    @Override
    public void drawRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        draw(new RoundRectangle2D.Float(x, y, w, h, arcWidth, arcHeight));
    }

    @Override
    public void fillOval(int x, int y, int w, int h) {
        fill(new Ellipse2D.Float(x, y, w, h));
    }

    @Override
    public void drawOval(int x, int y, int w, int h) {
        draw(new Ellipse2D.Float(x, y, w, h));
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void fill3DRect(int x, int y, int w, int h, boolean raised) {
        fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void draw3DRect(int x, int y, int w, int h, boolean raised) {
        draw(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        fill(new Arc2D.Float(x, y, w, h, startAngle, arcAngle, Arc2D.PIE));
    }

    @Override
    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        draw(new Arc2D.Float(x, y, w, h, startAngle, arcAngle, Arc2D.OPEN));
    }

    @Override
    public void clearRect(int x, int y, int w, int h) {
        Color old = mColor;
        mColor = mBackground;
        fill(new Rectangle2D.Float(x, y, w, h));
        mColor = old;
    }

    @Override
    public void drawBytes(byte[] data, int offset, int length, int x, int y) {
        drawString(new String(data, offset, length), x, y);
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
        drawString(new String(data, offset, length), x, y);
    }

    @Override
    public void copyArea(int x, int y, int w, int h, int dx, int dy) {
        // Not used by the Android view pipeline.
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        PathBuilder pb = new PathBuilder();
        for (int i = 0; i < nPoints; i++) {
            if (i == 0) pb.moveTo(xPoints[i], yPoints[i]);
            else pb.lineTo(xPoints[i], yPoints[i]);
        }
        drawTransformed(pb.detach());
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        PathBuilder pb = new PathBuilder();
        for (int i = 0; i < nPoints; i++) {
            if (i == 0) pb.moveTo(xPoints[i], yPoints[i]);
            else pb.lineTo(xPoints[i], yPoints[i]);
        }
        pb.closePath();
        fillTransformed(pb.detach());
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        drawPolygon(xPoints, yPoints, nPoints);
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------

    @Override
    public boolean drawImage(Image img, int x, int y, java.awt.image.ImageObserver observer) {
        return drawScaled(img, x, y, -1, -1);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, java.awt.image.ImageObserver observer) {
        return drawScaled(img, x, y, w, h);
    }

    private boolean drawScaled(Image img, float x, float y, int w, int h) {
        if (!(img instanceof BufferedImage)) {
            return false;
        }
        BufferedImage src = (BufferedImage) img;
        int iw = src.getWidth();
        int ih = src.getHeight();
        if (iw <= 0 || ih <= 0) return false;
        int dw = w > 0 ? w : iw;
        int dh = h > 0 ? h : ih;
        try {
            int[] pixels = new int[iw * ih];
            src.getRGB(0, 0, iw, ih, pixels, 0, iw);
            byte[] bgra = new byte[iw * ih * 4];
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int o = i * 4;
                bgra[o] = (byte) (argb & 0xFF);
                bgra[o + 1] = (byte) ((argb >> 8) & 0xFF);
                bgra[o + 2] = (byte) ((argb >> 16) & 0xFF);
                bgra[o + 3] = (byte) ((argb >> 24) & 0xFF);
            }
            io.github.humbleui.skija.Bitmap bmp = new io.github.humbleui.skija.Bitmap();
            bmp.allocN32Pixels(iw, ih);
            bmp.installPixels(bgra);
            io.github.humbleui.skija.Image image = io.github.humbleui.skija.Image.makeFromBitmap(bmp);
            Rect dst = Rect.makeLTRB(x, y, x + dw, y + dh);
            try (io.github.humbleui.skija.Paint paint = new io.github.humbleui.skija.Paint()) {
                paint.setAntiAlias(true);
                mCanvas.drawImageRect(image, dst, paint);
            }
            bmp.close();
            return true;
        } catch (Throwable t) {
            System.err.println("[-] [Skia] drawImage failed: " + t);
            return false;
        }
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, java.awt.image.ImageObserver observer) {
        return drawImage(img, x, y, observer);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, Color bgcolor, java.awt.image.ImageObserver observer) {
        return drawImage(img, x, y, w, h, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, java.awt.image.ImageObserver observer) {
        return drawImage(img, dx1, dy1, dx2 - dx1, dy2 - dy1, observer);
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                             int sx1, int sy1, int sx2, int sy2, Color bgcolor, java.awt.image.ImageObserver observer) {
        return drawImage(img, dx1, dy1, dx2 - dx1, dy2 - dy1, observer);
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {}

    @Override
    public void drawImage(java.awt.image.BufferedImage img, java.awt.image.BufferedImageOp op, int x, int y) {
        if (img == null) return;
        drawImage(img, x, y, null);
    }

    @Override
    public boolean drawImage(java.awt.Image img, AffineTransform xform, java.awt.image.ImageObserver obs) {
        if (img == null) return false;
        if (xform == null || xform.isIdentity()) {
            return drawImage(img, 0, 0, obs);
        } else {
            java.awt.geom.Point2D p = xform.transform(new java.awt.geom.Point2D.Float(0, 0), null);
            return drawScaled(img, (float) p.getX(), (float) p.getY(), -1, -1);
        }
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {}

    // ------------------------------------------------------------------
    // Font metrics
    // ------------------------------------------------------------------

    @Override
    public FontMetrics getFontMetrics(Font f) {
        final io.github.humbleui.skija.Font skFont = skijaFontFor(f);
        final java.awt.Font awtFont = f;
        if (skFont == null) {
            return new java.awt.FontMetrics(f) {
                public int getAscent() { return f.getSize(); }
                public int getDescent() { return f.getSize() / 4; }
                public int stringWidth(String str) { return str.length() * f.getSize() / 2; }
            };
        }
        final io.github.humbleui.skija.FontMetrics fm = skFont.getMetrics();
        return new java.awt.FontMetrics(f) {
            @Override
            public int getAscent() { return Math.round(-fm._ascent); }
            @Override
            public int getDescent() { return Math.round(fm._descent); }
            @Override
            public int getLeading() { return Math.round(fm._leading); }
            @Override
            public int getHeight() { return Math.round(-fm._ascent + fm._descent + fm._leading); }
            @Override
            public int getMaxAscent() { return Math.round(-fm._ascent); }
            @Override
            public int getMaxDescent() { return Math.round(fm._descent); }
            @Override
            public int stringWidth(String str) {
                if (str == null || str.isEmpty()) return 0;
                return Math.round(skFont.measureTextWidth(str));
            }
            @Override
            public int charsWidth(char[] ch, int off, int len) {
                return stringWidth(new String(ch, off, len));
            }
            @Override
            public java.awt.Font getFont() { return awtFont; }
        };
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    @Override
    public void setColor(Color c) {
        if (c != null) mColor = c;
    }

    @Override
    public Color getColor() {
        return mColor;
    }

    @Override
    public void setBackground(Color c) {
        if (c != null) mBackground = c;
    }

    @Override
    public Color getBackground() {
        return mBackground;
    }

    @Override
    public void setFont(Font f) {
        if (f != null) mAwtFont = f;
    }

    @Override
    public Font getFont() {
        return mAwtFont;
    }

    @Override
    public FontMetrics getFontMetrics() {
        return getFontMetrics(mAwtFont);
    }

    @Override
    public void setPaintMode() {}

    @Override
    public void setXORMode(Color c) {}

    @Override
    public Stroke getStroke() {
        return mStroke;
    }

    @Override
    public void setStroke(Stroke s) {
        if (s instanceof BasicStroke) {
            mStroke = (BasicStroke) s;
        }
    }

    @Override
    public void setRenderingHint(java.awt.RenderingHints.Key hintKey, Object hintValue) {
        // Skia always renders anti-aliased; quality hints are no-ops.
    }

    @Override
    public Object getRenderingHint(java.awt.RenderingHints.Key hintKey) {
        return null;
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {}

    @Override
    public void addRenderingHints(Map<?, ?> hints) {}

    @Override
    public RenderingHints getRenderingHints() {
        return new RenderingHints(null);
    }

    // ------------------------------------------------------------------
    // Transform
    // ------------------------------------------------------------------

    @Override
    public void translate(int tx, int ty) {
        mCTM.translate(tx, ty);
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        if (rect == null || s == null) return false;
        Shape device = mCTM.createTransformedShape(s);
        if (onStroke) {
            try {
                device = mStroke.createStrokedShape(device);
            } catch (Throwable ignored) {}
        }
        return device.intersects(rect);
    }

    @Override
    public void translate(double tx, double ty) {
        mCTM.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        mCTM.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        mCTM.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        mCTM.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        mCTM.shear(shx, shy);
    }

    @Override
    public void transform(AffineTransform Tx) {
        mCTM.concatenate(Tx);
    }

    @Override
    public void setTransform(AffineTransform Tx) {
        mCTM = Tx != null ? new AffineTransform(Tx) : new AffineTransform();
    }

    @Override
    public AffineTransform getTransform() {
        return (AffineTransform) mCTM.clone();
    }

    // ------------------------------------------------------------------
    // Clip
    // ------------------------------------------------------------------

    @Override
    public void clip(Shape s) {
        if (s == null) return;
        Path p = shapeToPath(s);
        mCanvas.clipPath(p, ClipMode.INTERSECT, true);
        Rectangle2D device = mCTM.createTransformedShape(s.getBounds2D()).getBounds2D();
        mClip = mClip.createIntersection(device);
    }

    @Override
    public void clipRect(int x, int y, int w, int h) {
        clip(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void setClip(Shape clip) {
        if (clip == null) {
            mClip = new Rectangle2D.Float(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        } else {
            mClip = mCTM.createTransformedShape(clip.getBounds2D()).getBounds2D();
        }
    }

    @Override
    public void setClip(int x, int y, int w, int h) {
        setClip(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public Shape getClip() {
        return mClip;
    }

    @Override
    public Rectangle getClipBounds() {
        Rectangle r = mClip.getBounds();
        if (r.width > mW) r.width = mW;
        if (r.height > mH) r.height = mH;
        return r;
    }

    // ------------------------------------------------------------------
    // Paint / Composite (not needed by the Android view pipeline)
    // ------------------------------------------------------------------

    @Override
    public Paint getPaint() {
        return null;
    }

    @Override
    public void setPaint(Paint paint) {}

    @Override
    public Composite getComposite() {
        return null;
    }

    @Override
    public void setComposite(Composite comp) {}

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
    }

    @Override
    public java.awt.font.FontRenderContext getFontRenderContext() {
        return new java.awt.font.FontRenderContext(mCTM, true, true);
    }

    @Override
    public Graphics create() {
        SkiaGraphics2D g = new SkiaGraphics2D(mCanvas, mW, mH);
        g.mColor = mColor;
        g.mBackground = mBackground;
        g.mAwtFont = mAwtFont;
        g.mStroke = mStroke;
        g.mCTM = new AffineTransform(mCTM);
        g.mClip = mClip;
        return g;
    }

    @Override
    public void dispose() {
        // Skija objects are reference-counted and freed on GC.
    }
}
