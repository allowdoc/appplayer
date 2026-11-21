package androidx.arch.core.executor;

/**
 * COMPILE-TIME ONLY stub (never shipped, never on the runtime classpath).
 * Mirrors androidx.arch.core.executor.TaskExecutor so the engine can install a
 * main-thread delegate. Runtime uses the app's real class.
 */
public abstract class TaskExecutor {
    public abstract void executeOnDiskIO(Runnable runnable);
    public abstract void postToMainThread(Runnable runnable);
    public abstract boolean isMainThread();
}
