package android.os;

public final class SystemClock {
    private static final long START_NANO = System.nanoTime();
    private static final long START_TIME = System.currentTimeMillis();

    private SystemClock() {}

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    public static boolean setCurrentTimeMillis(long millis) {
        return false;
    }

    public static long uptimeMillis() {
        return (System.nanoTime() - START_NANO) / 1_000_000L;
    }

    public static long elapsedRealtime() {
        return (System.nanoTime() - START_NANO) / 1_000_000L;
    }

    public static long elapsedRealtimeNanos() {
        return System.nanoTime() - START_NANO;
    }

    public static long currentThreadTimeMillis() {
        return (System.nanoTime() - START_NANO) / 1_000_000L;
    }

    public static long currentThreadTimeMicro() {
        return (System.nanoTime() - START_NANO) / 1_000L;
    }

    public static long currentTimeMicro() {
        return System.currentTimeMillis() * 1000L;
    }
}
