package androidx.arch.core.executor;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 * Mirrors androidx ArchTaskExecutor's getInstance()/setDelegate() surface.
 * Runtime uses the app's real class.
 */
public class ArchTaskExecutor extends TaskExecutor {
    private static volatile ArchTaskExecutor sInstance;
    private volatile TaskExecutor mDelegate;

    public static ArchTaskExecutor getInstance() {
        if (sInstance == null) {
            synchronized (ArchTaskExecutor.class) {
                if (sInstance == null) sInstance = new ArchTaskExecutor();
            }
        }
        return sInstance;
    }

    public void setDelegate(TaskExecutor delegate) { mDelegate = delegate; }

    @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
    @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
    @Override public boolean isMainThread() { return true; }
}
