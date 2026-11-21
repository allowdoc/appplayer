package org.headless.runner.tool.annotation;

import org.headless.runner.manifest.ApkInfo;
import org.headless.runner.tool.BaseBundlePatcher;
import org.headless.runner.tool.BytecodeFrameNormalizer;

import java.io.File;
import java.util.Map;

public class AnnotationPipeline {

    private static final String PATCH_MARKER = ".patched-for.txt";

    private static final String COMPAT_VERSION = "compat=2"; // bump when runner-compat patches change

    /** Stable identity of the APK the patched jar was built from (size is a cheap fingerprint). */
    private static String apkFingerprint(File apkFile) {
        return "apkSize=" + apkFile.length() + ";" + COMPAT_VERSION;
    }

    private static boolean markerMatches(File markerFile, File apkFile) {
        if (markerFile == null || !markerFile.isFile() || apkFile == null) return false;
        try {
            String recorded = new String(java.nio.file.Files.readAllBytes(markerFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return recorded.equals(apkFingerprint(apkFile));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeMarker(File markerFile, File apkFile) {
        try {
            java.nio.file.Files.write(markerFile.toPath(),
                    apkFingerprint(apkFile).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
    }

    public static File ensureAnnotationsInjected(File apkFile, File inputJar, File outputJar, File storageDir, ApkInfo info) {
        if (apkFile == null || !apkFile.exists() || inputJar == null || !inputJar.exists()) {
            return inputJar;
        }

        String pkg = (info != null && info.getPackageName() != null && !info.getPackageName().isEmpty())
                ? info.getPackageName() : "default";
        long vCode = (info != null) ? info.getVersionCode() : 1L;

        File pkgVersionDir = new File(storageDir, pkg + File.separator + vCode);
        File cacheFile = new File(pkgVersionDir, "annotation-cache.bin");
        File markerFile = new File(pkgVersionDir, PATCH_MARKER);

        // Reuse ONLY when the patched jar exists, is newer than the APK, AND was
        // built from THIS APK (fingerprint marker). Otherwise a patched jar left
        // over from a previous app would silently boot the wrong application.
        if (outputJar != null && outputJar.exists()
                && outputJar.lastModified() >= apkFile.lastModified()
                && markerMatches(markerFile, apkFile)) {
            System.out.println("[+] [AnnotationPipeline] Using up-to-date patched jar: " + outputJar.getName());
            return outputJar;
        }

        // 1. Recover annotations (with network stack detection and caching)
        Map<String, AnnotationDataStructures.ClassAnnotationModel> recovered = RecoverAnnotations.recover(apkFile, cacheFile);

        if (recovered.isEmpty()) {
            System.out.println("[*] [AnnotationPipeline] No Retrofit annotations to inject. Applying runner-compat patches only.");
        }

        // 2. Inject recovered annotations into outputJar with COMPUTE_FRAMES
        //    (runner-compat patches are applied even with an empty model so a
        //    patched jar with stable semantics is always produced)
        boolean success = InjectAnnotations.inject(inputJar, outputJar, recovered);
        if (success && outputJar.exists()) {
            writeMarker(markerFile, apkFile);
            return outputJar;
        }

        System.err.println("[-] [AnnotationPipeline] Injection failed, falling back to: " + inputJar.getName());
        return inputJar;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: AnnotationPipeline <apk-path> <input-app-jar> [output-app-jar] [storage-dir]");
            return;
        }

        File apk = new File(args[0]);
        File inJar = new File(args[1]);
        File outJar = args.length > 2 ? new File(args[2]) : new File(inJar.getParentFile(), "app-patched.jar");
        File storage = args.length > 3 ? new File(args[3]) : new File(inJar.getParentFile(), "data");

        System.out.println("==================================================================");
        System.out.println("            UNIVERSAL ANDROID ANNOTATION RECOVERY TOOL            ");
        System.out.println("==================================================================");

        BaseBundlePatcher.patchIfNeeded(new File("lib/android-all-10.jar"), new File("bin"));
        BytecodeFrameNormalizer.normalizeTo(new File("lib/android-all-10.jar"), new File("lib/android-all-10-framed.jar"));

        ApkInfo info = null;
        try {
            info = org.headless.runner.manifest.ManifestExtractor.extract(apk);
        } catch (Throwable ignored) {}
        File finalJar = ensureAnnotationsInjected(apk, inJar, outJar, storage, info);
        // CRITICAL: enjarify emits Java 8 (v52) classes WITHOUT stack-map frames.
        // JDK 13+ removed the old-verifier failover and JDK 21+/25+ ignore
        // -Xverify:none, so every shipped class needs recomputed frames.
        BytecodeFrameNormalizer.normalizeInPlace(finalJar);
        System.out.println("[+] Done. Output jar: " + finalJar.getAbsolutePath());
    }
}
