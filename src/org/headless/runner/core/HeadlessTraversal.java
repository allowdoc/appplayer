package org.headless.runner.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared headless traversal helpers.
 *
 * dispatchPreDraw fires every ViewTreeObserver.OnPreDrawListener manually from
 * a SNAPSHOT of the listener list instead of ViewTreeObserver.dispatchOnPreDraw().
 * The framework implementation iterates a CopyOnWriteArray with start()/end()
 * and throws "Iteration already started" on nested dispatches (an app listener
 * that triggers another pre-draw pass). Worse, when the exception aborts the
 * loop, end() never runs and every later dispatch fails forever. The snapshot
 * walk isolates each listener and survives re-entrancy.
 */
public final class HeadlessTraversal {

    private static final ThreadLocal<Boolean> sInPreDraw = ThreadLocal.withInitial(() -> false);

    private HeadlessTraversal() {}

    /** Fire pre-draw listeners of the given view's observer (re-entrancy safe). */
    public static boolean dispatchPreDraw(android.view.View view) {
        if (view == null) return true;
        if (sInPreDraw.get()) return true; // nested pass: skip, already firing
        sInPreDraw.set(true);
        try {
            List<Object> listeners = new ArrayList<>();
            try {
                Object attachInfo = reflectField(android.view.View.class, view, "mAttachInfo");
                if (attachInfo == null) return true;
                Object observer = reflectField(attachInfo.getClass(), attachInfo, "mTreeObserver");
                if (observer == null) return true;
                Object cowArray = reflectField(observer.getClass(), observer, "mOnPreDrawListeners");
                if (cowArray != null) {
                    Object data = reflectField(cowArray.getClass(), cowArray, "mData");
                    if (data instanceof List) listeners.addAll((List<?>) data);
                }
            } catch (Throwable ignoredReflect) {
            }
            boolean canDraw = true;
            for (Object listener : listeners) {
                try {
                    Object result = listener.getClass()
                            .getMethod("onPreDraw").invoke(listener);
                    if (Boolean.FALSE.equals(result)) canDraw = false;
                } catch (Throwable perListener) {
                    // One bad listener must not block the frame pipeline.
                }
            }
            return canDraw;
        } finally {
            sInPreDraw.set(false);
        }
    }

    private static Object reflectField(Class<?> cls, Object target, String name) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
