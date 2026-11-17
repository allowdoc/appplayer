package org.headless.runner.tool.annotation;

import org.headless.runner.manifest.ApkInfo;

import java.io.File;
import java.util.Map;

public class AnnotationPipeline {

    public static File ensureAnnotationsInjected(File apkFile, File inputJar, File outputJar, File storageDir, ApkInfo info) {
        if (apkFile == null || !apkFile.exists() || inputJar == null || !inputJar.exists()) {
            return inputJar;
        }

        String pkg = (info != null && info.getPackageName() != null && !info.getPackageName().isEmpty())
                ? info.getPackageName() : "default";
        long vCode = (info != null) ? info.getVersionCode() : 1L;

        File pkgVersionDir = new File(storageDir, pkg + File.separator + vCode);
        File cacheFile = new File(pkgVersionDir, "annotation-cache.bin");

        // If outputJar already exists and is newer than apkFile, reuse it immediately
        if (outputJar != null && outputJar.exists() && outputJar.lastModified() >= apkFile.lastModified()) {
            System.out.println("[+] [AnnotationPipeline] Using up-to-date patched jar: " + outputJar.getName());
            return outputJar;
        }

        // 1. Recover annotations (with network stack detection and caching)
        Map<String, AnnotationDataStructures.ClassAnnotationModel> recovered = RecoverAnnotations.recover(apkFile, cacheFile);

        if (recovered.isEmpty()) {
            System.out.println("[*] [AnnotationPipeline] No Retrofit annotations to inject. Using stock jar: " + inputJar.getName());
            return inputJar;
        }

        // 2. Inject recovered annotations into outputJar with COMPUTE_FRAMES
        boolean success = InjectAnnotations.inject(inputJar, outputJar, recovered);
        if (success && outputJar.exists()) {
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

        File finalJar = ensureAnnotationsInjected(apk, inJar, outJar, storage, null);
        System.out.println("[+] Done. Output jar: " + finalJar.getAbsolutePath());
    }
}
