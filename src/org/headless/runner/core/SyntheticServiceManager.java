package org.headless.runner.core;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IAlarmManager;
import android.app.INotificationManager;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.IWifiManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.telephony.TelephonyManager;
import com.android.internal.app.IAppOpsService;
import com.android.internal.telephony.ITelephony;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

public class SyntheticServiceManager {
    private static final Map<String, Object> SERVICE_INSTANCES = new HashMap<>();
    private static final Map<Class<?>, String> CLASS_TO_NAME = new HashMap<>();
    private static Context sContext;

    public static void setContext(Context context) {
        sContext = context;
        SyntheticJobScheduler.initialize(context);

        // Register IPackageManager service in ServiceManager sCache and bind ActivityThread.sPackageManager
        try {
            Class<?> ipmClass = Class.forName("android.content.pm.IPackageManager");
            final android.content.pm.PackageManager pm = context.getPackageManager();
            android.os.IInterface packageProxy = (android.os.IInterface) createSyntheticProxy((Class) ipmClass, (p, m, args) -> {
                String name = m.getName();
                if ("getApplicationInfo".equals(name)) {
                    String pkg = (String) args[0];
                    int flags = (args.length > 1 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                    return pm.getApplicationInfo(pkg, flags);
                }
                if ("getPackageInfo".equals(name)) {
                    String pkg = (String) args[0];
                    int flags = (args.length > 1 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                    return pm.getPackageInfo(pkg, flags);
                }
                if ("getActivityInfo".equals(name)) {
                    android.content.ComponentName cn = (android.content.ComponentName) args[0];
                    int flags = (args.length > 1 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                    return pm.getActivityInfo(cn, flags);
                }
                if ("getServiceInfo".equals(name)) {
                    android.content.ComponentName cn = (android.content.ComponentName) args[0];
                    int flags = (args.length > 1 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                    return pm.getServiceInfo(cn, flags);
                }
                if ("getProviderInfo".equals(name)) {
                    android.content.ComponentName cn = (android.content.ComponentName) args[0];
                    int flags = (args.length > 1 && args[1] instanceof Integer) ? (Integer) args[1] : 0;
                    return pm.getProviderInfo(cn, flags);
                }
                if ("getPackageInstaller".equals(name)) {
                    Class<?> ipiClass = Class.forName("android.content.pm.IPackageInstaller");
                    return createSyntheticProxy((Class) ipiClass, (p2, m2, a2) -> null);
                }
                if ("hasSystemFeature".equals(name)) {
                    String feat = (String) args[0];
                    return pm.hasSystemFeature(feat);
                }
                if ("checkPermission".equals(name)) {
                    return android.content.pm.PackageManager.PERMISSION_GRANTED;
                }
                if ("checkSignatures".equals(name)) {
                    return android.content.pm.PackageManager.SIGNATURE_MATCH;
                }
                return null;
            });

            Field sCacheField = ServiceManager.class.getDeclaredField("sCache");
            sCacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, IBinder> sCache = (Map<String, IBinder>) sCacheField.get(null);
            if (sCache != null) {
                registerBinder(sCache, "package", packageProxy, ipmClass.getName());
            }

            try {
                Field spmField = android.app.ActivityThread.class.getDeclaredField("sPackageManager");
                spmField.setAccessible(true);
                spmField.set(null, packageProxy);
            } catch (Throwable ignored) {}

            System.out.println("[+] [IPackageManager] Synthetic IPackageManager proxy registered and wired to ActivityThread.");
        } catch (Throwable t) {
            System.err.println("[-] Notice registering IPackageManager: " + t.getMessage());
        }
    }

    static {
        CLASS_TO_NAME.put(UserManager.class, Context.USER_SERVICE);
        CLASS_TO_NAME.put(ConnectivityManager.class, Context.CONNECTIVITY_SERVICE);
        CLASS_TO_NAME.put(TelephonyManager.class, Context.TELEPHONY_SERVICE);
        CLASS_TO_NAME.put(WifiManager.class, Context.WIFI_SERVICE);
        CLASS_TO_NAME.put(PowerManager.class, Context.POWER_SERVICE);
        CLASS_TO_NAME.put(NotificationManager.class, Context.NOTIFICATION_SERVICE);
        CLASS_TO_NAME.put(ActivityManager.class, Context.ACTIVITY_SERVICE);
        CLASS_TO_NAME.put(AppOpsManager.class, Context.APP_OPS_SERVICE);
        CLASS_TO_NAME.put(android.view.WindowManager.class, Context.WINDOW_SERVICE);
        CLASS_TO_NAME.put(android.view.LayoutInflater.class, Context.LAYOUT_INFLATER_SERVICE);
        CLASS_TO_NAME.put(android.hardware.display.DisplayManager.class, Context.DISPLAY_SERVICE);
        CLASS_TO_NAME.put(android.location.LocationManager.class, Context.LOCATION_SERVICE);
        CLASS_TO_NAME.put(android.media.AudioManager.class, Context.AUDIO_SERVICE);
        CLASS_TO_NAME.put(android.app.KeyguardManager.class, Context.KEYGUARD_SERVICE);
        CLASS_TO_NAME.put(android.app.job.JobScheduler.class, Context.JOB_SCHEDULER_SERVICE);
        CLASS_TO_NAME.put(android.content.pm.ShortcutManager.class, Context.SHORTCUT_SERVICE);
        CLASS_TO_NAME.put(android.telephony.SubscriptionManager.class, Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    public static class AdaptiveServiceMap extends HashMap<String, IBinder> {
        @Override
        public IBinder get(Object key) {
            IBinder existing = super.get(key);
            if (existing != null) return existing;
            if (key instanceof String) {
                String name = (String) key;
                Binder b = new Binder(name);
                put(name, b);
                return b;
            }
            return null;
        }
    }

    public static String getServiceName(Class<?> serviceClass) {
        return CLASS_TO_NAME.get(serviceClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends IInterface> T createSyntheticProxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{iface},
            (proxy, method, args) -> {
                String mName = method.getName();
                if (mName.equals("asBinder")) {
                    Binder b = new Binder();
                    b.attachInterface((IInterface) proxy, iface.getName());
                    return b;
                }
                if (handler != null) {
                    try {
                        Object val = handler.invoke(proxy, method, args);
                        if (val != null) return val;
                    } catch (Throwable ignored) {}
                }

                Class<?> ret = method.getReturnType();
                if (ret.equals(boolean.class)) return true;
                if (ret.equals(int.class)) return 0;
                if (ret.equals(long.class)) return 0L;
                if (ret.equals(float.class)) return 1.0f;
                if (ret.equals(double.class)) return 1.0;
                if (ret.equals(short.class)) return (short) 0;
                if (ret.equals(byte.class)) return (byte) 0;
                if (ret.equals(char.class)) return ' ';
                if (ret.equals(String.class)) return "";
                if (ret.equals(List.class)) return Collections.emptyList();
                if (ret.equals(IBinder.class)) return new Binder();
                return null;
            }
        );
    }

    public static void initializeGlobalServices() {
        try {
            // Install In-Process AndroidKeyStore Provider
            SyntheticKeyStoreProvider.install();

            // Install SafeOs wrapper to prevent UnsatisfiedLinkError on Linux.statvfs / fstatvfs
            try {
                libcore.io.Libcore.os = new libcore.io.ForwardingOs(libcore.io.Libcore.os) {
                    @Override
                    public android.system.StructStatVfs statvfs(String path) {
                        return new android.system.StructStatVfs(4096, 4096, 10000000L, 5000000L, 5000000L, 1000000L, 500000L, 500000L, 0, 0, 255);
                    }
                    @Override
                    public android.system.StructStatVfs fstatvfs(java.io.FileDescriptor fd) {
                        return new android.system.StructStatVfs(4096, 4096, 10000000L, 5000000L, 5000000L, 1000000L, 500000L, 500000L, 0, 0, 255);
                    }
                };
                System.out.println("[+] [Libcore] ForwardingOs installed for statvfs / fstatvfs.");
            } catch (Throwable t) {
                System.err.println("[-] Notice installing ForwardingOs: " + t.getMessage());
            }

            Field cacheField = ServiceManager.class.getDeclaredField("sCache");
            cacheField.setAccessible(true);
            AdaptiveServiceMap sCache = new AdaptiveServiceMap();
            cacheField.set(null, sCache);

            // 1. User Service
            IUserManager userProxy = createSyntheticProxy(IUserManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("isUserUnlocked") || name.equals("isUserRunning") || name.equals("isUserUnlockingOrUnlocked")) {
                    return true;
                }
                if (name.equals("isQuietModeEnabled")) return false;
                return null;
            });
            registerBinder(sCache, Context.USER_SERVICE, userProxy, IUserManager.class.getName());

            // 2. Connectivity Service
            NetworkInfo connectedWifi = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
            connectedWifi.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);

            android.net.Network activeNetwork = new android.net.Network(100);

            android.net.NetworkCapabilities caps = new android.net.NetworkCapabilities();
            caps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            caps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            caps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED);
            caps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
            caps.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            caps.addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            caps.setLinkDownstreamBandwidthKbps(1048576);
            caps.setLinkUpstreamBandwidthKbps(1048576);

            android.net.LinkProperties linkProps = new android.net.LinkProperties();
            linkProps.setInterfaceName("wlan0");

            IConnectivityManager connProxy = createSyntheticProxy(IConnectivityManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("getActiveNetworkInfo") || name.equals("getNetworkInfo") || name.equals("getActiveNetworkInfoForUid") || name.equals("getNetworkInfoForUid")) {
                    return connectedWifi;
                }
                if (name.equals("getAllNetworkInfo")) {
                    return new NetworkInfo[]{connectedWifi};
                }
                if (name.equals("getActiveNetwork") || name.equals("getActiveNetworkForUid") || name.equals("getNetworkForType")) {
                    return activeNetwork;
                }
                if (name.equals("getAllNetworks")) {
                    return new android.net.Network[]{activeNetwork};
                }
                if (name.equals("getNetworkCapabilities")) {
                    return caps;
                }
                if (name.equals("getDefaultNetworkCapabilitiesForUser")) {
                    return new android.net.NetworkCapabilities[]{caps};
                }
                if (name.equals("getLinkProperties")) {
                    return linkProps;
                }
                if (name.equals("isActiveNetworkMetered")) return false;
                if (name.equals("isNetworkSupported")) return true;

                if (name.equals("requestNetwork") || name.equals("listenForNetwork")) {
                    android.net.NetworkRequest req = new android.net.NetworkRequest(caps, ConnectivityManager.TYPE_WIFI, 1, android.net.NetworkRequest.Type.REQUEST);
                    if (args != null) {
                        for (Object arg : args) {
                            if (arg instanceof Messenger) {
                                Messenger msgr = (Messenger) arg;
                                try {
                                    Message msg = Message.obtain();
                                    msg.what = 524290; // CALLBACK_AVAILABLE
                                    android.os.Bundle b = new android.os.Bundle();
                                    b.putParcelable(android.net.NetworkRequest.class.getSimpleName(), req);
                                    b.putParcelable(android.net.Network.class.getSimpleName(), activeNetwork);
                                    b.putParcelable(android.net.NetworkCapabilities.class.getSimpleName(), caps);
                                    b.putParcelable(android.net.LinkProperties.class.getSimpleName(), linkProps);
                                    msg.setData(b);
                                    msgr.send(msg);

                                    Message msgCap = Message.obtain();
                                    msgCap.what = 524294; // CALLBACK_CAP_CHANGED
                                    android.os.Bundle bCap = new android.os.Bundle();
                                    bCap.putParcelable(android.net.NetworkRequest.class.getSimpleName(), req);
                                    bCap.putParcelable(android.net.Network.class.getSimpleName(), activeNetwork);
                                    bCap.putParcelable(android.net.NetworkCapabilities.class.getSimpleName(), caps);
                                    msgCap.setData(bCap);
                                    msgr.send(msgCap);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                    return req;
                }
                return null;
            });
            registerBinder(sCache, Context.CONNECTIVITY_SERVICE, connProxy, IConnectivityManager.class.getName());

            // 3. Telephony Service
            DeviceProfile dp = DeviceProfile.get();
            dp.applyToSystemProperties();

            ITelephony phoneProxy = createSyntheticProxy(ITelephony.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("getSimState")) return dp.simState;
                if (name.equals("getNetworkOperator") || name.equals("getSimOperator")) return dp.networkOperator;
                if (name.equals("getNetworkOperatorName") || name.equals("getSimOperatorName")) return dp.networkOperatorName;
                if (name.equals("getSimCountryIso") || name.equals("getNetworkCountryIso")) return dp.countryIso;
                if (name.equals("getDataState")) return dp.dataState;
                return null;
            });
            registerBinder(sCache, Context.TELEPHONY_SERVICE, phoneProxy, ITelephony.class.getName());
            registerBinder(sCache, "phone", phoneProxy, ITelephony.class.getName());

            // 4. Power Service
            IPowerManager powerProxy = createSyntheticProxy(IPowerManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("isInteractive")) return true;
                if (name.equals("isPowerSaveMode") || name.equals("isDeviceIdleMode")) return false;
                return null;
            });
            registerBinder(sCache, Context.POWER_SERVICE, powerProxy, IPowerManager.class.getName());

            // 5. Wifi Service
            IWifiManager wifiProxy = createSyntheticProxy(IWifiManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("isWifiEnabled")) return true;
                if (name.equals("getWifiEnabledState")) return WifiManager.WIFI_STATE_ENABLED;
                return null;
            });
            registerBinder(sCache, Context.WIFI_SERVICE, wifiProxy, IWifiManager.class.getName());

            // 6. AppOps Service - Universal Permission Bypass
            IAppOpsService appOpsProxy = createSyntheticProxy(IAppOpsService.class, (p, m, args) -> {
                String name = m.getName();
                if (name.contains("check") || name.contains("note") || name.contains("start") || name.contains("Op")) {
                    return AppOpsManager.MODE_ALLOWED; // 0 = Always Granted
                }
                return 0;
            });
            registerBinder(sCache, Context.APP_OPS_SERVICE, appOpsProxy, IAppOpsService.class.getName());

            // 7. Notification Service
            INotificationManager notifProxy = createSyntheticProxy(INotificationManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("areNotificationsEnabled") || name.equals("areNotificationsEnabledForPackage")) {
                    return true;
                }
                return null;
            });
            registerBinder(sCache, Context.NOTIFICATION_SERVICE, notifProxy, INotificationManager.class.getName());

            // 8. Alarm Service
            IAlarmManager alarmProxy = createSyntheticProxy(IAlarmManager.class, (p, m, args) -> {
                String mName = m.getName();
                if (mName.startsWith("set")) {
                    android.app.PendingIntent pi = null;
                    Object listener = null;
                    long triggerAt = 0;
                    long interval = 0;
                    if (args != null) {
                        for (Object a : args) {
                            if (a instanceof android.app.PendingIntent) pi = (android.app.PendingIntent) a;
                            else if (a != null && a.getClass().getName().contains("IAlarmListener")) listener = a;
                            else if (a instanceof Long) {
                                long val = (Long) a;
                                if (val > 1000000000L) triggerAt = val;
                                else if (val > 0) interval = val;
                            }
                        }
                    }
                    long delay = Math.max(50, triggerAt - System.currentTimeMillis());
                    final android.app.PendingIntent fPi = pi;
                    final Object fListener = listener;
                    Runnable task = () -> {
                        try {
                            if (fPi != null) {
                                System.out.println("[+] [AlarmManager] Timer fired -> invoking PendingIntent");
                                fPi.send();
                            }
                            if (fListener != null) {
                                System.out.println("[+] [AlarmManager] Timer fired -> invoking IAlarmListener");
                                java.lang.reflect.Method da = fListener.getClass().getMethod("doAlarm", IBinder.class);
                                da.invoke(fListener, new Binder("alarm_complete"));
                            }
                        } catch (Throwable t) {
                            System.err.println("[-] Error in Alarm firing: " + t.getMessage());
                        }
                    };
                    if (interval > 0) {
                        HeadlessTaskDispatcher.getInstance().getScheduler().scheduleAtFixedRate(task, delay, interval, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } else {
                        HeadlessTaskDispatcher.getInstance().getScheduler().schedule(task, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
                return null;
            });
            registerBinder(sCache, Context.ALARM_SERVICE, alarmProxy, IAlarmManager.class.getName());

            // 8b. Keystore Service (IKeystoreService)
            try {
                Class<?> iksClass = Class.forName("android.security.keystore.IKeystoreService");
                android.os.IInterface keystoreProxy = (android.os.IInterface) createSyntheticProxy((Class) iksClass, (p, m, a) -> {
                    String name = m.getName();
                    if (name.equals("getState")) return 1; // 1 = UNLOCKED
                    if (name.equals("isUnlocked")) return 1;
                    if (name.equals("isEmpty")) return 0;
                    if (m.getReturnType() == int.class) return 1; // 1 = NO_ERROR
                    return null;
                });
                registerBinder(sCache, "android.security.keystore", keystoreProxy, iksClass.getName());
            } catch (Throwable ignored) {}

            // 8c. JobScheduler Service (IJobScheduler)
            try {
                android.app.job.IJobScheduler jsProxy = SyntheticJobScheduler.createProxy();
                registerBinder(sCache, Context.JOB_SCHEDULER_SERVICE, jsProxy, "android.app.job.IJobScheduler");
                registerBinder(sCache, "jobscheduler", jsProxy, "android.app.job.IJobScheduler");
                System.out.println("[+] [JobScheduler] In-Process JobScheduler service registered.");
            } catch (Throwable t) {
                System.err.println("[-] Error registering JobScheduler: " + t);
            }

            // 9. Activity Service & Task Manager
            IActivityManager activityProxy = createSyntheticProxy(IActivityManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("finishActivity")) {
                    android.os.IBinder token = (args != null && args.length > 0 && args[0] instanceof android.os.IBinder) ? (android.os.IBinder) args[0] : null;
                    android.app.Activity targetAct = ActivityStack.getInstance().getActivityByToken(token);
                    if (targetAct == null) targetAct = ActivityStack.getInstance().getTopActivity();
                    System.out.println("[*] [IActivityManager] finishActivity called for: " + (targetAct != null ? targetAct.getClass().getName() : "null"));
                    new Throwable("[DEBUG] IActivityManager.finishActivity caller trace").printStackTrace(System.out);
                    if (targetAct != null) {
                        ActivityStack.getInstance().finishActivity(targetAct);
                    }
                    return true;
                }
                if (name.equals("getIntentSender")) {
                    int type = (Integer) args[0];
                    android.content.Intent[] intents = (android.content.Intent[]) args[5];
                    android.content.Intent targetIntent = (intents != null && intents.length > 0) ? intents[0] : null;

                    Class<?> iisClass = Class.forName("android.content.IIntentSender");
                    return createSyntheticProxy((Class) iisClass, (p2, m2, a2) -> {
                        if ("send".equals(m2.getName())) {
                            if (targetIntent != null && sContext != null) {
                                if (type == 4) { // INTENT_SENDER_SERVICE
                                    HeadlessTaskDispatcher.getInstance().startService(sContext, targetIntent);
                                } else if (type == 1) { // INTENT_SENDER_BROADCAST
                                    HeadlessTaskDispatcher.getInstance().sendBroadcast(sContext, targetIntent);
                                } else if (type == 2) { // INTENT_SENDER_ACTIVITY
                                    ActivityStack.getInstance().launchActivity(targetIntent);
                                }
                            }
                            return 0;
                        }
                        return null;
                    });
                }
                return null;
            });
            registerBinder(sCache, Context.ACTIVITY_SERVICE, activityProxy, IActivityManager.class.getName());

            android.app.IActivityTaskManager taskProxy = createSyntheticProxy(android.app.IActivityTaskManager.class, (p, m, args) -> {
                String name = m.getName();
                System.out.println("[*] [IActivityTaskManager] method invoked: " + name);
                if (name.equals("getTaskForActivity")) {
                    return 1;
                }
                if (name.equals("finishActivity")) {
                    android.os.IBinder token = (args != null && args.length > 0 && args[0] instanceof android.os.IBinder) ? (android.os.IBinder) args[0] : null;
                    android.app.Activity targetAct = ActivityStack.getInstance().getActivityByToken(token);
                    if (targetAct == null) targetAct = ActivityStack.getInstance().getTopActivity();
                    System.out.println("[*] [IActivityManager] finishActivity called for: " + (targetAct != null ? targetAct.getClass().getName() : "null"));
                    new Throwable("[DEBUG] IActivityManager.finishActivity caller trace").printStackTrace(System.out);
                    if (targetAct != null) {
                        ActivityStack.getInstance().finishActivity(targetAct);
                    }
                    return true;
                }
                return null;
            });
            registerBinder(sCache, "activity_task", taskProxy, "android.app.IActivityTaskManager");

            // 10. Window Service
            android.view.IWindowManager windowProxy = createSyntheticProxy(android.view.IWindowManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("getCurrentAnimatorScale")) return 1.0f;
                if (name.equals("getDefaultDisplayRotation")) return 0;
                if (name.equals("getInitialDisplayDensity")) return 420;
                return null;
            });
            registerBinder(sCache, Context.WINDOW_SERVICE, windowProxy, android.view.IWindowManager.class.getName());

            // 11. UI Mode Service
            android.app.IUiModeManager uiModeProxy = createSyntheticProxy(android.app.IUiModeManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("getNightMode")) return 1; // MODE_NIGHT_NO
                return null;
            });
            registerBinder(sCache, Context.UI_MODE_SERVICE, uiModeProxy, android.app.IUiModeManager.class.getName());

            // 12. Display Service
            android.view.DisplayInfo defaultDisplayInfo = new android.view.DisplayInfo();
            defaultDisplayInfo.logicalWidth = 1080;
            defaultDisplayInfo.logicalHeight = 1920;
            defaultDisplayInfo.appWidth = 1080;
            defaultDisplayInfo.appHeight = 1920;
            defaultDisplayInfo.largestNominalAppWidth = 1080;
            defaultDisplayInfo.smallestNominalAppWidth = 1080;
            defaultDisplayInfo.largestNominalAppHeight = 1920;
            defaultDisplayInfo.smallestNominalAppHeight = 1920;
            defaultDisplayInfo.rotation = 0;
            defaultDisplayInfo.logicalDensityDpi = 420;
            android.view.Display.Mode defaultMode = new android.view.Display.Mode(0, 1080, 1920, 60.0f);
            defaultDisplayInfo.modeId = 0;
            defaultDisplayInfo.defaultModeId = 0;
            defaultDisplayInfo.supportedModes = new android.view.Display.Mode[]{defaultMode};

            android.hardware.display.IDisplayManager displayProxy = createSyntheticProxy(android.hardware.display.IDisplayManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("getDisplayInfo")) return defaultDisplayInfo;
                if (name.equals("getDisplayIds")) return new int[]{0};
                return null;
            });
            registerBinder(sCache, Context.DISPLAY_SERVICE, displayProxy, android.hardware.display.IDisplayManager.class.getName());
            registerBinder(sCache, "display", displayProxy, android.hardware.display.IDisplayManager.class.getName());

            // 13. Location Service
            android.location.ILocationManager locationProxy = createSyntheticProxy(android.location.ILocationManager.class, (p, m, args) -> {
                String name = m.getName();
                if (name.equals("isLocationEnabled") || name.equals("isLocationEnabledForUser") || name.equals("isProviderEnabled") || name.equals("isProviderEnabledForUser")) {
                    return true;
                }
                if (name.equals("getAllProviders")) {
                    return java.util.Arrays.asList("gps", "network", "passive", "fused");
                }
                if (name.equals("getLastLocation") || name.equals("getLastKnownLocation")) {
                    android.location.Location loc = new android.location.Location("gps");
                    loc.setLatitude(28.6139);
                    loc.setLongitude(77.2090);
                    loc.setTime(System.currentTimeMillis());
                    return loc;
                }
                return null;
            });
            registerBinder(sCache, Context.LOCATION_SERVICE, locationProxy, android.location.ILocationManager.class.getName());
            registerBinder(sCache, "location", locationProxy, android.location.ILocationManager.class.getName());

        } catch (Exception e) {
            System.err.println("[-] Warning: Failed to init synthetic services: " + e.getMessage());
        }
    }

    private static void registerBinder(Map<String, IBinder> sCache, String name, IInterface proxy, String descriptor) {
        Binder b = new Binder(descriptor);
        b.attachInterface(proxy, descriptor);
        sCache.put(name, b);
    }

    public static synchronized Object getService(Context context, String name) {
        Object existing = SERVICE_INSTANCES.get(name);
        if (existing != null) return existing;

        try {
            IBinder binder = ServiceManager.getService(name);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            if (Context.USER_SERVICE.equals(name)) {
                IUserManager proxy = IUserManager.Stub.asInterface(binder);
                UserManager um = new UserManager(context, proxy);
                SERVICE_INSTANCES.put(name, um);
                return um;
            } else if (Context.CONNECTIVITY_SERVICE.equals(name)) {
                IConnectivityManager proxy = IConnectivityManager.Stub.asInterface(binder);
                ConnectivityManager cm = new ConnectivityManager(context, proxy);
                SERVICE_INSTANCES.put(name, cm);
                return cm;
            } else if (Context.TELEPHONY_SERVICE.equals(name)) {
                TelephonyManager tm = new TelephonyManager(context);
                SERVICE_INSTANCES.put(name, tm);
                return tm;
            } else if (Context.POWER_SERVICE.equals(name)) {
                IPowerManager proxy = IPowerManager.Stub.asInterface(binder);
                PowerManager pm = new PowerManager(context, proxy, mainHandler);
                SERVICE_INSTANCES.put(name, pm);
                return pm;
            } else if (Context.WIFI_SERVICE.equals(name)) {
                IWifiManager proxy = IWifiManager.Stub.asInterface(binder);
                WifiManager wm = new WifiManager(context, proxy, Looper.getMainLooper());
                SERVICE_INSTANCES.put(name, wm);
                return wm;
            } else if (Context.NOTIFICATION_SERVICE.equals(name)) {
                Constructor<NotificationManager> ctor = NotificationManager.class.getDeclaredConstructor(Context.class, Handler.class);
                ctor.setAccessible(true);
                NotificationManager nm = ctor.newInstance(context, mainHandler);
                SERVICE_INSTANCES.put(name, nm);
                return nm;
            } else if (Context.ACTIVITY_SERVICE.equals(name)) {
                Constructor<ActivityManager> ctor = ActivityManager.class.getDeclaredConstructor(Context.class, Handler.class);
                ctor.setAccessible(true);
                ActivityManager am = ctor.newInstance(context, mainHandler);
                SERVICE_INSTANCES.put(name, am);
                return am;
            } else if (Context.APP_OPS_SERVICE.equals(name)) {
                IAppOpsService proxy = IAppOpsService.Stub.asInterface(binder);
                Constructor<AppOpsManager> ctor = AppOpsManager.class.getDeclaredConstructor(Context.class, IAppOpsService.class);
                ctor.setAccessible(true);
                AppOpsManager aom = ctor.newInstance(context, proxy);
                SERVICE_INSTANCES.put(name, aom);
                return aom;
            } else if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                org.headless.runner.core.SyntheticLayoutInflater inflater = new org.headless.runner.core.SyntheticLayoutInflater(context);
                SERVICE_INSTANCES.put(name, inflater);
                return inflater;
            } else if (Context.WINDOW_SERVICE.equals(name)) {
                android.view.WindowManagerImpl wm = new android.view.WindowManagerImpl(context);
                SERVICE_INSTANCES.put(name, wm);
                return wm;
            } else if (Context.DISPLAY_SERVICE.equals(name)) {
                android.hardware.display.DisplayManager dm = new android.hardware.display.DisplayManager(context);
                SERVICE_INSTANCES.put(name, dm);
                return dm;
            } else if (Context.LOCATION_SERVICE.equals(name) || "location".equals(name)) {
                android.location.ILocationManager proxy = android.location.ILocationManager.Stub.asInterface(binder);
                android.location.LocationManager lm = new android.location.LocationManager(context, proxy);
                SERVICE_INSTANCES.put(name, lm);
                return lm;
            } else if (Context.AUDIO_SERVICE.equals(name) || "audio".equals(name)) {
                android.media.AudioManager am = new android.media.AudioManager(context);
                SERVICE_INSTANCES.put(name, am);
                return am;
            } else if (Context.SENSOR_SERVICE.equals(name) || "sensor".equals(name)) {
                try {
                    android.hardware.SystemSensorManager sm = new android.hardware.SystemSensorManager(context, Looper.getMainLooper());
                    SERVICE_INSTANCES.put(name, sm);
                    return sm;
                } catch (Throwable t) {
                    System.err.println("[-] Notice creating SensorManager: " + t.getMessage());
                }
            } else if (Context.KEYGUARD_SERVICE.equals(name) || "keyguard".equals(name)) {
                Constructor<android.app.KeyguardManager> ctor = android.app.KeyguardManager.class.getDeclaredConstructor(Context.class);
                ctor.setAccessible(true);
                android.app.KeyguardManager km = ctor.newInstance(context);
                SERVICE_INSTANCES.put(name, km);
                return km;
            } else if (Context.JOB_SCHEDULER_SERVICE.equals(name) || "jobscheduler".equals(name)) {
                Constructor<android.app.JobSchedulerImpl> ctor = android.app.JobSchedulerImpl.class.getDeclaredConstructor(android.app.job.IJobScheduler.class);
                ctor.setAccessible(true);
                android.app.JobSchedulerImpl js = ctor.newInstance(org.headless.runner.core.SyntheticJobScheduler.createProxy());
                SERVICE_INSTANCES.put(name, js);
                return js;
            } else if (Context.SHORTCUT_SERVICE.equals(name) || "shortcut".equals(name)) {
                Constructor<android.content.pm.ShortcutManager> ctor = android.content.pm.ShortcutManager.class.getDeclaredConstructor(Context.class);
                ctor.setAccessible(true);
                android.content.pm.ShortcutManager sm = ctor.newInstance(context);
                SERVICE_INSTANCES.put(name, sm);
                return sm;
            } else if (Context.TELEPHONY_SUBSCRIPTION_SERVICE.equals(name) || "telephony_subscription_service".equals(name)) {
                Constructor<android.telephony.SubscriptionManager> ctor = android.telephony.SubscriptionManager.class.getDeclaredConstructor(Context.class);
                ctor.setAccessible(true);
                android.telephony.SubscriptionManager subMgr = ctor.newInstance(context);
                SERVICE_INSTANCES.put(name, subMgr);
                return subMgr;
            }
        } catch (Exception e) {
            System.err.println("[-] Warning: Failed to create system service '" + name + "': " + e.getMessage());
        }

        return null;
    }

    public static Object getServiceByClass(Context context, Class<?> serviceClass) {
        String name = getServiceName(serviceClass);
        if (name != null) {
            return getService(context, name);
        }
        return null;
    }
}
