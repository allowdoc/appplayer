package com.facebook.react.turbomodule.core;

import com.facebook.react.turbomodule.core.interfaces.NativeMethodCallInvokerHolder;

/**
 * Runner shim for com.facebook.react.turbomodule.core.NativeMethodCallInvokerHolderImpl.
 * Satisfies `as NativeMethodCallInvokerHolderImpl` casts in app-side Kotlin
 * (expo-modules-core and RN turbo-module plumbing) on the pure-Java runner.
 */
public class NativeMethodCallInvokerHolderImpl implements NativeMethodCallInvokerHolder {

    public NativeMethodCallInvokerHolderImpl() {
    }

    @Override
    public String toString() {
        return "NativeMethodCallInvokerHolderImpl(no-op pure-Java runner shim)";
    }
}
