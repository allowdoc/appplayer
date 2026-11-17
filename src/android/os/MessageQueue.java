package android.os;

import android.util.Log;
import android.util.Printer;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import java.io.FileDescriptor;
import java.util.ArrayList;

public final class MessageQueue {
    private static final String TAG = "MessageQueue";
    final boolean mQuitAllowed;
    Message mMessages;
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<>();
    private boolean mQuitting;
    private boolean mBlocked;
    private int mNextBarrierToken = 1;

    public interface IdleHandler {
        boolean queueIdle();
    }

    public interface OnFileDescriptorEventListener {
        int EVENT_INPUT = 1;
        int EVENT_OUTPUT = 2;
        int EVENT_ERROR = 4;
        int onFileDescriptorEvents(FileDescriptor fd, int events);
    }

    MessageQueue(boolean quitAllowed) {
        mQuitAllowed = quitAllowed;
    }

    public boolean isIdle() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            return mMessages == null || now < mMessages.when;
        }
    }

    public boolean isPolling() {
        synchronized (this) {
            return !mQuitting && mBlocked;
        }
    }

    public void addIdleHandler(IdleHandler handler) {
        if (handler == null) throw new NullPointerException("Can't add a null IdleHandler");
        synchronized (this) {
            mIdleHandlers.add(handler);
        }
    }

    public void removeIdleHandler(IdleHandler handler) {
        synchronized (this) {
            mIdleHandlers.remove(handler);
        }
    }

    public void addOnFileDescriptorEventListener(FileDescriptor fd, int events, OnFileDescriptorEventListener listener) {}
    public void removeOnFileDescriptorEventListener(FileDescriptor fd) {}

    public int drainPendingMessages() {
        int count = 0;
        final long now = SystemClock.uptimeMillis();
        while (count < 500) {
            Message msg = null;
            synchronized (this) {
                if (mMessages == null || mMessages.when > now) {
                    break;
                }
                msg = mMessages;
                mMessages = msg.next;
                msg.next = null;
                msg.markInUse();
            }
            if (msg != null) {
                try {
                    if (msg.target != null) {
                        msg.target.dispatchMessage(msg);
                    } else if (msg.callback != null) {
                        msg.callback.run();
                    }
                } catch (Throwable t) {
                    System.err.println("[-] Message dispatch notice: " + t);
                } finally {
                    msg.recycleUnchecked();
                }
                count++;
            }
        }
        return count;
    }

    Message next() {
        int pendingIdleHandlerCount = -1;
        int nextPollTimeoutMillis = 0;
        for (;;) {
            if (nextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }

            synchronized (this) {
                final long now = SystemClock.uptimeMillis();
                Message prevMsg = null;
                Message msg = mMessages;
                if (msg != null && msg.target == null) {
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {
                    if (now < msg.when) {
                        nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        mBlocked = false;
                        if (prevMsg != null) {
                            prevMsg.next = msg.next;
                        } else {
                            mMessages = msg.next;
                        }
                        msg.next = null;
                        msg.markInUse();
                        return msg;
                    }
                } else {
                    nextPollTimeoutMillis = -1;
                }

                if (mQuitting) {
                    return null;
                }

                mBlocked = true;
                try {
                    if (nextPollTimeoutMillis > 0) {
                        wait(nextPollTimeoutMillis);
                    } else if (nextPollTimeoutMillis < 0) {
                        wait();
                    }
                } catch (InterruptedException ignored) {}
            }
        }
    }

    void quit(boolean safe) {
        if (!mQuitAllowed) {
            throw new IllegalStateException("Main thread not allowed to quit.");
        }
        synchronized (this) {
            if (mQuitting) return;
            mQuitting = true;
            removeAllMessagesLocked();
            notifyAll();
        }
    }

    public int postSyncBarrier() {
        return postSyncBarrier(SystemClock.uptimeMillis());
    }

    private int postSyncBarrier(long when) {
        synchronized (this) {
            final int token = mNextBarrierToken++;
            final Message msg = Message.obtain();
            msg.markInUse();
            msg.when = when;
            msg.arg1 = token;

            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
            }
            if (prev != null) {
                msg.next = p;
                prev.next = msg;
            } else {
                msg.next = p;
                mMessages = msg;
            }
            return token;
        }
    }

    public void removeSyncBarrier(int token) {
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null && (p.target != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
            if (p != null) {
                final boolean needWake;
                if (prev != null) {
                    prev.next = p.next;
                    needWake = false;
                } else {
                    mMessages = p.next;
                    needWake = mMessages == null || mMessages.target != null;
                }
                p.recycleUnchecked();
                if (needWake && !mQuitting) {
                    notifyAll();
                }
            }
        }
    }

    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {
            throw new IllegalArgumentException("Message must have a target.");
        }
        if (msg.isInUse()) {
            throw new IllegalStateException(msg + " This message is already in use.");
        }

        synchronized (this) {
            if (mQuitting) {
                msg.recycle();
                return false;
            }

            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p;
                prev.next = msg;
            }

            if (needWake) {
                notifyAll();
            }
        }
        return true;
    }

    boolean hasMessages(Handler h, int what, Object object) {
        if (h == null) return false;
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.what == what && (object == null || p.obj == object)) return true;
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h, Runnable r, Object object) {
        if (h == null) return false;
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.callback == r && (object == null || p.obj == object)) return true;
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h) {
        if (h == null) return false;
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h) return true;
                p = p.next;
            }
            return false;
        }
    }

    void removeMessages(Handler h, int what, Object object) {
        if (h == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.target == h && p.what == what && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.target == h && n.what == what && (object == null || n.obj == object)) {
                    Message nn = n.next;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    void removeMessages(Handler h, Runnable r, Object object) {
        if (h == null || r == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.target == h && p.callback == r && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.target == h && n.callback == r && (object == null || n.obj == object)) {
                    Message nn = n.next;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    void removeCallbacksAndMessages(Handler h, Object token) {
        if (h == null) return;
        synchronized (this) {
            Message p = mMessages;
            while (p != null && p.target == h && (token == null || p.obj == token)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }
            while (p != null) {
                Message n = p.next;
                if (n != null && n.target == h && (token == null || n.obj == token)) {
                    Message nn = n.next;
                    n.recycleUnchecked();
                    p.next = nn;
                    continue;
                }
                p = n;
            }
        }
    }

    private void removeAllMessagesLocked() {
        Message p = mMessages;
        while (p != null) {
            Message n = p.next;
            p.recycleUnchecked();
            p = n;
        }
        mMessages = null;
    }

    void dump(Printer pw, String prefix, Handler h) {}
    void writeToProto(ProtoOutputStream proto, long fieldId) {}
}
