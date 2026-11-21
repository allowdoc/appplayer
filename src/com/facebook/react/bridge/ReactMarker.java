package com.facebook.react.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReactMarker {
    public interface MarkerListener {
        void logMarker(ReactMarkerConstants name, String tag, int instanceKey);
    }

    public interface FabricMarkerListener {
        void logFabricMarker(ReactMarkerConstants name, String tag, int instanceKey, long timestamp);
    }

    private static final List<FabricMarkerListener> sFabricMarkerListeners = new ArrayList<>();
    private static final List<MarkerListener> sListeners = new ArrayList<>();
    private static Queue<Object> sNativeReactMarkerQueue = new ConcurrentLinkedQueue<>();

    public static void addFabricListener(FabricMarkerListener listener) {
        synchronized (sFabricMarkerListeners) {
            sFabricMarkerListeners.add(listener);
        }
    }

    public static void addListener(MarkerListener listener) {
        synchronized (sListeners) {
            sListeners.add(listener);
        }
    }

    public static void clearFabricMarkerListeners() {
        synchronized (sFabricMarkerListeners) {
            sFabricMarkerListeners.clear();
        }
    }

    public static void clearMarkerListeners() {
        synchronized (sListeners) {
            sListeners.clear();
        }
    }

    public static void logFabricMarker(ReactMarkerConstants name, String tag, int instanceKey) {}
    public static void logFabricMarker(ReactMarkerConstants name, String tag, int instanceKey, long timestamp) {}
    public static void logFabricMarker(ReactMarkerConstants name, String tag, int instanceKey, long timestamp, int code) {}

    public static void logMarker(ReactMarkerConstants name) {
        logMarker(name, null, 0);
    }

    public static void logMarker(ReactMarkerConstants name, int instanceKey) {
        logMarker(name, null, instanceKey);
    }

    public static void logMarker(ReactMarkerConstants name, long timestamp) {
        logMarker(name, null, 0);
    }

    public static void logMarker(ReactMarkerConstants name, String tag) {
        logMarker(name, tag, 0);
    }

    public static void logMarker(ReactMarkerConstants name, String tag, int instanceKey) {
        logMarker(name, tag, instanceKey, null);
    }

    public static void logMarker(ReactMarkerConstants name, String tag, int instanceKey, Long timestamp) {
        synchronized (sListeners) {
            for (MarkerListener l : sListeners) {
                try {
                    l.logMarker(name, tag, instanceKey);
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void logMarker(String name) {
        logMarker(name, null, 0);
    }

    public static void logMarker(String name, int instanceKey) {
        logMarker(name, null, instanceKey);
    }

    public static void logMarker(String name, String tag) {
        logMarker(name, tag, 0);
    }

    public static void logMarker(String name, String tag, int instanceKey) {}

    private static void nativeLogMarker(String name, long timestamp) {}

    private static void notifyNativeMarker(ReactMarkerConstants name, Long timestamp) {}

    public static void removeFabricListener(FabricMarkerListener listener) {
        synchronized (sFabricMarkerListeners) {
            sFabricMarkerListeners.remove(listener);
        }
    }

    public static void removeListener(MarkerListener listener) {
        synchronized (sListeners) {
            sListeners.remove(listener);
        }
    }
}
