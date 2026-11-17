package android.os;

public class Process {
    public static final int INVALID_UID = -1;
    public static final int ROOT_UID = 0;
    public static final int SYSTEM_UID = 1000;
    public static final int PHONE_UID = 1001;
    public static final int SHELL_UID = 2000;
    public static final int FIRST_APPLICATION_UID = 10000;
    public static final int LAST_APPLICATION_UID = 19999;
    public static final int FIRST_ISOLATED_UID = 99000;
    public static final int LAST_ISOLATED_UID = 99999;

    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_LOWEST = 19;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_VIDEO = -10;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = 1;

    public static final int SIGNAL_QUIT = 3;
    public static final int SIGNAL_KILL = 9;
    public static final int SIGNAL_USR1 = 10;

    private static final int APP_UID = 10100;
    private static final int APP_PID = 10001;

    public static final int myUid() {
        return APP_UID;
    }

    public static final int myPid() {
        return APP_PID;
    }

    public static final int myPpid() {
        return 1;
    }

    public static final int myTid() {
        return (int) Thread.currentThread().getId();
    }

    public static UserHandle myUserHandle() {
        return UserHandle.of(0);
    }

    public static boolean isCoreUid(int uid) {
        return UserHandle.isCore(uid);
    }

    public static boolean isApplicationUid(int uid) {
        return UserHandle.isApp(uid);
    }

    public static final boolean isIsolated() {
        return true;
    }

    public static final boolean isIsolated(int uid) {
        return true;
    }

    public static final boolean is64Bit() {
        return true;
    }

    public static final void setThreadPriority(int priority) {}
    public static final void setThreadPriority(int tid, int priority) {}
    public static final int getThreadPriority(int tid) { return 0; }

    public static final void setThreadGroup(int tid, int group) {}
    public static final void setProcessGroup(int pid, int group) {}

    public static final long getElapsedCpuTime() {
        return System.currentTimeMillis();
    }

    public static final long getStartElapsedRealtime() {
        return 0;
    }

    public static final long getStartUptimeMillis() {
        return 0;
    }

    public static final long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    public static final long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    public static final void killProcess(int pid) {
        System.out.println("[*] Process.killProcess(" + pid + ") called.");
    }

    public static final void killProcessQuiet(int pid) {}
    public static final void sendSignal(int pid, int signal) {}
    public static final void sendSignalQuiet(int pid, int signal) {}
}
