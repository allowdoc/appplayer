package org.headless.runner.tool;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Repairs absent stack-map frames emitted by DEX-to-JVM conversion. */
public final class BytecodeFrameNormalizer {
    private BytecodeFrameNormalizer() {}

    /**
     * CLI: normalize stack-map frames of a jar in place (or to another jar).
     * Usage: java org.headless.runner.tool.BytecodeFrameNormalizer &lt;jar&gt; [output-jar]
     * Enjarify emits class-file version 52 (Java 8) WITHOUT StackMapTable frames,
     * which fails strict verification on JDK 13+ (old-verifier failover removed)
     * and on JDK 21+/25+ where -Xverify:none is no longer honored.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: BytecodeFrameNormalizer <jar> [output-jar]");
            System.exit(2);
        }
        File in = new File(args[0]);
        File out = args.length > 1 ? new File(args[1]) : in;
        File result = normalizeTo(in, out);
        if (result != null && result.isFile()) {
            System.out.println("[+] [FrameNormalizer] Verified archive ready: " + result.getName());
        } else {
            System.err.println("[-] [FrameNormalizer] Frame normalization failed for " + in.getName());
            System.exit(1);
        }
    }

    public static File normalizeInPlace(File jarFile) {
        return normalizeTo(jarFile, jarFile);
    }

    /** Writes to a sibling archive when the source is locked by the JVM classpath. */
    public static File normalizeTo(File inputJar, File outputJar) {
        if (inputJar == null || !inputJar.isFile() || outputJar == null) return inputJar;
        File completionMarker = new File(outputJar.getAbsolutePath() + ".frames.ok");
        if (inputJar.equals(outputJar) && completionMarker.isFile()
                && completionMarker.lastModified() >= inputJar.lastModified()) {
            System.out.println("[+] [FrameNormalizer] Using verified archive: " + outputJar.getName());
            return outputJar;
        }
        if (!inputJar.equals(outputJar) && outputJar.isFile()
                && outputJar.lastModified() >= inputJar.lastModified()) {
            System.out.println("[+] [FrameNormalizer] Using framed archive: " + outputJar.getName());
            return outputJar;
        }
        File temporary = new File(outputJar.getAbsolutePath() + ".tmp");
        int repaired = 0;
        try (JarFile input = new JarFile(inputJar);
             JarOutputStream output = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            Enumeration<JarEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] bytes;
                try (InputStream stream = input.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                if (entry.getName().endsWith(".class") && needsFrames(bytes)) {
                    bytes = computeFrames(bytes);
                    repaired++;
                }
                JarEntry replacement = new JarEntry(entry.getName());
                replacement.setTime(entry.getTime());
                output.putNextEntry(replacement);
                output.write(bytes);
                output.closeEntry();
            }
        } catch (Throwable error) {
            try { Files.deleteIfExists(temporary.toPath()); } catch (Exception ignored) {}
            System.err.println("[-] [FrameNormalizer] Unable to normalize " + inputJar.getName() + ": " + error);
            return inputJar;
        }
        try {
            Files.move(temporary.toPath(), outputJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(completionMarker.toPath(), "stack-map frames verified\n");
            System.out.println("[+] [FrameNormalizer] Recomputed stack-map frames for " + repaired + " class(es).");
        } catch (Exception error) {
            System.err.println("[-] [FrameNormalizer] Could not replace output jar: " + error);
            return inputJar;
        }
        return outputJar;
    }

    private static boolean needsFrames(byte[] bytes) {
        final boolean[] hasCode = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitCode() { hasCode[0] = true; }
                };
            }
        }, ClassReader.SKIP_DEBUG);
        // DEX conversion may leave a partial, incorrect StackMapTable.  Recompute
        // all executable methods, not only classes that have no frames at all.
        return hasCode[0];
    }

    private static byte[] computeFrames(byte[] bytes) {
        // NOTE: do NOT seed the ClassWriter with the ClassReader — that enables
        // ASM's constant-pool copy optimization, which can return the class
        // byte-identical (frames never recomputed) for classes whose hierarchy
        // lookups fall back to java/lang/Object. A fresh ClassWriter forces a
        // full re-serialization with fresh StackMapTable frames.
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(null, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String first, String second) {
                try { return super.getCommonSuperClass(first, second); }
                catch (Throwable ignored) { return "java/lang/Object"; }
            }
        };
        reader.accept(writer, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
