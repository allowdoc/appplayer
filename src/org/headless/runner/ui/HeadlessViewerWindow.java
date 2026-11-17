package org.headless.runner.ui;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import org.headless.runner.core.ActivityStack;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;

public class HeadlessViewerWindow {
    private static final int VIEW_WIDTH = 380;
    private static final int VIEW_HEIGHT = 675;

    public static void show(Activity activity) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[*] [UI Player] Headless environment detected (no display server). Skipping desktop window.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {}

            JFrame frame = new JFrame("Native Android Player (Zero VM)");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(VIEW_WIDTH + 16, VIEW_HEIGHT + 39);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    Activity top = ActivityStack.getInstance().getTopActivity();
                    if (top == null) top = activity;

                    BufferedImage snapshot = HeadlessScreenCapture.renderToImage(top);
                    if (snapshot != null) {
                        g2d.drawImage(snapshot, 0, 0, getWidth(), getHeight(), null);
                    } else {
                        g2d.setColor(new Color(0x18, 0x18, 0x1C));
                        g2d.fillRect(0, 0, getWidth(), getHeight());
                    }

                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                    String actName = top.getClass().getSimpleName();
                    frame.setTitle("Native Android Player - " + actName + " [" + usedMB + " MB RAM]");
                }
            };

            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    Activity top = ActivityStack.getInstance().getTopActivity();
                    if (top == null) top = activity;

                    float scaleX = 1080.0f / panel.getWidth();
                    float scaleY = 1920.0f / panel.getHeight();
                    int ax = (int) (e.getX() * scaleX);
                    int ay = (int) (e.getY() * scaleY);

                    System.out.println("[*] [Live Player] Mouse click at (" + e.getX() + ", " + e.getY() + ") -> Android (" + ax + ", " + ay + ")");

                    org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                    android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
                    final Activity activeTop = top;
                    mainHandler.post(() -> {
                        org.headless.runner.core.ActivityStack.ensureMainLooperOnThread();
                        // 1. Dispatch MotionEvent (Safe JNI guard)
                        dispatchTouch(activeTop, MotionEvent.ACTION_DOWN, ax, ay);
                        dispatchTouch(activeTop, MotionEvent.ACTION_UP, ax, ay);

                        // 2. Universal Real View Click Dispatch (Spatial Hit Target -> Tree Fallback)
                        View target = null;
                        java.util.List<HeadlessScreenCapture.SpatialHitTarget> hits = HeadlessScreenCapture.getSpatialHitTargets();
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
                        if (target == null) {
                            target = findClickableViewAt(activeTop.getWindow().getDecorView(), ax, ay);
                        }
                        if (target != null) {
                            System.out.println("[+] [Live Player] Click dispatched to: " + target.getClass().getSimpleName() + " (id=" + target.getId() + ")");
                            target.performClick();
                        } else {
                            System.out.println("[*] [Live Player] No clickable view found at (" + ax + ", " + ay + ")");
                        }

                        // 3. Immediately drain and execute any pending Looper tasks triggered by click
                        try {
                            int drained = Looper.getMainLooper().getQueue().drainPendingMessages();
                            if (drained > 0) {
                                System.out.println("[+] [Live Player] Processed " + drained + " click-triggered Looper task(s).");
                            }
                        } catch (Throwable ignored) {}

                        panel.repaint();
                    });
                }
            });

            frame.add(panel);
            frame.setVisible(true);

            // 60ms Periodic Auto-Refresh Timer (~16 FPS)
            Timer refreshTimer = new Timer(60, ev -> panel.repaint());
            refreshTimer.start();

            System.out.println("[+] [Live Player] Interactive window live at " + VIEW_WIDTH + "x" + VIEW_HEIGHT);
            System.out.println("[+] [Live Player] You can now interact with the app in real-time!");
        });
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
            // MotionEvent JNI not present on pure JVM; handled via direct view invocation
        }
    }

    private static View findClickableViewAt(View root, int x, int y) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;

        int l = root.getLeft();
        int t = root.getTop();
        int r = root.getRight();
        int b = root.getBottom();

        if (x < l || x > r || y < t || y > b) {
            if (root.getParent() != null && (r > l && b > t)) {
                return null;
            }
        }

        int relX = x - l;
        int relY = y - t;

        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                View child = vg.getChildAt(i);
                View clicked = findClickableViewAt(child, relX, relY);
                if (clicked != null) return clicked;
            }
        }

        if (root.isClickable()) {
            return root;
        }

        return null;
    }
}
