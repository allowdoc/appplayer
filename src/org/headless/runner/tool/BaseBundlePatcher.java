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
        if (targetClass.exists() && targetClass.length() > 0) {
            return;
        }

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
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if ("getString".equals(name) && "(Ljava/lang/String;)Ljava/lang/String;".equals(desc)) {
                        MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // unparcel();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/BaseBundle", "unparcel", "()V", false);

                                // if (this.mMap == null) return null;
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD, "android/os/BaseBundle", "mMap", "Landroid/util/ArrayMap;");
                                Label nullLabel = new Label();
                                mv.visitJumpInsn(Opcodes.IFNULL, nullLabel);

                                // Object o = this.mMap.get(key);
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD, "android/os/BaseBundle", "mMap", "Landroid/util/ArrayMap;");
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/util/ArrayMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                                mv.visitVarInsn(Opcodes.ASTORE, 2);

                                // if (o == null) return null;
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitJumpInsn(Opcodes.IFNULL, nullLabel);

                                // return String.valueOf(o);
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                                mv.visitInsn(Opcodes.ARETURN);

                                // nullLabel: return null;
                                mv.visitLabel(nullLabel);
                                mv.visitInsn(Opcodes.ACONST_NULL);
                                mv.visitInsn(Opcodes.ARETURN);

                                mv.visitMaxs(2, 3);
                                mv.visitEnd();
                            }
                        };
                    }
                    return super.visitMethod(access, name, desc, sig, ex);
                }
            };
            cr.accept(cv, 0);
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
