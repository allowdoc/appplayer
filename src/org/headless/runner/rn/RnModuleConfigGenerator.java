package org.headless.runner.rn;

import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JS-side remoteModuleConfig from the app's REAL NativeModuleRegistry
 * via reflection. This is the universal equivalent of what the C++ bridge
 * (JavaModuleWrapper / NativeModuleRegistryBuilder) does on a real Android device.
 *
 * Works for ANY React Native APK: modules come from the app itself, never from us.
 */
public final class RnModuleConfigGenerator {

    private RnModuleConfigGenerator() {}

    /** One remote module as the JS MessageQueue expects it. */
    public static final class ModuleSpec {
        public String name;
        public Object constants;              // Map or null
        public List<String> methodNames = new ArrayList<>();
        public List<Integer> promiseMethodIds = new ArrayList<>();
        public List<Integer> syncMethodIds = new ArrayList<>();
        public Map<String, Method> methodTable = new LinkedHashMap<>(); // methodName -> reflect Method
        public Object moduleInstance;  // NativeModule | ExpoKotlinDispatcher | any dispatch target
    }

    /**
     * Generate config for every module in the registry.
     * Returns parallel structures: configJson-ready list + moduleSpecs by index.
     */
    public static List<ModuleSpec> generate(Collection<NativeModule> modules) {
        List<ModuleSpec> specs = new ArrayList<>();
        if (modules == null) return specs;

        for (NativeModule module : modules) {
            if (module == null) continue;
            try {
                ModuleSpec spec = inspectModule(module);
                if (spec != null) {
                    specs.add(spec);
                }
            } catch (Throwable t) {
                System.out.println("[-] [RnConfig] Skipping module '"
                        + safeName(module) + "': " + t.getMessage());
            }
        }
        return specs;
    }

    private static String safeName(NativeModule m) {
        try { return m.getName(); } catch (Throwable t) { return m.getClass().getSimpleName(); }
    }

    @SuppressWarnings("unchecked")
    private static ModuleSpec inspectModule(NativeModule module) {
        ModuleSpec spec = new ModuleSpec();
        spec.moduleInstance = module;
        // Name resolution: real Android derives the JS module name from the
        // @ReactModule(name=...) annotation (via ModuleHolder). Plain getName()
        // often falls back to the class simple name, which breaks every
        // requireNativeModule('PlatformConstants') lookup from JS.
        spec.name = null;
        try {
            Class<?> c = module.getClass();
            while (c != null && c != Object.class) {
                try {
                    com.facebook.react.module.annotations.ReactModule ann =
                            c.getAnnotation(com.facebook.react.module.annotations.ReactModule.class);
                    if (ann != null && ann.name() != null && !ann.name().isEmpty()) {
                        spec.name = ann.name();
                        break;
                    }
                } catch (Throwable ignored) {}
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        if (spec.name == null) {
            try {
                spec.name = module.getName();
            } catch (Throwable t) {
                spec.name = null;
            }
        }
        if (spec.name == null || spec.name.isEmpty()
                || spec.name.equals(module.getClass().getSimpleName())) {
            // Last resort: strip a trailing "Module" from the class name
            // (SourceCodeModule -> SourceCode matches RN naming conventions)
            String simple = module.getClass().getSimpleName();
            String stripped = simple.endsWith("Module") ? simple.substring(0, simple.length() - 6) : simple;
            spec.name = spec.name != null && !spec.name.isEmpty() ? spec.name : stripped;
        }
        // JS rejects RCT/RK-prefixed names; mirror real RN behavior of stripping.
        while (spec.name.startsWith("RCT") || spec.name.startsWith("RK")) {
            spec.name = spec.name.substring(3);
        }

        // --- Constants ---
        try {
            Method getConstants = module.getClass().getMethod("getConstants");
            Object constants = getConstants.invoke(module);
            if (constants instanceof Map) {
                spec.constants = constants;
            }
        } catch (Throwable ignored) {
            // try getConstantsMap (newer RN variants)
            try {
                Method gcm = module.getClass().getMethod("getConstantsMap");
                Object constants = gcm.invoke(module);
                if (constants instanceof Map) spec.constants = constants;
            } catch (Throwable ignored2) { }
        }

        // --- @ReactMethod reflection across the class hierarchy ---
        Class<?> cls = module.getClass();
        Map<String, Method> found = new LinkedHashMap<>();
        while (cls != null && cls != Object.class) {
            Method[] declared;
            try {
                declared = cls.getDeclaredMethods();
            } catch (Throwable t) {
                break;
            }
            for (Method m : declared) {
                Annotation a = m.getAnnotation(ReactMethod.class);
                if (a == null) continue;
                String mName = m.getName();
                if (!found.containsKey(mName)) {
                    found.put(mName, m);
                }
            }
            cls = cls.getSuperclass();
        }

        int idx = 0;
        for (Method m : found.values()) {
            spec.methodNames.add(m.getName());
            spec.methodTable.put(m.getName(), m);

            boolean isPromise = isPromiseMethod(m);
            boolean isSync = false;
            try {
                ReactMethod rm = m.getAnnotation(ReactMethod.class);
                if (rm != null) {
                    isSync = rm.isBlockingSynchronousMethod();
                }
            } catch (Throwable ignored) {}

            if (isPromise) {
                spec.promiseMethodIds.add(idx);
            } else if (isSync) {
                spec.syncMethodIds.add(idx);
            }
            idx++;
        }
        return spec;
    }

    private static boolean isPromiseMethod(Method m) {
        Class<?>[] params = m.getParameterTypes();
        if (params.length == 0) return false;
        Class<?> last = params[params.length - 1];
        return "com.facebook.react.bridge.Promise".equals(last.getName());
    }

    /**
     * Expand the module list to the EXACT layout the JS bridge will index by:
     * expo legacy aliases (EXNativeModulesProxy / NativeUnimoduleProxy /
     * ExpoModulesCore) are inserted inline right after their source module.
     * CRITICAL: the JS bridge assigns numeric module ids by POSITION in the
     * remoteModuleConfig array. The runner's dispatch map (mSpecsById) MUST
     * use this same expanded list, otherwise every module after the first
     * alias is shifted and calls land on the wrong module or "Unknown module
     * id" (this silently killed every expo sync hook and all Timing calls).
     */
    public static List<ModuleSpec> expandForConfig(List<ModuleSpec> specs) {
        List<ModuleSpec> expanded = new ArrayList<>();
        for (ModuleSpec spec : specs) {
            expanded.add(spec);
            if ("NativeModulesProxy".equals(spec.name)) {
                for (String aliasName : new String[]{"EXNativeModulesProxy", "NativeUnimoduleProxy"}) {
                    expanded.add(makeAlias(aliasName, spec));
                }
            } else if ("ExpoBridge".equals(spec.name)) {
                expanded.add(makeAlias("ExpoModulesCore", spec));
            }
        }
        return expanded;
    }

    private static ModuleSpec makeAlias(String aliasName, ModuleSpec spec) {
        ModuleSpec alias = new ModuleSpec();
        alias.name = aliasName;
        alias.constants = spec.constants;
        alias.methodNames = spec.methodNames;
        alias.promiseMethodIds = spec.promiseMethodIds;
        alias.syncMethodIds = spec.syncMethodIds;
        alias.methodTable = spec.methodTable;
        alias.moduleInstance = spec.moduleInstance;
        return alias;
    }

    /**
     * Build the JSON-encodable remoteModuleConfig array for the JS side.
     * Writes 1:1 entries from the given list - the caller MUST pass the
     * {@link #expandForConfig}-expanded list so ids align with the dispatch
     * map.
     */
    public static List<Object> buildRemoteModuleConfig(List<ModuleSpec> specs) {
        List<Object> config = new ArrayList<>();
        for (ModuleSpec spec : specs) {
            config.add(specToEntry(spec));
        }
        return config;
    }

    private static List<Object> specToEntry(ModuleSpec spec) {
        List<Object> entry = new ArrayList<>();
        entry.add(spec.name);
        entry.add(spec.constants != null ? spec.constants : null);
        entry.add(spec.methodNames.isEmpty() ? null : spec.methodNames);
        entry.add(spec.promiseMethodIds.isEmpty() ? null : spec.promiseMethodIds);
        entry.add(spec.syncMethodIds.isEmpty() ? null : spec.syncMethodIds);
        return entry;
    }
}
