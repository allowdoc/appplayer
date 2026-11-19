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
        indexCollisionsIfNeeded();

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null || className.startsWith("java/") || className.startsWith("jdk/")
                        || className.startsWith("sun/") || className.startsWith("org/headless/runner/")
                        || className.startsWith("android/") || className.startsWith("androidx/")) {
                    return null;
                }

                // 1. Resolve Windows case-insensitive zip collisions (e.g. c1 vs C1) in O(1) time
                byte[] exact = COLLISION_CLASS_CACHE.get(className);
                byte[] effectiveBuffer = (exact != null) ? exact : classfileBuffer;
                boolean replacedBuffer = (exact != null);

                // 2. Rewrite System.exit / Runtime.exit only if bytecode contains 'exit' or 'halt'
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
