package org.headless.runner.agent;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class RuntimeNeutralizerAgent {

    public static void onExitIntercepted(int code) {
        System.out.println("[+] [Engine Neutralizer] Successfully blocked System.exit(" + code + ") call!");
    }

    public static void onRuntimeExitIntercepted(Runtime rt, int code) {
        System.out.println("[+] [Engine Neutralizer] Successfully blocked Runtime.exit(" + code + ") call!");
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[*] [Engine Agent] Native Security & Exit Neutralizer Active!");
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null || className.startsWith("java/") || className.startsWith("jdk/") || className.startsWith("sun/")) {
                    return null;
                }
                if (!containsBytes(classfileBuffer, EXIT_BYTES) && !containsBytes(classfileBuffer, HALT_BYTES)) {
                    return null;
                }
                try {
                    ClassReader cr = new ClassReader(classfileBuffer);
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
                    return modified[0] ? cw.toByteArray() : null;
                } catch (Throwable t) {
                    return null;
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
