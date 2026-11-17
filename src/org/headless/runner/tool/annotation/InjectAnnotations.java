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
                    } else if (cam != null) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] classBytes = is.readAllBytes();
                            byte[] patchedBytes = patchClass(classBytes, cam);

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

    private static byte[] patchClass(byte[] classBytes, ClassAnnotationModel cam) {
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
                if (mam == null) {
                    // Try name-only match if descriptor slightly differs
                    for (MethodAnnotationModel m : cam.methods.values()) {
                        if (m.name.equals(name)) {
                            mam = m;
                            break;
                        }
                    }
                }

                String effectiveSignature = (mam != null && mam.signature != null) ? mam.signature : signature;
                MethodVisitor mv = super.visitMethod(access, name, descriptor, effectiveSignature, exceptions);

                if (mam != null) {
                    final MethodAnnotationModel targetMam = mam;
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean annotationsWritten = false;

                        private void ensureAnnotationsWritten() {
                            if (annotationsWritten) return;
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
                        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            // If this annotation is already one of the recovered ones, drop it so we write fresh
                            for (AnnotationModel ma : targetMam.annotations) {
                                if (ma.desc.equals(desc)) return null;
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
                return mv;
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
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
}
