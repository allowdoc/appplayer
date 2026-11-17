package android.animation;

import android.content.Context;
import android.content.res.Resources;

public class AnimatorInflater {
    public static StateListAnimator loadStateListAnimator(Context context, int id) throws Resources.NotFoundException {
        if (id <= 0) return null;
        return new StateListAnimator();
    }

    public static Animator loadAnimator(Context context, int id) throws Resources.NotFoundException {
        if (id <= 0) return null;
        return new ValueAnimator();
    }

    public static Animator loadAnimator(Resources resources, Resources.Theme theme, int id) throws Resources.NotFoundException {
        if (id <= 0) return null;
        return new ValueAnimator();
    }
}
