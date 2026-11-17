package org.headless.runner.tool.annotation;

import java.io.Serializable;
import java.util.*;

public class AnnotationDataStructures {

    public static class EnumValue implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String enumDesc;
        public final String enumName;

        public EnumValue(String enumDesc, String enumName) {
            this.enumDesc = enumDesc;
            this.enumName = enumName;
        }

        @Override
        public String toString() {
            return enumDesc + "->" + enumName;
        }
    }

    public static class ArrayValue implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<Object> items = new ArrayList<>();

        public ArrayValue() {}

        @Override
        public String toString() {
            return items.toString();
        }
    }

    public static class TypeValue implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String typeDesc;

        public TypeValue(String typeDesc) {
            this.typeDesc = typeDesc;
        }

        @Override
        public String toString() {
            return "Class[" + typeDesc + "]";
        }
    }

    public static class NestedAnnotationValue implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String desc;
        public final Map<String, Object> values = new LinkedHashMap<>();

        public NestedAnnotationValue(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return "@" + desc + "(" + values + ")";
        }
    }

    public static class AnnotationModel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String desc;
        public final boolean visible;
        public final Map<String, Object> values = new LinkedHashMap<>();

        public AnnotationModel(String desc, boolean visible) {
            this.desc = desc;
            this.visible = visible;
        }

        @Override
        public String toString() {
            return "@" + desc + "(" + values + ")";
        }
    }

    public static class FieldAnnotationModel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String name;
        public final String desc;
        public String signature;
        public final List<AnnotationModel> annotations = new ArrayList<>();

        public FieldAnnotationModel(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    public static class MethodAnnotationModel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String name;
        public final String desc;
        public String signature;
        public final List<AnnotationModel> annotations = new ArrayList<>();
        public final Map<Integer, List<AnnotationModel>> paramAnnotations = new LinkedHashMap<>();

        public MethodAnnotationModel(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    public static class ClassAnnotationModel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String className; // In internal JVM format: "com/example/MyApi"
        public String signature;
        public final List<AnnotationModel> classAnnotations = new ArrayList<>();
        public final Map<String, FieldAnnotationModel> fields = new LinkedHashMap<>(); // "name" -> model
        public final Map<String, MethodAnnotationModel> methods = new LinkedHashMap<>(); // "name:desc" -> model

        public ClassAnnotationModel(String className) {
            this.className = className;
        }

        public FieldAnnotationModel getOrCreateField(String name, String desc) {
            return fields.computeIfAbsent(name, k -> new FieldAnnotationModel(name, desc));
        }

        public MethodAnnotationModel getOrCreateMethod(String name, String desc) {
            String key = name + ":" + desc;
            return methods.computeIfAbsent(key, k -> new MethodAnnotationModel(name, desc));
        }
    }
}
