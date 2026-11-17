package org.headless.runner.tool.annotation;

import org.headless.runner.tool.annotation.AnnotationDataStructures.*;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.value.*;

import java.io.*;
import java.util.*;

public class RecoverAnnotations {

    public static class DetectionResult {
        public final Set<String> stacks = new LinkedHashSet<>();
        public boolean hasRetrofit = false;
        public boolean hasVolley = false;
        public boolean hasOkHttp = false;
        public boolean hasGrpc = false;

        @Override
        public String toString() {
            return stacks.toString();
        }
    }

    public static DetectionResult detectNetworkStacks(File apkFile) {
        DetectionResult result = new DetectionResult();
        if (apkFile == null || !apkFile.exists()) return result;

        try {
            MultiDexContainer<? extends DexFile> container = DexFileFactory.loadDexContainer(apkFile, Opcodes.getDefault());
            for (String entryName : container.getDexEntryNames()) {
                DexFile dex = container.getEntry(entryName).getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    String t = cd.getType();
                    if (t.contains("Lretrofit2/Retrofit;") || t.startsWith("Lretrofit2/http/")) {
                        result.hasRetrofit = true;
                        result.stacks.add("Retrofit");
                    }
                    if (t.contains("Lcom/android/volley/RequestQueue;") || t.startsWith("Lcom/android/volley/")) {
                        result.hasVolley = true;
                        result.stacks.add("Volley");
                    }
                    if (t.contains("Lokhttp3/OkHttpClient;") || t.startsWith("Lokhttp3/")) {
                        result.hasOkHttp = true;
                        result.stacks.add("OkHttp");
                    }
                    if (t.contains("Lio/grpc/Channel;") || t.startsWith("Lio/grpc/")) {
                        result.hasGrpc = true;
                        result.stacks.add("gRPC");
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[-] [NetworkDetector] Notice inspecting APK: " + t.getMessage());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ClassAnnotationModel> recover(File apkFile, File cacheFile) {
        if (cacheFile != null && cacheFile.exists() && apkFile != null && cacheFile.lastModified() >= apkFile.lastModified()) {
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
                System.out.println("[+] [AnnotationRecovery] Loaded cached annotations from: " + cacheFile.getAbsolutePath());
                return (Map<String, ClassAnnotationModel>) in.readObject();
            } catch (Throwable t) {
                System.err.println("[-] Notice reading annotation cache: " + t.getMessage() + " -> re-extracting");
            }
        }

        Map<String, ClassAnnotationModel> result = new LinkedHashMap<>();
        if (apkFile == null || !apkFile.exists()) return result;

        long start = System.currentTimeMillis();
        System.out.println("[*] [AnnotationRecovery] Inspecting APK network stacks and extracting annotations via dexlib2...");

        try {
            MultiDexContainer<? extends DexFile> container = DexFileFactory.loadDexContainer(apkFile, Opcodes.getDefault());
            DetectionResult detection = new DetectionResult();
            Set<String> referencedTypes = new HashSet<>();

            // Pass 1: Detect network stacks and collect classes referenced in Retrofit methods
            for (String entryName : container.getDexEntryNames()) {
                DexFile dex = container.getEntry(entryName).getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    String t = cd.getType();
                    if (t.contains("Lretrofit2/Retrofit;") || t.startsWith("Lretrofit2/http/")) {
                        detection.hasRetrofit = true;
                        detection.stacks.add("Retrofit");
                    }
                    if (t.contains("Lcom/android/volley/RequestQueue;") || t.startsWith("Lcom/android/volley/")) {
                        detection.hasVolley = true;
                        detection.stacks.add("Volley");
                    }
                    if (t.contains("Lokhttp3/OkHttpClient;") || t.startsWith("Lokhttp3/")) {
                        detection.hasOkHttp = true;
                        detection.stacks.add("OkHttp");
                    }
                    if (t.contains("Lio/grpc/Channel;") || t.startsWith("Lio/grpc/")) {
                        detection.hasGrpc = true;
                        detection.stacks.add("gRPC");
                    }

                    // Check if this class defines any Retrofit HTTP methods
                    for (Method m : cd.getMethods()) {
                        boolean isHttp = false;
                        for (Annotation a : m.getAnnotations()) {
                            if (a.getType().startsWith("Lretrofit2/http/")) {
                                isHttp = true;
                                break;
                            }
                        }
                        if (isHttp) {
                            detection.hasRetrofit = true;
                            detection.stacks.add("Retrofit");
                            for (Annotation a : m.getAnnotations()) {
                                if ("Ldalvik/annotation/Signature;".equals(a.getType())) {
                                    String sig = extractSignature(a);
                                    extractClassTypesFromSignature(sig, referencedTypes);
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("[*] [NetworkDetector] Detected network stack(s): " + detection);

            if (!detection.hasRetrofit) {
                System.out.println("[*] [AnnotationRecovery] No Retrofit stack detected in APK. Skipping annotation recovery.");
                return result;
            }

            // Pass 2: Extract annotations, generic signatures, methods and fields
            for (String entryName : container.getDexEntryNames()) {
                DexFile dex = container.getEntry(entryName).getDexFile();
                for (ClassDef cd : dex.getClasses()) {
                    String t = cd.getType();
                    String internalName = t.substring(1, t.length() - 1);
                    boolean isAnnotationDef = (cd.getAccessFlags() & 0x2000) != 0;
                    boolean isCandidate = false;

                    String classSignature = null;
                    for (Annotation ca : cd.getAnnotations()) {
                        if ("Ldalvik/annotation/Signature;".equals(ca.getType())) {
                            classSignature = extractSignature(ca);
                            break;
                        }
                    }

                    String low = t.toLowerCase();
                    if (isAnnotationDef) {
                        if (t.startsWith("Lretrofit2/http/")) {
                            isCandidate = true;
                        }
                    } else if (t.equals("Lretrofit2/Call;") || t.equals("Lretrofit2/Response;")
                            || t.equals("Lio/reactivex/Observable;") || t.equals("Lio/reactivex/Single;")
                            || t.equals("Lio/reactivex/Maybe;") || t.equals("Lio/reactivex/Flowable;")
                            || t.equals("Lkotlin/coroutines/Continuation;")) {
                        isCandidate = true;
                    } else if (classSignature != null && (referencedTypes.contains(internalName)
                            || low.contains("/dto/") || low.contains("/model/") || low.contains("/data/")
                            || low.contains("/api/") || low.contains("/response/") || low.contains("/entity/")
                            || low.contains("/bean/") || low.contains("response") || low.contains("result")
                            || low.contains("airtel") || t.startsWith("Lkotlin/"))) {
                        isCandidate = true;
                    } else {
                        for (Method m : cd.getMethods()) {
                            for (Annotation a : m.getAnnotations()) {
                                if (a.getType().startsWith("Lretrofit2/http/")) {
                                    isCandidate = true;
                                    break;
                                }
                            }
                            if (isCandidate) break;
                        }
                    }

                    if (!isCandidate) continue;

                    ClassAnnotationModel cam = result.computeIfAbsent(internalName, ClassAnnotationModel::new);

                    // 1. Class annotations & Class signatures
                    if (classSignature != null) {
                        cam.signature = classSignature;
                    }
                    for (Annotation ca : cd.getAnnotations()) {
                        if ("Ldalvik/annotation/Signature;".equals(ca.getType())) {
                            continue;
                        }
                        AnnotationModel am = parseAnnotation(ca);
                        if (am != null) cam.classAnnotations.add(am);
                    }
                    if (cam.signature == null) {
                        if ("kotlin/coroutines/Continuation".equals(internalName)) {
                            cam.signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
                        } else if ("retrofit2/Call".equals(internalName)) {
                            cam.signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/lang/Cloneable;";
                        } else if ("retrofit2/Response".equals(internalName)) {
                            cam.signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
                        }
                    }

                    // 2. Field annotations & Signatures
                    for (Field f : cd.getFields()) {
                        FieldAnnotationModel fam = null;
                        for (Annotation fa : f.getAnnotations()) {
                            if ("Ldalvik/annotation/Signature;".equals(fa.getType())) {
                                if (fam == null) fam = cam.getOrCreateField(f.getName(), f.getType());
                                fam.signature = cleanSignature(extractSignature(fa));
                                continue;
                            }
                            AnnotationModel am = parseAnnotation(fa);
                            if (am != null) {
                                if (fam == null) fam = cam.getOrCreateField(f.getName(), f.getType());
                                fam.annotations.add(am);
                            }
                        }
                    }

                    // 3. Method annotations & Parameter annotations & Signatures
                    for (Method m : cd.getMethods()) {
                        String mName = m.getName();
                        String mDesc = buildMethodDescriptor(m);
                        MethodAnnotationModel mam = null;

                        for (Annotation ma : m.getAnnotations()) {
                            if ("Ldalvik/annotation/Signature;".equals(ma.getType())) {
                                if (mam == null) mam = cam.getOrCreateMethod(mName, mDesc);
                                mam.signature = cleanSignature(extractSignature(ma));
                                continue;
                            }
                            AnnotationModel am = parseAnnotation(ma);
                            if (am != null) {
                                if (mam == null) mam = cam.getOrCreateMethod(mName, mDesc);
                                mam.annotations.add(am);
                            }
                        }

                        int paramIdx = 0;
                        for (MethodParameter mp : m.getParameters()) {
                            for (Annotation pa : mp.getAnnotations()) {
                                AnnotationModel am = parseAnnotation(pa);
                                if (am != null) {
                                    if (mam == null) mam = cam.getOrCreateMethod(mName, mDesc);
                                    mam.paramAnnotations.computeIfAbsent(paramIdx, k -> new ArrayList<>()).add(am);
                                }
                            }
                            paramIdx++;
                        }
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[+] [AnnotationRecovery] Recovered annotations for " + result.size() + " Retrofit interface(s) across APK in " + elapsed + " ms.");

            if (cacheFile != null && !result.isEmpty()) {
                File parent = cacheFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)))) {
                    out.writeObject(result);
                    System.out.println("[+] [AnnotationRecovery] Successfully cached annotations to: " + cacheFile.getAbsolutePath());
                } catch (Throwable t) {
                    System.err.println("[-] Warning caching annotations: " + t.getMessage());
                }
            }

        } catch (Throwable t) {
            System.err.println("[-] Error recovering annotations: " + t.getMessage());
            t.printStackTrace();
        }

        return result;
    }

    public static String cleanSignature(String sig) {
        return sig;
    }

    private static void extractClassTypesFromSignature(String sig, Set<String> types) {
        if (sig == null) return;
        int len = sig.length();
        int idx = 0;
        while (idx < len) {
            int start = sig.indexOf('L', idx);
            if (start == -1) break;
            int end = sig.indexOf(';', start);
            if (end == -1) break;
            String type = sig.substring(start, end + 1);
            if (!type.contains("<") && !type.startsWith("Ljava/") && !type.startsWith("Landroid/")) {
                types.add(type);
            }
            idx = end + 1;
        }
    }

    private static String extractSignature(Annotation a) {
        if (a == null) return null;
        for (AnnotationElement el : a.getElements()) {
            if ("value".equals(el.getName())) {
                Object val = convertEncodedValue(el.getValue());
                if (val instanceof ArrayValue) {
                    StringBuilder sb = new StringBuilder();
                    for (Object item : ((ArrayValue) val).items) {
                        if (item != null) sb.append(item.toString());
                    }
                    return sb.toString();
                } else if (val instanceof String) {
                    return (String) val;
                }
            }
        }
        return null;
    }

    public static void applyRetrofitDefaults(AnnotationModel am) {
        if (am == null || am.desc == null) return;
        String d = am.desc;
        if (d.endsWith("/GET;") || d.endsWith("/POST;") || d.endsWith("/PUT;")
                || d.endsWith("/DELETE;") || d.endsWith("/PATCH;") || d.endsWith("/HEAD;")
                || d.endsWith("/OPTIONS;")) {
            if (!am.values.containsKey("value")) {
                am.values.put("value", "");
            }
        } else if (d.endsWith("/HTTP;")) {
            if (!am.values.containsKey("hasBody")) am.values.put("hasBody", false);
            if (!am.values.containsKey("path")) am.values.put("path", "");
        } else if (d.endsWith("/Query;") || d.endsWith("/QueryName;") || d.endsWith("/QueryMap;")
                || d.endsWith("/Field;") || d.endsWith("/FieldMap;") || d.endsWith("/Path;")) {
            if (!am.values.containsKey("encoded")) {
                am.values.put("encoded", false);
            }
        } else if (d.endsWith("/Part;")) {
            if (!am.values.containsKey("encoding")) am.values.put("encoding", "binary");
            if (!am.values.containsKey("value")) am.values.put("value", "");
        } else if (d.endsWith("/PartMap;")) {
            if (!am.values.containsKey("encoding")) am.values.put("encoding", "binary");
        }
    }

    private static AnnotationModel parseAnnotation(Annotation a) {
        if (a == null) return null;
        if (a.getType().startsWith("Ldalvik/annotation/")) return null;
        boolean isVisible = (a.getVisibility() == org.jf.dexlib2.AnnotationVisibility.RUNTIME);
        AnnotationModel am = new AnnotationModel(a.getType(), isVisible);
        for (AnnotationElement el : a.getElements()) {
            Object val = convertEncodedValue(el.getValue());
            if (val != null) {
                am.values.put(el.getName(), val);
            }
        }
        applyRetrofitDefaults(am);
        return am;
    }

    private static Object convertEncodedValue(EncodedValue ev) {
        if (ev == null) return null;
        if (ev instanceof StringEncodedValue) {
            return ((StringEncodedValue) ev).getValue();
        } else if (ev instanceof BooleanEncodedValue) {
            return ((BooleanEncodedValue) ev).getValue();
        } else if (ev instanceof IntEncodedValue) {
            return ((IntEncodedValue) ev).getValue();
        } else if (ev instanceof LongEncodedValue) {
            return ((LongEncodedValue) ev).getValue();
        } else if (ev instanceof FloatEncodedValue) {
            return ((FloatEncodedValue) ev).getValue();
        } else if (ev instanceof DoubleEncodedValue) {
            return ((DoubleEncodedValue) ev).getValue();
        } else if (ev instanceof ByteEncodedValue) {
            return ((ByteEncodedValue) ev).getValue();
        } else if (ev instanceof ShortEncodedValue) {
            return ((ShortEncodedValue) ev).getValue();
        } else if (ev instanceof CharEncodedValue) {
            return ((CharEncodedValue) ev).getValue();
        } else if (ev instanceof EnumEncodedValue) {
            FieldReference fr = ((EnumEncodedValue) ev).getValue();
            return new EnumValue(fr.getDefiningClass(), fr.getName());
        } else if (ev instanceof TypeEncodedValue) {
            return new TypeValue(((TypeEncodedValue) ev).getValue());
        } else if (ev instanceof ArrayEncodedValue) {
            ArrayValue av = new ArrayValue();
            for (EncodedValue item : ((ArrayEncodedValue) ev).getValue()) {
                Object c = convertEncodedValue(item);
                if (c != null) av.items.add(c);
            }
            return av;
        } else if (ev instanceof AnnotationEncodedValue) {
            AnnotationEncodedValue aev = (AnnotationEncodedValue) ev;
            NestedAnnotationValue nav = new NestedAnnotationValue(aev.getType());
            for (AnnotationElement el : aev.getElements()) {
                Object c = convertEncodedValue(el.getValue());
                if (c != null) nav.values.put(el.getName(), c);
            }
            return nav;
        }
        return null;
    }

    private static String buildMethodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (CharSequence pt : m.getParameterTypes()) {
            sb.append(pt);
        }
        sb.append(")").append(m.getReturnType());
        return sb.toString();
    }
}
