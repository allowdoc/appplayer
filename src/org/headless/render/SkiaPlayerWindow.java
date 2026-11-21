package org.headless.render;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import org.headless.runner.core.ActivityStack;
import org.headless.runner.ui.HeadlessScreenCapture;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * SKIA-RENDERED interactive Android player window (Zero VM / Zero QEMU).
 *
 * Every frame:
 *   1. a real Skia raster surface is created (GPU/Skia engine, same library
 *      Android devices use for UI composition),
 *   2. the genuine Android View hierarchy paints into it through the
 *      android.graphics.Canvas shim routed to SkiaGraphics2D,
 *   3. the Skia frame is blitted into an AWT window,
 *   4. host mouse events are mapped back to Android MotionEvents and routed
 *      through the real view hierarchy.
 *
 * Falls back to HeadlessViewerWindow (Java2D) when Skija jars are absent.
 */
public class SkiaPlayerWindow {
    public static final int VIEW_WIDTH = 1080;
    public static final int VIEW_HEIGHT = 1920;
    private static final int FRAME_MS = 33; // ~30 fps

    public static boolean isAvailable() {
        try {
            Class.forName("io.github.humbleui.skija.Surface");
            Class.forName("io.github.humbleui.types.Rect");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void show(Activity activity) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[*] [SkiaPlayer] Headless environment (DISPLAY=" + System.getenv("DISPLAY")
                    + ", java.awt.headless=" + System.getProperty("java.awt.headless")
                    + ") - skipping window.");
            return;
        }
        if (!isAvailable()) {
            System.out.println("[*] [SkiaPlayer] Skija jars not on classpath - falling back to Java2D player.");
            org.headless.runner.ui.HeadlessViewerWindow.show(activity);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // Force the Skia native library to load once, up front, with a clear log line.
                io.github.humbleui.skija.Surface s = io.github.humbleui.skija.Surface.makeRasterN32Premul(4, 4);
                if (s != null) s.close();
                System.out.println("[+] [SkiaPlayer] GOOGLE SKIA native engine loaded (Skija " + version() + ").");
            } catch (Throwable t) {
                System.err.println("[-] [SkiaPlayer] Skia native load failed, falling back to Java2D: " + t);
                org.headless.runner.ui.HeadlessViewerWindow.show(activity);
                return;
            }

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {}

            JFrame frame = new JFrame("Skia Android Player (Zero VM)");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            // Scale the 1080x1920 device frame into a desktop window.
            int winW = 400;
            int winH = (int) Math.round((double) VIEW_HEIGHT / VIEW_WIDTH * winW);
            frame.setSize(winW + 16, winH + 39);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);

            JComponent panel = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Activity top = ActivityStack.getInstance().getTopActivity();
                    if (top == null) top = activity;
                    BufferedImage frameImg = renderFrame(top);
                    if (frameImg != null) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(frameImg, 0, 0, getWidth(), getHeight(), null);
                    } else {
                        g.setColor(new Color(0x18, 0x18, 0x1C));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                    String actName = top != null ? top.getClass().getSimpleName() : "None";
                    frame.setTitle("Skia Android Player - " + actName + " [" + usedMB + " MB RAM]");
                }
            };
            panel.setPreferredSize(new Dimension(winW, winH));

            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    Activity top = ActivityStack.getInstance().getTopActivity();
                    if (top == null) top = activity;
                    if (top == null) return;

                    float scaleX = (float) VIEW_WIDTH / panel.getWidth();
                    float scaleY = (float) VIEW_HEIGHT / panel.getHeight();
                    final int ax = Math.round(e.getX() * scaleX);
                    final int ay = Math.round(e.getY() * scaleY);

                    System.out.println("[*] [SkiaPlayer] Click at (" + e.getX() + ", " + e.getY()
                            + ") -> Android (" + ax + ", " + ay + ")");

                    Activity activeTop = top;
                    org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                    new android.os.Handler(Looper.getMainLooper()).post(() -> {
                        org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                        dispatchTouch(activeTop, MotionEvent.ACTION_DOWN, ax, ay);
                        dispatchTouch(activeTop, MotionEvent.ACTION_UP, ax, ay);

                        // Spatial hit target -> tree walk fallback (same contract as Java2D player)
                        View target = null;
                        List<HeadlessScreenCapture.SpatialHitTarget> hits = HeadlessScreenCapture.getSpatialHitTargets();
                        for (int i = hits.size() - 1; i >= 0; i--) {
                            HeadlessScreenCapture.SpatialHitTarget hit = hits.get(i);
                            if (hit.isClickable && hit.contains(ax, ay)) {
                                target = hit.view;
                                break;
                            }
                        }
                        if (target == null) {
                            for (HeadlessScreenCapture.InteractiveNode node : HeadlessScreenCapture.getInteractiveNodes()) {
                                if (node.isClickable && ax >= node.screenX && ax <= (node.screenX + node.screenW)
                                        && ay >= node.screenY && ay <= (node.screenY + node.screenH)) {
                                    target = node.view;
                                    break;
                                }
                            }
                        }
                        if (target != null) {
                            System.out.println("[+] [SkiaPlayer] Click dispatched to: "
                                    + target.getClass().getSimpleName() + " (id=" + target.getId() + ")");
                            target.performClick();
                        } else {
                            System.out.println("[*] [SkiaPlayer] No clickable view found at (" + ax + ", " + ay + ")");
                        }

                        try {
                            int drained = Looper.getMainLooper().getQueue().drainPendingMessages();
                            if (drained > 0) {
                                System.out.println("[+] [SkiaPlayer] Processed " + drained + " click-triggered Looper task(s).");
                            }
                        } catch (Throwable ignored) {}

                        panel.repaint();
                    });
                }
            });

            frame.add(panel);
            frame.pack();
            frame.setVisible(true);

            Timer refreshTimer = new Timer(FRAME_MS, ev -> panel.repaint());
            refreshTimer.start();

            System.out.println("[+] [SkiaPlayer] SKIA-RENDERED interactive window live ("
                    + VIEW_WIDTH + "x" + VIEW_HEIGHT + " @ ~30fps). You can now interact with the app!");
        });
    }

    private static String version() {
        return "0.143.18";
    }

    private static void dispatchTouch(Activity activity, int action, float x, float y) {
        if (activity == null || activity.getWindow() == null) return;
        try {
            long now = SystemClock.uptimeMillis();
            MotionEvent ev = MotionEvent.obtain(now, now, action, x, y, 0);
            try {
                activity.getWindow().getDecorView().dispatchTouchEvent(ev);
            } finally {
                ev.recycle();
            }
        } catch (Throwable ignored) {
            // MotionEvent JNI not present on pure JVM; click dispatch covers interaction
        }
    }

    /**
     * Render one full device frame (1080x1920) of the app through SKIA.
     * The Android view tree paints via android.graphics.Canvas -> SkiaGraphics2D.
     */
    public static BufferedImage renderFrame(Activity activity) {
        try {
            io.github.humbleui.skija.Surface surface =
                    io.github.humbleui.skija.Surface.makeRasterN32Premul(VIEW_WIDTH, VIEW_HEIGHT);
            io.github.humbleui.skija.Canvas skCanvas = surface.getCanvas();

            SkiaGraphics2D g2 = new SkiaGraphics2D(skCanvas, VIEW_WIDTH, VIEW_HEIGHT);
            HeadlessScreenCapture.renderToGraphics(activity, g2);

            io.github.humbleui.skija.Image image = surface.makeImageSnapshot();
            ByteBuffer pixels = image.peekPixels();
            if (pixels == null) {
                System.err.println("[-] [SkiaPlayer] Skia surface produced no pixels.");
                return null;
            }
            pixels.order(ByteOrder.LITTLE_ENDIAN);

            // Copy pixels OUT of the Skia-managed memory BEFORE closing the
            // surface: peekPixels returns a view into native memory that dies
            // with the surface - reading after close() is use-after-free
            // (SIGSEGV in DirectByteBuffer.get).
            byte[] raw = new byte[VIEW_WIDTH * VIEW_HEIGHT * 4];
            pixels.get(raw);

            BufferedImage out = new BufferedImage(VIEW_WIDTH, VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            int n = VIEW_WIDTH * VIEW_HEIGHT;
            for (int i = 0; i < n; i++) {
                int o = i * 4;
                int b = raw[o] & 0xFF;
                int g = raw[o + 1] & 0xFF;
                int r = raw[o + 2] & 0xFF;
                int a = raw[o + 3] & 0xFF;
                if (a > 0 && a < 255) {
                    // Un-premultiply (N32 premul surface -> straight alpha)
                    r = Math.min(255, Math.round(r * 255f / a));
                    g = Math.min(255, Math.round(g * 255f / a));
                    b = Math.min(255, Math.round(b * 255f / a));
                }
                dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            surface.close();
            return out;
        } catch (Throwable t) {
            System.err.println("[-] [SkiaPlayer] Frame render failed: " + t);
            return null;
        }
    }

    /** Capture one Skia-rendered frame to a PNG (for headless smoke tests). */
    public static File captureFrame(Activity activity, File output) {
        BufferedImage img = renderFrame(activity);
        if (img == null) return null;
        try {
            if (output.getParentFile() != null) output.getParentFile().mkdirs();
            ImageIO.write(img, "PNG", output);
            System.out.println("[+] [SkiaPlayer] SKIA frame captured to: " + output.getAbsolutePath());
            return output;
        } catch (Throwable t) {
            System.err.println("[-] [SkiaPlayer] PNG write failed: " + t);
            return null;
        }
    }
}
