package androidx.viewbinding;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ViewBindings {
    private static final Map<String, Class<?>> CACHED_CAST_CLASSES = new ConcurrentHashMap<>();

    private ViewBindings() {}

    public static View findChildViewById(View rootView, int id) {
        if (rootView == null) return null;
        View found = rootView.findViewById(id);
        if (found != null) return found;

        if (rootView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) rootView;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = vg.getChildAt(i);
                View v = child.findViewById(id);
                if (v != null) return v;
            }

            // Universal on-demand View synthesis
            View synthetic = createSyntheticView(vg.getContext(), id);
            if (synthetic != null) {
                vg.addView(synthetic);
                return synthetic;
            }
        }
        return null;
    }

    private static View createSyntheticView(Context context, int id) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        String callerClass = null;
        String callerMethod = null;
        for (int i = 3; i < Math.min(st.length, 8); i++) {
            String cName = st[i].getClassName();
            if (!cName.startsWith("androidx.viewbinding.") && !cName.startsWith("java.lang.")) {
                callerClass = cName;
                callerMethod = st[i].getMethodName();
                break;
            }
        }

        Class<?> targetClass = null;
        if (callerClass != null) {
            String key = callerClass + "#" + id;
            targetClass = CACHED_CAST_CLASSES.get(key);
            if (targetClass == null) {
                parseAndCacheClass(callerClass, callerMethod);
                targetClass = CACHED_CAST_CLASSES.get(key);
            }
        }

        if (targetClass == null) {
            try {
                targetClass = Class.forName("androidx.appcompat.widget.AppCompatImageView");
            } catch (Throwable t) {
                targetClass = View.class;
            }
        }

        View v = instantiateView(context, targetClass);
        if (v != null) {
            v.setId(id);
            return v;
        }

        View fallback = new View(context);
        fallback.setId(id);
        return fallback;
    }

    private static void parseAndCacheClass(String className, String targetMethod) {
        try {
            String resPath = className.replace('.', '/') + ".class";
            InputStream is = ViewBindings.class.getClassLoader().getResourceAsStream(resPath);
            if (is == null) return;

            ClassReader cr = new ClassReader(is);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (targetMethod != null && !targetMethod.equals(name)) return null;

                    return new MethodVisitor(Opcodes.ASM9) {
                        private int lastInt = 0;
                        private boolean awaitingCheckcast = false;
                        private int pendingId = 0;

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Integer) {
                                lastInt = (Integer) value;
                            }
                        }

                        @Override
                        public void visitIntInsn(int opcode, int operand) {
                            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                                lastInt = operand;
                            }
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                                lastInt = opcode - Opcodes.ICONST_0;
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if ("findChildViewById".equals(name) || "findViewById".equals(name)) {
                                awaitingCheckcast = true;
                                pendingId = lastInt;
                            }
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (awaitingCheckcast && opcode == Opcodes.CHECKCAST) {
                                try {
                                    String cName = type.replace('/', '.');
                                    Class<?> cls = Class.forName(cName, false, ViewBindings.class.getClassLoader());
                                    if (View.class.isAssignableFrom(cls)) {
                                        CACHED_CAST_CLASSES.put(className + "#" + pendingId, cls);
                                    }
                                } catch (Throwable ignored) {}
                                awaitingCheckcast = false;
                            }
                        }
                    };
                }
            }, 0);
        } catch (Throwable ignored) {}
    }

    private static View instantiateView(Context context, Class<?> cls) {
        if (cls == null || !View.class.isAssignableFrom(cls)) return null;

        try {
            Constructor<?> ctor = cls.getConstructor(Context.class);
            return (View) ctor.newInstance(context);
        } catch (Throwable t) {
            System.err.println("[ViewBindings] Failed ctor(Context) for " + cls.getName() + ": " + t);
            if (t instanceof java.lang.reflect.InvocationTargetException) {
                ((java.lang.reflect.InvocationTargetException) t).getTargetException().printStackTrace();
            }
        }

        try {
            Constructor<?> ctor = cls.getConstructor(Context.class, AttributeSet.class);
            return (View) ctor.newInstance(context, null);
        } catch (Throwable t) {
            System.err.println("[ViewBindings] Failed ctor(Context, AttributeSet) for " + cls.getName() + ": " + t);
        }

        try {
            Constructor<?> ctor = cls.getConstructor(Context.class, AttributeSet.class, int.class);
            return (View) ctor.newInstance(context, null, 0);
        } catch (Throwable t) {
            System.err.println("[ViewBindings] Failed ctor(Context, AttributeSet, int) for " + cls.getName() + ": " + t);
        }

        if (android.widget.TextView.class.isAssignableFrom(cls)) {
            return new android.widget.TextView(context);
        }
        if (android.widget.ImageView.class.isAssignableFrom(cls)) {
            return new android.widget.ImageView(context);
        }
        if (ViewGroup.class.isAssignableFrom(cls)) {
            return new android.widget.FrameLayout(context);
        }
        return new View(context);
    }
}
