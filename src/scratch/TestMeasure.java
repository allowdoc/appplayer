package scratch;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

public class TestMeasure {
    public static void inspect(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            System.out.println("[-] Activity or Window is null");
            return;
        }
        View decor = activity.getWindow().getDecorView();
        System.out.println("[*] DecorView = " + decor);
        try {
            int wSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
            System.out.println("[*] Calling decor.measure()...");
            decor.measure(wSpec, hSpec);
            System.out.println("[+] decor.measured: " + decor.getMeasuredWidth() + "x" + decor.getMeasuredHeight());
            System.out.println("[*] Calling decor.layout()...");
            decor.layout(0, 0, 1080, 1920);
            System.out.println("[+] decor.layout done: " + decor.getWidth() + "x" + decor.getHeight());
            dumpSizes(decor, 0);
        } catch (Throwable t) {
            System.err.println("[-] Exception during measure/layout: " + t);
            t.printStackTrace(System.err);
        }
    }

    private static void dumpSizes(View v, int depth) {
        if (v == null) return;
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < depth; i++) ind.append("  ");
        System.out.println(ind + v.getClass().getSimpleName() + " bounds=[" + v.getLeft() + "," + v.getTop() + "-" + v.getRight() + "," + v.getBottom() + "] measured=" + v.getMeasuredWidth() + "x" + v.getMeasuredHeight() + " vis=" + v.getVisibility() + " lp=" + (v.getLayoutParams() != null ? v.getLayoutParams().getClass().getSimpleName() + "(" + v.getLayoutParams().width + "," + v.getLayoutParams().height + ")" : "null"));
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                dumpSizes(vg.getChildAt(i), depth + 1);
            }
        }
    }
}
