package com.facebook.react.turbomodule.core;

import com.facebook.react.turbomodule.core.interfaces.CallInvokerHolder;

/**
 * Runner shim for com.facebook.react.turbomodule.core.CallInvokerHolderImpl.
 *
 * Expo modules (expo.modules.kotlin.RuntimeContext.installJSIContext) cast
 * ReactContext.getJSCallInvokerHolder() to this concrete class before
 * installing JSI bindings. The real class is a final JNI hybrid with a
 * private constructor and cannot exist on the desktop; this pure-Java
 * stand-in satisfies the cast so expo falls back to its legacy bridge path,
 * which the universal stdio bridge fully implements.
 */
public class CallInvokerHolderImpl implements CallInvokerHolder {

    public CallInvokerHolderImpl() {
    }

    /** Best-effort descriptor for diagnostics. */
    @Override
    public String toString() {
        return "CallInvokerHolderImpl(no-op pure-Java runner shim)";
    }
}
