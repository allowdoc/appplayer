package com.facebook.react.devsupport;

public final class InspectorFlags {
    public static final InspectorFlags INSTANCE = new InspectorFlags();

    private InspectorFlags() {}

    public static boolean getFuseboxEnabled() {
        return false;
    }

    public static boolean getIsProfilingBuild() {
        return false;
    }
}
