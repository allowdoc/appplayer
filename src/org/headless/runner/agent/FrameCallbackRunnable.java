package org.headless.runner.agent;

import android.view.Choreographer;

/**
 * Bridges a Choreographer frame callback onto the main looper. The runner's
 * ReactChoreographer.postFrameCallback transform wraps every registered frame
 * callback in this Runnable and posts it to the main looper, whose lifetime
 * outlives the context-creation thread (whose Choreographer would otherwise
 * swallow queued view operations forever).
 */
public final class FrameCallbackRunnable implements Runnable {
    private final Choreographer.FrameCallback mCallback;
    private final long mFrameTimeNanos;

    public FrameCallbackRunnable(Choreographer.FrameCallback callback, long frameTimeNanos) {
        mCallback = callback;
        mFrameTimeNanos = frameTimeNanos;
    }

    @Override
    public void run() {
        mCallback.doFrame(mFrameTimeNanos);
    }
}
