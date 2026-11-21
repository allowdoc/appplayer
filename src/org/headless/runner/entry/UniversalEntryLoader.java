package org.headless.runner.entry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * UNIVERSAL JAVA ENTRY LOADER ("all type of Java loader").
 *
 * The Android boot path in AppLauncher only understands Android APKs. A plain
 * Java application (console / Swing / AWT / library jar with a Main-Class)
 * previously had NO path at all - the runner forced it through the APK
 * manifest parser and failed. This loader makes the runner a universal JVM
 * app runner, same philosophy as the Android path: the app brings its own
 * code, the environment provides the runtime (runner classes, framework
 * shims, desktop natives), zero per-app glue.
 *
 * Input classification (first launch argument):
 *   - Android APK  : zip with AndroidManifest.xml  -> false (Android boot path)
 *   - JAR / ZIP    : any other zip                 -> Java entry
 *   - .class file  : single compiled class         -> Java entry
 *   - directory    : folder of classes / runnable  -> Java entry
 *   - --java FQCN  : explicit main class override  -> Java entry
 *
 * Entry discovery order (all universal, no per-app knowledge):
 *   1. --main / --java launch argument
 *   2. MANIFEST.MF Main-Class attribute
 *   3. bytecode scan of the input jar/dir for public static void main(String[])
 *      (ASM header scan - classes are never loaded during discovery)
 *
 * Classloading: child-first URLClassLoader over the app jar/dir + sibling
 * lib/ + libs/ folders, parented on the runner loader so framework shims
 * (android.*, runner compat classes, desktop natives) stay available. The
 * thread context classloader is set for SPI/service-loader style apps.
 */
public final class UniversalEntryLoader {

    private UniversalEntryLoader() {
    }

    /**
     * Try to launch the first argument as a plain Java application.
     * Returns false when the input is an Android APK (Android boot path runs).
     */
    public static boolean tryLaunch(String[] args) {
        String target = null;
        String explicitMain = null;
        boolean forceJava = false;
        List<String> appArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--java".equalsIgnoreCase(a) && i + 1 < args.length) {
                explicitMain = args[++i];
                forceJava = true;
                continue;
            }
            if ("--main".equalsIgnoreCase(a) && i + 1 < args.length) {
                explicitMain = args[++i];
                continue;
            }
            if (a.equalsIgnoreCase("--ui") || a.equalsIgnoreCase("-ui")) {
                continue;
            }
            if (a.equalsIgnoreCase("--timeout") || a.equalsIgnoreCase("--exit-after")
                    || a.equalsIgnoreCase("--activity")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) i++;
                continue;
            }
            if (target == null) {
                target = a;
            } else {
                appArgs.add(a);
            }
        }
        if (target == null) return false;

        File f = new File(target);
        if (!f.exists()) return false;

        if (target.toLowerCase().endsWith(".apk") && isAndroidApk(f)) {
            return false;   // real Android app - normal boot path
        }
        if (target.toLowerCase().endsWith(".apk")) {
            System.out.println("[*] [UniversalEntry] .apk without AndroidManifest.xml - "
                    + "treating as a plain Java jar (environment, not app, decides).");
        }

        try {
            launchJavaApp(f, explicitMain, appArgs);
        } catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            System.err.println("[-] [UniversalEntry] Java app failed: " + cause);
            StackTraceElement[] st = cause.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 20); i++) System.err.println("      at " + st[i]);
            System.exit(1);
        }
        return true;
    }

    /** Throw any Throwable preserving its type, unchecked (used for app main()). */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable t) throws T {
        throw (T) t;
    }

    private static boolean isAndroidApk(File f) {
        try (JarFile jf = new JarFile(f)) {
            return jf.getEntry("AndroidManifest.xml") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Java application boot
    // ------------------------------------------------------------------

    private static void launchJavaApp(File f, String explicitMain, List<String> appArgs)
            throws Exception {
        long start = System.currentTimeMillis();
        System.out.println("==================================================================");
        System.out.println("  UNIVERSAL JAVA ENTRY (pure JVM / zero VM layers)");
        System.out.println("  Target: " + f.getAbsolutePath());
        System.out.println("==================================================================");

        List<File> cpEntries = new ArrayList<>();
        collectClasspath(f, cpEntries);

        List<URL> urls = new ArrayList<>();
        for (File e : cpEntries) {
            System.out.println("[+] [UniversalEntry] classpath: " + e.getAbsolutePath());
            urls.add(e.toURI().toURL());
        }
        // child-first loader; parent = runner loader (framework shims stay visible)
        ClassLoader parent = UniversalEntryLoader.class.getClassLoader();
        URLClassLoader appLoader = new URLClassLoader(
                urls.toArray(new URL[0]), parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException e) {
                            c = super.loadClass(name, false);
                        }
                    }
                    if (resolve) resolveClass(c);
                    return c;
                }
            }
        };
        Thread.currentThread().setContextClassLoader(appLoader);

        String mainClass = explicitMain;
        if (mainClass == null) {
            mainClass = readManifestMainClass(f);
        }
        if (mainClass == null) {
            mainClass = scanForMainClass(f);
        }
        if (mainClass == null) {
            System.err.println("[-] [UniversalEntry] No entry point found:");
            System.err.println("    - no --main/--java argument,");
            System.err.println("    - no Main-Class in the manifest,");
            System.err.println("    - no class with public static void main(String[]) in the input.");
            System.exit(2);
        }

        System.out.println("[+] [UniversalEntry] Entry point: " + mainClass);
        Class<?> cls = Class.forName(mainClass, true, appLoader);
        Method main = null;
        for (Method m : cls.getMethods()) {
            if ("main".equals(m.getName())
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && ((m.getParameterCount() == 0)
                        || (m.getParameterCount() == 1
                            && String[].class.isAssignableFrom(m.getParameterTypes()[0])))) {
                main = m;
                break;
            }
        }
        if (main == null) {
            System.err.println("[-] [UniversalEntry] Class " + mainClass
                    + " has no public static void main(String[]).");
            System.exit(2);
        }

        String[] argv = appArgs.toArray(new String[0]);
        long loaded = System.currentTimeMillis();
        System.out.println("[+] [UniversalEntry] Launching in " + (loaded - start) + " ms...");
        try {
            if (main.getParameterCount() == 1) {
                main.invoke(null, (Object) argv);
            } else {
                main.invoke(null);
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable c = ite.getCause() != null ? ite.getCause() : ite;
            throw sneaky(c);   // preserve the app's real exception type
        }
        long done = System.currentTimeMillis();
        System.out.println("==================================================================");
        System.out.println("[+] [UniversalEntry] " + mainClass + " completed in "
                + (done - start) + " ms.");
        System.out.println("==================================================================");
        // Java apps own the process lifetime: exit with success when main returns.
        System.exit(0);
    }

    /** The input plus any sibling lib/ and libs/ jars (common app layouts). */
    private static void collectClasspath(File f, List<File> out) {
        if (f.isDirectory()) {
            out.add(f);
            addJars(new File(f, "lib").listFiles(), out);
            addJars(new File(f, "libs").listFiles(), out);
            return;
        }
        String n = f.getName().toLowerCase();
        if (n.endsWith(".class")) {
            // package root = two levels up for x/y/Class.class, one level for Class.class
            File dir = f.getParentFile();
            if (dir != null) {
                out.add(dir.getParentFile() != null ? dir.getParentFile() : dir);
                out.add(dir);
            }
            return;
        }
        out.add(f);   // jar / zip
        File parent = f.getParentFile();
        for (String libDir : new String[]{"lib", "libs"}) {
            File d = parent == null ? new File(libDir) : new File(parent, libDir);
            addJars(d.listFiles(), out);
        }
    }

    private static void addJars(File[] files, List<File> out) {
        if (files == null) return;
        for (File j : files) {
            String n = j.getName().toLowerCase();
            if (n.endsWith(".jar") || n.endsWith(".zip")) out.add(j);
        }
    }

    private static String readManifestMainClass(File f) {
        try (JarFile jf = new JarFile(f)) {
            ZipEntry mf = jf.getEntry("META-INF/MANIFEST.MF");
            if (mf == null) return null;
            String text = new String(readAll(jf.getInputStream(mf)), StandardCharsets.UTF_8);
            String current = null;
            // Manifest lines may wrap (continuation lines start with a space)
            for (String raw : text.split("\r?\n")) {
                String line;
                if (raw.startsWith(" ") && current != null) {
                    current = current + raw.substring(1);
                    line = current;
                } else {
                    current = raw;
                    line = raw;
                }
                if (line.toLowerCase().startsWith("main-class:")) {
                    return line.substring("main-class:".length()).trim().replace('/', '.');
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * ASM header scan: find a class with public static void main(String[]).
     * Never loads classes through the app loader during discovery, so the
     * scan is safe for apps with fragile static initializers.
     */
    private static String scanForMainClass(File f) throws Exception {
        List<String> candidates = new ArrayList<>();
        if (f.isDirectory()) {
            scanDirectory(f, f, candidates);
        } else {
            try (JarFile jf = new JarFile(f)) {
                Enumeration<JarEntry> it = jf.entries();
                while (it.hasMoreElements()) {
                    JarEntry e = it.nextElement();
                    if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                    if (e.getName().startsWith("META-INF/")) continue;
                    candidates.add(e.getName());
                }
            }
        }
        String fallback = null;
        List<String> withMain = new ArrayList<>();
        for (String entry : candidates) {
            byte[] bytes = readEntryBytes(f, entry);
            if (bytes == null) continue;
            try {
                ClassReader cr = new ClassReader(bytes);
                final boolean[] found = {false};
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name,
                            String desc, String sig, String[] exceptions) {
                        if ("main".equals(name)
                                && (access & Opcodes.ACC_PUBLIC) != 0
                                && (access & Opcodes.ACC_STATIC) != 0
                                && "([Ljava/lang/String;)V".equals(desc)) {
                            found[0] = true;
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                if (found[0]) {
                    String cn = cr.getClassName().replace('/', '.');
                    withMain.add(cn);
                    if (fallback == null && (cn.endsWith(".Main") || cn.toLowerCase().endsWith("main"))) {
                        fallback = cn;
                    }
                }
            } catch (Throwable ignored) {
                // not a parseable class - skip
            }
        }
        if (!withMain.isEmpty()) {
            if (fallback != null) return fallback;
            if (withMain.size() == 1) return withMain.get(0);
            System.out.println("[*] [UniversalEntry] Multiple main candidates: " + withMain
                    + " - using the first (pass --main to override).");
            return withMain.get(0);
        }
        return null;
    }

    private static void scanDirectory(File root, File dir, List<String> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) scanDirectory(root, k, out);
            else if (k.getName().endsWith(".class")) {
                out.add(root.toPath().relativize(k.toPath()).toString()
                        .replace(File.separatorChar, '/'));
            }
        }
    }

    private static byte[] readEntryBytes(File f, String entryName) {
        try {
            if (f.isDirectory()) {
                File file = new File(f, entryName);
                if (file.isFile()) return readAll(new FileInputStream(file));
                return null;
            }
            try (JarFile jf = new JarFile(f)) {
                JarEntry e = jf.getJarEntry(entryName);
                if (e == null) return null;
                return readAll(jf.getInputStream(e));
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
