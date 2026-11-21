package org.headless.runner.core;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/**
 * Drawable resolution helper: attempts a REAL framework vector inflate for
 * XML-valued drawable ids before falling back to the placeholder drawable.
 * Called by Resources.getDrawable / Resources.getDrawableForDensity.
 */
public final class SyntheticDrawableFactory {

    private SyntheticDrawableFactory() {}

    /** @return a framework VectorDrawable when id is a <vector> XML drawable, else null. */
    public static Drawable vector(Resources res, int id, Resources.Theme theme) {
        if (res == null || id == 0) return null;
        try {
            java.io.File apk = res.getApkFile();
            if (apk == null || !apk.exists()) return null;
            ArscParser a = ArscParser.getShared(apk);
            if (a == null) return null;
            ArscParser.ResVal v = a.getValue(id);
            if (v == null || v.str == null || !v.str.endsWith(".xml")) return null;
            return android.graphics.drawable.VectorDrawable.create(res, id, theme);
        } catch (Throwable t) {
            return null;
        }
    }
}
