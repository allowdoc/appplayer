package org.headless.runner.agent;

import org.objectweb.asm.*;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class RuntimeNeutralizerAgent {

    private static final Map<String, byte[]> COLLISION_CLASS_CACHE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong TRANSFORM_COUNT =
            new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean collisionsIndexed = false;

    private static void indexCollisionsIfNeeded() {
        if (collisionsIndexed) return;
        synchronized (COLLISION_CLASS_CACHE) {
            if (collisionsIndexed) return;
            for (String jarName : new String[]{"app-patched.jar", "app.jar"}) {
                File f = new File(jarName);
                if (!f.exists()) continue;
                try (JarFile jf = new JarFile(f)) {
                    Map<String, List<JarEntry>> lowerMap = new HashMap<>();
                    Enumeration<JarEntry> en = jf.entries();
                    while (en.hasMoreElements()) {
                        JarEntry e = en.nextElement();
                        String name = e.getName();
                        if (name.endsWith(".class")) {
                            lowerMap.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e);
                        }
                    }
                    int count = 0;
                    for (List<JarEntry> entries : lowerMap.values()) {
                        if (entries.size() > 1) {
                            for (JarEntry e : entries) {
                                try (InputStream is = jf.getInputStream(e)) {
                                    String internalName = e.getName().substring(0, e.getName().length() - 6);
                                    COLLISION_CLASS_CACHE.put(internalName, is.readAllBytes());
                                    count++;
                                }
                            }
                        }
                    }
                    if (count > 0) {
                        System.out.println("[+] [Engine Agent] Indexed " + count + " case-sensitive class(es) to prevent Windows case collisions from: " + jarName);
                        break;
                    }
                } catch (Throwable t) {
                    System.err.println("[-] [Engine Agent] Error indexing jar collisions: " + t.getMessage());
                }
            }
            collisionsIndexed = true;
        }
    }

    public static void onExitIntercepted(int code) {
        System.out.println("[+] [Engine Neutralizer] Successfully blocked System.exit(" + code + ") call!");
    }

    public static void onRuntimeExitIntercepted(Runtime rt, int code) {
        System.out.println("[+] [Engine Neutralizer] Successfully blocked Runtime.exit(" + code + ") call!");
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[*] [Engine Agent] Native Security & Exit Neutralizer Active!");
        try {
            indexCollisionsIfNeeded();
        } catch (Throwable tIndex) {
            System.out.println("[-] [Engine Agent] collision indexing failed: " + tIndex);
        }

        try {
            inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null || className.startsWith("java/") || className.startsWith("jdk/")
                        || className.startsWith("sun/") || className.startsWith("org/headless/runner/")
                        || className.startsWith("androidx/")) {
                    return null;
                }

                // android/* is skipped EXCEPT for the one targeted transform
                // below (View.getRootWindowInsets -> desktop insets provider).
                if ("android/view/View".equals(className)) {
                    try {
                        ClassReader cr = new ClassReader(classfileBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                if ("dispatchAttachedToWindow".equals(name)) {
                                    final MethodVisitor real = mv;
                                    return new MethodVisitor(Opcodes.ASM9, mv) {
                                        @Override
                                        public void visitCode() {
                                            super.visitCode();
                                            real.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                            real.visitLdcInsn("[trace] View.dispatchAttachedToWindow");
                                            real.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                                        }
                                    };
                                }
                                if ("getRootWindowInsets".equals(name)
                                        && "()Landroid/view/WindowInsets;".equals(desc)) {
                                    System.out.println("[+] [Engine Neutralizer] View.getRootWindowInsets -> desktop insets provider");
                                    final MethodVisitor body = mv;
                                    return new MethodVisitor(Opcodes.ASM9, null) {
                                        @Override
                                        public void visitCode() {
                                            body.visitCode();
                                            body.visitMethodInsn(Opcodes.INVOKESTATIC,
                                                    "org/headless/runner/agent/DesktopWindowInsetsProvider",
                                                    "getRootWindowInsets",
                                                    "()Landroid/view/WindowInsets;", false);
                                            body.visitInsn(Opcodes.ARETURN);
                                            // NO visitFrame here: a frame emitted after the
                                            // last instruction lands at offset == code_length,
                                            // which HotSpot rejects as "StackMapTable error:
                                            // bad offset" DURING CLASS PARSE - regardless of
                                            // any -XX:-BytecodeVerification* flags (verified
                                            // empirically on JDK 17/21/25). A straight-line
                                            // body needs NO StackMapTable at all; omitting it
                                            // verifies clean on a stock JVM.
                                            body.visitMaxs(1, 1);
                                        }
                                        @Override
                                        public void visitEnd() {
                                            body.visitEnd();
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable tView) {
                        System.out.println("[-] [Engine Neutralizer] View transform failed: " + tView);
                        return null;
                    }
                }
                // DecorView.onAttachedToWindow calls
                // getViewRootImpl().addWindowCallbacks(...) - the desktop
                // runner has no ViewRootImpl, so delegate to the superclass
                // body (FrameLayout) instead of crashing the whole attach.
                if ("com/android/internal/policy/DecorView".equals(className)) {
                    try {
                        ClassReader cr = new ClassReader(classfileBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                if ("onAttachedToWindow".equals(name) && "()V".equals(desc)) {
                                    System.out.println("[+] [Engine Neutralizer] DecorView.onAttachedToWindow -> FrameLayout.onAttachedToWindow");
                                    final MethodVisitor body = mv;
                                    return new MethodVisitor(Opcodes.ASM9, null) {
                                        @Override
                                        public void visitCode() {
                                            body.visitCode();
                                            body.visitVarInsn(Opcodes.ALOAD, 0);
                                            body.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                                    "android/widget/FrameLayout",
                                                    "onAttachedToWindow", "()V", false);
                                            body.visitInsn(Opcodes.RETURN);
                                            // NO visitFrame (same parse-time validation as the
                                            // View.getRootWindowInsets fix above).
                                            body.visitMaxs(1, 1);
                                        }
                                        @Override
                                        public void visitEnd() {
                                            body.visitEnd();
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable tDecor) {
                        System.out.println("[-] [Engine Neutralizer] DecorView transform failed: " + tDecor);
                        return null;
                    }
                }
                if (className.startsWith("android/")) {
                    return null;
                }

                TRANSFORM_COUNT.incrementAndGet();
                if (TRANSFORM_COUNT.get() % 500 == 1) {
                    System.out.println("[trace] transform #" + TRANSFORM_COUNT.get() + ": " + className);
                }
                if (className != null && className.contains("uimanager")) {
                    System.out.println("[trace] transform: " + className);
                }

                // 1. Resolve Windows case-insensitive zip collisions (e.g. c1 vs C1) in O(1) time
                byte[] exact = COLLISION_CLASS_CACHE.get(className);
                byte[] effectiveBuffer = (exact != null) ? exact : classfileBuffer;
                boolean replacedBuffer = (exact != null);

                // 2. Desktop-neutralize RN's ShadowNodeRegistry single-thread
                //    asserter. On a device all shadow-tree ops share one thread;
                //    in this runner they share the catalyst UI queue FIFO, and
                //    the app's own flow arms the asserter on a thread that then
                //    never runs again, which would block every render call.
                if ("com/facebook/react/uimanager/ShadowNodeRegistry$SingleThreadAsserter".equals(className)) {
                    try {
                        ClassReader cr = new ClassReader(effectiveBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                if ("assertNow".equals(name)) {
                                    System.out.println("[+] [Engine Neutralizer] ShadowNodeRegistry thread asserter neutralized (runner owns the thread discipline)");
                                    // Discard the original body entirely and emit a
                                    // clean 'return' body at visitEnd (visiting the
                                    // original events after an emitted body would
                                    // corrupt the frame and make ASM throw).
                                    final MethodVisitor body = mv;
                                    return new MethodVisitor(Opcodes.ASM9, null) {
                                        @Override
                                        public void visitEnd() {
                                            body.visitCode();
                                            body.visitInsn(Opcodes.RETURN);
                                            body.visitMaxs(0, 1);
                                            body.visitEnd();
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        byte[] outBytes = cw.toByteArray();
                        try {
                            java.io.FileOutputStream fdbg = new java.io.FileOutputStream("/tmp/transformed-asserter.class");
                            fdbg.write(outBytes);
                            fdbg.close();
                            System.out.println("[+] [Engine Neutralizer] transformed asserter bytes: " + outBytes.length);
                        } catch (Throwable ignoredDbg) {}
                        return outBytes;
                    } catch (Throwable t) {
                        System.out.println("[-] [Engine Neutralizer] asserter transform failed: " + t);
                        return replacedBuffer ? effectiveBuffer : null;
                    }
                }

                // 2b. Render-pipeline traces: prove whether UIViewOperationQueue
                //     executes its batched operations on the desktop.
                boolean traceUoq = "com/facebook/react/uimanager/UIViewOperationQueue".equals(className);
                boolean traceNvhm = "com/facebook/react/uimanager/NativeViewHierarchyManager".equals(className);
                if ((traceUoq || traceNvhm) && !replacedBuffer) {
                    try {
                        ClassReader cr = new ClassReader(effectiveBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            private String traceName = null;
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                boolean hit = traceUoq
                                        ? ("dispatchViewUpdates".equals(name) || name.startsWith("dispatchPending") || "dispatchBatch".equals(name))
                                        : ("createView".equals(name) || "manageChildren".equals(name) || "setChildren".equals(name) || "addRootView".equals(name));
                                if (traceNvhm && mv != null) {
                                    // NVHM.createView(int, String, int, ReadableMap):
                                    // local 1 = tag, local 2 = className, local 3 = rootViewTag.
                                    // NVHM.manageChildren(int, ...) / setChildren(int, ...):
                                    // local 1 = parent tag.
                                    final String label = name;
                                    final MethodVisitor real = mv;
                                    return new MethodVisitor(Opcodes.ASM9, mv) {
                                        @Override
                                        public void visitCode() {
                                            super.visitCode();
                                            real.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                            real.visitLdcInsn("[trace] " + label + " (thread fixed at class load)");
                                            real.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                                            if ("createView".equals(label)) {
                                                // RN 0.8x: createView(ThemedReactContext ctx, int tag, String className, ReactStylesDiffMap props)
                                                // locals: 0=this, 1=ctx, 2=tag, 3=className
                                                real.visitVarInsn(Opcodes.ILOAD, 2);
                                                real.visitVarInsn(Opcodes.ALOAD, 3);
                                                real.visitMethodInsn(Opcodes.INVOKESTATIC, "org/headless/runner/agent/TraceHelper", "logCreateView", "(ILjava/lang/String;)V", false);
                                            } else if ("manageChildren".equals(label) || "setChildren".equals(label)) {
                                                real.visitVarInsn(Opcodes.ILOAD, 1);
                                                real.visitMethodInsn(Opcodes.INVOKESTATIC, "org/headless/runner/agent/TraceHelper", "log" + label, "(I)V", false);
                                            }
                                        }
                                        // The batch drain is scheduled through the
                                        // Choreographer of whichever thread called
                                        // dispatchViewUpdates - on this runner that
                                        // thread dies right after batch creation,
                                        // so the callback never fires. Replace the
                                        // Choreographer post with a direct doFrame:
                                        // the batched operations execute immediately
                                        // on the current (UI-queue) thread.
                                        @Override
                                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                                            if (Opcodes.INVOKEVIRTUAL == opcode
                                                    && "postFrameCallback".equals(mName)
                                                    && (owner.endsWith("Choreographer") || owner.endsWith("ChoreographerCompat"))) {
                                                System.out.println("[+] [Engine Neutralizer] intercepting " + owner + ".postFrameCallback in " + label);
                                                if ("android/view/Choreographer".equals(owner)) {
                                                    // stack: ..., choreographer, callback
                                                    super.visitInsn(Opcodes.SWAP);               // ..., callback, choreographer
                                                    super.visitInsn(Opcodes.POP);               // ..., callback
                                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false); // ..., callback, nowNanos
                                                    super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/view/Choreographer$FrameCallback", "doFrame", "(J)V", true);
                                                } else {
                                                    // ReactChoreographer.postFrameCallback(type, callback)
                                                    // stack: ..., reactChoreographer, type, callback
                                                    super.visitInsn(Opcodes.SWAP);               // ..., rc, callback, type
                                                    super.visitInsn(Opcodes.POP);               // ..., rc, callback
                                                    super.visitInsn(Opcodes.SWAP);               // ..., callback, rc
                                                    super.visitInsn(Opcodes.POP);               // ..., callback
                                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false); // ..., callback, nowNanos
                                                    super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/view/Choreographer$FrameCallback", "doFrame", "(J)V", true);
                                                }
                                                System.out.println("[+] [Engine Neutralizer] Choreographer.postFrameCallback replaced with immediate doFrame in " + label);
                                                return;
                                            }
                                            super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable t) {
                        System.out.println("[-] [Engine Neutralizer] UOQ trace transform failed: " + t);
                        return null;
                    }
                }

                // 2b2. EventDispatcher trace: prove whether RN events (e.g.
                //      safe-area onInsetsChange) enter the dispatcher at all.
                if ("com/facebook/react/uimanager/events/EventDispatcherImpl".equals(className) && !replacedBuffer) {
                    try {
                        ClassReader cr = new ClassReader(effectiveBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                if ("dispatchEvent".equals(name) && mv != null) {
                                    final MethodVisitor real = mv;
                                    return new MethodVisitor(Opcodes.ASM9, mv) {
                                        @Override
                                        public void visitCode() {
                                            super.visitCode();
                                            real.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                            real.visitLdcInsn("[trace] EventDispatcherImpl.dispatchEvent entered");
                                            real.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable tEv) {
                        System.out.println("[-] [Engine Neutralizer] EventDispatcher trace failed: " + tEv);
                        return null;
                    }
                }

                // 2b3. Traversal trace: prove whether attach/pre-draw flows run.
                String traceHit = "com/th3rdwave/safeareacontext/SafeAreaProvider".equals(className)
                        ? "com/th3rdwave/safeareacontext/SafeAreaProvider" : null;
                if (traceHit != null) {
                    try {
                        ClassReader cr = new ClassReader(effectiveBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                boolean trace = ("onAttachedToWindow".equals(name) && "()V".equals(desc))
                                        || ("onPreDraw".equals(name) && "()Z".equals(desc));
                                if (trace && mv != null) {
                                    final MethodVisitor real = mv;
                                    final String label = traceHit + "." + name;
                                    return new MethodVisitor(Opcodes.ASM9, mv) {
                                        @Override
                                        public void visitCode() {
                                            super.visitCode();
                                            real.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                                            real.visitLdcInsn("[trace] " + label + " entered");
                                            real.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable tSp) {
                        return null;
                    }
                }

                // 2c. ReactChoreographer.postFrameCallback: execute the frame
                //     callback IMMEDIATELY instead of queueing it behind a
                //     Choreographer vsync. RN forwards these to the framework
                //     Choreographer of the calling thread - here the context
                //     thread, which dies right after batch creation, so queued
                //     frame callbacks (and every queued view operation) never
                //     run. Immediate execution on the caller (the routed UI
                //     queue FIFO thread) preserves ordering; the asserter is
                //     already neutralized above.
                if ("com/facebook/react/modules/core/ReactChoreographer".equals(className)) {
                    try {
                        ClassReader cr = new ClassReader(effectiveBuffer);
                        ClassWriter cw = new ClassWriter(cr, 0);
                        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                if ("postFrameCallback".equals(name)
                                        && mv != null
                                        && desc != null
                                        && desc.endsWith("FrameCallback;)V")) {
                                    System.out.println("[+] [Engine Neutralizer] ReactChoreographer.postFrameCallback -> main looper");
                                    final MethodVisitor body = mv;
                                    return new MethodVisitor(Opcodes.ASM9, null) {
                                        @Override
                                        public void visitEnd() {
                                            // void postFrameCallback(CallbackType type, FrameCallback cb)
                                            // locals: 0=this, 1=type, 2=cb
                                            body.visitCode();
                                            body.visitTypeInsn(Opcodes.NEW, "org/headless/runner/agent/FrameCallbackRunnable");
                                            body.visitInsn(Opcodes.DUP);
                                            body.visitVarInsn(Opcodes.ALOAD, 2);           // cb
                                            body.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false); // cb, now
                                            body.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/headless/runner/agent/FrameCallbackRunnable", "<init>", "(Landroid/view/Choreographer$FrameCallback;J)V", false);
                                            body.visitVarInsn(Opcodes.ASTORE, 3);           // runnable
                                            body.visitTypeInsn(Opcodes.NEW, "android/os/Handler");
                                            body.visitInsn(Opcodes.DUP);
                                            body.visitMethodInsn(Opcodes.INVOKESTATIC, "android/os/Looper", "getMainLooper", "()Landroid/os/Looper;", false);
                                            body.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Handler", "<init>", "(Landroid/os/Looper;)V", false);
                                            body.visitVarInsn(Opcodes.ASTORE, 4);           // handler
                                            body.visitVarInsn(Opcodes.ALOAD, 4);
                                            body.visitVarInsn(Opcodes.ALOAD, 3);
                                            body.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Handler", "post", "(Ljava/lang/Runnable;)Z", false);
                                            body.visitInsn(Opcodes.POP);
                                            body.visitInsn(Opcodes.RETURN);
                                            body.visitMaxs(6, 5);
                                            body.visitEnd();
                                        }
                                    };
                                }
                                return mv;
                            }
                        };
                        cr.accept(cv, 0);
                        return cw.toByteArray();
                    } catch (Throwable t) {
                        System.out.println("[-] [Engine Neutralizer] ReactChoreographer transform failed: " + t);
                        return replacedBuffer ? effectiveBuffer : null;
                    }
                }

                // 3. Rewrite System.exit / Runtime.exit only if bytecode contains 'exit' or 'halt'
                if (!containsBytes(effectiveBuffer, EXIT_BYTES) && !containsBytes(effectiveBuffer, HALT_BYTES)) {
                    return replacedBuffer ? effectiveBuffer : null;
                }

                try {
                    ClassReader cr = new ClassReader(effectiveBuffer);
                    ClassWriter cw = new ClassWriter(cr, 0);
                    final boolean[] modified = new boolean[1];
                    ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            return new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                                    if (opcode == Opcodes.INVOKESTATIC && "java/lang/System".equals(owner) && "exit".equals(mName) && "(I)V".equals(mDesc)) {
                                        System.out.println("[+] [Engine Neutralizer] Rewriting System.exit() in " + className + "." + name);
                                        super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/headless/runner/agent/RuntimeNeutralizerAgent",
                                            "onExitIntercepted",
                                            "(I)V",
                                            false
                                        );
                                        modified[0] = true;
                                        return;
                                    }
                                    if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/Runtime".equals(owner) && ("exit".equals(mName) || "halt".equals(mName)) && "(I)V".equals(mDesc)) {
                                        System.out.println("[+] [Engine Neutralizer] Rewriting Runtime." + mName + "() in " + className + "." + name);
                                        super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/headless/runner/agent/RuntimeNeutralizerAgent",
                                            "onRuntimeExitIntercepted",
                                            "(Ljava/lang/Runtime;I)V",
                                            false
                                        );
                                        modified[0] = true;
                                        return;
                                    }
                                    super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                                }
                            };
                        }
                    };
                    cr.accept(cv, 0);
                    return modified[0] ? cw.toByteArray() : (replacedBuffer ? effectiveBuffer : null);
                } catch (Throwable t) {
                    return replacedBuffer ? effectiveBuffer : null;
                }
            }
            });
        } catch (Throwable tAgent) {
            System.out.println("[-] [Engine Agent] transformer registration failed: " + tAgent);
        }
        System.out.println("[*] [Engine Agent] premain complete (transformer registered)");
    }

    private static final byte[] EXIT_BYTES = new byte[] {'e', 'x', 'i', 't'};
    private static final byte[] HALT_BYTES = new byte[] {'h', 'a', 'l', 't'};

    private static boolean containsBytes(byte[] source, byte[] target) {
        if (source == null || target == null || source.length < target.length) return false;
        outer:
        for (int i = 0; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
