package org.headless.runner.core;

import android.content.res.XmlResourceParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyntheticXmlResourceParser implements XmlResourceParser {
    private static final Map<String, String> ROOT_TAG_CACHE = new ConcurrentHashMap<>();
    private final String mRootTag;
    private int mState = 0;

    public SyntheticXmlResourceParser(String rootTag) {
        this.mRootTag = rootTag;
    }

    @Override
    public int getDepth() {
        return (mState == 1 || mState == 2) ? 1 : 0;
    }

    @Override
    public int next() {
        if (mState == 0) {
            mState = 1;
            return START_TAG;
        } else if (mState == 1) {
            mState = 2;
            return END_TAG;
        } else {
            mState = 3;
            return END_DOCUMENT;
        }
    }

    @Override public int getEventType() {
        if (mState == 0) return START_DOCUMENT;
        if (mState == 1) return START_TAG;
        if (mState == 2) return END_TAG;
        return END_DOCUMENT;
    }

    @Override public String getName() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(st.length, 10); i++) {
            String cName = st[i].getClassName();
            if (cName.contains("MenuInflater")) {
                return "menu";
            }
            if (cName.equals("android.view.LayoutInflater") || cName.contains("LayoutInflater")) {
                for (int j = i + 1; j < Math.min(st.length, 12); j++) {
                    String caller = st[j].getClassName();
                    if (!caller.startsWith("android.view.") && !caller.startsWith("java.lang.")) {
                        return resolveRootTag(caller, st[j].getMethodName());
                    }
                }
            }
        }
        return mRootTag != null ? mRootTag : "FrameLayout";
    }

    private static String resolveRootTag(String callerClass, String callerMethod) {
        String key = callerClass + "#" + callerMethod;
        String cached = ROOT_TAG_CACHE.get(key);
        if (cached != null) return cached;

        try {
            String resPath = callerClass.replace('.', '/') + ".class";
            InputStream is = SyntheticXmlResourceParser.class.getClassLoader().getResourceAsStream(resPath);
            if (is != null) {
                ClassReader cr = new ClassReader(is);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        if (callerMethod != null && !callerMethod.equals(name)) return null;

                        return new MethodVisitor(Opcodes.ASM9) {
                            private boolean afterInflate = false;
                            private boolean afterFindChild = false;

                            @Override
                            public void visitMethodInsn(int op, String owner, String mName, String mDesc, boolean itf) {
                                if ("inflate".equals(mName) && owner.contains("LayoutInflater")) {
                                    afterInflate = true;
                                }
                                if ("findChildViewById".equals(mName) || "findViewById".equals(mName)) {
                                    afterFindChild = true;
                                }
                            }

                            @Override
                            public void visitTypeInsn(int op, String type) {
                                if (op == Opcodes.CHECKCAST) {
                                    if (afterFindChild) {
                                        afterFindChild = false;
                                    } else if (afterInflate) {
                                        String cName = type.replace('/', '.');
                                        if (cName.startsWith("android.widget.")) {
                                            cName = cName.substring("android.widget.".length());
                                        }
                                        ROOT_TAG_CACHE.put(key, cName);
                                        afterInflate = false;
                                    }
                                }
                            }
                        };
                    }
                }, 0);
            }
        } catch (Throwable ignored) {}

        String result = ROOT_TAG_CACHE.get(key);
        if (result == null) {
            result = "RelativeLayout";
            ROOT_TAG_CACHE.put(key, result);
        }
        return result;
    }

    @Override public String getNamespace() { return ""; }
    @Override public String getPrefix() { return ""; }
    @Override public boolean isEmptyElementTag() { return true; }
    @Override public int getAttributeCount() { return 0; }
    @Override public boolean isAttributeDefault(int index) { return false; }
    @Override public String getAttributeNamespace(int index) { return ""; }
    @Override public String getAttributeName(int index) { return ""; }
    @Override public String getAttributePrefix(int index) { return ""; }
    @Override public String getAttributeType(int index) { return "CDATA"; }
    @Override public String getAttributeValue(int index) { return ""; }
    @Override public String getAttributeValue(String namespace, String name) { return null; }
    @Override public String getPositionDescription() { return ""; }
    @Override public int getLineNumber() { return 1; }
    @Override public int getColumnNumber() { return 1; }
    @Override public boolean isWhitespace() { return false; }
    @Override public String getText() { return ""; }
    @Override public char[] getTextCharacters(int[] holderForStartAndLength) { return new char[0]; }
    @Override public String getNamespace(String prefix) { return null; }
    @Override public int getNamespaceCount(int depth) { return 0; }
    @Override public String getNamespacePrefix(int pos) { return null; }
    @Override public String getNamespaceUri(int pos) { return null; }
    @Override public int nextToken() { return next(); }
    @Override public void require(int type, String namespace, String name) {}
    @Override public String nextText() { return ""; }
    @Override public int nextTag() { return next(); }
    @Override public void setInput(Reader in) {}
    @Override public void setInput(InputStream in, String inputEncoding) {}
    @Override public String getInputEncoding() { return "UTF-8"; }
    @Override public void setProperty(String name, Object value) {}
    @Override public Object getProperty(String name) { return null; }
    @Override public void setFeature(String name, boolean state) {}
    @Override public boolean getFeature(String name) { return false; }
    @Override public void defineEntityReplacementText(String entityName, String replacementText) {}

    // AttributeSet
    @Override public int getAttributeNameResource(int index) { return 0; }
    @Override public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) { return defaultValue; }
    @Override public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) { return defaultValue; }
    @Override public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) { return defaultValue; }
    @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) { return defaultValue; }
    @Override public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) { return defaultValue; }
    @Override public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) { return defaultValue; }
    @Override public int getAttributeListValue(int index, String[] options, int defaultValue) { return defaultValue; }
    @Override public boolean getAttributeBooleanValue(int index, boolean defaultValue) { return defaultValue; }
    @Override public int getAttributeResourceValue(int index, int defaultValue) { return defaultValue; }
    @Override public int getAttributeIntValue(int index, int defaultValue) { return defaultValue; }
    @Override public int getAttributeUnsignedIntValue(int index, int defaultValue) { return defaultValue; }
    @Override public float getAttributeFloatValue(int index, float defaultValue) { return defaultValue; }
    @Override public String getIdAttribute() { return null; }
    @Override public String getClassAttribute() { return null; }
    @Override public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
    @Override public int getStyleAttribute() { return 0; }

    @Override public void close() {}
}
