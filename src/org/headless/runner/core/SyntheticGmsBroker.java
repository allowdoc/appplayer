package org.headless.runner.core;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.Method;

/**
 * Universal In-Process Google Play Services Broker Shim.
 * Zero external GmsCore APK / Zero background daemons.
 * Provides instant SUCCESS (ConnectionResult.SUCCESS = 0) to all Google Play Services
 * clients (Location, Auth, FCM, Ads, PlayIntegrity, Analytics) across any Android APK.
 */
public class SyntheticGmsBroker extends Binder {
    private static final String DESCRIPTOR = "com.google.android.gms.common.internal.IGmsServiceBroker";
    private static final int TRANSACTION_GET_SERVICE = 46;

    private static final SyntheticGmsBroker sInstance = new SyntheticGmsBroker();

    public static SyntheticGmsBroker getInstance() {
        return sInstance;
    }

    public SyntheticGmsBroker() {
        attachInterface(new IInterface() {
            @Override
            public IBinder asBinder() {
                return SyntheticGmsBroker.this;
            }
        }, DESCRIPTOR);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == TRANSACTION_GET_SERVICE) {
            try {
                data.enforceInterface(DESCRIPTOR);
                IBinder callbacksBinder = data.readStrongBinder();
                int serviceId = 0;
                if (data.dataAvail() > 0) {
                    serviceId = data.readInt();
                }

                System.out.println("[+] [GmsBroker] Handshaking GMS Client (serviceId=" + serviceId + ") -> Dispatching ConnectionResult.SUCCESS (0)");

                if (callbacksBinder != null) {
                    dispatchPostInitComplete(callbacksBinder, serviceId);
                }

                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
            } catch (Throwable t) {
                System.err.println("[-] Error handling GmsBroker.onTransact: " + t.getMessage());
                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
            }
        }

        // Generic fallback for any other broker transaction codes
        if (reply != null) {
            reply.writeNoException();
        }
        return super.onTransact(code, data, reply, flags);
    }

    private void dispatchPostInitComplete(IBinder callbacksBinder, int serviceId) {
        IBinder genericInternalService = new SyntheticGmsInternalService(serviceId);
        Bundle resBundle = new Bundle();
        resBundle.putInt("statusCode", 0);

        // Try direct reflection first if IGmsCallbacks interface exists
        try {
            IInterface localIf = callbacksBinder.queryLocalInterface("com.google.android.gms.common.internal.IGmsCallbacks");
            if (localIf != null) {
                Method m = localIf.getClass().getMethod("onPostInitComplete", int.class, IBinder.class, Bundle.class);
                m.invoke(localIf, 0, genericInternalService, resBundle);
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback: Send transaction 1 (onPostInitComplete) via Binder IPC
        try {
            Parcel pData = Parcel.obtain();
            Parcel pReply = Parcel.obtain();
            try {
                pData.writeInterfaceToken("com.google.android.gms.common.internal.IGmsCallbacks");
                pData.writeInt(0); // statusCode = 0 (SUCCESS)
                pData.writeStrongBinder(genericInternalService);
                resBundle.writeToParcel(pData, 0);
                callbacksBinder.transact(1, pData, pReply, 0);
                pReply.readException();
            } finally {
                pData.recycle();
                pReply.recycle();
            }
        } catch (Throwable t) {
            System.err.println("[-] Notice dispatching onPostInitComplete: " + t.getMessage());
        }
    }

    /**
     * Generic GMS Internal Service Binder that responds with SUCCESS to any client query.
     */
    public static class SyntheticGmsInternalService extends Binder {
        private final int serviceId;

        public SyntheticGmsInternalService(int serviceId) {
            this.serviceId = serviceId;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (reply != null) {
                reply.writeNoException();
                if (code == 1) {
                    // String getId() for IAdvertisingIdService
                    reply.writeString("38400000-8cf0-11bd-b23e-10b96e4ef00d");
                } else if (code == 2) {
                    // boolean isLimitAdTrackingEnabled() -> false (0)
                    reply.writeInt(0);
                }
            }
            return true;
        }
    }
}
