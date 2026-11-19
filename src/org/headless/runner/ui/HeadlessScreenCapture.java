package org.headless.runner.ui;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal In-Process Android 2D Spatial View Renderer.
 * Zero VM, Zero QEMU, Zero background daemons, Zero app-specific mock facades.
 * Renders genuine Android View hierarchies directly in standard OpenJDK.
 */
public class HeadlessScreenCapture {
    public static final int DEFAULT_WIDTH = 1080;
    public static final int DEFAULT_HEIGHT = 1920;

    public static class SpatialHitTarget {
        public final View view;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final boolean isClickable;

        public SpatialHitTarget(View view, int x, int y, int w, int h, boolean isClickable) {
            this.view = view;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.isClickable = isClickable;
        }

        public boolean contains(int px, int py) {
            return px >= x && px <= (x + w) && py >= y && py <= (y + h);
        }
    }

    public static class InteractiveNode {
        public final View view;
        public final String className;
        public final String resId;
        public final String text;
        public final boolean isClickable;
        public final int depth;
        public int screenX;
        public int screenY;
        public int screenW;
        public int screenH;

        public InteractiveNode(View view, String className, String resId, String text, boolean isClickable, int depth) {
            this.view = view;
            this.className = className;
            this.resId = resId;
            this.text = text;
            this.isClickable = isClickable;
            this.depth = depth;
        }
    }

    private static final List<SpatialHitTarget> sHitTargets = new ArrayList<>();
    private static final List<InteractiveNode> sCurrentNodes = new ArrayList<>();

    public static synchronized List<SpatialHitTarget> getSpatialHitTargets() {
        return new ArrayList<>(sHitTargets);
    }

    public static synchronized List<InteractiveNode> getInteractiveNodes() {
        return new ArrayList<>(sCurrentNodes);
    }

    public static BufferedImage renderToImage(Activity activity) {
        if (activity == null) {
            return null;
        }

        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor != null) {
            // Trigger universal window attach and measure & layout pass on genuine Android View hierarchy
            try {
                org.headless.runner.core.ActivityStack.attachWindowIfNeeded(activity);
                int wSpec = View.MeasureSpec.makeMeasureSpec(DEFAULT_WIDTH, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(DEFAULT_HEIGHT, View.MeasureSpec.EXACTLY);
                decor.measure(wSpec, hSpec);
                decor.layout(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            } catch (Throwable ignored) {}
        }

        BufferedImage image = new BufferedImage(DEFAULT_WIDTH, DEFAULT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 1. Base Canvas (Clean Surface)
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 2. System Status Bar (Top 0 to 64px)
        g2.setColor(new Color(0xF8, 0xFA, 0xFC));
        g2.fillRect(0, 0, DEFAULT_WIDTH, 64);
        g2.setColor(new Color(0xE2, 0xE8, 0xF0));
        g2.drawLine(0, 64, DEFAULT_WIDTH, 64);

        g2.setColor(new Color(0x0F, 0x17, 0x2A));
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2.drawString("12:00", 48, 44);

        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.drawString("5G   LTE   100%", DEFAULT_WIDTH - 240, 44);

        // 3. Recursive 2D Spatial Layout Walk on Genuine Inflated Android Views
        List<SpatialHitTarget> hitTargets = new ArrayList<>();
        List<InteractiveNode> nodes = new ArrayList<>();

        if (decor != null) {
            try {
                android.graphics.Canvas androidCanvas = new android.graphics.Canvas(g2, DEFAULT_WIDTH, DEFAULT_HEIGHT);
                decor.draw(androidCanvas);
            } catch (Throwable t) {
                System.err.println("[-] [HeadlessScreenCapture] decor.draw() error: " + t.getClass().getName() + ": " + t.getMessage());
            }
            renderViewSpatial(decor, 0, 0, g2, hitTargets, nodes, 0, activity);
        }

        synchronized (HeadlessScreenCapture.class) {
            sHitTargets.clear();
            sHitTargets.addAll(hitTargets);
            sCurrentNodes.clear();
            sCurrentNodes.addAll(nodes);
        }

        // 4. System Navigation Bar (Subtle floating gesture pill)
        g2.setColor(new Color(0x64, 0x74, 0x8B, 180));
        g2.fill(new RoundRectangle2D.Float(DEFAULT_WIDTH / 2f - 90, DEFAULT_HEIGHT - 24, 180, 6, 3, 3));

        g2.dispose();
        return image;
    }

    private static void renderViewSpatial(View view, int parentX, int parentY, Graphics2D g2,
                                         List<SpatialHitTarget> hitTargets, List<InteractiveNode> nodes,
                                         int depth, Activity activity) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        int x = parentX + view.getLeft();
        int y = parentY + view.getTop();
        int w = view.getWidth();
        int h = view.getHeight();

        if (w <= 0 || h <= 0) return;

        boolean isClickable = view.isClickable();
        String className = view.getClass().getSimpleName();
        if (className.isEmpty()) className = view.getClass().getName();
        String resId = getResourceEntry(view, activity);

        // Record genuine spatial hit target for click events
        if (isClickable) {
            hitTargets.add(new SpatialHitTarget(view, x, y, w, h, true));
        }

        // A. Background Rendering
        Drawable bg = view.getBackground();
        if (bg instanceof ColorDrawable) {
            int color = ((ColorDrawable) bg).getColor();
            if ((color & 0xFF000000) != 0) {
                g2.setColor(new Color(color, true));
                g2.fillRect(x, y, w, h);
            }
        } else if (bg != null) {
            try {
                android.graphics.Canvas bgCanvas = new android.graphics.Canvas(g2, DEFAULT_WIDTH, DEFAULT_HEIGHT);
                bg.setBounds(x, y, x + w, y + h);
                bg.draw(bgCanvas);
            } catch (Throwable ignored) {}
        } else if (isClickable && w > 150 && h > 60 && !(view instanceof ViewGroup)) {
            // Clickable surface styling
            g2.setColor(new Color(0xF8, 0xFA, 0xFC));
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.setColor(new Color(0xCB, 0xD5, 0xE1));
            g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
        }

        // B. TextView / Label Rendering (Genuine Android text properties)
        String text = null;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            boolean isHint = false;
            if (cs == null || cs.length() == 0) {
                cs = tv.getHint();
                isHint = true;
            }
            if (cs != null && cs.length() > 0) {
                text = cs.toString().trim();
                while (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                    text = text.substring(1, text.length() - 1).trim();
                }

                // Color Selection from genuine TextView
                if (isHint) {
                    g2.setColor(new Color(0x94, 0xA3, 0xB8));
                } else {
                    int curColor = tv.getCurrentTextColor();
                    if (curColor != 0 && (curColor & 0xFF000000) != 0) {
                        g2.setColor(new Color(curColor, true));
                    } else {
                        g2.setColor(new Color(0x0F, 0x17, 0x2A)); // Dark Slate fallback
                    }
                }

                // Typography & Font Sizing
                float px = tv.getTextSize();
                int fontSize = Math.max(16, Math.min(52, Math.round(px > 0 ? px : 32)));
                int style = Font.PLAIN;
                if (tv.getTypeface() != null && tv.getTypeface().isBold()) {
                    style = Font.BOLD;
                }
                Font font = new Font("SansSerif", style, fontSize);
                g2.setFont(font);

                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(text);
                int textX = x + tv.getCompoundPaddingLeft();

                int gravity = tv.getGravity();
                if ((gravity & 0x01) != 0 || gravity == 17) {
                    textX = x + (w - textW) / 2;
                } else if ((gravity & 0x05) != 0) {
                    textX = x + w - textW - tv.getCompoundPaddingRight();
                }

                int textY = y + (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, Math.max(x, textX), textY);
            }
        }

        // C. ImageView Rendering
        if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            Drawable d = iv.getDrawable();
            if (d != null) {
                try {
                    android.graphics.Canvas ivCanvas = new android.graphics.Canvas(g2, DEFAULT_WIDTH, DEFAULT_HEIGHT);
                    d.setBounds(x, y, x + w, y + h);
                    d.draw(ivCanvas);
                } catch (Throwable ignored) {}
            } else if (view.getBackground() == null) {
                g2.setColor(new Color(0xF1, 0xF5, 0xF9));
                g2.fill(new RoundRectangle2D.Float(x, y, w, h, 8, 8));
            }
        }

        nodes.add(new InteractiveNode(view, className, resId, text, isClickable, depth));

        // D. Recursive Walk on Children
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int childCount = vg.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = vg.getChildAt(i);
                renderViewSpatial(child, x, y, g2, hitTargets, nodes, depth + 1, activity);
            }
        }
    }

    private static String getResourceEntry(View view, Activity activity) {
        if (view == null || activity == null) return null;
        int id = view.getId();
        if (id == View.NO_ID) return null;
        try {
            return "@id/" + activity.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return "0x" + Integer.toHexString(id);
        }
    }

    public static File capture(Activity activity, File outputFile) {
        BufferedImage image = renderToImage(activity);
        if (image == null) {
            System.err.println("[-] Screen capture failed: No active window or activity");
            return null;
        }

        try {
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("[+] [Headless Screen] Frame captured to: " + outputFile.getAbsolutePath());

            try {
                String jsonName = outputFile.getName().contains(".")
                        ? outputFile.getName().substring(0, outputFile.getName().lastIndexOf('.')) + ".json"
                        : outputFile.getName() + ".json";
                File jsonFile = new File(outputFile.getParentFile(), jsonName);
                HeadlessUiTree.saveHierarchyJson(activity, jsonFile);
            } catch (Throwable ignored) {}

            return outputFile;
        } catch (Exception e) {
            System.err.println("[-] Failed to write screen capture: " + e.getMessage());
            return null;
        }
    }
}
