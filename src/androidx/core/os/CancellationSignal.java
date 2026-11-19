package androidx.core.os;

/**
 * AndroidX compatibility CancellationSignal implementation.
 * Required by FragmentManager / FragmentTransition during ViewPager page instantiation.
 */
public final class CancellationSignal {
    private boolean mIsCanceled;
    private OnCancelListener mOnCancelListener;
    private Object mCancellationSignalObj;
    private boolean mCancelInProgress;

    public interface OnCancelListener {
        void onCancel();
    }

    public CancellationSignal() {}

    public boolean isCanceled() {
        synchronized (this) {
            return mIsCanceled;
        }
    }

    public void throwIfCanceled() {
        if (isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    public void cancel() {
        synchronized (this) {
            if (mIsCanceled) return;
            mIsCanceled = true;
            mCancelInProgress = true;
        }
        try {
            if (mOnCancelListener != null) {
                mOnCancelListener.onCancel();
            }
        } finally {
            synchronized (this) {
                mCancelInProgress = false;
                notifyAll();
            }
        }
    }

    public void setOnCancelListener(OnCancelListener listener) {
        synchronized (this) {
            waitForCancelFinishedLocked();
            if (mOnCancelListener == listener) return;
            mOnCancelListener = listener;
            if (!mIsCanceled || listener == null) return;
        }
        listener.onCancel();
    }

    public Object getCancellationSignalObject() {
        synchronized (this) {
            if (mCancellationSignalObj == null) {
                mCancellationSignalObj = new android.os.CancellationSignal();
                if (mIsCanceled) {
                    ((android.os.CancellationSignal) mCancellationSignalObj).cancel();
                }
            }
            return mCancellationSignalObj;
        }
    }

    private void waitForCancelFinishedLocked() {
        while (mCancelInProgress) {
            try {
                wait();
            } catch (InterruptedException ignored) {}
        }
    }
}
