package dalvik.system;

import java.io.File;

/**
 * Pure Java in-process DexClassLoader.
 * Safely delegates class loading without requiring native openDexFileNative C++ runtime.
 */
public class DexClassLoader extends BaseDexClassLoader {
    public DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, (File) null, librarySearchPath, parent);
    }
}
