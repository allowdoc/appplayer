package dalvik.system;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Pure Java in-process BaseDexClassLoader.
 * Eliminates openDexFileNative UnsatisfiedLinkError across standard OpenJDK runtimes.
 */
public class BaseDexClassLoader extends ClassLoader {
    public interface Reporter {
        void report(ClassLoader classLoader);
    }

    protected final ClassLoader[] sharedLibraryLoaders;

    public BaseDexClassLoader(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.sharedLibraryLoaders = null;
    }

    public BaseDexClassLoader(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent, boolean isTrusted) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.sharedLibraryLoaders = null;
    }

    public BaseDexClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.sharedLibraryLoaders = sharedLibraryLoaders;
    }

    public BaseDexClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, ClassLoader[] sharedLibraryLoaders, boolean isTrusted) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.sharedLibraryLoaders = sharedLibraryLoaders;
    }

    public BaseDexClassLoader(ByteBuffer[] dexFiles, String librarySearchPath, ClassLoader parent) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
        this.sharedLibraryLoaders = null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    public void addDexPath(String dexPath) {}
    public void addDexPath(String dexPath, boolean isTrusted) {}
    public void addNativePath(Collection<String> paths) {}
    public String findLibrary(String libname) { return null; }
    public String getLdLibraryPath() { return ""; }

    public static void setReporter(Reporter reporter) {}
    public static Reporter getReporter() { return null; }
}
