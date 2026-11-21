package org.headless.runner.core;

import android.content.SharedPreferences;

import java.io.*;
import java.util.*;

public class SharedPreferencesImpl implements SharedPreferences {
    private final File file;
    private final Map<String, Object> values = new HashMap<>();

    public SharedPreferencesImpl(File file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        if (file == null || !file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    // Re-infer the stored type: Android's SharedPreferencesImpl
                    // persists typed XML; this flat format loses the type, and
                    // apps that cast getAll()/entry values to Integer/Boolean
                    // crash with ClassCastException on the second launch.
                    values.put(k, coerce(v));
                }
            }
        } catch (Exception e) {
            System.err.println("[-] Error loading prefs (" + (file != null ? file.getAbsolutePath() : "null") + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Re-infer a persisted value's type (int / long / float / boolean / string). */
    private static Object coerce(String v) {
        if (v == null) return v;
        try { return Integer.valueOf(v); } catch (Exception ignored) {}
        try { return Long.valueOf(v); } catch (Exception ignored) {}
        try { return Double.valueOf(v); } catch (Exception ignored) {}
        if ("true".equalsIgnoreCase(v)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(v)) return Boolean.FALSE;
        return v;
    }

    private synchronized void save() {
        if (file == null) return;
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    writer.println(entry.getKey() + "=" + entry.getValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[-] Error saving prefs (" + (file != null ? file.getAbsolutePath() : "null") + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        Object val = values.get(key);
        return val != null ? val.toString() : defValue;
    }

    @Override
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        Object val = values.get(key);
        if (val == null) return defValue;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defValue; }
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        Object val = values.get(key);
        if (val == null) return defValue;
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return defValue; }
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        Object val = values.get(key);
        if (val == null) return defValue;
        try { return Float.parseFloat(val.toString()); } catch (Exception e) { return defValue; }
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        Object val = values.get(key);
        if (val == null) return defValue;
        return Boolean.parseBoolean(val.toString());
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new EditorImpl();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    public class EditorImpl implements Editor {
        private final Map<String, Object> tempValues = new HashMap<>();

        @Override
        public Editor putString(String key, String value) {
            tempValues.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            tempValues.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            tempValues.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            tempValues.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            tempValues.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            tempValues.put(key, null);
            return this;
        }

        @Override
        public Editor clear() {
            tempValues.clear();
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            synchronized (SharedPreferencesImpl.this) {
                for (Map.Entry<String, Object> entry : tempValues.entrySet()) {
                    if (entry.getValue() == null) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                save();
            }
        }
    }
}
