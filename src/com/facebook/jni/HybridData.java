package com.facebook.jni;

public class HybridData {
    public static class Destructor {
        private volatile long mNativePointer = 1L;
    }

    private final Destructor mDestructor = new Destructor();

    public boolean isValid() {
        return true;
    }

    public synchronized void resetNative() {
    }
}
