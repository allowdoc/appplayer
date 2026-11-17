package org.headless.runner.core;

import android.app.job.IJobScheduler;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.ParceledListSlice;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Process Synthetic IJobScheduler Implementation.
 * Eliminates missing JobScheduler service errors and executes scheduled
 * JobService tasks in-process on host JVM daemon threads.
 */
public class SyntheticJobScheduler {
    private static final Map<Integer, JobInfo> sPendingJobs = new ConcurrentHashMap<>();
    private static Context sContext;

    public static void initialize(Context context) {
        sContext = context;
    }

    public static IJobScheduler createProxy() {
        return SyntheticServiceManager.createSyntheticProxy(IJobScheduler.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("schedule".equals(name) || "scheduleAsPackage".equals(name) || "enqueue".equals(name)) {
                JobInfo jobInfo = null;
                if (args != null) {
                    for (Object a : args) {
                        if (a instanceof JobInfo) {
                            jobInfo = (JobInfo) a;
                            break;
                        }
                    }
                }
                if (jobInfo != null) {
                    sPendingJobs.put(jobInfo.getId(), jobInfo);
                    System.out.println("[+] [JobScheduler] Enqueued Job #" + jobInfo.getId() + " (service=" + 
                            (jobInfo.getService() != null ? jobInfo.getService().getClassName() : "null") + ")");
                    if (sContext != null) {
                        HeadlessTaskDispatcher.getInstance().dispatchJob(sContext, jobInfo);
                    }
                }
                return JobScheduler.RESULT_SUCCESS; // 1
            }
            if ("cancel".equals(name)) {
                if (args != null && args.length > 0 && args[0] instanceof Integer) {
                    sPendingJobs.remove((Integer) args[0]);
                }
                return null;
            }
            if ("cancelAll".equals(name)) {
                sPendingJobs.clear();
                return null;
            }
            if ("getAllPendingJobs".equals(name)) {
                List<JobInfo> list = new ArrayList<>(sPendingJobs.values());
                try {
                    return new ParceledListSlice<>(list);
                } catch (Throwable t) {
                    return null;
                }
            }
            if ("getPendingJob".equals(name)) {
                if (args != null && args.length > 0 && args[0] instanceof Integer) {
                    return sPendingJobs.get((Integer) args[0]);
                }
                return null;
            }
            if ("getStartedJobs".equals(name)) {
                return new ArrayList<>(sPendingJobs.values());
            }
            return null;
        });
    }
}
