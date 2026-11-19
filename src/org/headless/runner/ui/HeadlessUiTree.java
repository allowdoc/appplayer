package org.headless.runner.ui;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HeadlessUiTree {

    public static String dump(Activity activity) {
        System.out.println("[*] [HeadlessUiTree] dump() called, activity=" + activity);
        if (activity == null || activity.getWindow() == null) {
            System.out.println("[-] [HeadlessUiTree] activity or window is null!");
            return "<No active window/activity>";
        }
        System.out.println("[*] [HeadlessUiTree] Fetching decorView...");
        View decor = activity.getWindow().getDecorView();
        System.out.println("[*] [HeadlessUiTree] decorView = " + decor);
        if (decor != null) {
            try {
                org.headless.runner.core.ActivityStack.attachWindowIfNeeded(activity);
                int w = decor.getWidth() > 0 ? decor.getWidth() : 1080;
                int h = decor.getHeight() > 0 ? decor.getHeight() : 1920;
                int wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
                decor.measure(wSpec, hSpec);
                decor.layout(0, 0, w, h);
            } catch (Throwable ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n==================================================================\n");
        sb.append("                 HEADLESS UI HIERARCHY TREE                      \n");
        sb.append("  Activity: ").append(activity.getClass().getName()).append("\n");
        sb.append("==================================================================\n");
        System.out.println("[*] [HeadlessUiTree] Starting dumpView recursion...");
        dumpView(decor, 0, sb, activity);
        System.out.println("[*] [HeadlessUiTree] dumpView recursion finished!");
        sb.append("==================================================================\n");
        String result = sb.toString();
        System.out.println(result);
        return result;
    }

    private static void dumpView(View v, int depth, StringBuilder sb, Activity activity) {
        if (v == null) return;
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }

        String className = v.getClass().getSimpleName();
        if (className.isEmpty()) className = v.getClass().getName();

        sb.append("[").append(className).append("]");

        // View ID
        int id = v.getId();
        if (id != View.NO_ID) {
            String entryName = null;
            try {
                entryName = activity.getResources().getResourceEntryName(id);
            } catch (Throwable ignored) {}
            if (entryName != null) {
                sb.append(" id=@id/").append(entryName);
            } else {
                sb.append(" id=0x").append(Integer.toHexString(id));
            }
        }

        // Text
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.length() > 0) {
                sb.append(" text=\"").append(text.toString().replace("\n", "\\n")).append("\"");
            }
        } else {
            try {
                Method getText = v.getClass().getMethod("getText");
                Object val = getText.invoke(v);
                if (val instanceof CharSequence && ((CharSequence) val).length() > 0) {
                    sb.append(" text=\"").append(val.toString().replace("\n", "\\n")).append("\"");
                }
            } catch (Throwable ignored) {}
        }

        // Ensure visible view is measured and laid out if bounds are 0
        if (v.getVisibility() != View.GONE && v.getWidth() == 0 && v.getHeight() == 0 && v.getRight() == 0 && v.getBottom() == 0) {
            View parent = v.getParent() instanceof View ? (View) v.getParent() : null;
            int pw = parent != null && parent.getWidth() > 0 ? parent.getWidth() : 1082;
            int ph = parent != null && parent.getHeight() > 0 ? parent.getHeight() : 1920;
            try {
                int wSpec = View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY);
                v.measure(wSpec, hSpec);
                v.layout(0, 0, pw, ph);
            } catch (Throwable ignored) {}
        }

        // Bounds
        int l = v.getLeft();
        int t = v.getTop();
        int r = v.getRight();
        int b = v.getBottom();
        int w = v.getWidth();
        int h = v.getHeight();
        if (w == 0 && r > l) w = r - l;
        if (h == 0 && b > t) h = b - t;
        sb.append(" [").append(l).append(",").append(t).append("-").append(r).append(",").append(b).append("]");

        // Visibility & Clickable
        int vis = v.getVisibility();
        if (vis == View.INVISIBLE) sb.append(" (INVISIBLE)");
        if (vis == View.GONE) sb.append(" (GONE)");
        if (v.isClickable()) sb.append(" (CLICKABLE)");

        sb.append("\n");

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int childCount = vg.getChildCount();
            for (int i = 0; i < childCount; i++) {
                dumpView(vg.getChildAt(i), depth + 1, sb, activity);
            }
        }
    }

    public static List<View> findViewsByText(Activity activity, String query) {
        List<View> matches = new ArrayList<>();
        if (activity == null || activity.getWindow() == null || query == null) return matches;
        findViewsByTextRecursive(activity.getWindow().getDecorView(), query.toLowerCase(), matches);
        return matches;
    }

    private static void findViewsByTextRecursive(View v, String query, List<View> matches) {
        if (v == null) return;
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.toString().toLowerCase().contains(query)) {
                matches.add(v);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findViewsByTextRecursive(vg.getChildAt(i), query, matches);
            }
        }
    }

    public static View findClickableViewAt(View root, int x, int y) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        int[] loc = new int[2];
        try {
            root.getLocationOnScreen(loc);
        } catch (Throwable t) {
            loc[0] = root.getLeft();
            loc[1] = root.getTop();
        }
        int rx = loc[0];
        int ry = loc[1];
        int rw = root.getWidth();
        int rh = root.getHeight();
        if (rw <= 0) rw = root.getRight() - root.getLeft();
        if (rh <= 0) rh = root.getBottom() - root.getTop();
        if (x < rx || x > rx + rw || y < ry || y > ry + rh) {
            return null;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                View child = vg.getChildAt(i);
                View match = findClickableViewAt(child, x, y);
                if (match != null) return match;
            }
        }
        if (root.isClickable() || root.hasOnClickListeners()) {
            return root;
        }
        return null;
    }

    public static boolean click(Activity activity, View view) {
        if (view == null || activity == null) return false;
        System.out.println("[*] [Headless Interaction] Triggering click on view: " + view.getClass().getSimpleName());
        try {
            // 1. Direct performClick
            boolean clicked = view.performClick();
            if (clicked) {
                System.out.println("[+] [Headless Interaction] performClick() handled successfully!");
                return true;
            }

            // 2. Fallback to MotionEvent tap
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            float cx = loc[0] + (view.getWidth() / 2f);
            float cy = loc[1] + (view.getHeight() / 2f);
            if (cx <= 0) cx = (view.getLeft() + view.getRight()) / 2f;
            if (cy <= 0) cy = (view.getTop() + view.getBottom()) / 2f;

            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0);
            MotionEvent up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, cx, cy, 0);

            activity.getWindow().getDecorView().dispatchTouchEvent(down);
            activity.getWindow().getDecorView().dispatchTouchEvent(up);

            down.recycle();
            up.recycle();
            System.out.println("[+] [Headless Interaction] MotionEvent TAP dispatched at (" + cx + ", " + cy + ")!");
            return true;
        } catch (Throwable t) {
            System.err.println("[-] Error clicking view: " + t.getMessage());
            return false;
        }
    }

    public static boolean click(Activity activity, String query) {
        List<View> matches = findViewsByText(activity, query);
        if (!matches.isEmpty()) {
            return click(activity, matches.get(0));
        }
        System.err.println("[-] No view found matching text: \"" + query + "\"");
        return false;
    }

    public static String dumpJson(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return "{\"activity\": null, \"error\": \"No active window\"}";
        }
        View decor = activity.getWindow().getDecorView();
        try {
            if (decor.getWidth() == 0 || decor.getHeight() == 0) {
                int wSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
                decor.measure(wSpec, hSpec);
                decor.layout(0, 0, 1080, 1920);
            }
        } catch (Throwable ignored) {}
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"activity\": \"").append(activity.getClass().getName()).append("\",\n");
        sb.append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        sb.append("  \"root\": ");
        buildJsonNode(decor, activity, sb, 2);
        sb.append("\n}");
        return sb.toString();
    }

    public static void saveHierarchyJson(Activity activity, java.io.File destFile) {
        if (activity == null || destFile == null) return;
        try {
            if (destFile.getParentFile() != null) destFile.getParentFile().mkdirs();
            String json = dumpJson(activity);
            java.nio.file.Files.writeString(destFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("[+] [Headless State] UI Hierarchy saved to: " + destFile.getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[-] Failed to save hierarchy JSON: " + t.getMessage());
        }
    }

    private static void buildJsonNode(View v, Activity activity, StringBuilder sb, int indent) {
        if (v == null) {
            sb.append("null");
            return;
        }
        String pad = "  ".repeat(indent);
        String childPad = "  ".repeat(indent + 1);

        sb.append("{\n");
        String className = v.getClass().getName();
        sb.append(childPad).append("\"type\": \"").append(escapeJson(className)).append("\",\n");

        int id = v.getId();
        String idStr = "";
        if (id != View.NO_ID) {
            try {
                idStr = activity.getResources().getResourceEntryName(id);
            } catch (Throwable ignored) {}
            if (idStr == null || idStr.isEmpty()) {
                idStr = "0x" + Integer.toHexString(id);
            }
        }
        sb.append(childPad).append("\"id\": \"").append(escapeJson(idStr)).append("\",\n");

        String textStr = "";
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null) textStr = cs.toString();
        }
        sb.append(childPad).append("\"text\": \"").append(escapeJson(textStr)).append("\",\n");

        int l = v.getLeft();
        int t = v.getTop();
        int r = v.getRight();
        int b = v.getBottom();
        int w = v.getWidth() > 0 ? v.getWidth() : (r - l);
        int h = v.getHeight() > 0 ? v.getHeight() : (b - t);
        sb.append(childPad).append("\"bounds\": [").append(l).append(", ").append(t).append(", ").append(r).append(", ").append(b).append("],\n");
        sb.append(childPad).append("\"width\": ").append(w).append(",\n");
        sb.append(childPad).append("\"height\": ").append(h).append(",\n");
        sb.append(childPad).append("\"visible\": ").append(v.getVisibility() == View.VISIBLE).append(",\n");
        sb.append(childPad).append("\"clickable\": ").append(v.isClickable()).append(",\n");
        sb.append(childPad).append("\"enabled\": ").append(v.isEnabled()).append(",\n");
        sb.append(childPad).append("\"focusable\": ").append(v.isFocusable());

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int count = vg.getChildCount();
            sb.append(",\n").append(childPad).append("\"children\": [\n");
            for (int i = 0; i < count; i++) {
                sb.append(childPad).append("  ");
                buildJsonNode(vg.getChildAt(i), activity, sb, indent + 2);
                if (i < count - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(childPad).append("]");
        } else {
            sb.append(",\n").append(childPad).append("\"children\": []");
        }
        sb.append("\n").append(pad).append("}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
