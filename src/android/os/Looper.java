package android.os;

import android.util.Log;
import android.util.Printer;
import android.util.proto.ProtoOutputStream;

/**
 * Desktop-runner Looper (shadows the android-all framework class; bin/ comes
 * first on the classpath).
 *
 * Compiled from the AOSP-10 design with one deliberate accommodation for the
 * desktop environment: on Android, every main-queue message runs on the single
 * thread that called Looper.prepareMainLooper(), so androidx / kotlinx code
 * asserts identity with either
 *     Looper.myLooper() == Looper.getMainLooper()      (DefaultTaskExecutor)
 * or  Looper.getMainLooper().getThread() == current    (older flavors).
 *
 * In this runner the main queue can be drained by more than one host thread
 * (the boot main thread during synchronous lifecycle setup, the MainLooperPump
 * while the main thread is still busy, AWT-EDT input drains in SkiaPlayerWindow,
 * and the real main loop once AppLauncher reaches Looper.loop()). Every dispatch
 * of a MAIN-queue message goes through MessageQueue.drainPendingMessages() or
 * Looper.loop(), both of which bracket the dispatch with enterMainDispatch() /
 * exitMainDispatch(). While that flag is set, myLooper(), getThread() and
 * isCurrentThread() report MAIN identity for the dispatching thread - exactly
 * what a real Android main thread would observe.
 *
 * All dispatches are bracketed in THIS package, so javac emits correct
 * stack-map frames (no runtime bytecode surgery) and the class verifies clean
 * on a stock JVM.
 */
public final class Looper {
    private static final String TAG = "Looper";

    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    private static Looper sMainLooper;
    private static volatile Observer sObserver;

    /** True while the current thread is dispatching a main-queue message. */
    static final ThreadLocal<Boolean> sMainQueueDispatch = new ThreadLocal<Boolean>();

    static void enterMainDispatch() {
        sMainQueueDispatch.set(Boolean.TRUE);
    }

    static void exitMainDispatch() {
        sMainQueueDispatch.remove();
    }

    private static boolean inMainDispatch() {
        return Boolean.TRUE.equals(sMainQueueDispatch.get());
    }

    /** Set once the host main thread enters Looper.loop(); lets the
     *  MainLooperPump retire (single-consumer discipline after boot). */
    private static volatile boolean sMainLoopActive;

    /** @hide */
    public static boolean isMainLoopActive() {
        return sMainLoopActive;
    }

    final MessageQueue mQueue;
    final Thread mThread;
    private Printer mLogging;
    private long mTraceTag;
    private long mSlowDispatchThresholdMs;

    /** {@hide} */
    public interface Observer {
        void messageDispatched(long latencyMillis, Message msg);
        void messageFinished(Message msg);
    }

    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mThread = Thread.currentThread();
    }

    /**
     * Initialize the current thread as a looper.
     */
    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    /**
     * Initialize the current thread as a looper, marking it as an
     * application's main looper.
     */
    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
        // Register the main queue so MessageQueue.drainPendingMessages() can
        // bracket its dispatches with main-identity semantics.
        MessageQueue.sMainQueue = myLooper().mQueue;
    }

    /**
     * Returns the application's main looper, which lives in the main thread of
     * the application.
     */
    public static Looper getMainLooper() {
        synchronized (Looper.class) {
            return sMainLooper;
        }
    }

    /**
     * Control the observer of message dispatch (framework API {@hide}).
     */
    public static void setObserver(Observer observer) {
        sObserver = observer;
    }

    /**
     * Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        sMainLoopActive = true;
        final MessageQueue queue = me.mQueue;

        final long ident = Binder.clearCallingIdentity();

        for (;;) {
            Message msg = queue.next(); // might block
            if (msg == null) {
                return;
            }

            final Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }

            final Object observerSnapshot = sObserver;
            final long dispatchStart = (observerSnapshot != null)
                    ? SystemClock.uptimeMillis() : 0;

            enterMainDispatch();
            try {
                if (observerSnapshot != null) {
                    ((Observer) observerSnapshot).messageDispatched(dispatchStart, msg);
                }
                msg.target.dispatchMessage(msg);
                if (observerSnapshot != null) {
                    ((Observer) observerSnapshot).messageFinished(msg);
                }
            } finally {
                exitMainDispatch();
            }

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }

            // Make sure that during the course of dispatching the
            // identity of the thread wasn't corrupted.
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + ": " + msg.what);
            }

            msg.recycleUnchecked();
        }
    }

    /**
     * Return the {@link Looper} object associated with the current thread.
     * Returns null if the current thread is not running a looper, or the
     * thread is dispatching a message from the application's main queue
     * (desktop runner: the dispatching thread acts as the main thread for
     * the duration of the dispatch, exactly as on a device).
     */
    public static Looper myLooper() {
        if (inMainDispatch()) {
            return sMainLooper;
        }
        return sThreadLocal.get();
    }

    /**
     * Return the {@link MessageQueue} object associated with the current
     * thread. This must be called from a thread running a {@link Looper},
     * or a NullPointerException is thrown.
     */
    public static MessageQueue myQueue() {
        return myLooper().mQueue;
    }

    /**
     * Returns true if the current thread is this looper's thread.
     */
    public boolean isCurrentThread() {
        if (this == sMainLooper && inMainDispatch()) {
            return true;
        }
        return Thread.currentThread() == mThread;
    }

    /**
     * Control logging of messages when they are dispatched.
     */
    public void setMessageLogging(Printer printer) {
        mLogging = printer;
    }

    /** {@hide} */
    public void setTraceTag(long traceTag) {
        mTraceTag = traceTag;
    }

    /** {@hide} */
    public void setSlowLogThresholdMs(long slowLogThresholdMs) {
        mSlowDispatchThresholdMs = slowLogThresholdMs;
    }

    /**
     * Quits the looper.
     */
    public void quit() {
        mQueue.quit(false);
    }

    /**
     * Quit the looper safely.
     */
    public void quitSafely() {
        mQueue.quit(true);
    }

    /**
     * Return the Thread associated with this Looper. While the caller thread
     * is dispatching a message from the application's main queue, the main
     * looper's thread IS the dispatching thread (desktop main-identity rule).
     */
    public Thread getThread() {
        if (this == sMainLooper && inMainDispatch()) {
            return Thread.currentThread();
        }
        return mThread;
    }

    /**
     * Get this looper's message queue.
     */
    public MessageQueue getQueue() {
        return mQueue;
    }

    /**
     * Dumps the state of the looper for debugging purposes.
     */
    public void dump(Printer pw, String prefix) {
        pw.println(prefix + toString());
        pw.println(prefix + "mQueue=" + (mQueue == null ? "null" : "queued messages"));
    }

    /** {@hide} */
    public void dump(Printer pw, String prefix, Handler h) {
        dump(pw, prefix);
    }

    /** {@hide} */
    public void writeToProto(ProtoOutputStream proto, long fieldId) {
        // Desktop runner: no proto dump.
    }

    @Override
    public String toString() {
        return "Looper (" + mThread.getName() + ", tid " + mThread.getId()
                + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}
