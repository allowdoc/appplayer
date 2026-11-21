package org.headless.runner.rn;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;

import java.util.ArrayList;
import java.util.List;

/**
 * Callback / Promise implementations that route back into the app's JS
 * through the Universal React Native stdio bridge.
 *
 * Callback ids follow the MessageQueue encoding:
 *   success id = (callID << 1) | 1,  fail id = (callID << 1)
 */
public final class JsCallbackBridge {

    private JsCallbackBridge() {}

    public static final class CallbackImpl implements Callback {
        private final int mCallbackId;
        private final UniversalReactBridge mBridge;
        private volatile boolean mInvoked = false;

        public CallbackImpl(int callbackId, UniversalReactBridge bridge) {
            mCallbackId = callbackId;
            mBridge = bridge;
        }

        @Override
        public void invoke(Object... args) {
            if (mInvoked || mCallbackId < 0) return;
            mInvoked = true;
            List<Object> list = new ArrayList<>();
            if (args != null) {
                for (Object a : args) list.add(a);
            }
            mBridge.invokeJsCallback(mCallbackId, list);
        }
    }

    public static final class PromiseImpl implements Promise {
        private final int mSuccessId;
        private final int mFailId;
        private final UniversalReactBridge mBridge;
        private volatile boolean mSettled = false;

        public PromiseImpl(int successId, int failId, UniversalReactBridge bridge) {
            mSuccessId = successId;
            mFailId = failId;
            mBridge = bridge;
        }

        @Override
        public void resolve(Object value) {
            if (mSettled || mSuccessId < 0) return;
            mSettled = true;
            List<Object> list = new ArrayList<>();
            list.add(value);
            mBridge.invokeJsCallback(mSuccessId, list);
        }

        @Override
        public void reject(String code, String message) {
            reject(code, message, (Throwable) null);
        }

        @Override
        public void reject(String code, Throwable cause) {
            reject(code, cause != null ? cause.getMessage() : null, cause);
        }

        @Override
        public void reject(String code, String message, Throwable cause) {
            if (mSettled || mFailId < 0) return;
            mSettled = true;
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            if (code != null && !code.isEmpty()) err.put("code", code);
            err.put("message", message != null ? message
                    : (cause != null && cause.getMessage() != null ? cause.getMessage() : "Error"));
            List<Object> list = new ArrayList<>();
            list.add(err);
            mBridge.invokeJsCallback(mFailId, list);
        }

        public void reject(Throwable cause) {
            reject(null, (Throwable) cause);
        }

        public void reject(String message) {
            reject(null, message, (Throwable) null);
        }

        // The userInfo overloads exist on some RN versions; declaring them
        // unconditionally is safe either way.
        public void reject(String message, com.facebook.react.bridge.WritableMap userInfo) {
            reject(null, message, (Throwable) null);
        }

        public void reject(String code, String stringValue, com.facebook.react.bridge.WritableMap userInfo) {
            reject(code, stringValue, (Throwable) null);
        }

        public void reject(Throwable cause, com.facebook.react.bridge.WritableMap userInfo) {
            reject(null, cause != null ? cause.getMessage() : null, cause);
        }

        public void reject(String code, Throwable throwable, com.facebook.react.bridge.WritableMap userInfo) {
            reject(code, throwable != null ? throwable.getMessage() : null, throwable);
        }

        public void reject(String code, String message, Throwable cause, com.facebook.react.bridge.WritableMap userInfo) {
            reject(code, message, cause);
        }
    }
}
