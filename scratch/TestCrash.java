package test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class TestCrash {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.vm.version", "2.1.0");
        android.os.Looper.prepareMainLooper();
        org.headless.runner.core.SyntheticServiceManager.initializeGlobalServices();
        File apkFile = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\headless-android\\base.apk");
        org.headless.runner.manifest.ApkInfo info = org.headless.runner.manifest.ManifestExtractor.extract(apkFile);
        File storageDir = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data");
        org.headless.runner.core.SyntheticContext context = new org.headless.runner.core.SyntheticContext(info, apkFile, storageDir);
        Class<?> appClass = Class.forName("com.myairtelapp.global.App");
        Object app = appClass.getDeclaredConstructor().newInstance();
        Method m = appClass.getDeclaredMethod("attachBaseContext", android.content.Context.class);
        m.setAccessible(true);
        m.invoke(app, context);

        Method appOnCreate = appClass.getMethod("onCreate");
        appOnCreate.invoke(app);
        
        Class<?> delegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate");
        Method setNight = delegateClass.getMethod("setDefaultNightMode", int.class);
        setNight.invoke(null, 1);

        String mainActivityName = "com.myairtelapp.home.views.activities.SplashScreenActivity";
        Class<?> actClass = Class.forName(mainActivityName);
        android.app.Activity act = (android.app.Activity) actClass.getDeclaredConstructor().newInstance();
        
        Constructor<android.app.ActivityThread> atCtor = android.app.ActivityThread.class.getDeclaredConstructor();
        atCtor.setAccessible(true);
        android.app.ActivityThread at = atCtor.newInstance();

        android.content.Intent launchIntent = new android.content.Intent();
        launchIntent.setClassName(info.getPackageName(), mainActivityName);

        android.content.pm.ActivityInfo actInfo = new android.content.pm.ActivityInfo();
        actInfo.packageName = info.getPackageName();
        actInfo.name = mainActivityName;
        actInfo.applicationInfo = context.getApplicationInfo();

        android.content.res.Configuration config = new android.content.res.Configuration();
        config.uiMode = android.content.res.Configuration.UI_MODE_TYPE_NORMAL | android.content.res.Configuration.UI_MODE_NIGHT_NO;

        org.headless.runner.core.SyntheticInstrumentation instrumentation = new org.headless.runner.core.SyntheticInstrumentation();
        org.headless.runner.core.ActivityStack.getInstance().initialize(
            context, at, (android.app.Application) app, instrumentation
        );
        
        Method attach = android.app.Activity.class.getDeclaredMethod("attach",
            android.content.Context.class, android.app.ActivityThread.class, android.app.Instrumentation.class,
            android.os.IBinder.class, int.class, android.app.Application.class, android.content.Intent.class,
            android.content.pm.ActivityInfo.class, CharSequence.class, android.app.Activity.class, String.class,
            Class.forName("android.app.Activity$NonConfigurationInstances"),
            android.content.res.Configuration.class, String.class,
            Class.forName("com.android.internal.app.IVoiceInteractor"),
            android.view.Window.class,
            Class.forName("android.view.ViewRootImpl$ActivityConfigCallback"),
            android.os.IBinder.class
        );
        attach.setAccessible(true);
        attach.invoke(act, context, at, instrumentation,
            new android.os.Binder(), 0, (android.app.Application) app, launchIntent, actInfo,
            mainActivityName, null, "id", null, config, "ref", null, null, null, new android.os.Binder()
        );
        org.headless.runner.core.ActivityStack.getInstance().pushInitialActivity(act);

        System.out.println("[*] Calling SplashScreenActivity.onCreate directly...");
        Method onCreate = actClass.getMethod("onCreate", android.os.Bundle.class);
        onCreate.invoke(act, (android.os.Bundle) null);
        System.out.println("[+] SplashScreenActivity.onCreate FINISHED!");

        Method performStart = android.app.Activity.class.getDeclaredMethod("performStart", String.class);
        performStart.setAccessible(true);
        performStart.invoke(act, "launch");

        Method performResume = android.app.Activity.class.getDeclaredMethod("performResume", boolean.class, String.class);
        performResume.setAccessible(true);
        performResume.invoke(act, false, "launch");

        System.out.println("[*] Dumping UI tree:");
        String tree = org.headless.runner.ui.HeadlessUiTree.dump(act);
        System.out.println(tree);

        System.out.println("[*] Capturing screen to file:");
        File screenFile = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\screen.png");
        org.headless.runner.ui.HeadlessScreenCapture.capture(act, screenFile);
        System.out.println("[+] Screen file created: " + screenFile.exists() + ", size=" + screenFile.length());
        System.out.flush();
    }
}
