package okio;

import java.lang.reflect.Method;

public final class AsyncTimeout$Watchdog extends Thread {
    public AsyncTimeout$Watchdog() {
        super("Okio Watchdog");
        setDaemon(true);
    }

    @Override
    public void run() {
        Method awaitMethod = null;
        try {
            awaitMethod = AsyncTimeout.Companion.getClass().getMethod("awaitTimeout$okio");
        } catch (Throwable ignored) {}

        while (true) {
            try {
                AsyncTimeout timedOut = null;
                synchronized (AsyncTimeout.class) {
                    if (awaitMethod != null) {
                        try {
                            Object res = awaitMethod.invoke(AsyncTimeout.Companion);
                            if (res instanceof AsyncTimeout) {
                                timedOut = (AsyncTimeout) res;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (timedOut != null) {
                    timedOut.timedOut();
                } else {
                    Thread.sleep(100);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
