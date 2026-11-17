package org.headless.runner.core;

import android.app.ActivityThread;
import android.app.Application;
import android.app.Service;
import android.app.job.IJobCallback;
import android.app.job.IJobService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Universal In-Process Headless Task Runner & Background Service Pipeline.
 * Zero VM / Zero QEMU. Executes real Android Services, JobServices, WorkManager tasks,
 * alarms, GMS connection requests, and broadcast receivers in-process on host JVM daemon worker pools.
 * Completely generic and universal for ANY APK.
 */
public class HeadlessTaskDispatcher {
    private static HeadlessTaskDispatcher sInstance;

    private final ExecutorService mWorkerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "HeadlessTaskWorker");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "HeadlessTimerWorker");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Service> mActiveServices = new ConcurrentHashMap<>();
    private final Map<ServiceConnection, BoundServiceRecord> mBoundServices = new ConcurrentHashMap<>();
    private final Map<BroadcastReceiver, IntentFilter> mReceivers = new ConcurrentHashMap<>();
    private final AtomicInteger mStartIdGen = new AtomicInteger(100);
    private final AtomicInteger mExecutedTaskCount = new AtomicInteger(0);

    private static class BoundServiceRecord {
        final Service service;
        final Intent intent;
        final ComponentName component;

        BoundServiceRecord(Service service, Intent intent, ComponentName component) {
            this.service = service;
            this.intent = intent;
            this.component = component;
        }
    }

    public static synchronized HeadlessTaskDispatcher getInstance() {
        if (sInstance == null) {
            sInstance = new HeadlessTaskDispatcher();
        }
        return sInstance;
    }

    public ExecutorService getWorkerPool() {
        return mWorkerPool;
    }

    public ScheduledExecutorService getScheduler() {
        return mScheduler;
    }

    public int getActiveServiceCount() {
        return mActiveServices.size();
    }

    public int getExecutedTaskCount() {
        return mExecutedTaskCount.get();
    }

    // =========================================================================
    // 1. Service Lifecycle Management (startService, bindService, stopService)
    // =========================================================================

    public ComponentName startService(Context context, Intent intent) {
        if (intent == null) return null;
        ComponentName comp = intent.getComponent();
        String className = comp != null ? comp.getClassName() : null;
        if (className == null && intent.getAction() != null) {
            className = intent.getAction();
        }
        if (className == null) return null;

        // Skip remote GmsCore APK startService requests
        if (className.contains("com.google.android.gms") && !className.contains(context.getPackageName())) {
            System.out.println("[+] [HeadlessTask] Intercepted external GMS startService: " + className);
            return new ComponentName("com.google.android.gms", className);
        }

        final String finalClassName = className;
        final int startId = mStartIdGen.incrementAndGet();

        mWorkerPool.submit(() -> {
            try {
                Service s = getOrCreateService(context, finalClassName);
                if (s != null) {
                    System.out.println("[+] [HeadlessTask] Service.onStartCommand: " + finalClassName + " (startId=" + startId + ")");
                    s.onStartCommand(intent, 0, startId);
                    mExecutedTaskCount.incrementAndGet();
                }
            } catch (Throwable t) {
                System.err.println("[-] Error in Service.onStartCommand (" + finalClassName + "): " + t.getMessage());
            }
        });

        return new ComponentName(context.getPackageName(), className);
    }

    public boolean bindService(Context context, Intent intent, ServiceConnection conn, int flags) {
        if (intent == null || conn == null) return false;

        ComponentName comp = intent.getComponent();
        String className = comp != null ? comp.getClassName() : null;
        String pkg = comp != null ? comp.getPackageName() : intent.getPackage();
        String action = intent.getAction();

        if (className == null && action != null) {
            className = action;
        }

        // Intercept Google Play Services binding and connect SyntheticGmsBroker
        boolean isGms = (pkg != null && (pkg.contains("com.google.android.gms") || pkg.contains("com.android.vending")))
                || (className != null && (className.contains("google") || className.contains("gms")))
                || (action != null && (action.contains("google") || action.contains("gms")));

        if (isGms) {
            final ComponentName finalComp = comp != null ? comp : new ComponentName("com.google.android.gms", className != null ? className : "GmsCoreService");
            String svcName = finalComp.getClassName();
            if (svcName.contains("ads.identifier") || svcName.contains("SessionLifecycleService")) {
                mWorkerPool.submit(() -> {
                    System.out.println("[+] [HeadlessTask] Connecting SyntheticGmsBroker to: " + finalComp.getClassName());
                    IBinder broker = SyntheticGmsBroker.getInstance();
                    mBoundServices.put(conn, new BoundServiceRecord(null, intent, finalComp));
                    mExecutedTaskCount.incrementAndGet();

                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            conn.onServiceConnected(finalComp, broker);
                            System.out.println("[+] [HeadlessTask] GmsBroker connected successfully to " + finalComp.getClassName());
                        } catch (Throwable t) {
                            System.err.println("[-] Error in onServiceConnected for GmsBroker: " + t);
                        }
                    });
                });
                return true;
            } else {
                // Honest state: SERVICE_MISSING / not installed on this headless instance.
                // Avoids half-connected infinite reconnect loop and wasted CPU/RAM.
                return false;
            }
        }

        if (className == null) return false;

        final String finalClassName = className;
        final ComponentName finalComp = comp != null ? comp : new ComponentName(context.getPackageName(), className);

        mWorkerPool.submit(() -> {
            try {
                Service s = getOrCreateService(context, finalClassName);
                if (s != null) {
                    IBinder binder = s.onBind(intent);
                    mBoundServices.put(conn, new BoundServiceRecord(s, intent, finalComp));
                    mExecutedTaskCount.incrementAndGet();

                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            conn.onServiceConnected(finalComp, binder);
                            System.out.println("[+] [HeadlessTask] Service connected: " + finalClassName);
                        } catch (Throwable t) {
                            System.err.println("[-] Error in onServiceConnected: " + t);
                        }
                    });
                }
            } catch (Throwable t) {
                System.err.println("[-] Error binding Service " + finalClassName + ": " + t.getMessage());
            }
        });
        return true;
    }

    public boolean stopService(Context context, Intent intent) {
        if (intent == null) return false;
        ComponentName comp = intent.getComponent();
        String className = comp != null ? comp.getClassName() : intent.getAction();
        if (className == null) return false;

        Service s = mActiveServices.remove(className);
        if (s != null) {
            try {
                s.onDestroy();
                System.out.println("[+] [HeadlessTask] Service destroyed: " + className);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public void unbindService(Context context, ServiceConnection conn) {
        if (conn == null) return;
        BoundServiceRecord record = mBoundServices.remove(conn);
        if (record != null) {
            try {
                if (record.service != null) {
                    record.service.onUnbind(record.intent);
                }
                conn.onServiceDisconnected(record.component);
                System.out.println("[+] [HeadlessTask] Service disconnected: " + record.component.getClassName());
            } catch (Throwable ignored) {}
        }
    }

    private synchronized Service getOrCreateService(Context context, String className) {
        Service s = mActiveServices.get(className);
        if (s == null) {
            try {
                Class<?> sCls = Class.forName(className);
                s = (Service) sCls.getDeclaredConstructor().newInstance();

                ActivityThread at = ActivityThread.currentActivityThread();
                Application app = context.getApplicationContext() instanceof Application
                        ? (Application) context.getApplicationContext() : null;

                Method attachMethod = Service.class.getDeclaredMethod(
                        "attach",
                        Context.class,
                        ActivityThread.class,
                        String.class,
                        IBinder.class,
                        Application.class,
                        Object.class
                );
                attachMethod.setAccessible(true);
                attachMethod.invoke(s, context, at, className, new Binder(className), app, null);

                s.onCreate();

                // Ensure JobServiceEngine mHandler is bound to Looper.getMainLooper()
                if (s instanceof JobService) {
                    try {
                        Field engineField = JobService.class.getDeclaredField("mEngine");
                        engineField.setAccessible(true);
                        Object engine = engineField.get(s);
                        if (engine != null) {
                            Class<?> engineCls = Class.forName("android.app.job.JobServiceEngine");
                            Field handlerField = engineCls.getDeclaredField("mHandler");
                            handlerField.setAccessible(true);
                            Class<?> handlerCls = Class.forName("android.app.job.JobServiceEngine$JobHandler");
                            Constructor<?> hCtor = handlerCls.getDeclaredConstructor(engineCls, Looper.class);
                            hCtor.setAccessible(true);
                            handlerField.set(engine, hCtor.newInstance(engine, Looper.getMainLooper()));
                        }
                    } catch (Throwable ignored) {}
                }

                mActiveServices.put(className, s);
                System.out.println("[+] [HeadlessTask] Service initialized: " + className);
            } catch (Throwable t) {
                System.err.println("[-] Error creating Service " + className + ": " + t);
                return null;
            }
        }
        return s;
    }

    // =========================================================================
    // 2. JobService & JobScheduler Execution Pipeline
    // =========================================================================

    public void dispatchJob(Context context, JobInfo jobInfo) {
        if (jobInfo == null || jobInfo.getService() == null) return;
        ComponentName comp = jobInfo.getService();
        String className = comp.getClassName();

        mWorkerPool.submit(() -> {
            try {
                // Create synthetic IJobCallback
                IJobCallback callback = new IJobCallback.Stub() {
                    @Override
                    public void acknowledgeStartMessage(int jobId, boolean ongoing) {}

                    @Override
                    public void acknowledgeStopMessage(int jobId, boolean ongoing) {}

                    @Override
                    public android.app.job.JobWorkItem dequeueWork(int jobId) { return null; }

                    @Override
                    public boolean completeWork(int jobId, int workId) { return true; }

                    @Override
                    public void jobFinished(int jobId, boolean reschedule) {
                        System.out.println("[+] [JobScheduler] Job #" + jobId + " finished (reschedule=" + reschedule + ")");
                        if (reschedule || jobInfo.isPeriodic()) {
                            long interval = Math.max(15000, jobInfo.getIntervalMillis());
                            mScheduler.schedule(() -> dispatchJob(context, jobInfo), interval, TimeUnit.MILLISECONDS);
                        }
                    }
                };

                // Instantiate JobParameters via reflection
                JobParameters params = createJobParameters(jobInfo, callback.asBinder());

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        Service baseSvc = getOrCreateService(context, className);
                        if (!(baseSvc instanceof JobService)) {
                            System.out.println("[-] [JobScheduler] Class " + className + " is not a JobService");
                            return;
                        }
                        JobService jobService = (JobService) baseSvc;
                        Intent bindIntent = new Intent();
                        bindIntent.setComponent(comp);
                        jobService.onBind(bindIntent);

                        System.out.println("[+] [JobScheduler] Executing Job #" + jobInfo.getId() + " on " + className +
                                " (Thread: " + Thread.currentThread().getName() + ", Looper: " + (Looper.myLooper() != null ? "active" : "null") + ")");
                        ActivityStack.ensureMainLooperOnThread();
                        jobService.onStartJob(params);
                        mExecutedTaskCount.incrementAndGet();
                    } catch (Throwable t) {
                        System.err.println("[-] Error running Job on main looper " + className + ": " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                System.err.println("[-] Error preparing JobService " + className + ": " + t.getMessage());
            }
        });
    }

    private JobParameters createJobParameters(JobInfo jobInfo, IBinder callback) {
        try {
            for (Constructor<?> ctor : JobParameters.class.getDeclaredConstructors()) {
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length > 0 && pTypes[0] == IBinder.class) {
                    ctor.setAccessible(true);
                    Object[] args = new Object[pTypes.length];
                    for (int i = 0; i < pTypes.length; i++) {
                        Class<?> pt = pTypes[i];
                        if (pt == boolean.class) args[i] = false;
                        else if (pt == int.class) args[i] = 0;
                        else if (pt == long.class) args[i] = 0L;
                        else if (pt == float.class) args[i] = 0.0f;
                        else if (pt == double.class) args[i] = 0.0;
                    }
                    args[0] = callback;
                    if (pTypes.length > 1 && (pTypes[1] == int.class || pTypes[1] == Integer.class)) {
                        args[1] = jobInfo != null ? jobInfo.getId() : 1001;
                    }
                    if (pTypes.length > 2 && pTypes[2] == PersistableBundle.class && jobInfo != null) {
                        args[2] = jobInfo.getExtras();
                    }
                    if (pTypes.length > 3 && pTypes[3] == Bundle.class && jobInfo != null) {
                        args[3] = jobInfo.getTransientExtras();
                    }
                    if (pTypes.length > 4 && pTypes[4] == android.content.ClipData.class && jobInfo != null) {
                        args[4] = jobInfo.getClipData();
                    }
                    if (pTypes.length > 5 && (pTypes[5] == int.class || pTypes[5] == Integer.class) && jobInfo != null) {
                        args[5] = jobInfo.getClipGrantFlags();
                    }
                    return (JobParameters) ctor.newInstance(args);
                }
            }
        } catch (Throwable t) {
            System.err.println("[-] Error creating JobParameters: " + t);
        }
        return null;
    }

    // =========================================================================
    // 3. In-Process BroadcastReceiver Dispatch Pipeline
    // =========================================================================

    public Intent registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        if (receiver != null && filter != null) {
            mReceivers.put(receiver, filter);
        }
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver receiver) {
        if (receiver != null) {
            mReceivers.remove(receiver);
        }
    }

    public void sendBroadcast(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        for (Map.Entry<BroadcastReceiver, IntentFilter> entry : mReceivers.entrySet()) {
            IntentFilter filter = entry.getValue();
            if (filter.hasAction(action)) {
                BroadcastReceiver r = entry.getKey();
                mWorkerPool.submit(() -> {
                    try {
                        r.onReceive(context, intent);
                        mExecutedTaskCount.incrementAndGet();
                    } catch (Throwable t) {
                        System.err.println("[-] Error in BroadcastReceiver for action " + action + ": " + t.getMessage());
                    }
                });
            }
        }
    }
}
