package org.headless.runner.tool;

import org.objectweb.asm.*;
import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class BaseBundlePatcher {

    public static void main(String[] args) {
        File androidJar = (args.length > 0) ? new File(args[0]) : new File("lib/android-all-10.jar");
        File binDir = (args.length > 1) ? new File(args[1]) : new File("bin");
        patchIfNeeded(androidJar, binDir);
    }

    public static void patchIfNeeded(File androidAllJar, File binDir) {
        File targetClass = new File(binDir, "android/os/BaseBundle.class");
        if (androidAllJar == null || !androidAllJar.exists()) {
            return;
        }

        try (JarFile jf = new JarFile(androidAllJar)) {
            JarEntry entry = jf.getJarEntry("android/os/BaseBundle.class");
            if (entry == null) return;

            byte[] originalBytes;
            try (InputStream is = jf.getInputStream(entry)) {
                originalBytes = is.readAllBytes();
            }

            ClassReader cr = new ClassReader(originalBytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    try {
                        return super.getCommonSuperClass(type1, type2);
                    } catch (Throwable ignored) {
                        return "java/lang/Object";
                    }
                }
            };
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if ("getString".equals(name) && "(Ljava/lang/String;)Ljava/lang/String;".equals(desc)) {
                        MethodVisitor output = super.visitMethod(access, name, desc, sig, ex);
                        // Discard the original method body.  Appending to it leaves
                        // unreachable bytecode and invalid control-flow frames.
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitEnd() {
                                output.visitCode();
                                // unparcel();
                                output.visitVarInsn(Opcodes.ALOAD, 0);
                                output.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/BaseBundle", "unparcel", "()V", false);

                                // if (this.mMap == null) return null;
                                output.visitVarInsn(Opcodes.ALOAD, 0);
                                output.visitFieldInsn(Opcodes.GETFIELD, "android/os/BaseBundle", "mMap", "Landroid/util/ArrayMap;");
                                Label nullLabel = new Label();
                                output.visitJumpInsn(Opcodes.IFNULL, nullLabel);

                                // Object o = this.mMap.get(key);
                                output.visitVarInsn(Opcodes.ALOAD, 0);
                                output.visitFieldInsn(Opcodes.GETFIELD, "android/os/BaseBundle", "mMap", "Landroid/util/ArrayMap;");
                                output.visitVarInsn(Opcodes.ALOAD, 1);
                                output.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/util/ArrayMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                                output.visitVarInsn(Opcodes.ASTORE, 2);

                                // if (o == null) return null;
                                output.visitVarInsn(Opcodes.ALOAD, 2);
                                output.visitJumpInsn(Opcodes.IFNULL, nullLabel);

                                // return String.valueOf(o);
                                output.visitVarInsn(Opcodes.ALOAD, 2);
                                output.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                                output.visitInsn(Opcodes.ARETURN);

                                // nullLabel: return null;
                                output.visitLabel(nullLabel);
                                output.visitInsn(Opcodes.ACONST_NULL);
                                output.visitInsn(Opcodes.ARETURN);

                                output.visitMaxs(0, 0);
                                output.visitEnd();
                            }
                        };
                    }
                    return super.visitMethod(access, name, desc, sig, ex);
                }
            };
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            byte[] patched = cw.toByteArray();

            targetClass.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(targetClass)) {
                fos.write(patched);
            }
            System.out.println("[+] [BaseBundlePatcher] Successfully generated safe BaseBundle.class in " + targetClass.getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[-] [BaseBundlePatcher] Failed to patch BaseBundle: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }
}
