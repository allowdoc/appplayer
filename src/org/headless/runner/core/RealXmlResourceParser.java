package org.headless.runner.core;

import android.content.res.XmlResourceParser;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;

public class RealXmlResourceParser implements XmlResourceParser {
    public static class Node {
        public String tag;
        public final Map<String, String> attributes = new LinkedHashMap<>();
        public final Map<String, Integer> resIds = new LinkedHashMap<>();
        public final Map<Integer, String> attrValues = new LinkedHashMap<>();
        public final Map<Integer, Integer> attrResIds = new LinkedHashMap<>();
        public final List<Node> children = new ArrayList<>();
        public Node parent;
    }

    private static class Event {
        final int type;
        final Node node;
        Event(int type, Node node) {
            this.type = type;
            this.node = node;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private int cursor = 0;
    private final Node root;

    public RealXmlResourceParser(Node root) {
        this.root = root;
        events.add(new Event(START_DOCUMENT, null));
        if (root != null) {
            flatten(root);
        }
        events.add(new Event(END_DOCUMENT, null));
    }

    private void flatten(Node node) {
        events.add(new Event(START_TAG, node));
        for (Node child : node.children) {
            flatten(child);
        }
        events.add(new Event(END_TAG, node));
    }

    public Node currentNode() {
        if (cursor >= 0 && cursor < events.size()) {
            return events.get(cursor).node;
        }
        return null;
    }

    @Override
    public int next() {
        if (cursor < events.size() - 1) {
            cursor++;
        }
        return getEventType();
    }

    @Override
    public int getEventType() {
        if (cursor >= 0 && cursor < events.size()) {
            return events.get(cursor).type;
        }
        return END_DOCUMENT;
    }

    @Override
    public String getName() {
        Node n = currentNode();
        return (n != null && n.tag != null) ? n.tag : "";
    }

    @Override
    public int getAttributeCount() {
        Node n = currentNode();
        return (n != null) ? n.attributes.size() : 0;
    }

    @Override
    public String getAttributeName(int index) {
        Node n = currentNode();
        if (n == null || index < 0 || index >= n.attributes.size()) return "";
        int i = 0;
        for (String k : n.attributes.keySet()) {
            if (i == index) return k;
            i++;
        }
        return "";
    }

    @Override
    public String getAttributeValue(int index) {
        Node n = currentNode();
        if (n == null || index < 0 || index >= n.attributes.size()) return "";
        int i = 0;
        for (String v : n.attributes.values()) {
            if (i == index) return v;
            i++;
        }
        return "";
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
        Node n = currentNode();
        if (n == null || name == null) return null;
        return n.attributes.get(name);
    }

    @Override
    public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        Node n = currentNode();
        if (n == null || attribute == null) return defaultValue;
        Integer resId = n.resIds.get(attribute);
        if (resId != null) return resId;

        String val = n.attributes.get(attribute);
        if (val != null && (val.startsWith("@0x") || val.startsWith("@ref/0x") || val.startsWith("@"))) {
            String hex = val.replace("@0x", "").replace("@ref/0x", "").replace("@", "");
            try {
                return (int) Long.parseLong(hex, 16);
            } catch (Throwable ignored) {}
        }
        return defaultValue;
    }

    @Override
    public int getAttributeResourceValue(int index, int defaultValue) {
        String name = getAttributeName(index);
        return getAttributeResourceValue(null, name, defaultValue);
    }

    @Override
    public int getIdAttributeResourceValue(int defaultValue) {
        return getAttributeResourceValue(null, "id", defaultValue);
    }

    @Override
    public String getIdAttribute() {
        return getAttributeValue(null, "id");
    }

    @Override
    public String getClassAttribute() {
        return getAttributeValue(null, "class");
    }

    @Override
    public int getStyleAttribute() {
        return getAttributeResourceValue(null, "style", 0);
    }

    @Override
    public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        String val = getAttributeValue(namespace, attribute);
        if (val == null) return defaultValue;
        try {
            if (val.startsWith("0x") || val.startsWith("#")) {
                return (int) Long.parseLong(val.replace("0x", "").replace("#", ""), 16);
            }
            return Integer.parseInt(val);
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    @Override
    public int getAttributeIntValue(int index, int defaultValue) {
        return getAttributeIntValue(null, getAttributeName(index), defaultValue);
    }

    @Override
    public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        String val = getAttributeValue(namespace, attribute);
        if (val == null) return defaultValue;
        try {
            return Float.parseFloat(val.replaceAll("[^0-9.-]", ""));
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    @Override
    public float getAttributeFloatValue(int index, float defaultValue) {
        return getAttributeFloatValue(null, getAttributeName(index), defaultValue);
    }

    @Override
    public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        String val = getAttributeValue(namespace, attribute);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    @Override
    public boolean getAttributeBooleanValue(int index, boolean defaultValue) {
        return getAttributeBooleanValue(null, getAttributeName(index), defaultValue);
    }

    @Override
    public int getDepth() {
        int d = 0;
        Node cur = currentNode();
        while (cur != null) {
            d++;
            cur = cur.parent;
        }
        return d;
    }

    @Override public String getNamespace() { return ""; }
    @Override public String getPrefix() { return ""; }
    @Override public boolean isEmptyElementTag() { 
        Node n = currentNode();
        return n != null && n.children.isEmpty();
    }
    @Override public boolean isAttributeDefault(int index) { return false; }
    @Override public String getAttributeNamespace(int index) { return ""; }
    @Override public String getAttributePrefix(int index) { return ""; }
    @Override public String getAttributeType(int index) { return "CDATA"; }
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
    @Override public int getAttributeNameResource(int index) { return 0; }
    @Override public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) { return defaultValue; }
    @Override public int getAttributeListValue(int index, String[] options, int defaultValue) { return defaultValue; }
    @Override public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) { return getAttributeIntValue(namespace, attribute, defaultValue); }
    @Override public int getAttributeUnsignedIntValue(int index, int defaultValue) { return getAttributeIntValue(index, defaultValue); }
    @Override public void close() {}
    @Override public void setFeature(String name, boolean state) {}
    @Override public boolean getFeature(String name) { return false; }
    @Override public void setProperty(String name, Object value) {}
    @Override public Object getProperty(String name) { return null; }
    @Override public void setInput(Reader in) {}
    @Override public void setInput(InputStream inputStream, String inputEncoding) {}
    @Override public String getInputEncoding() { return "UTF-8"; }
    @Override public void defineEntityReplacementText(String entityName, String replacementText) {}
}
