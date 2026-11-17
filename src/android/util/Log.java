package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static final int LOG_ID_MAIN = 0;
    public static final int LOG_ID_RADIO = 1;
    public static final int LOG_ID_EVENTS = 2;
    public static final int LOG_ID_SYSTEM = 3;
    public static final int LOG_ID_CRASH = 4;

    private Log() {}

    private static String getPriorityLetter(int priority) {
        switch (priority) {
            case VERBOSE: return "V";
            case DEBUG:   return "D";
            case INFO:    return "I";
            case WARN:    return "W";
            case ERROR:   return "E";
            case ASSERT:  return "A";
            default:      return "?";
        }
    }

    public static int println_native(int bufID, int priority, String tag, String msg) {
        if ("SQLiteCursor".equals(tag) || "CursorWindowStats".equals(tag)) {
            return 1;
        }
        String level = getPriorityLetter(priority);
        System.out.println("[" + level + "/" + tag + "] " + msg);
        return 1;
    }

    public static int println(int priority, String tag, String msg) {
        return println_native(LOG_ID_MAIN, priority, tag, msg);
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static int v(String tag, String msg) {
        return println_native(LOG_ID_MAIN, VERBOSE, tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, VERBOSE, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int d(String tag, String msg) {
        return println_native(LOG_ID_MAIN, DEBUG, tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, DEBUG, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int i(String tag, String msg) {
        return println_native(LOG_ID_MAIN, INFO, tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, INFO, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int w(String tag, String msg) {
        return println_native(LOG_ID_MAIN, WARN, tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, WARN, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int w(String tag, Throwable tr) {
        return println_native(LOG_ID_MAIN, WARN, tag, getStackTraceString(tr));
    }

    public static int e(String tag, String msg) {
        return println_native(LOG_ID_MAIN, ERROR, tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, ERROR, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int wtf(String tag, String msg) {
        return println_native(LOG_ID_MAIN, ASSERT, tag, msg);
    }

    public static int wtf(String tag, Throwable tr) {
        return println_native(LOG_ID_MAIN, ASSERT, tag, getStackTraceString(tr));
    }

    public static int wtf(String tag, String msg, Throwable tr) {
        return println_native(LOG_ID_MAIN, ASSERT, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
