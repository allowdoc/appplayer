package com.facebook.react.turbomodule.core.interfaces;

/**
 * Runner shim for the RN turbo-module JS call-invoker holder interface.
 * On a real device this interface is implemented by the JNI-backed
 * CallInvokerHolderImpl; in the pure-Java desktop runner the holder is a
 * no-op (the JS engine is a separate patched-Hermes process, calls are
 * routed through the stdio bridge instead of C++ JSI).
 */
public interface CallInvokerHolder {
}
