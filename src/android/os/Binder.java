package android.os;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public class Binder implements IBinder {
    private String mDescriptor;
    private IInterface mOwner;

    public Binder() {
        this(null);
    }

    public Binder(String descriptor) {
        mDescriptor = descriptor;
    }

    public void attachInterface(IInterface owner, String descriptor) {
        mOwner = owner;
        mDescriptor = descriptor;
    }

    public String getInterfaceDescriptor() {
        return mDescriptor;
    }

    public boolean pingBinder() {
        return true;
    }

    public boolean isBinderAlive() {
        return true;
    }

    public IInterface queryLocalInterface(String descriptor) {
        if (mOwner != null) {
            return mOwner;
        }
        if (descriptor != null && !descriptor.isEmpty()) {
            try {
                Class<?> iface = Class.forName(descriptor);
                if (iface.isInterface() && IInterface.class.isAssignableFrom(iface)) {
                    mOwner = (IInterface) java.lang.reflect.Proxy.newProxyInstance(
                        iface.getClassLoader(),
                        new Class<?>[]{iface},
                        (proxy, method, args) -> {
                            if (method.getName().equals("asBinder")) return Binder.this;
                            Class<?> ret = method.getReturnType();
                            if (ret.equals(boolean.class)) return true;
                            if (ret.equals(int.class)) return 0;
                            if (ret.equals(long.class)) return 0L;
                            if (ret.equals(String.class)) return "";
                            if (ret.equals(java.util.List.class)) return java.util.Collections.emptyList();
                            if (ret.equals(IBinder.class)) return new Binder();
                            return null;
                        }
                    );
                    return mOwner;
                }
            } catch (Throwable ignored) {}
        }
        if (mDescriptor != null && mDescriptor.equals(descriptor)) {
            return mOwner;
        }
        return null;
    }

    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        return false;
    }

    public void dump(FileDescriptor fd, String[] args) {}
    public void dumpAsync(FileDescriptor fd, String[] args) {}
    public void shellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args, ShellCallback callback, ResultReceiver resultReceiver) {}

    public final boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        return onTransact(code, data, reply, flags);
    }

    public void linkToDeath(DeathRecipient recipient, int flags) {}
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
        return true;
    }

    public static final int getCallingPid() {
        return (int) ProcessHandle.current().pid();
    }

    public static final int getCallingUid() {
        return 10000;
    }

    public static final int getCallingUidOrThrow() {
        return 10000;
    }

    public static final UserHandle getCallingUserHandle() {
        return Process.myUserHandle();
    }

    public static final long clearCallingIdentity() {
        return 0L;
    }

    public static final void restoreCallingIdentity(long token) {}

    public static final void setThreadStrictModePolicy(int policyMask) {}
    public static final int getThreadStrictModePolicy() { return 0; }
    public static final void flushPendingCommands() {}
    public static final void joinThreadPool() {}

    public static long clearCallingWorkSource() { return 0L; }
    public static void restoreCallingWorkSource(long token) {}
    public static long setCallingWorkSourceUid(int workSource) { return 0L; }
    public static int getCallingWorkSourceUid() { return 0; }
}
