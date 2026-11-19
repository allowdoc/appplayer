package org.headless.runner.tool.annotation;

import org.headless.runner.tool.annotation.AnnotationDataStructures.*;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public class InjectAnnotations {

    public static boolean inject(File inputJar, File outputJar, Map<String, ClassAnnotationModel> annotationsMap) {
        if (inputJar == null || !inputJar.exists() || outputJar == null || annotationsMap == null || annotationsMap.isEmpty()) {
            return false;
        }

        long start = System.currentTimeMillis();
        System.out.println("[*] [AnnotationInjector] Patching " + annotationsMap.size() + " class(es) into " + outputJar.getName() + " with COMPUTE_FRAMES...");

        // Generic Java 8 interface method call verifier fix:
        // Collect all interface names so that any INVOKESTATIC targeting an interface
        // has its itf flag set to true (preventing IncompatibleClassChangeError)
        Set<String> interfaceSet = new HashSet<>();
        try {
            try (JarFile scanJar = new JarFile(inputJar)) {
                Enumeration<JarEntry> en = scanJar.entries();
                while (en.hasMoreElements()) {
                    JarEntry je = en.nextElement();
                    if (je.getName().endsWith(".class")) {
                        try (InputStream is = scanJar.getInputStream(je)) {
                            ClassReader cr = new ClassReader(is);
                            if ((cr.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                                interfaceSet.add(cr.getClassName());
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        System.out.println("[+] [AnnotationInjector] Discovered " + interfaceSet.size() + " interface(s) in APK jar for Java 8 bytecode alignment.");

        int patchedCount = 0;
        File tempOut = new File(outputJar.getAbsolutePath() + ".tmp");
        if (tempOut.exists()) tempOut.delete();

        try (JarFile jar = new JarFile(inputJar);
             JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempOut)))) {

            Enumeration<JarEntry> entries = jar.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6);
                    ClassAnnotationModel cam = annotationsMap.get(className);

                    if ("com/network/NetworkManager".equals(className)) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchNetworkManager(classBytes);

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error instrumenting NetworkManager: " + t.getMessage());
                        }
                    } else if ("okhttp3/OkHttpClient$Builder".equals(className)) {
                        // Bug 2 Fix: intercept OkHttpClient.Builder.build() to install
                        // our diagnostic EventListener before the client is constructed.
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchOkHttpClientBuilder(classBytes);

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error patching OkHttpClient$Builder: " + t.getMessage());
                        }
                    } else if ("com/bugsnag/android/t".equals(className)) {
                        // Neutralize Bugsnag telemetry client methods to prevent NPE during lifecycle/setUserVisibleHint
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchBugsnagClient(classBytes);

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error patching BugsnagClient: " + t.getMessage());
                        }
                    } else if (className.startsWith("com/facebook/react/") && (
                            className.endsWith("ReactModule") ||
                            className.endsWith("ReactMethod") ||
                            className.endsWith("ReactProp") ||
                            className.endsWith("ReactPropGroup") ||
                            className.endsWith("ReactPropertyHolder")
                    )) {
                        // Inject @Retention(RetentionPolicy.RUNTIME) and default values for all annotation methods
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchReactAnnotation(classBytes);

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error patching React annotation " + className + ": " + t.getMessage());
                        }
                    } else if (className.startsWith("com/swmansion/") ||
                            className.startsWith("com/facebook/") ||
                            "com/facebook/react/devsupport/InspectorFlags".equals(className)) {
                        // Strip ACC_NATIVE and stub method bodies to prevent UnsatisfiedLinkError across pure JVM
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = classBytes;

                            if ("com/facebook/react/uimanager/NativeViewHierarchyManager".equals(className)) {
                                patchedBytes = stripFinalFromFields(patchedBytes);
                            }

                            patchedBytes = stubNativeMethods(patchedBytes);
                            patchedBytes = patchReflectionHooks(patchedBytes);

                            if (cam != null) {
                                patchedBytes = patchClass(patchedBytes, cam, interfaceSet);
                            }

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error stubbing native methods for " + className + ": " + t.getMessage());
                        }
                    } else if (cam != null) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchClass(classBytes, cam, interfaceSet);

                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patchedBytes);
                            jos.closeEntry();
                            patchedCount++;
                            continue;
                        } catch (Throwable t) {
                            System.err.println("[-] Error patching class " + className + ": " + t.getMessage());
                        }
                    } else {
                        // Check if class needs InterfaceMethodref bytecode fix
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patched = patchInterfaceStaticCalls(classBytes, interfaceSet);
                            JarEntry newEntry = new JarEntry(name);
                            newEntry.setTime(entry.getTime());
                            jos.putNextEntry(newEntry);
                            jos.write(patched != null ? patched : classBytes);
                            jos.closeEntry();
                            if (patched != null) patchedCount++;
                            continue;
                        } catch (Throwable ignored) {}
                    }
                }

                // Copy unpatched entry as-is
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                jos.putNextEntry(newEntry);
                try (InputStream is = jar.getInputStream(entry)) {
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        jos.write(buffer, 0, read);
                    }
                }
                jos.closeEntry();
            }
        } catch (Throwable t) {
            System.err.println("[-] Fatal error during jar injection: " + t.getMessage());
            t.printStackTrace();
            if (tempOut.exists()) tempOut.delete();
            return false;
        }

        if (outputJar.exists()) outputJar.delete();
        boolean renamed = tempOut.renameTo(outputJar);
        if (!renamed) {
            try {
                java.nio.file.Files.move(tempOut.toPath(), outputJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                renamed = true;
            } catch (Throwable ignored) {}
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[+] [AnnotationInjector] Successfully patched " + patchedCount + " class(es) into " + outputJar.getName() + " in " + elapsed + " ms!");
        return renamed;
    }

    private static byte[] patchNetworkManager(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if ("createService".equals(name) && desc.contains("NetworkRequest")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 1);
                            mv.visitVarInsn(Opcodes.ALOAD, 2);
                            mv.visitVarInsn(Opcodes.ALOAD, 4);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/headless/runner/core/NetworkInstrumenter", "logCreateService",
                                    "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                        }
                    };
                }
                return mv;
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchClass(byte[] classBytes, ClassAnnotationModel cam, Set<String> interfaceSet) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String effectiveSignature = (cam.signature != null) ? cam.signature : signature;
                super.visit(version, access, name, effectiveSignature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                for (AnnotationModel ca : cam.classAnnotations) {
                    if (ca.desc.equals(desc)) return null;
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitEnd() {
                // Invalidate/inject class level annotations
                for (AnnotationModel ca : cam.classAnnotations) {
                    RecoverAnnotations.applyRetrofitDefaults(ca);
                    AnnotationVisitor av = super.visitAnnotation(ca.desc, ca.visible);
                    if (av != null) {
                        for (Map.Entry<String, Object> e : ca.values.entrySet()) {
                            writeAnnotationElement(av, e.getKey(), e.getValue());
                        }
                        av.visitEnd();
                    }
                }
                super.visitEnd();
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                FieldAnnotationModel fam = cam.fields.get(name);
                String effectiveSignature = (fam != null && fam.signature != null) ? fam.signature : signature;
                FieldVisitor fv = super.visitField(access, name, descriptor, effectiveSignature, value);
                if (fam != null) {
                    for (AnnotationModel fa : fam.annotations) {
                        RecoverAnnotations.applyRetrofitDefaults(fa);
                        AnnotationVisitor av = fv.visitAnnotation(fa.desc, fa.visible);
                        if (av != null) {
                            for (Map.Entry<String, Object> e : fa.values.entrySet()) {
                                writeAnnotationElement(av, e.getKey(), e.getValue());
                            }
                            av.visitEnd();
                        }
                    }
                }
                return fv;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodAnnotationModel mam = cam.methods.get(name + ":" + descriptor);

                String effectiveSignature = (mam != null && mam.signature != null) ? mam.signature : signature;
                MethodVisitor mv = super.visitMethod(access, name, descriptor, effectiveSignature, exceptions);

                final MethodAnnotationModel targetMam = mam;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean annotationsWritten = false;

                    private void ensureAnnotationsWritten() {
                        if (annotationsWritten || targetMam == null) return;
                        annotationsWritten = true;

                        // 1. Method annotations
                        for (AnnotationModel ma : targetMam.annotations) {
                            RecoverAnnotations.applyRetrofitDefaults(ma);
                            AnnotationVisitor av = super.visitAnnotation(ma.desc, ma.visible);
                            if (av != null) {
                                for (Map.Entry<String, Object> e : ma.values.entrySet()) {
                                    writeAnnotationElement(av, e.getKey(), e.getValue());
                                }
                                av.visitEnd();
                            }
                        }

                        // 2. Parameter annotations
                        for (Map.Entry<Integer, List<AnnotationModel>> pe : targetMam.paramAnnotations.entrySet()) {
                            int pIdx = pe.getKey();
                            for (AnnotationModel pa : pe.getValue()) {
                                RecoverAnnotations.applyRetrofitDefaults(pa);
                                AnnotationVisitor pav = super.visitParameterAnnotation(pIdx, pa.desc, pa.visible);
                                if (pav != null) {
                                    for (Map.Entry<String, Object> e : pa.values.entrySet()) {
                                        writeAnnotationElement(pav, e.getKey(), e.getValue());
                                    }
                                    pav.visitEnd();
                                }
                            }
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && !itf && interfaceSet.contains(owner)) {
                            itf = true;
                        }
                        super.visitMethodInsn(opcode, owner, mName, desc, itf);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (targetMam != null) {
                            for (AnnotationModel ma : targetMam.annotations) {
                                if (ma.desc.equals(desc)) return null;
                            }
                        }
                        return super.visitAnnotation(desc, visible);
                    }

                    @Override
                    public void visitCode() {
                        ensureAnnotationsWritten();
                        super.visitCode();
                    }

                    @Override
                    public void visitEnd() {
                        ensureAnnotationsWritten();
                        super.visitEnd();
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchInterfaceStaticCalls(byte[] classBytes, Set<String> interfaceSet) {
        if (interfaceSet == null || interfaceSet.isEmpty()) return null;
        boolean[] modified = new boolean[1];
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && !itf && interfaceSet.contains(owner)) {
                            itf = true;
                            modified[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, mName, desc, itf);
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return modified[0] ? cw.toByteArray() : null;
    }

    private static void writeAnnotationElement(AnnotationVisitor av, String name, Object val) {
        if (av == null || val == null) return;
        if (val instanceof EnumValue) {
            EnumValue ev = (EnumValue) val;
            av.visitEnum(name, ev.enumDesc, ev.enumName);
        } else if (val instanceof TypeValue) {
            av.visit(name, Type.getType(((TypeValue) val).typeDesc));
        } else if (val instanceof ArrayValue) {
            ArrayValue arr = (ArrayValue) val;
            AnnotationVisitor aav = av.visitArray(name);
            if (aav != null) {
                for (Object item : arr.items) {
                    writeAnnotationElement(aav, null, item);
                }
                aav.visitEnd();
            }
        } else if (val instanceof NestedAnnotationValue) {
            NestedAnnotationValue nav = (NestedAnnotationValue) val;
            AnnotationVisitor subAv = av.visitAnnotation(name, nav.desc);
            if (subAv != null) {
                for (Map.Entry<String, Object> entry : nav.values.entrySet()) {
                    writeAnnotationElement(subAv, entry.getKey(), entry.getValue());
                }
                subAv.visitEnd();
            }
        } else {
            av.visit(name, val);
        }
    }

    /**
     * Bug 2 Fix: Patch okhttp3/OkHttpClient$Builder so that build() calls
     * NetworkInstrumenter.installEventListenerFactory(this) before constructing
     * the OkHttpClient. This installs a diagnostic EventListener that logs the
     * real IOException (SSLHandshakeException, UnknownHostException, etc.)
     * before the app swallows it in a generic error mapper.
     *
     * This is 100% generic — it patches the OkHttp library class itself, not
     * any app-specific code, so it works for any app using okhttp3.
     */
    private static byte[] patchOkHttpClientBuilder(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try { return super.getCommonSuperClass(type1, type2); }
                catch (Throwable t) { return "java/lang/Object"; }
            }
        };
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                // Intercept the build() method: ()Lokhttp3/OkHttpClient;
                if ("build".equals(name) && "()Lokhttp3/OkHttpClient;".equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // NetworkInstrumenter.installEventListenerFactory(this)
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "org/headless/runner/core/NetworkInstrumenter",
                                    "installEventListenerFactory",
                                    "(Ljava/lang/Object;)V",
                                    false);
                        }
                    };
                }
                return mv;
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchBugsnagClient(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if ("<init>".equals(name)) {
                    return super.visitMethod(access, name, desc, sig, ex);
                }
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Type ret = Type.getReturnType(desc);
                        switch (ret.getSort()) {
                            case Type.VOID:
                                mv.visitInsn(Opcodes.RETURN);
                                break;
                            case Type.BOOLEAN:
                            case Type.BYTE:
                            case Type.CHAR:
                            case Type.SHORT:
                            case Type.INT:
                                mv.visitInsn(Opcodes.ICONST_0);
                                mv.visitInsn(Opcodes.IRETURN);
                                break;
                            case Type.LONG:
                                mv.visitInsn(Opcodes.LCONST_0);
                                mv.visitInsn(Opcodes.LRETURN);
                                break;
                            case Type.FLOAT:
                                mv.visitInsn(Opcodes.FCONST_0);
                                mv.visitInsn(Opcodes.FRETURN);
                                break;
                            case Type.DOUBLE:
                                mv.visitInsn(Opcodes.DCONST_0);
                                mv.visitInsn(Opcodes.DRETURN);
                                break;
                            default:
                                mv.visitInsn(Opcodes.ACONST_NULL);
                                mv.visitInsn(Opcodes.ARETURN);
                                break;
                        }
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchReactAnnotation(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String mName, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mName, descriptor, signature, exceptions);
                if (mv != null) {
                    if ("canOverrideExistingModule".equals(mName) || "needsEagerInit".equals(mName) || "isCxxModule".equals(mName)
                            || "isBlockingSynchronousMethod".equals(mName) || "defaultBoolean".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Boolean.FALSE);
                            av.visitEnd();
                        }
                    } else if ("hasConstants".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Boolean.TRUE);
                            av.visitEnd();
                        }
                    } else if ("customType".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, "__default_type__");
                            av.visitEnd();
                        }
                    } else if ("name".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, "");
                            av.visitEnd();
                        }
                    } else if ("defaultDouble".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Double.valueOf(0.0));
                            av.visitEnd();
                        }
                    } else if ("defaultFloat".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Float.valueOf(0.0f));
                            av.visitEnd();
                        }
                    } else if ("defaultInt".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Integer.valueOf(0));
                            av.visitEnd();
                        }
                    } else if ("defaultLong".equals(mName)) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        if (av != null) {
                            av.visit(null, Long.valueOf(0L));
                            av.visitEnd();
                        }
                    }
                }
                return mv;
            }

            @Override
            public void visitEnd() {
                AnnotationVisitor av = super.visitAnnotation("Ljava/lang/annotation/Retention;", true);
                if (av != null) {
                    av.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
                    av.visitEnd();
                }
                super.visitEnd();
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] stubNativeMethods(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if ((access & Opcodes.ACC_NATIVE) != 0) {
                    int newAccess = access & ~Opcodes.ACC_NATIVE;
                    MethodVisitor mv = super.visitMethod(newAccess, name, desc, sig, ex);
                    mv.visitCode();
                    Type ret = Type.getReturnType(desc);
                    switch (ret.getSort()) {
                        case Type.VOID:
                            mv.visitInsn(Opcodes.RETURN);
                            break;
                        case Type.BOOLEAN:
                        case Type.BYTE:
                        case Type.CHAR:
                        case Type.SHORT:
                        case Type.INT:
                            mv.visitInsn(Opcodes.ICONST_0);
                            mv.visitInsn(Opcodes.IRETURN);
                            break;
                        case Type.LONG:
                            mv.visitInsn(Opcodes.LCONST_1);
                            mv.visitInsn(Opcodes.LRETURN);
                            break;
                        case Type.FLOAT:
                            mv.visitInsn(Opcodes.FCONST_0);
                            mv.visitInsn(Opcodes.FRETURN);
                            break;
                        case Type.DOUBLE:
                            mv.visitInsn(Opcodes.DCONST_0);
                            mv.visitInsn(Opcodes.DRETURN);
                            break;
                        case Type.OBJECT:
                            if ("Lcom/facebook/jni/HybridData;".equals(ret.getDescriptor())) {
                                mv.visitTypeInsn(Opcodes.NEW, "com/facebook/jni/HybridData");
                                mv.visitInsn(Opcodes.DUP);
                                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/facebook/jni/HybridData", "<init>", "()V", false);
                                mv.visitInsn(Opcodes.ARETURN);
                            } else {
                                mv.visitInsn(Opcodes.ACONST_NULL);
                                mv.visitInsn(Opcodes.ARETURN);
                            }
                            break;
                        case Type.ARRAY:
                            Type elemType = ret.getElementType();
                            mv.visitInsn(Opcodes.ICONST_0);
                            if (elemType.getSort() == Type.OBJECT) {
                                mv.visitTypeInsn(Opcodes.ANEWARRAY, elemType.getInternalName());
                            } else {
                                int newArrayOp = Opcodes.T_INT;
                                switch (elemType.getSort()) {
                                    case Type.BOOLEAN: newArrayOp = Opcodes.T_BOOLEAN; break;
                                    case Type.BYTE: newArrayOp = Opcodes.T_BYTE; break;
                                    case Type.CHAR: newArrayOp = Opcodes.T_CHAR; break;
                                    case Type.SHORT: newArrayOp = Opcodes.T_SHORT; break;
                                    case Type.INT: newArrayOp = Opcodes.T_INT; break;
                                    case Type.LONG: newArrayOp = Opcodes.T_LONG; break;
                                    case Type.FLOAT: newArrayOp = Opcodes.T_FLOAT; break;
                                    case Type.DOUBLE: newArrayOp = Opcodes.T_DOUBLE; break;
                                }
                                mv.visitIntInsn(Opcodes.NEWARRAY, newArrayOp);
                            }
                            mv.visitInsn(Opcodes.ARETURN);
                            break;
                        default:
                            mv.visitInsn(Opcodes.ACONST_NULL);
                            mv.visitInsn(Opcodes.ARETURN);
                            break;
                    }
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, desc, sig, ex);
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchReflectionHooks(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && "java/lang/Class".equals(owner) &&
                                "getDeclaredField".equals(mName) && "(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(mDesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/headless/runner/core/ReflectionHooks",
                                    "getDeclaredField", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface);
                    }
                };
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] stripFinalFromFields(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                int newAccess = access & ~Opcodes.ACC_FINAL;
                return super.visitField(newAccess, name, descriptor, signature, value);
            }
        };
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
