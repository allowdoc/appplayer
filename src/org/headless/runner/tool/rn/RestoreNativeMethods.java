package org.headless.runner.tool.rn;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Two-pass bytecode normalizer for desktop execution:
 *
 * Pass 1 (app jar): com/facebook/yoga/** classes with jni_* methods get
 *   ACC_NATIVE restored (stripped by the de-nativization pipeline) so the
 *   desktop libyoga.so / yoga.dll can RegisterNatives against them.
 *
 * Pass 2 (framework jar): android/graphics/** native methods (ColorSpace$Rgb,
 *   LinearGradient, ...) are converted to concrete stub bodies returning
 *   default values so the window/Drawable pipeline never hits an
 *   UnsatisfiedLinkError on desktop (there are no host graphics natives).
 *
 * Output: small overlay jar layered ABOVE app-patched.jar on the classpath.
 */
public final class RestoreNativeMethods {

    private static final String JNI_PREFIX = "jni_";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: RestoreNativeMethods <appJar> <frameworkJar> <outJar> [apk]");
            System.exit(2);
        }
        File in = new File(args[0]);
        File frameworkJar = new File(args[1]);
        File out = new File(args[2]);
        File apk = args.length >= 4 ? new File(args[3]) : null;
        int reNativedMethods = 0;
        int deNativedMethods = 0;
        int classesProcessed = 0;
        int servicesRecovered = 0;
        List<String> notes = new ArrayList<>();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(out))) {
            // ---- Pass 1: app jar — yoga re-nativing ----
            try (JarFile jar = new JarFile(in)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (!name.endsWith(".class")) continue;
                    if (!name.contains("com/facebook/yoga/")) continue;

                    try (InputStream is = jar.getInputStream(entry)) {
                        byte[] bytes = readAll(is);
                        Normalizer n = new Normalizer(true, false);
                        byte[] patched = n.normalize(bytes);
                        if (n.changed) {
                            classesProcessed++;
                            reNativedMethods += n.reNatived;
                            notes.add(name + " (re-natived " + n.reNatived + ")");
                            jos.putNextEntry(new ZipEntry(name));
                            jos.write(patched);
                            jos.closeEntry();
                        }
                    }
                }
            }

            // ---- Pass 2: framework jar — graphics de-nativing ----
            try (JarFile jar = new JarFile(frameworkJar)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (!name.endsWith(".class")) continue;
                    if (!name.contains("android/graphics/")) continue;

                    try (InputStream is = jar.getInputStream(entry)) {
                        byte[] bytes = readAll(is);
                        Normalizer n = new Normalizer(false, true);
                        byte[] patched = n.normalize(bytes);
                        if (n.changed && n.deNatived > 0) {
                            classesProcessed++;
                            deNativedMethods += n.deNatived;
                            notes.add(name + " (stubbed " + n.deNatived + ")");
                            jos.putNextEntry(new ZipEntry(name));
                            jos.write(patched);
                            jos.closeEntry();
                        }
                    }
                }
            }
            // ---- Pass 3: APK — recover META-INF/services (enjarify drops them) ----
            if (apk != null && apk.exists()) {
                try (ZipFile zip = new ZipFile(apk)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (entry.isDirectory()) continue;
                        if (!name.startsWith("META-INF/services/")) continue;
                        try (InputStream is = zip.getInputStream(entry)) {
                            byte[] bytes = readAll(is);
                            jos.putNextEntry(new ZipEntry(name));
                            jos.write(bytes);
                            jos.closeEntry();
                            servicesRecovered++;
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[-] [RestoreNativeMethods] services recovery: " + t.getMessage());
                }
            }
        }

        System.out.println("[+] [RestoreNativeMethods] Classes processed: " + classesProcessed
                + ", yoga methods re-natived: " + reNativedMethods
                + ", framework graphics natives stubbed: " + deNativedMethods
                + ", services recovered: " + servicesRecovered);
        for (String s : notes) {
            System.out.println("    - " + s);
        }
        System.out.println("[+] Output: " + out.getAbsolutePath());
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static final class Normalizer {
        final boolean yogaMode;
        final boolean graphicsMode;
        boolean changed = false;
        int reNatived = 0;
        int deNatived = 0;

        Normalizer(boolean yogaMode, boolean graphicsMode) {
            this.yogaMode = yogaMode;
            this.graphicsMode = graphicsMode;
        }

        byte[] normalize(byte[] original) {
            ClassReader cr = new ClassReader(original);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cr.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    // --- Yoga: restore native flags ---
                    if (yogaMode && name.startsWith(JNI_PREFIX)
                            && (access & Opcodes.ACC_NATIVE) == 0) {
                        changed = true;
                        reNatived++;
                        access |= Opcodes.ACC_NATIVE;
                        super.visitMethod(access, name, descriptor, signature, exceptions);
                        return new MethodVisitor(Opcodes.ASM9, null) {};
                    }
                    // --- Framework graphics: de-native with safe stub bodies ---
                    if (graphicsMode && (access & Opcodes.ACC_NATIVE) != 0) {
                        changed = true;
                        deNatived++;
                        access &= ~Opcodes.ACC_NATIVE;
                        MethodVisitor mv = super.visitMethod(access, name, descriptor,
                                signature, exceptions);
                        emitStubBody(mv, descriptor);
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            return cw.toByteArray();
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong SENTINEL_POINTER_POOL =
            new java.util.concurrent.atomic.AtomicLong(0x6000000000000001L);

    /** Distinct non-zero sentinel native pointer per stubbed native. */
    private static long nextSentinelPointer() {
        return SENTINEL_POINTER_POOL.getAndIncrement();
    }

    /** Emit a body that returns the descriptor's default value. */
    private static void emitStubBody(MethodVisitor mv, String descriptor) {
        mv.visitCode();
        String retType = descriptor.substring(descriptor.indexOf(')') + 1);
        char c = retType.charAt(0);
        int maxStack = 1;
        switch (c) {
            case 'V':
                mv.visitInsn(Opcodes.RETURN);
                break;
            case 'J':
                // Native pointers (nativeConstructor etc.) must NOT stub to 0 -
                // AOSP constructors null-check the returned native pointer and
                // throw (Region(long) etc.). Each stubbed native gets a unique
                // non-zero sentinel "pointer" instead, which all other stubbed
                // natives accept as an opaque handle.
                mv.visitLdcInsn(nextSentinelPointer());
                mv.visitInsn(Opcodes.LRETURN);
                maxStack = 2;
                break;
            case 'D':
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DRETURN);
                maxStack = 2;
                break;
            case 'F':
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
                break;
            case 'Z':
            case 'I':
            case 'S':
            case 'B':
            case 'C':
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                break;
            default: // objects and arrays
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                break;
        }
        mv.visitMaxs(maxStack, 0);
        mv.visitEnd();
    }
}
